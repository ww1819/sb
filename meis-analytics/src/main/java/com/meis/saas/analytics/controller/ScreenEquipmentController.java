package com.meis.saas.analytics.controller;

import com.meis.saas.common.result.Result;
import com.meis.saas.analytics.service.ScreenDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/screen/equipment")
@RequiredArgsConstructor
public class ScreenEquipmentController {
    private final ScreenDashboardService dashboardService;

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        return Result.ok(dashboardService.equipmentDashboard());
    }
}
