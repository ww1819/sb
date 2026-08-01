package com.meis.saas.asset.controller;

import com.meis.saas.asset.service.DevicePartReplacementService;
import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AST-PART-01：非维修配件更换补录。
 */
@RestController
@RequestMapping("/api/asset/part-replacement")
@RequiredArgsConstructor
public class DevicePartReplacementController {
    private final DevicePartReplacementService partReplacementService;

    @GetMapping("/by-device/{deviceId}")
    public Result<List<Map<String, Object>>> byDevice(
            @PathVariable UUID deviceId,
            @RequestParam(value = "include_draft", required = false, defaultValue = "true") boolean includeDraft) {
        return Result.ok(partReplacementService.listByDevice(deviceId, includeDraft));
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "asset", description = "新增非维修配件更换")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return Result.ok(partReplacementService.create(body));
    }

    @PutMapping("/{id}")
    @Transactional
    @OperationLog(module = "asset", description = "修改非维修配件更换草稿")
    public Result<Map<String, Object>> update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return Result.ok(partReplacementService.updateDraft(id, body));
    }

    @DeleteMapping("/{id}")
    @Transactional
    @OperationLog(module = "asset", description = "删除非维修配件更换草稿")
    public Result<Void> delete(@PathVariable UUID id) {
        partReplacementService.deleteDraft(id);
        return Result.ok();
    }

    @PostMapping("/{id}/confirm")
    @Transactional
    @OperationLog(module = "asset", description = "确认非维修配件更换生效")
    public Result<Map<String, Object>> confirm(@PathVariable UUID id) {
        return Result.ok(partReplacementService.confirm(id));
    }
}
