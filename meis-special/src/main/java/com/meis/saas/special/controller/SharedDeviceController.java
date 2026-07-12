package com.meis.saas.special.controller;

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

import java.util.*;

@RestController
@RequestMapping("/api/shared/device")
@RequiredArgsConstructor
public class SharedDeviceController {
    private final JdbcTemplate jdbc;

    private static final String LOAN_STATUS_EXPR = """
            CASE
              WHEN EXISTS (
                SELECT 1 FROM shared_device_loan l
                INNER JOIN shared_device_return r ON r.loan_id = l.id
                WHERE l.device_id = d.id AND l.status = 'on_loan'
                  AND r.status = 'pending' AND r.approval_status = 'pending'
              ) THEN 'return_pending'
              WHEN EXISTS (
                SELECT 1 FROM shared_device_loan l
                WHERE l.device_id = d.id AND l.status = 'on_loan'
              ) THEN 'on_loan'
              WHEN EXISTS (
                SELECT 1 FROM shared_device_loan l
                WHERE l.device_id = d.id AND l.status IN ('draft', 'pending')
              ) THEN 'loan_pending'
              ELSE 'in_stock'
            END
            """;

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        var q = new PageQuery();
        q.setPage(1);
        q.setSize(500);
        return Result.ok(queryDevices(q, true, null).getRecords());
    }

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query) {
        return Result.ok(queryDevices(query, true, null));
    }

    @GetMapping("/candidates/list")
    public Result<List<Map<String, Object>>> candidateList() {
        var q = new PageQuery();
        q.setPage(1);
        q.setSize(500);
        return Result.ok(queryDevices(q, false, null).getRecords());
    }

    @GetMapping("/candidates")
    public Result<PageResult<Map<String, Object>>> candidates(PageQuery query) {
        return Result.ok(queryDevices(query, false, null));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("""
                SELECT d.*, dept.dept_name,
                """ + LOAN_STATUS_EXPR + " AS shared_loan_status "
                + """
                FROM medical_device d
                LEFT JOIN department dept ON dept.id = d.dept_id
                WHERE d.id = ?::uuid AND d.is_shared_device = TRUE
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", "d"), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        return Result.ok(rows.get(0));
    }

    @GetMapping("/{id}/loans")
    public Result<List<Map<String, Object>>> loans(@PathVariable UUID id) {
        return Result.ok(jdbc.queryForList("""
            SELECT l.*, td.dept_name AS to_dept_name
            FROM shared_device_loan l
            LEFT JOIN department td ON td.id = l.to_dept_id
            WHERE l.device_id = ?::uuid
            ORDER BY l.created_at DESC
            """, id));
    }

    @PostMapping("/register")
    @Transactional
    @OperationLog(module = "shared", description = "登记公用设备")
    public Result<Map<String, Object>> register(@RequestBody Map<String, Object> body) {
        UUID deviceId = UUID.fromString(body.get("device_id").toString());
        assertNotInActiveLoan(deviceId);
        jdbc.update("""
            UPDATE medical_device SET is_shared_device = TRUE,
            shared_fee_mode = ?, shared_fee_time_unit = ?, shared_fee_unit_price = ?,
            updated_at = NOW(), updated_by = ?::uuid
            WHERE id = ?::uuid AND COALESCE(is_shared_device, FALSE) = FALSE
            """,
                body.get("shared_fee_mode"),
                body.get("shared_fee_time_unit"),
                body.getOrDefault("shared_fee_unit_price", 0),
                SoftDeleteSupport.currentUserId(),
                deviceId);
        return get(deviceId);
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "shared", description = "更新公用设备计费")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = UUID.fromString(body.get("id").toString());
        jdbc.update("""
            UPDATE medical_device SET shared_fee_mode = ?, shared_fee_time_unit = ?,
            shared_fee_unit_price = ?, location_detail = COALESCE(?, location_detail),
            updated_at = NOW(), updated_by = ?::uuid
            WHERE id = ?::uuid AND is_shared_device = TRUE
            """,
                body.get("shared_fee_mode"),
                body.get("shared_fee_time_unit"),
                body.get("shared_fee_unit_price"),
                body.get("location_detail"),
                SoftDeleteSupport.currentUserId(),
                id);
        return get(id);
    }

    @PostMapping("/{id}/cancel")
    @Transactional
    @OperationLog(module = "shared", description = "取消公用设备")
    public Result<Void> cancel(@PathVariable UUID id) {
        assertNotInActiveLoan(id);
        jdbc.update("""
            UPDATE medical_device SET is_shared_device = FALSE,
            shared_fee_mode = NULL, shared_fee_time_unit = NULL, shared_fee_unit_price = NULL,
            updated_at = NOW(), updated_by = ?::uuid
            WHERE id = ?::uuid
            """, SoftDeleteSupport.currentUserId(), id);
        return Result.ok();
    }

    private PageResult<Map<String, Object>> queryDevices(PageQuery query, boolean sharedOnly, UUID deviceId) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", "d"));
        List<Object> args = new ArrayList<>();
        if (sharedOnly) {
            where.append(" AND d.is_shared_device = TRUE ");
        } else {
            where.append(" AND COALESCE(d.is_shared_device, FALSE) = FALSE ");
        }
        if (deviceId != null) {
            where.append(" AND d.id = ?::uuid ");
            args.add(deviceId);
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            where.append(" AND (d.device_code ILIKE ? OR d.device_name ILIKE ?) ");
            String kw = "%" + query.getKeyword().trim() + "%";
            args.add(kw);
            args.add(kw);
        }
        String from = """
            FROM medical_device d
            LEFT JOIN department dept ON dept.id = d.dept_id
            """;
        long total = jdbc.queryForObject("SELECT COUNT(*) " + from + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.limit());
        pageArgs.add(query.offset());
        var rows = jdbc.queryForList("""
            SELECT d.*, dept.dept_name,
            """ + LOAN_STATUS_EXPR + " AS shared_loan_status "
                + from + where + " ORDER BY d.device_code LIMIT ? OFFSET ?", pageArgs.toArray());
        return PageResult.of(rows, total, query.getPage(), query.getSize());
    }

    private void assertNotInActiveLoan(UUID deviceId) {
        var active = jdbc.queryForList("""
            SELECT 1 FROM shared_device_loan
            WHERE device_id = ?::uuid AND status IN ('draft', 'pending', 'approved', 'on_loan')
            LIMIT 1
            """, deviceId);
        var returnPending = jdbc.queryForList("""
            SELECT 1 FROM shared_device_return r
            INNER JOIN shared_device_loan l ON l.id = r.loan_id
            WHERE l.device_id = ?::uuid AND r.status = 'pending'
            LIMIT 1
            """, deviceId);
        if (!active.isEmpty() || !returnPending.isEmpty()) {
            throw new BizException(400, "设备存在进行中借调或归还审批，不可变更公用状态");
        }
    }
}
