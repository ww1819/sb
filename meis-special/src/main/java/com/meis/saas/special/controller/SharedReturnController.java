package com.meis.saas.special.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.tenant.TenantContext;
import com.meis.saas.common.workflow.ApprovalInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/shared/return")
@RequiredArgsConstructor
public class SharedReturnController {
    private final JdbcTemplate jdbc;
    private final ApprovalInstanceService approvalService;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean pendingOnly) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            where.append(" AND (r.return_no ILIKE ? OR l.device_name ILIKE ?) ");
            String kw = "%" + query.getKeyword().trim() + "%";
            args.add(kw);
            args.add(kw);
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND r.status = ? ");
            args.add(status);
        }
        if (Boolean.TRUE.equals(pendingOnly)) {
            where.append(" AND r.status = 'pending' ");
        }
        String from = """
            FROM shared_device_return r
            LEFT JOIN shared_device_loan l ON l.id = r.loan_id
            LEFT JOIN sys_user u ON u.id = r.applicant_id
            """;
        long total = jdbc.queryForObject("SELECT COUNT(*) " + from + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.limit());
        pageArgs.add(query.offset());
        var rows = jdbc.queryForList("""
            SELECT r.*, l.loan_no, l.device_code, l.device_name, u.real_name AS applicant_name
            """ + from + where + " ORDER BY r.created_at DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        return Result.ok(PageResult.of(rows, total, query.getPage(), query.getSize()));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("""
            SELECT r.*, l.loan_no, l.device_code, l.device_name
            FROM shared_device_return r
            LEFT JOIN shared_device_loan l ON l.id = r.loan_id
            WHERE r.id = ?::uuid
            """, id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        return Result.ok(rows.get(0));
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "shared", description = "归还申请")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        var loan = jdbc.queryForList("SELECT * FROM shared_device_loan WHERE id = ?::uuid", body.get("loan_id"));
        if (loan.isEmpty()) throw new BizException(404, "借调单不存在");
        if (!"on_loan".equals(String.valueOf(loan.get(0).get("status")))) {
            throw new BizException(400, "仅借出中单据可申请归还");
        }
        jdbc.update("""
            INSERT INTO shared_device_return (id, return_no, loan_id, device_id, return_date, condition_desc,
            applicant_id, status, approval_status)
            VALUES (?::uuid,?,?::uuid,?::uuid,?,?,?::uuid,?,?)
            """, id, body.getOrDefault("return_no", "SR" + System.currentTimeMillis()),
                body.get("loan_id"), loan.get(0).get("device_id"), body.get("return_date"),
                body.get("condition_desc"), body.get("applicant_id"),
                "pending", "pending");
        return get(id);
    }

    @PostMapping("/{id}/submit")
    @OperationLog(module = "shared", description = "提交归还审批")
    public Result<Map<String, Object>> submit(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        var ret = loadReturn(id);
        UUID applicantId = body.get("applicantId") != null
                ? UUID.fromString(body.get("applicantId").toString())
                : (TenantContext.getUserId() != null ? UUID.fromString(TenantContext.getUserId()) : null);
        approvalService.submit("shared_device_return", id, ret.get("return_no").toString(),
                "公用设备归还 " + ret.get("return_no"), applicantId, 0);
        return get(id);
    }

    @PostMapping("/{id}/approve")
    @Transactional
    @OperationLog(module = "shared", description = "审批归还")
    public Result<Map<String, Object>> approve(@PathVariable UUID id) {
        var ret = loadReturn(id);
        String userId = TenantContext.getUserId();
        jdbc.update("""
            UPDATE shared_device_return SET status='approved', approval_status='approved',
            approved_by=?::uuid, approved_at=NOW(), updated_at=NOW() WHERE id=?::uuid
            """, userId != null ? UUID.fromString(userId) : null, id);
        completeReturn(ret);
        return get(id);
    }

    @PostMapping("/{id}/reject")
    @OperationLog(module = "shared", description = "驳回归还")
    public Result<Map<String, Object>> reject(@PathVariable UUID id) {
        jdbc.update("""
            UPDATE shared_device_return SET status='rejected', approval_status='rejected', updated_at=NOW()
            WHERE id=?::uuid
            """, id);
        return get(id);
    }

    private void completeReturn(Map<String, Object> ret) {
        var loans = jdbc.queryForList("SELECT * FROM shared_device_loan WHERE id = ?::uuid", ret.get("loan_id"));
        if (loans.isEmpty()) return;
        Map<String, Object> loan = loans.get(0);
        jdbc.update("""
            UPDATE shared_device_loan SET status='returned', return_time=NOW(), updated_at=NOW() WHERE id=?::uuid
            """, loan.get("id"));
        if (loan.get("device_id") != null && loan.get("from_dept_id") != null) {
            jdbc.update("UPDATE medical_device SET dept_id = ?::uuid, updated_at = NOW() WHERE id = ?::uuid",
                    loan.get("from_dept_id"), loan.get("device_id"));
        }
        if (loan.get("shared_device_id") != null) {
            jdbc.update("""
                UPDATE shared_device SET availability_status='available', updated_at=NOW() WHERE id=?::uuid
                """, loan.get("shared_device_id"));
        }
    }

    private Map<String, Object> loadReturn(UUID id) {
        var rows = jdbc.queryForList("SELECT * FROM shared_device_return WHERE id = ?::uuid", id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        return rows.get(0);
    }
}
