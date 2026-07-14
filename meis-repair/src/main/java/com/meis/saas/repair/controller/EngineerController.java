package com.meis.saas.repair.controller;

import com.meis.saas.common.audit.EntityChangeLogService;
import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/repair/engineer")
@RequiredArgsConstructor
public class EngineerController {
    private static final String UUID_PATH =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    private final JdbcTemplate jdbc;
    private final EntityChangeLogService changeLog;

    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        String userId = TenantContext.getUserId();
        if (userId == null || userId.isBlank()) {
            return Result.ok(Map.of("isRepairEngineer", false));
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT COALESCE(is_repair_engineer, false) AS is_repair_engineer FROM sys_user WHERE id = ?::uuid AND is_active = true"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "sys_user", null),
                userId);
        boolean flag = !rows.isEmpty() && toBool(rows.get(0).get("is_repair_engineer"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("isRepairEngineer", flag);
        data.put("userId", userId);
        return Result.ok(data);
    }

    /** 维修工程师列表（is_repair_engineer = true） */
    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query,
                                                         @RequestParam(required = false) String keyword,
                                                         @RequestParam(required = false) String deptId,
                                                         @RequestParam(required = false) String workload) {
        StringBuilder where = new StringBuilder(" WHERE u.is_active = true AND COALESCE(u.is_repair_engineer, false) = true ");
        List<Object> args = new ArrayList<>();
        appendKeyword(where, args, keyword);
        if (deptId != null && !deptId.isBlank()) {
            where.append(" AND u.dept_id = ?::uuid ");
            args.add(deptId);
        }
        if ("has".equalsIgnoreCase(workload)) {
            where.append(" AND COALESCE(wc.workorder_count, 0) > 0 ");
        } else if ("none".equalsIgnoreCase(workload)) {
            where.append(" AND COALESCE(wc.workorder_count, 0) = 0 ");
        }
        return Result.ok(pageUsers(where, args, query));
    }

    /** 候选人员（非维修工程师，用于批量新增） */
    @GetMapping("/candidates/page")
    public Result<PageResult<Map<String, Object>>> candidates(PageQuery query, @RequestParam(required = false) String keyword) {
        StringBuilder where = new StringBuilder(" WHERE u.is_active = true AND COALESCE(u.is_repair_engineer, false) = false ");
        List<Object> args = new ArrayList<>();
        appendKeyword(where, args, keyword);
        return Result.ok(pageUsers(where, args, query));
    }

