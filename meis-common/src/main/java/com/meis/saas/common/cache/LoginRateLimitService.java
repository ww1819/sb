package com.meis.saas.common.cache;

import com.meis.saas.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginRateLimitService {
    private final RedisJsonCache cache;
    private final MeisCacheProperties props;

    public void checkAllowed(String tenantCode, String username) {
        String key = loginKey(tenantCode, username);
        String val = cache.getString(key);
        if (val != null) {
            try {
                if (Long.parseLong(val) >= props.getLoginMaxAttempts()) {
                    throw new BizException(429, "登录尝试次数过多，请稍后再试");
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    public void onFailure(String tenantCode, String username) {
        String key = loginKey(tenantCode, username);
        Long fails = cache.increment(key, props.getLoginLockTtl());
        if (fails >= props.getLoginMaxAttempts()) {
            throw new BizException(429, "登录尝试次数过多，请稍后再试");
        }
    }

    public void onSuccess(String tenantCode, String username) {
        cache.delete(loginKey(tenantCode, username));
    }

    private String loginKey(String tenantCode, String username) {
        return CacheKeys.loginFail(tenantCode == null ? "platform" : tenantCode, username);
    }
}
