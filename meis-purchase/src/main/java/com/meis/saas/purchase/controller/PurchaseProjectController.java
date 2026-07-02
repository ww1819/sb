package com.meis.saas.purchase.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/purchase/project")
@RequiredArgsConstructor
public class PurchaseProjectController {
    private final JdbcTemplate jdbc;
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "draft", Set.of("bidding"), "bidding", Set.of("awarded"), "awarded", Set.of("closed"));

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("SELECT * FROM purchase_project WHERE id = ?::uuid", id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        return Result.ok(rows.get(0));
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "purchase", description = "保存采购项目")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = body.containsKey("id") ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = !jdbc.queryForList("SELECT 1 FROM purchase_project WHERE id = ?::uuid", id).isEmpty();
        if (exists) {
            jdbc.update("UPDATE purchase_project SET project_name=?, plan_id=?::uuid, purchase_method=?, status=?, updated_at=NOW() WHERE id=?::uuid",
                    body.get("project_name"), body.get("plan_id"), body.get("purchase_method"), body.getOrDefault("status", "draft"), id);
        } else {
            jdbc.update("INSERT INTO purchase_project (id, project_code, project_name, plan_id, purchase_method, status) VALUES (?::uuid,?,?,?::uuid,?,?)",
                    id, body.getOrDefault("project_code", "PJ" + System.currentTimeMillis()),
                    body.get("project_name"), body.get("plan_id"), body.get("purchase_method"), "draft");
        }
        return get(id);
    }

    @PostMapping("/{id}/transition")
    @OperationLog(module = "purchase", description = "采购项目状态流转")
    public Result<Map<String, Object>> transition(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        var rows = jdbc.queryForList("SELECT status FROM purchase_project WHERE id = ?::uuid", id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        String cur = rows.get(0).get("status").toString();
        String target = body.get("status");
        if (!TRANSITIONS.getOrDefault(cur, Set.of()).contains(target)) throw new BizException(400, "invalid transition");
        jdbc.update("UPDATE purchase_project SET status = ?, updated_at = NOW() WHERE id = ?::uuid", target, id);
        return get(id);
    }
}
