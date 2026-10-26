package com.meis.saas.system.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.ops.OpsClientChannel;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.wx.SysUserWxBindService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/system/user-wx-bind")
@RequiredArgsConstructor
public class SysUserWxBindController {
    private final SysUserWxBindService bindService;

    @GetMapping("/by-user/{userId}")
    public Result<List<Map<String, Object>>> byUser(
            @PathVariable UUID userId,
            @RequestParam(value = "active_only", required = false, defaultValue = "false") boolean activeOnly) {
        return Result.ok(bindService.listByUser(userId, activeOnly));
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "system", description = "关联微信账号")
    public Result<Map<String, Object>> bind(@RequestBody Map<String, Object> body) {
        UUID userId = parseUuid(body.get("user_id"));
        if (userId == null) throw new BizException(400, "缺少 user_id");
        String openid = body.get("wx_openid") == null ? null : String.valueOf(body.get("wx_openid")).trim();
        String unionid = body.get("wx_unionid") == null ? null : String.valueOf(body.get("wx_unionid"));
        String appId = body.get("mp_app_id") == null ? null : String.valueOf(body.get("mp_app_id"));
        String remark = body.get("remark") == null ? null : String.valueOf(body.get("remark"));
        return Result.ok(bindService.bind(userId, openid, unionid, appId, OpsClientChannel.of(body), remark));
    }

    @PostMapping("/{id}/unbind")
    @Transactional
    @OperationLog(module = "system", description = "解除微信绑定")
    public Result<Map<String, Object>> unbind(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        return Result.ok(bindService.unbind(id, OpsClientChannel.of(body != null ? body : Map.of())));
    }

    private static UUID parseUuid(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) return null;
        try {
            return UUID.fromString(String.valueOf(raw).trim());
        } catch (Exception e) {
            return null;
        }
    }
}
