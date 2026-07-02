package com.meis.saas.tenant.controller;

import com.meis.saas.api.dto.TenantCreateRequest;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.tenant.TenantConstants;
import com.meis.saas.tenant.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tenant")
@RequiredArgsConstructor
public class TenantController {
    private final TenantService tenantService;

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(
            @RequestHeader(value = TenantConstants.HEADER_USER_TYPE, required = false) String userType) {
        tenantService.requirePlatformAdmin(userType);
        return Result.ok(tenantService.list());
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id,
                               @RequestBody Map<String, Object> body,
                               @RequestHeader(value = TenantConstants.HEADER_USER_TYPE, required = false) String userType) {
        tenantService.requirePlatformAdmin(userType);
        tenantService.update(id, body);
        return Result.ok();
    }

    @PostMapping("/create")
    public Result<Map<String, Object>> create(
            @RequestBody TenantCreateRequest request,
            @RequestHeader(value = TenantConstants.HEADER_USER_TYPE, required = false) String userType) {
        tenantService.requirePlatformAdmin(userType);
        return Result.ok(tenantService.create(request));
    }
}
