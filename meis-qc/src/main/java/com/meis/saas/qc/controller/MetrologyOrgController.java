package com.meis.saas.qc.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/metrology/org")
@RequiredArgsConstructor
public class MetrologyOrgController {
    private final JdbcTemplate jdbc;

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("SELECT * FROM metrology_org WHERE id = ?::uuid", id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        return Result.ok(rows.get(0));
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "metrology", description = "保存检定机构")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = body.containsKey("id") ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = !jdbc.queryForList("SELECT 1 FROM metrology_org WHERE id = ?::uuid", id).isEmpty();
        if (exists) {
            jdbc.update("""
                UPDATE metrology_org SET org_code=?, org_name=?, qualification_no=?, contact_person=?, contact_phone=?, address=?, is_active=?, updated_at=NOW()
                WHERE id=?::uuid
                """, body.get("org_code"), body.get("org_name"), body.get("qualification_no"),
                    body.get("contact_person"), body.get("contact_phone"), body.get("address"),
                    body.getOrDefault("is_active", true), id);
        } else {
            jdbc.update("""
                INSERT INTO metrology_org (id, org_code, org_name, qualification_no, contact_person, contact_phone, address, is_active)
                VALUES (?::uuid,?,?,?,?,?,?,?)
                """, id, body.get("org_code"), body.get("org_name"), body.get("qualification_no"),
                    body.get("contact_person"), body.get("contact_phone"), body.get("address"),
                    body.getOrDefault("is_active", true));
        }
        return get(id);
    }
}
