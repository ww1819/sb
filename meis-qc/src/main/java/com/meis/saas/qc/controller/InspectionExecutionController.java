package com.meis.saas.qc.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/inspect/execution")
@RequiredArgsConstructor
public class InspectionExecutionController {
    private final JdbcTemplate jdbc;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query, @RequestParam(required = false) String status) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "inspection_execution", "e"));
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            where.append(" AND e.status = ? ");
            args.add(status);
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String kw = "%" + query.getKeyword().trim() + "%";
            where.append(" AND (e.execution_no ILIKE ? OR e.remark ILIKE ?) ");
            args.add(kw);
            args.add(kw);
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM inspection_execution e" + where, Long.class, args.toArray());
        int offset = (query.getPage() - 1) * query.getSize();
        args.add(query.getSize());
        args.add(offset);
        var rows = jdbc.queryForList("""
                SELECT e.*, t.template_name, it.type_name AS inspection_type_name,
                       u.real_name AS assigned_inspector_name
                FROM inspection_execution e
                LEFT JOIN inspection_template t ON t.id = e.template_id
                LEFT JOIN inspection_type it ON it.id = e.inspection_type_id
                LEFT JOIN sys_user u ON u.id = e.assigned_inspector_id
                """ + where + " ORDER BY e.created_at DESC LIMIT ? OFFSET ?", args.toArray());
        return Result.ok(new PageResult<>(rows, total, query.getPage(), query.getSize()));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("""
                SELECT e.*, t.template_name, it.type_name AS inspection_type_name
                FROM inspection_execution e
                LEFT JOIN inspection_template t ON t.id = e.template_id
                LEFT JOIN inspection_type it ON it.id = e.inspection_type_id
                WHERE e.id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_execution", "e"), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        Map<String, Object> result = new LinkedHashMap<>(rows.get(0));
        var items = jdbc.queryForList("""
                SELECT ei.*, d.dept_name
                FROM inspection_execution_item ei
                LEFT JOIN department d ON d.id = ei.dept_id
                WHERE ei.execution_id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_execution_item", "ei")
                + " ORDER BY ei.created_at", id);
        for (Map<String, Object> item : items) {
            UUID itemId = (UUID) item.get("id");
            item.put("results", jdbc.queryForList(
                    "SELECT * FROM inspection_execution_result WHERE execution_item_id = ?::uuid "
                            + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_execution_result", null)
                            + " ORDER BY created_at",
                    itemId));
        }
        result.put("items", items);
        return Result.ok(result);
    }

    @PostMapping("/{id}/start")
    @Transactional
    @OperationLog(module = "inspect", description = "开始巡检执行")
    public Result<Map<String, Object>> start(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        Object executorId = body != null ? body.get("executor_id") : null;
        jdbc.update("""
                UPDATE inspection_execution SET status='in_progress', executor_id=?::uuid,
                execute_start_time=COALESCE(execute_start_time, NOW()), updated_at=NOW()
                WHERE id=?::uuid AND status IN ('pending','draft')
                """, executorId, id);
        jdbc.update("UPDATE inspection_execution_item SET status='in_progress', updated_at=NOW() WHERE execution_id=?::uuid AND status='pending'", id);
        return get(id);
    }

    @PostMapping("/item/{itemId}/complete")
    @Transactional
    @OperationLog(module = "inspect", description = "完成巡检设备项")
    public Result<Map<String, Object>> completeItem(@PathVariable UUID itemId, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) body.getOrDefault("results", List.of());
        for (Map<String, Object> r : results) {
            if (r.get("id") != null) {
                jdbc.update("""
                    UPDATE inspection_execution_result SET result_value=?, result_status=?, remark=?, updated_at=NOW()
                    WHERE id=?::uuid
                    """, r.get("result_value"), r.getOrDefault("result_status", "pass"), r.get("remark"), r.get("id"));
            }
        }
        jdbc.update("""
                UPDATE inspection_execution_item SET status='completed', overall_result=?, remark=?, updated_at=NOW()
                WHERE id=?::uuid
                """, body.getOrDefault("overall_result", "pass"), body.get("remark"), itemId);
        var execRows = jdbc.queryForList(
                "SELECT execution_id FROM inspection_execution_item WHERE id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_execution_item", null), itemId);
        if (!execRows.isEmpty()) {
            UUID execId = (UUID) execRows.get(0).get("execution_id");
            long pending = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM inspection_execution_item WHERE execution_id=?::uuid AND status <> 'completed'"
                            + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_execution_item", null),
                    Long.class, execId);
            if (pending == 0) {
                jdbc.update("UPDATE inspection_execution SET status='completed', execute_end_time=NOW(), updated_at=NOW() WHERE id=?::uuid", execId);
                updatePlansAfterComplete(execId);
            }
            return get(execId);
        }
        throw new BizException(404, "execution item not found");
    }

    private void updatePlansAfterComplete(UUID execId) {
        var planIds = jdbc.queryForList("""
                SELECT DISTINCT plan_id FROM inspection_execution_item
                WHERE execution_id=?::uuid AND plan_id IS NOT NULL
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_execution_item", null), execId);
        for (Map<String, Object> row : planIds) {
            UUID planId = (UUID) row.get("plan_id");
            var plan = jdbc.queryForList(
                    "SELECT cycle_days, next_due_date FROM inspection_plan WHERE id=?::uuid "
                            + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_plan", null), planId);
            if (plan.isEmpty()) continue;
            Integer cycleDays = (Integer) plan.get(0).get("cycle_days");
            LocalDate next = cycleDays != null && cycleDays > 0
                    ? LocalDate.now().plusDays(cycleDays)
                    : LocalDate.now().plusDays(7);
            jdbc.update("""
                    UPDATE inspection_plan SET last_inspected_at=CURRENT_DATE, next_due_date=?, status='active', updated_at=NOW()
                    WHERE id=?::uuid
                    """, next, planId);
        }
    }

    static String nextNo() {
        return "IE" + System.currentTimeMillis();
    }
}
