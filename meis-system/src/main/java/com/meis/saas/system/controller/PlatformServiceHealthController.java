package com.meis.saas.system.controller;

import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.tenant.TenantContext;
import com.meis.saas.system.service.ServiceHealthProbeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/system/platform")
@RequiredArgsConstructor
public class PlatformServiceHealthController {
    private final ServiceHealthProbeService probeService;

    @GetMapping("/service-health")
    public Result<Map<String, Object>> serviceHealth() {
        if (!TenantContext.isPlatformAdmin()) {
            throw new BizException(403, "仅平台管理员可查看服务状态");
        }
        return Result.ok(probeService.checkAll());
    }
}
