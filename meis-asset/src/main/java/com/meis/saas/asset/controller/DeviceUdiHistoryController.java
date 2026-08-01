package com.meis.saas.asset.controller;

import com.meis.saas.asset.service.DeviceUdiHistoryService;
import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AST-GAP O-01：设备 UDI 变更历史。
 */
@RestController
@RequestMapping("/api/asset/device-udi-history")
@RequiredArgsConstructor
public class DeviceUdiHistoryController {
    private final DeviceUdiHistoryService udiHistoryService;

    @GetMapping("/by-device/{deviceId}")
    public Result<List<Map<String, Object>>> byDevice(@PathVariable UUID deviceId) {
        return Result.ok(udiHistoryService.listByDevice(deviceId));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        return Result.ok(udiHistoryService.get(id));
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "asset", description = "新增 UDI 变更历史")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return Result.ok(udiHistoryService.create(body));
    }

    @DeleteMapping("/{id}")
    @Transactional
    @OperationLog(module = "asset", description = "删除 UDI 变更历史")
    public Result<Void> delete(@PathVariable UUID id) {
        udiHistoryService.softDelete(id);
        return Result.ok();
    }
}
