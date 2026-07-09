package com.meis.saas.integration.controller;

import com.meis.saas.common.result.Result;
import com.meis.saas.integration.service.IntegrationSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/integration")
@RequiredArgsConstructor
public class IntegrationController {
    private final JdbcTemplate jdbc;
    private final IntegrationSyncService syncService;

    @GetMapping("/adapters")
    public Result<List<Map<String, String>>> adapters() {
        return Result.ok(List.of(
                Map.of("code", "HIS", "name", "医院信息系统", "status", "stub", "endpoint", "/api/integration/HIS/sync"),
                Map.of("code", "PACS", "name", "影像系统", "status", "stub", "endpoint", "/api/integration/PACS/sync"),
                Map.of("code", "LIS", "name", "检验系统", "status", "stub", "endpoint", "/api/integration/LIS/sync"),
                Map.of("code", "HRP", "name", "医院资源规划", "status", "stub", "endpoint", "/api/integration/HRP/sync"),
                Map.of("code", "WECHAT_WORK", "name", "企业微信", "status", "stub", "endpoint", "/api/integration/WECHAT_WORK/sync")
        ));
    }

    @PostMapping("/{system}/sync")
    public Result<Map<String, Object>> sync(@PathVariable String system, @RequestBody Map<String, Object> payload) {
        return Result.ok(syncService.runSync(system, payload));
    }

    @GetMapping("/tasks")
    public Result<List<Map<String, Object>>> tasks() {
        return Result.ok(jdbc.queryForList(
                "SELECT * FROM integration_sync_task ORDER BY created_at DESC LIMIT 50"));
    }

    @GetMapping("/{system}/health")
    public Result<Map<String, String>> health(@PathVariable String system) {
        return Result.ok(Map.of("system", system.toUpperCase(), "status", "mock_ok"));
    }

    @GetMapping("/openapi")
    public Result<Map<String, Object>> openApi() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("openapi", "3.0.3");
        spec.put("info", Map.of("title", "MEIS Integration API", "version", "2.0"));
        spec.put("paths", Map.of(
                "/api/integration/{system}/sync", Map.of("post", Map.of("summary", "触发同步")),
                "/api/integration/tasks", Map.of("get", Map.of("summary", "同步任务列表"))
        ));
        return Result.ok(spec);
    }
}
