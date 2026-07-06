package com.meis.saas.gateway.filter;

import com.meis.saas.common.cache.TokenBlacklistService;
import com.meis.saas.common.security.JwtUtil;
import com.meis.saas.common.tenant.TenantConstants;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklist;
    private final AntPathMatcher matcher = new AntPathMatcher();
    private static final List<String> WHITELIST = List.of(
            "/api/auth/login",
            "/api/auth/platform/login",
            "/api/auth/health",
            "/actuator/**",
            "/v3/api-docs/**",
            "/swagger-ui/**"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (isWhite(path)) {
            return chain.filter(exchange);
        }
        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        String token = auth.substring(7);
        if (!jwtUtil.valid(token)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        if (tokenBlacklist.isBlacklisted(token)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        Claims claims = jwtUtil.parse(token);
        ServerHttpRequest req = exchange.getRequest().mutate()
                .header(TenantConstants.HEADER_TENANT_SCHEMA, str(claims.get("schemaName")))
                .header(TenantConstants.HEADER_TENANT_ID, str(claims.get("tenantId")))
                .header(TenantConstants.HEADER_USER_TYPE, str(claims.get("userType")))
                .header(TenantConstants.HEADER_USER_ID, str(claims.get("userId")))
                .header(TenantConstants.HEADER_USERNAME, str(claims.get("username")))
                .build();
        return chain.filter(exchange.mutate().request(req).build());
    }

    private boolean isWhite(String path) {
        for (String p : WHITELIST) {
            if (matcher.match(p, path)) return true;
        }
        return false;
    }

    private String str(Object o) { return o == null ? "" : o.toString(); }

    @Override
    public int getOrder() { return -100; }
}
