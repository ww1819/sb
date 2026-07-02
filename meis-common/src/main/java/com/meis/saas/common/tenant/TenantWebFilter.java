package com.meis.saas.common.tenant;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TenantWebFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            if (request instanceof HttpServletRequest req) {
                String schema = req.getHeader(TenantConstants.HEADER_TENANT_SCHEMA);
                if (StringUtils.hasText(schema)) {
                    TenantInfo.TenantInfoBuilder b = TenantInfo.builder().schemaName(schema);
                    String tenantId = req.getHeader(TenantConstants.HEADER_TENANT_ID);
                    String userId = req.getHeader(TenantConstants.HEADER_USER_ID);
                    String username = req.getHeader(TenantConstants.HEADER_USERNAME);
                    if (StringUtils.hasText(tenantId)) b.tenantId(tenantId);
                    if (StringUtils.hasText(userId)) b.userId(userId);
                    if (StringUtils.hasText(username)) b.username(username);
                    TenantContext.set(b.build());
                }
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
