package com.meis.saas.special.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/shared/fee")
@RequiredArgsConstructor
public class SharedFeeController {
    private final JdbcTemplate jdbc;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(
            PageQuery query,
            @RequestParam(required = false) String paidStatus,
            @RequestParam(required = false) UUID deviceId) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            where.append(" AND (f.fee_no ILIKE ? OR f.loan_no ILIKE ? OR f.device_name ILIKE ? OR f.device_code ILIKE ?) ");
            String kw = "%" + query.getKeyword().trim() + "%";
            args.add(kw);
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        if (paidStatus != null && !paidStatus.isBlank()) {
            where.append(" AND f.paid_status = ? ");
            args.add(paidStatus);
        }
        if (deviceId != null) {
            where.append(" AND f.device_id = ?::uuid ");
            args.add(deviceId);
        }
        String from = " FROM shared_device_fee f LEFT JOIN shared_device_loan l ON l.id = f.loan_id ";
        long total = jdbc.queryForObject("SELECT COUNT(*) " + from + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.limit());
        pageArgs.add(query.offset());
        var rows = jdbc.queryForList("""
            SELECT f.*, l.to_dept_id
            """ + from + where + " ORDER BY f.fee_date DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        return Result.ok(PageResult.of(rows, total, query.getPage(), query.getSize()));
    }

    @PostMapping
    @OperationLog(module = "shared", description = "登记借调收费")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = body.containsKey("id") && body.get("id") != null
                ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = !jdbc.queryForList("SELECT 1 FROM shared_device_fee WHERE id = ?::uuid", id).isEmpty();
        if (!exists) {
            Object loanId = body.get("loan_id");
            Map<String, Object> loanSnap = Map.of();
            if (loanId != null && !String.valueOf(loanId).isBlank()) {
                var loans = jdbc.queryForList(
                        "SELECT loan_no, device_id, device_code, device_name FROM shared_device_loan WHERE id = ?::uuid",
                        UUID.fromString(loanId.toString()));
                if (!loans.isEmpty()) loanSnap = loans.get(0);
            }
            jdbc.update("""
                INSERT INTO shared_device_fee (id, fee_no, loan_id, loan_no, device_id, device_code, device_name,
                    fee_amount, fee_date, paid_status, remark)
                VALUES (?::uuid,?,?::uuid,?,?::uuid,?,?,?,?,?,?)
                """, id, body.getOrDefault("fee_no", "SF" + System.currentTimeMillis()),
                    loanId, loanSnap.get("loan_no"), loanSnap.get("device_id"),
                    loanSnap.get("device_code"), loanSnap.get("device_name"),
                    body.get("fee_amount"), body.get("fee_date"),
                    body.getOrDefault("paid_status", "unpaid"), body.get("remark"));
        } else {
            jdbc.update("""
                UPDATE shared_device_fee SET fee_amount=?, fee_date=?, paid_status=?, remark=?, updated_at=NOW()
                WHERE id=?::uuid
                """, body.get("fee_amount"), body.get("fee_date"), body.get("paid_status"),
                    body.get("remark"), id);
        }
        return Result.ok(jdbc.queryForList("SELECT * FROM shared_device_fee WHERE id = ?::uuid", id).get(0));
    }

    @PostMapping("/{id}/pay")
    @OperationLog(module = "shared", description = "确认收费")
    public Result<Map<String, Object>> pay(@PathVariable UUID id) {
        jdbc.update("UPDATE shared_device_fee SET paid_status='paid', updated_at=NOW() WHERE id=?::uuid", id);
        return Result.ok(jdbc.queryForList("SELECT * FROM shared_device_fee WHERE id = ?::uuid", id).get(0));
    }
}
