package com.meis.saas.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
@RequiredArgsConstructor
public class JwtUtil {
    private final JwtProperties props;

    public String generate(String userId, String username, String tenantId, String tenantCode,
                           String schemaName, List<String> roles, Map<String, Object> permissions) {
        return generate(userId, username, tenantId, tenantCode, schemaName, roles, permissions, "tenant");
    }

    public String generate(String userId, String username, String tenantId, String tenantCode,
                           String schemaName, List<String> roles, Map<String, Object> permissions,
                           String userType) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("tenantId", tenantId);
        claims.put("tenantCode", tenantCode);
        claims.put("schemaName", schemaName);
        claims.put("roles", roles);
        claims.put("permissions", permissions);
        claims.put("userType", userType != null ? userType : "tenant");
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuer(props.getIssuer())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + props.getExpirationMs()))
                .signWith(key())
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
    }

    public boolean valid(String token) {
        try { parse(token); return true; } catch (Exception e) { return false; }
    }

    private SecretKey key() {
        byte[] bytes = props.getSecret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, Math.min(bytes.length, 32));
            bytes = padded;
        }
        return Keys.hmacShaKeyFor(bytes);
    }
}
