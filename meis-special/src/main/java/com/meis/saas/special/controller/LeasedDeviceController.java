package com.meis.saas.special.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/special/leased")
@RequiredArgsConstructor
public class LeasedDeviceController {
    private final JdbcTemplate jdbc;

    @PostMapping("/{id}/renew")
    @OperationLog(module = "special", description = "租赁续租")
    public Result<Map<String, Object>> renew(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        jdbc.update("UPDATE leased_device SET lease_end_date = ?, monthly_rent = ?, updated_at = NOW() WHERE id = ?::uuid",
                body.get("lease_end_date"), body.get("monthly_rent"), id);
        return Result.ok(jdbc.queryForList("SELECT * FROM leased_device WHERE id = ?::uuid", id).get(0));
    }

    @PostMapping("/{id}/return")
    @OperationLog(module = "special", description = "租赁退租")
    public Result<Map<String, Object>> returnDevice(@PathVariable UUID id) {
        jdbc.update("UPDATE leased_device SET status = 'returned', updated_at = NOW() WHERE id = ?::uuid", id);
        return Result.ok(jdbc.queryForList("SELECT * FROM leased_device WHERE id = ?::uuid", id).get(0));
    }
}
