package com.meis.saas.asset.controller;

import com.meis.saas.asset.service.DeviceOwnershipPeriodService;
import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AST-OWN-01/02 + AST-UI-23 + AST-LOC-01：归属区间 / 补录 / 变更科室与位置。
 */
@RestController
@RequestMapping("/api/asset/ownership-period")
@RequiredArgsConstructor
public class DeviceOwnershipPeriodController {
    private final DeviceOwnershipPeriodService ownershipService;

    @GetMapping("/by-device/{deviceId}")
    public Result<List<Map<String, Object>>> byDevice(
            @PathVariable UUID deviceId,
            @RequestParam(value = "include_draft", required = false, defaultValue = "false") boolean includeDraft) {
        return Result.ok(ownershipService.listByDevice(deviceId, includeDraft));
    }

    @GetMapping("/location/by-device/{deviceId}")
    public Result<List<Map<String, Object>>> locationByDevice(
            @PathVariable UUID deviceId,
            @RequestParam(value = "include_draft", required = false, defaultValue = "false") boolean includeDraft) {
        return Result.ok(ownershipService.listLocationByDevice(deviceId, includeDraft));
    }

    @PostMapping("/backfill")
    @Transactional
    @OperationLog(module = "asset", description = "补录归属历史")
    public Result<Map<String, Object>> backfill(@RequestBody Map<String, Object> body) {
        return Result.ok(ownershipService.createBackfill(body));
    }

    @PutMapping("/{id}")
    @Transactional
    @OperationLog(module = "asset", description = "修改补录归属草稿")
    public Result<Map<String, Object>> update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return Result.ok(ownershipService.updateDraft(id, body));
    }

    @DeleteMapping("/{id}")
    @Transactional
    @OperationLog(module = "asset", description = "删除补录归属草稿")
    public Result<Void> delete(@PathVariable UUID id) {
        ownershipService.deleteDraft(id);
        return Result.ok();
    }

    @PostMapping("/{id}/confirm")
    @Transactional
    @OperationLog(module = "asset", description = "确认归属补录生效")
    public Result<Map<String, Object>> confirm(@PathVariable UUID id) {
        return Result.ok(ownershipService.confirm(id));
    }

    @PostMapping("/device/{deviceId}/change-dept")
    @Transactional
    @OperationLog(module = "asset", description = "变更所属科室")
    public Result<List<Map<String, Object>>> changeDept(
            @PathVariable UUID deviceId, @RequestBody Map<String, Object> body) {
        UUID deptId = parseUuid(body.get("dept_id"));
        if (deptId == null) throw new BizException(400, "请选择所属科室");
        String mode = body.get("mode") == null ? "" : String.valueOf(body.get("mode")).trim();
        boolean realTransfer = "transfer".equalsIgnoreCase(mode) || "real".equalsIgnoreCase(mode)
                || "manual_transfer".equalsIgnoreCase(mode);
        boolean correct = "correct".equalsIgnoreCase(mode) || "manual_correct".equalsIgnoreCase(mode);
        if (!realTransfer && !correct) {
            throw new BizException(400, "mode 须为 transfer（真变更）或 correct（纠错）");
        }
        ownershipService.changeDept(deviceId, deptId, realTransfer);
        return Result.ok(ownershipService.listByDevice(deviceId, true));
    }

    @PostMapping("/device/{deviceId}/change-location")
    @Transactional
    @OperationLog(module = "asset", description = "变更安装存放位置")
    public Result<List<Map<String, Object>>> changeLocation(
            @PathVariable UUID deviceId, @RequestBody Map<String, Object> body) {
        String mode = body.get("mode") == null ? "" : String.valueOf(body.get("mode")).trim();
        boolean realTransfer = "transfer".equalsIgnoreCase(mode) || "real".equalsIgnoreCase(mode)
                || "manual_transfer".equalsIgnoreCase(mode);
        boolean correct = "correct".equalsIgnoreCase(mode) || "manual_correct".equalsIgnoreCase(mode);
        if (!realTransfer && !correct) {
            throw new BizException(400, "mode 须为 transfer（真变更）或 correct（纠错）");
        }
        String floor = body.get("location_floor") == null ? null : String.valueOf(body.get("location_floor"));
        String room = body.get("room_number") == null ? null : String.valueOf(body.get("room_number"));
        String detail = body.containsKey("location_detail")
                ? (body.get("location_detail") == null ? null : String.valueOf(body.get("location_detail")))
                : null;
        ownershipService.changeLocation(deviceId, floor, room, detail, realTransfer);
        return Result.ok(ownershipService.listLocationByDevice(deviceId, true));
    }

    private static UUID parseUuid(Object v) {
        if (v == null) return null;
        if (v instanceof UUID u) return u;
        String s = String.valueOf(v).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return null;
        try {
            return UUID.fromString(s);
        } catch (Exception e) {
            return null;
        }
    }
}
