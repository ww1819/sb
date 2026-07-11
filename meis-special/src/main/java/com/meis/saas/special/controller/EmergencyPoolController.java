package com.meis.saas.special.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/special/emergency")
@RequiredArgsConstructor
public class EmergencyPoolController {
    private final JdbcTemplate jdbc;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            where.append(" AND (p.pool_name ILIKE ? OR p.location ILIKE ?) ");
            String kw = "%" + query.getKeyword().trim() + "%";
            args.add(kw);
            args.add(kw);
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM emergency_device_pool p" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.limit());
        pageArgs.add(query.offset());
        var rows = jdbc.queryForList("""
            SELECT p.*, c.campus_name, u.real_name AS manager_name
            FROM emergency_device_pool p
            LEFT JOIN campus c ON c.id = p.campus_id
            LEFT JOIN sys_user u ON u.id = p.manager_id
            """ + where + " ORDER BY p.pool_name LIMIT ? OFFSET ?", pageArgs.toArray());
        return Result.ok(PageResult.of(rows, total, query.getPage(), query.getSize()));
    }

    @GetMapping("/allocations/page")
    public Result<PageResult<Map<String, Object>>> allocationPage(PageQuery query,
            @RequestParam(required = false) String status) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            where.append(" AND a.status = ? ");
            args.add(status);
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            where.append(" AND (a.allocation_no ILIKE ? OR d.device_name ILIKE ?) ");
            String kw = "%" + query.getKeyword().trim() + "%";
            args.add(kw);
            args.add(kw);
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM emergency_device_allocation a" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.limit());
        pageArgs.add(query.offset());
        var rows = jdbc.queryForList("""
            SELECT a.*, d.device_code, d.device_name, p.pool_name, dept.dept_name AS to_dept_name
            FROM emergency_device_allocation a
            LEFT JOIN medical_device d ON d.id = a.device_id
            LEFT JOIN emergency_device_pool p ON p.id = a.from_pool_id
            LEFT JOIN department dept ON dept.id = a.to_dept_id
            """ + where + " ORDER BY a.application_time DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        return Result.ok(PageResult.of(rows, total, query.getPage(), query.getSize()));
    }

    @PostMapping("/allocate")
    @Transactional
    @OperationLog(module = "special", description = "应急设备调配")
    public Result<Map<String, Object>> allocate(@RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        String userId = TenantContext.getUserId();
        jdbc.update("""
            INSERT INTO emergency_device_allocation (id, allocation_no, device_id, from_pool_id, to_dept_id,
            applicant_id, application_time, reason, urgency_level, status)
            VALUES (?::uuid,?,?::uuid,?::uuid,?::uuid,?::uuid,NOW(),?,?,?)
            """, id, body.getOrDefault("allocation_no", "EA" + System.currentTimeMillis()),
                body.get("device_id"), body.get("from_pool_id"), body.get("to_dept_id"),
                body.get("applicant_id") != null ? body.get("applicant_id") : userId,
                body.get("reason"), body.getOrDefault("urgency_level", "normal"), "pending");
        if (body.get("device_id") != null) {
            jdbc.update("UPDATE medical_device SET is_emergency = true, updated_at = NOW() WHERE id = ?::uuid",
                    body.get("device_id"));
        }
        return Result.ok(jdbc.queryForList("SELECT * FROM emergency_device_allocation WHERE id = ?::uuid", id).get(0));
    }

    @PostMapping("/allocate/{id}/approve")
    @OperationLog(module = "special", description = "审批应急调配")
    public Result<Map<String, Object>> approve(@PathVariable UUID id) {
        String userId = TenantContext.getUserId();
        jdbc.update("""
            UPDATE emergency_device_allocation SET status='approved', approved_by=?::uuid, approved_at=NOW(),
            allocation_time=NOW(), updated_at=NOW() WHERE id=?::uuid
            """, userId != null ? UUID.fromString(userId) : null, id);
        return Result.ok(allocation(id));
    }

    @PostMapping("/allocate/{id}/return")
    @OperationLog(module = "special", description = "应急设备归还")
    public Result<Map<String, Object>> returnDevice(@PathVariable UUID id) {
        jdbc.update("""
            UPDATE emergency_device_allocation SET status='returned', return_time=NOW(), updated_at=NOW()
            WHERE id=?::uuid
            """, id);
        return Result.ok(allocation(id));
    }

    private Map<String, Object> allocation(UUID id) {
        var rows = jdbc.queryForList("SELECT * FROM emergency_device_allocation WHERE id = ?::uuid", id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        return rows.get(0);
    }
}
