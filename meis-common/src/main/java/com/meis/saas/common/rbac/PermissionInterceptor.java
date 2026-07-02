package com.meis.saas.common.rbac;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meis.saas.common.tenant.TenantConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PermissionInterceptor implements HandlerInterceptor {
    private final ObjectMapper mapper = new ObjectMapper();
    public static final ThreadLocal<PermissionContext> CTX = new ThreadLocal<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String permsHeader = request.getHeader("X-Permissions");
        PermissionContext ctx = PermissionContext.builder()
                .userId(request.getHeader(TenantConstants.HEADER_USER_ID))
                .tenantId(request.getHeader(TenantConstants.HEADER_TENANT_ID))
                .schemaName(request.getHeader(TenantConstants.HEADER_TENANT_SCHEMA))
                .dataScope("all")
                .build();
        if (permsHeader != null && !permsHeader.isBlank()) {
            try {
                Map<String, Object> p = mapper.readValue(permsHeader, new TypeReference<>() {});
                ctx.setMenus((List<String>) p.get("menus"));
                ctx.setButtons((List<String>) p.get("buttons"));
                ctx.setDataScope((String) p.getOrDefault("dataScope", "all"));
                ctx.setDeptIds((List<String>) p.get("deptIds"));
                ctx.setWarehouseIds((List<String>) p.get("warehouseIds"));
            } catch (Exception ignored) {}
        }
        CTX.set(ctx);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        CTX.remove();
    }
}
