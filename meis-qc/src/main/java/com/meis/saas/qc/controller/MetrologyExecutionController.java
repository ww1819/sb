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
@RequestMapping("/api/metrology/execution")
@RequiredArgsConstructor
public class MetrologyExecutionController {
    private final JdbcTemplate jdbc;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query, @RequestParam(required = false) String status) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "metrology_execution", "e"));
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
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM metrology_execution e" + where, Long.class, args.toArray());
        int offset = (query.getPage() - 1) * query.getSize();
        args.add(query.getSize());
        args.add(offset);
        var rows = jdbc.queryForList("""
                SELECT e.*, t.template_name, c.category_name, o.org_name, u.real_name AS assigned_inspector_name
                FROM metrology_execution e
                LEFT JOIN metrology_template t ON t.id = e.template_id
                LEFT JOIN metrology_category c ON c.id = e.category_id
                LEFT JOIN metrology_org o ON o.id = e.org_id
                LEFT JOIN sys_user u ON u.id = e.assigned_inspector_id
                """ + where + " ORDER BY e.created_at DESC LIMIT ? OFFSET ?", args.toArray());
        return Result.ok(new PageResult<>(rows, total, query.getPage(), query.getSize()));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("""
                SELECT e.*, t.template_name, c.category_name, o.org_name
                FROM metrology_execution e
                LEFT JOIN metrology_template t ON t.id = e.template_id
                LEFT JOIN metrology_category c ON c.id = e.category_id
                LEFT JOIN metrology_org o ON o.id = e.org_id
                WHERE e.id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "metrology_execution", "e"), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        Map<String, Object> result = new LinkedHashMap<>(rows.get(0));
        var items = jdbc.queryForList("""
                SELECT ei.*, d.dept_name
                FROM metrology_execution_item ei
                LEFT JOIN department d ON d.id = ei.dept_id
                WHERE ei.execution_id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "metrology_execution_item", "ei")
                + " ORDER BY ei.created_at", id);
        for (Map<String, Object> item : items) {
            UUID itemId = (UUID) item.get("id");
            item.put("results", jdbc.queryForList(
                    "SELECT * FROM metrology_execution_result WHERE execution_item_id = ?::uuid "
                            + SoftDeleteSupport.notDeletedClause(jdbc, "metrology_execution_result", null)
                            + " ORDER BY created_at", itemId));
        }
        result.put("items", items);
        return Result.ok(result);
    }

    @PostMapping("/{id}/start")
    @Transactional
    @OperationLog(module = "metrology", description = "开始计量执行")
    public Result<Map<String, Object>> start(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        Object executorId = body != null ? body.get("executor_id") : null;
        jdbc.update("""
                UPDATE metrology_execution SET status='in_progress', executor_id=?::uuid,
                execute_start_time=COALESCE(execute_start_time, NOW()), updated_at=NOW()
                WHERE id=?::uuid AND status IN ('pending','draft')
                """, executorId, id);
        jdbc.update("UPDATE metrology_execution_item SET status='in_progress', updated_at=NOW() WHERE execution_id=?::uuid AND status='pending'", id);
        return get(id);
    }

    @PostMapping("/item/{itemId}/complete")
    @Transactional
    @OperationLog(module = "metrology", description = "完成计量设备项")
    public Result<Map<String, Object>> completeItem(@PathVariable UUID itemId, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) body.getOrDefault("results", List.of());
        for (Map<String, Object> r : results) {
            if (r.get("id") != null) {
                jdbc.update("""
                    UPDATE metrology_execution_result SET result_value=?, result_status=?, remark=?, updated_at=NOW()
                    WHERE id=?::uuid
                    """, r.get("result_value"), r.getOrDefault("result_status", "pass"), r.get("remark"), r.get("id"));
            }
        }
        jdbc.update("""
                UPDATE metrology_execution_item SET status='completed', overall_result=?, certificate_no=?, certificate_url=?, cost=?, remark=?, updated_at=NOW()
                WHERE id=?::uuid
                """, body.getOrDefault("overall_result", "pass"), body.get("certificate_no"),
                body.get("certificate_url"), body.get("cost"), body.get("remark"), itemId);
        var execRows = jdbc.queryForList(
                "SELECT execution_id FROM metrology_execution_item WHERE id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "metrology_execution_item", null), itemId);
        if (!execRows.isEmpty()) {
            UUID execId = (UUID) execRows.get(0).get("execution_id");
            long pending = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM metrology_execution_item WHERE execution_id=?::uuid AND status <> 'completed'"
                            + SoftDeleteSupport.notDeletedClause(jdbc, "metrology_execution_item", null),
                    Long.class, execId);
            if (pending == 0) {
                jdbc.update("UPDATE metrology_execution SET status='completed', execute_end_time=NOW(), updated_at=NOW() WHERE id=?::uuid", execId);
                updatePlansAfterComplete(execId);
                syncLegacyRecord(itemId);
            }
            return get(execId);
        }
        throw new BizException(404, "execution item not found");
    }

    private void updatePlansAfterComplete(UUID execId) {
        var planIds = jdbc.queryForList("""
                SELECT DISTINCT plan_id FROM metrology_execution_item
                WHERE execution_id=?::uuid AND plan_id IS NOT NULL
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "metrology_execution_item", null), execId);
        for (Map<String, Object> row : planIds) {
            UUID planId = (UUID) row.get("plan_id");
            var plan = jdbc.queryForList(
                    "SELECT cycle_days FROM metrology_plan WHERE id=?::uuid "
                            + SoftDeleteSupport.notDeletedClause(jdbc, "metrology_plan", null), planId);
            if (plan.isEmpty()) continue;
            Integer cycleDays = (Integer) plan.get(0).get("cycle_days");
            LocalDate next = cycleDays != null && cycleDays > 0
                    ? LocalDate.now().plusDays(cycleDays)
                    : LocalDate.now().plusDays(365);
            jdbc.update("""
                    UPDATE metrology_plan SET last_calibrated_at=CURRENT_DATE, next_due_date=?, status='active', updated_at=NOW()
                    WHERE id=?::uuid
                    """, next, planId);
            jdbc.update("""
                    UPDATE medical_device SET last_calibration_date=CURRENT_DATE, next_calibration_date=?, updated_at=NOW()
                    WHERE id=(SELECT device_id FROM metrology_plan WHERE id=?::uuid
                    """ + SoftDeleteSupport.notDeletedClause(jdbc, "metrology_plan", null) + ")",
                    next, planId);
        }
    }

    private void syncLegacyRecord(UUID itemId) {
        var item = jdbc.queryForList("""
                SELECT ei.*, e.org_id, e.planned_date
                FROM metrology_execution_item ei
                JOIN metrology_execution e ON e.id = ei.execution_id
                WHERE ei.id=?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "metrology_execution_item", "ei")
                + SoftDeleteSupport.notDeletedClause(jdbc, "metrology_execution", "e"), itemId);
        if (item.isEmpty()) return;
        Map<String, Object> row = item.get(0);
        var org = jdbc.queryForList(
                "SELECT org_name FROM metrology_org WHERE id=?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "metrology_org", null), row.get("org_id"));
        String orgName = org.isEmpty() ? null : (String) org.get(0).get("org_name");
        var plan = jdbc.queryForList(
                "SELECT next_due_date FROM metrology_plan WHERE id=?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "metrology_plan", null), row.get("plan_id"));
        Object nextDue = plan.isEmpty() ? null : plan.get(0).get("next_due_date");
        jdbc.update("""
                INSERT INTO metrology_record (id, metrology_no, device_id, device_code, device_name, metrology_org,
                    scheduled_date, actual_date, next_due_date, certificate_no, certificate_url, result, cost, status)
                VALUES (?::uuid, ?, ?::uuid, ?, ?, ?, ?, CURRENT_DATE, ?, ?, ?, ?, ?, 'completed')
                """, UUID.randomUUID(), "MR" + System.currentTimeMillis(), row.get("device_id"), row.get("device_code"),
                row.get("device_name"), orgName, row.get("planned_date"), nextDue,
                row.get("certificate_no"), row.get("certificate_url"), row.get("overall_result"), row.get("cost"));
    }

    static String nextNo() {
        return "ME" + System.currentTimeMillis();
    }
}
