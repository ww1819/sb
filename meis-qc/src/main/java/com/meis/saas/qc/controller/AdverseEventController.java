package com.meis.saas.qc.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/qc/adverse")
@RequiredArgsConstructor
public class AdverseEventController {
    private final JdbcTemplate jdbc;
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "reported", Set.of("handling"), "handling", Set.of("reviewed"), "reviewed", Set.of("closed"));

    @PostMapping("/{id}/transition")
    @OperationLog(module = "qc", description = "不良事件状态流转")
    public Result<Map<String, Object>> transition(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        var rows = jdbc.queryForList("SELECT status FROM adverse_event WHERE id = ?::uuid", id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        String cur = rows.get(0).get("status").toString();
        String target = body.get("status");
        if (!TRANSITIONS.getOrDefault(cur, Set.of()).contains(target)) throw new BizException(400, "invalid transition");
        jdbc.update("UPDATE adverse_event SET status = ?, updated_at = NOW() WHERE id = ?::uuid", target, id);
        return Result.ok(jdbc.queryForList("SELECT * FROM adverse_event WHERE id = ?::uuid", id).get(0));
    }

    @PostMapping("/{id}/report-regulator")
    @OperationLog(module = "qc", description = "上报监管")
    public Result<Map<String, Object>> reportRegulator(@PathVariable UUID id) {
        jdbc.update("UPDATE adverse_event SET reported_to_authority = true, report_date = CURRENT_DATE, updated_at = NOW() WHERE id = ?::uuid", id);
        return Result.ok(jdbc.queryForList("SELECT * FROM adverse_event WHERE id = ?::uuid", id).get(0));
    }
}
