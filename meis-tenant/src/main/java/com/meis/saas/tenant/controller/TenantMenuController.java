package com.meis.saas.tenant.controller;

import com.meis.saas.common.result.Result;
import com.meis.saas.common.tenant.TenantConstants;
import com.meis.saas.tenant.service.TenantMenuService;
import com.meis.saas.tenant.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenant")
@RequiredArgsConstructor
public class TenantMenuController {
    private final TenantMenuService tenantMenuService;
    private final TenantService tenantService;

    @GetMapping("/{tenantId}/menus")
    public Result<List<String>> getMenus(
            @PathVariable UUID tenantId,
            @RequestHeader(value = TenantConstants.HEADER_USER_TYPE, required = false) String userType) {
        tenantService.requirePlatformAdmin(userType);
        return Result.ok(tenantMenuService.getAuthorizedMenus(tenantId));
    }

    @PostMapping("/{tenantId}/menus")
    public Result<Void> saveMenus(
            @PathVariable UUID tenantId,
            @RequestBody List<String> menuCodes,
            @RequestHeader(value = TenantConstants.HEADER_USER_TYPE, required = false) String userType) {
        tenantService.requirePlatformAdmin(userType);
        tenantMenuService.saveAuthorizedMenus(tenantId, menuCodes);
        return Result.ok();
    }

    @GetMapping("/packages")
    public Result<List<Map<String, Object>>> packages(
            @RequestHeader(value = TenantConstants.HEADER_USER_TYPE, required = false) String userType) {
        tenantService.requirePlatformAdmin(userType);
        return Result.ok(tenantMenuService.listPackages());
    }

    @GetMapping("/packages/{packageCode}/menus")
    public Result<List<String>> packageMenus(
            @PathVariable String packageCode,
            @RequestHeader(value = TenantConstants.HEADER_USER_TYPE, required = false) String userType) {
        tenantService.requirePlatformAdmin(userType);
        return Result.ok(tenantMenuService.packageMenus(packageCode));
    }
}
