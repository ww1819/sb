package com.meis.saas.purchase.controller;

import com.meis.saas.common.result.Result;
import com.meis.saas.purchase.support.PurchaseAlertService;
import com.meis.saas.purchase.support.PurchaseDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/purchase/dashboard")
@RequiredArgsConstructor
public class PurchaseDashboardController {
    private final JdbcTemplate jdbc;

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.ok(PurchaseDashboardService.buildStats(jdbc));
    }

    @PostMapping("/refresh-alerts")
    public Result<List<Map<String, Object>>> refreshAlerts() {
        PurchaseAlertService.scanAndNotify(jdbc);
        return Result.ok(PurchaseAlertService.listActive(jdbc));
    }
}