    /** 外键选择器：在职维修工程师（/options 避免与通用 /{table}/list 冲突） */
    @GetMapping({"/list", "/options"})
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String keyword) {
        StringBuilder where = new StringBuilder(" WHERE u.is_active = true AND COALESCE(u.is_repair_engineer, false) = true ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "sys_user", "u"));
        List<Object> args = new ArrayList<>();
        appendKeyword(where, args, keyword);
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT u.id, u.real_name, u.employee_no, u.phone, d.dept_name
                FROM sys_user u
                LEFT JOIN department d ON d.id = u.dept_id"""
                        + SoftDeleteSupport.notDeletedClause(jdbc, "department", "d")
                        + where + " ORDER BY u.real_name LIMIT 500", args.toArray());
        for (Map<String, Object> row : rows) {
            if (row.get("id") instanceof UUID id) {
                row.put("id", id.toString());
            }
        }
        return Result.ok(rows);
    }

    @PostMapping("/batch-add")
    @Transactional
    @OperationLog(module = "repair", description = "批量设维修工程师")
    public Result<Map<String, Object>> batchAdd(@RequestBody Map<String, Object> body) {
        List<String> ids = parseUserIds(body);
        if (ids.isEmpty()) throw new BizException(400, "请选择人员");
        int updated = 0;
        for (String id : ids) {
            Map<String, Object> before = changeLog.loadRow("sys_user", id);
            if (before == null) continue;
            jdbc.update("UPDATE sys_user SET is_repair_engineer = true, updated_at = NOW() WHERE id = ?::uuid", id);
            changeLog.recordUpdate("sys_user", UUID.fromString(id), before, changeLog.loadRow("sys_user", id));
            updated++;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updated", updated);
        return Result.ok(result);
    }

    @PostMapping("/{id:" + UUID_PATH + "}/revoke")
    @Transactional
    @OperationLog(module = "repair", description = "撤销维修工程师")
    public Result<Void> revoke(@PathVariable UUID id) {
        Map<String, Object> before = changeLog.loadRow("sys_user", id.toString());
        if (before == null) throw new BizException(404, "用户不存在");
        if (!Boolean.TRUE.equals(before.get("is_repair_engineer"))) {
            throw new BizException(400, "该用户不是维修工程师");
        }
        jdbc.update("UPDATE sys_user SET is_repair_engineer = false, updated_at = NOW() WHERE id = ?::uuid", id);
        changeLog.recordUpdate("sys_user", id, before, changeLog.loadRow("sys_user", id.toString()));
        return Result.ok();
    }

    @GetMapping("/workload")
    public Result<List<Map<String, Object>>> workload() {
        String woClause = SoftDeleteSupport.notDeletedClause(jdbc, "repair_workorder", "w");
        return Result.ok(jdbc.queryForList("""
                SELECT u.id, u.real_name AS engineer_name, u.employee_no,
                       COUNT(w.id) AS workorder_count,
                       COALESCE(SUM(w.total_cost), 0) AS total_cost
                FROM sys_user u
                LEFT JOIN repair_workorder w ON w.assigned_user_id = u.id""" + woClause + """
                WHERE COALESCE(u.is_repair_engineer, false) = true
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "sys_user", "u") + """
                GROUP BY u.id, u.real_name, u.employee_no
                ORDER BY workorder_count DESC
                """));
    }

    private PageResult<Map<String, Object>> pageUsers(StringBuilder where, List<Object> args, PageQuery query) {
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "sys_user", "u"));
        String from = """
                FROM sys_user u
                LEFT JOIN department d ON d.id = u.dept_id"""
                + SoftDeleteSupport.notDeletedClause(jdbc, "department", "d") + """
                LEFT JOIN (
                    SELECT assigned_user_id, COUNT(*) AS workorder_count
                    FROM repair_workorder
                    WHERE assigned_user_id IS NOT NULL
                      AND status IN ('reported','dispatching','pending_accept','accepted',
                                     'repairing','suspended','verify_rejected','pending_verify')
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "repair_workorder", null) + """
                    GROUP BY assigned_user_id
                ) wc ON wc.assigned_user_id = u.id
                """;
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + from + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.limit());
        pageArgs.add(query.offset());
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT u.id, u.username, u.real_name, u.employee_no, u.phone, u.email,
                       u.dept_id, d.dept_name, u.is_repair_engineer,
                       COALESCE(wc.workorder_count, 0) AS workorder_count
                """ + from + where + " ORDER BY u.real_name LIMIT ? OFFSET ?",
                pageArgs.toArray());
        return PageResult.of(rows, total != null ? total : 0, query.getPage(), query.getSize());
    }

    private static void appendKeyword(StringBuilder where, List<Object> args, String keyword) {
        if (keyword == null || keyword.isBlank()) return;
        String kw = "%" + keyword.trim() + "%";
        where.append(" AND (u.real_name ILIKE ? OR u.employee_no ILIKE ? OR u.username ILIKE ? OR u.phone ILIKE ?) ");
        args.add(kw);
        args.add(kw);
        args.add(kw);
        args.add(kw);
    }

    private static boolean toBool(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        String s = String.valueOf(v).trim();
        return "true".equalsIgnoreCase(s) || "t".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s);
    }

    @SuppressWarnings("unchecked")
    private static List<String> parseUserIds(Map<String, Object> body) {
        Object raw = body.get("userIds") != null ? body.get("userIds") : body.get("user_ids");
        if (raw == null) return List.of();
        if (raw instanceof List<?> list) {
            List<String> ids = new ArrayList<>();
            for (Object o : list) {
                if (o != null && !String.valueOf(o).isBlank()) ids.add(String.valueOf(o));
            }
            return ids;
        }
        return List.of();
    }
}
