package com.meis.saas.common.cache;

import com.meis.saas.common.security.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class TokenBlacklistService {
    private final RedisJsonCache cache;
    private final JwtUtil jwtUtil;

    public void blacklist(String token) {
        try {
            Claims claims = jwtUtil.parse(token);
            String jti = jwtUtil.getJti(claims);
            long ttlMs = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (ttlMs > 0 && jti != null) {
                cache.setString(CacheKeys.tokenBlacklist(jti), "1", Duration.ofMillis(ttlMs));
            }
        } catch (Exception ignored) {
        }
    }

    public boolean isBlacklisted(String token) {
        try {
            Claims claims = jwtUtil.parse(token);
            String jti = jwtUtil.getJti(claims);
            if (jti == null) return false;
            return cache.hasKey(CacheKeys.tokenBlacklist(jti));
        } catch (Exception e) {
            return true;
        }
    }
}
