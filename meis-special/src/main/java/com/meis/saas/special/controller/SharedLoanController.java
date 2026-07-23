package com.meis.saas.special.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.page.FilterCsvSupport;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.tenant.TenantContext;
import com.meis.saas.common.workflow.ApprovalInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/shared/loan")
@RequiredArgsConstructor
public class SharedLoanController {
    private final JdbcTemplate jdbc;
    private final ApprovalInstanceService approvalService;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(
            PageQuery query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean pendingOnly,
            @RequestParam(required = false) UUID deviceId) {
        return Result.ok(queryLoans(query, status, pendingOnly, deviceId));
    }

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) UUID deviceId) {
        return Result.ok(queryLoans(new PageQuery(), null, null, deviceId).getRecords());
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        return Result.ok(loadLoan(id));
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "shared", description = "保存借调申请")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = body.containsKey("id") && body.get("id") != null
                ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        fillLoanSnapshot(body);
        boolean exists = !jdbc.queryForList("SELECT 1 FROM shared_device_loan WHERE id = ?::uuid", id).isEmpty();
        if (!exists) {
            jdbc.update("""
                INSERT INTO shared_device_loan (id, loan_no, device_id, device_code, device_name,
                from_dept_id, to_dept_id, applicant_id, loan_start, loan_end,
                fee_mode, fee_time_unit, fee_unit_price, reason, status, approval_status)
                VALUES (?::uuid,?,?::uuid,?,?,?::uuid,?::uuid,?::uuid,?,?,?,?,?,?,?,?)
                """, id, body.getOrDefault("loan_no", "SL" + System.currentTimeMillis()),
                    body.get("device_id"), body.get("device_code"), body.get("device_name"),
                    body.get("from_dept_id"), body.get("to_dept_id"), body.get("applicant_id"),
                    body.get("loan_start"), body.get("loan_end"),
                    body.get("fee_mode"), body.get("fee_time_unit"), body.get("fee_unit_price"),
                    body.get("reason"),
                    body.getOrDefault("status", "draft"), body.getOrDefault("approval_status", "draft"));
        } else {
            jdbc.update("""
                UPDATE shared_device_loan SET to_dept_id=?::uuid, loan_start=?, loan_end=?,
                reason=?, remark=?, updated_at=NOW()
                WHERE id=?::uuid AND status IN ('draft','pending')
                """, body.get("to_dept_id"), body.get("loan_start"), body.get("loan_end"),
                    body.get("reason"), body.get("remark"), id);
        }
        return get(id);
    }

    @PostMapping("/{id}/submit")
    @OperationLog(module = "shared", description = "提交借调审批")
    public Result<Map<String, Object>> submit(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        var loan = loadLoan(id);
        if (!Set.of("draft", "pending").contains(String.valueOf(loan.get("status")))) {
            throw new BizException(400, "当前状态不可提交");
        }
        UUID applicantId = body.get("applicantId") != null
                ? UUID.fromString(body.get("applicantId").toString())
                : (TenantContext.getUserId() != null ? UUID.fromString(TenantContext.getUserId()) : null);
        jdbc.update("""
            UPDATE shared_device_loan SET status='pending', approval_status='pending', updated_at=NOW()
            WHERE id=?::uuid
            """, id);
        approvalService.submit("shared_device_loan", id, loan.get("loan_no").toString(),
                "公用设备借调 " + loan.get("loan_no"), applicantId, 0);
        return get(id);
    }

    @PostMapping("/{id}/approve")
    @OperationLog(module = "shared", description = "审批借调")
    public Result<Map<String, Object>> approve(@PathVariable UUID id) {
        String userId = TenantContext.getUserId();
        UUID approver = userId != null ? UUID.fromString(userId) : null;
        String approverName = SoftDeleteSupport.resolveUserDisplayName(jdbc, approver);
        jdbc.update("""
            UPDATE shared_device_loan SET status='approved', approval_status='approved',
            approved_by=?::uuid, approved_by_name=?, approved_at=NOW(), billing_start_at=NOW(), updated_at=NOW()
            WHERE id=?::uuid
            """, approver, approverName, id);
        return get(id);
    }

    @PostMapping("/{id}/reject")
    @OperationLog(module = "shared", description = "驳回借调")
    public Result<Map<String, Object>> reject(@PathVariable UUID id) {
        jdbc.update("""
            UPDATE shared_device_loan SET status='rejected', approval_status='rejected', updated_at=NOW()
            WHERE id=?::uuid
            """, id);
        return get(id);
    }

    @PostMapping("/{id}/lend")
    @Transactional
    @OperationLog(module = "shared", description = "执行借出")
    public Result<Map<String, Object>> lend(@PathVariable UUID id) {
        var loan = loadLoan(id);
        if (!"approved".equals(String.valueOf(loan.get("status")))) {
            throw new BizException(400, "仅已审批单据可借出");
        }
        jdbc.update("""
            UPDATE shared_device_loan SET status='on_loan', loan_time=NOW(), updated_at=NOW() WHERE id=?::uuid
            """, id);
        if (loan.get("device_id") != null && loan.get("to_dept_id") != null) {
            jdbc.update("UPDATE medical_device SET dept_id = ?::uuid, updated_at = NOW() WHERE id = ?::uuid",
                    loan.get("to_dept_id"), loan.get("device_id"));
        }
        return get(id);
    }

    private PageResult<Map<String, Object>> queryLoans(
            PageQuery query, String status, Boolean pendingOnly, UUID deviceId) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            where.append(" AND (l.loan_no ILIKE ? OR l.device_name ILIKE ? OR l.device_code ILIKE ?) ");
            String kw = "%" + query.getKeyword().trim() + "%";
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        if (!Boolean.TRUE.equals(pendingOnly)) {
            FilterCsvSupport.appendStrIn(where, args, "l.status", status);
        } else {
            where.append(" AND l.status = 'pending' ");
        }
        if (deviceId != null) {
            where.append(" AND l.device_id = ?::uuid ");
            args.add(deviceId);
        }
        String from = """
            FROM shared_device_loan l
            LEFT JOIN department fd ON fd.id = l.from_dept_id
            LEFT JOIN department td ON td.id = l.to_dept_id
            LEFT JOIN sys_user u ON u.id = l.applicant_id
            """;
        long total = jdbc.queryForObject("SELECT COUNT(*) " + from + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.limit());
        pageArgs.add(query.offset());
        var rows = jdbc.queryForList("""
            SELECT l.*, fd.dept_name AS from_dept_name, td.dept_name AS to_dept_name, u.real_name AS applicant_name
            """ + from + where + " ORDER BY l.created_at DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        return PageResult.of(rows, total, query.getPage(), query.getSize());
    }

    private Map<String, Object> loadLoan(UUID id) {
        var rows = jdbc.queryForList("""
            SELECT l.*, fd.dept_name AS from_dept_name, td.dept_name AS to_dept_name, u.real_name AS applicant_name
            FROM shared_device_loan l
            LEFT JOIN department fd ON fd.id = l.from_dept_id
            LEFT JOIN department td ON td.id = l.to_dept_id
            LEFT JOIN sys_user u ON u.id = l.applicant_id
            WHERE l.id = ?::uuid
            """, id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        return rows.get(0);
    }

    private void fillLoanSnapshot(Map<String, Object> body) {
        if (body.get("device_id") == null) return;
        var devices = jdbc.queryForList("""
            SELECT device_code, device_name, dept_id, shared_fee_mode, shared_fee_time_unit, shared_fee_unit_price
            FROM medical_device WHERE id = ?::uuid AND is_shared_device = TRUE
            """, body.get("device_id"));
        if (devices.isEmpty()) throw new BizException(400, "设备不是公用设备或不存在");
        var d = devices.get(0);
        if (body.get("device_code") == null) body.put("device_code", d.get("device_code"));
        if (body.get("device_name") == null) body.put("device_name", d.get("device_name"));
        if (body.get("from_dept_id") == null) body.put("from_dept_id", d.get("dept_id"));
        if (body.get("fee_mode") == null) body.put("fee_mode", d.get("shared_fee_mode"));
        if (body.get("fee_time_unit") == null) body.put("fee_time_unit", d.get("shared_fee_time_unit"));
        if (body.get("fee_unit_price") == null) body.put("fee_unit_price", d.get("shared_fee_unit_price"));
    }
}
