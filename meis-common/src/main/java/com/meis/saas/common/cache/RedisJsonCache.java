package com.meis.saas.common.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisJsonCache {
    private final StringRedisTemplate redis;
    private final MeisCacheProperties props;
    private final ObjectMapper mapper = new ObjectMapper();

    public <T> T getOrLoad(String key, Duration ttl, TypeReference<T> type, Supplier<T> loader) {
        if (!props.isEnabled()) {
            return loader.get();
        }
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                return mapper.readValue(cached, type);
            }
        } catch (Exception e) {
            log.warn("Redis cache read failed for {}: {}", key, e.getMessage());
        }
        T value = loader.get();
        try {
            redis.opsForValue().set(key, mapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            log.warn("Redis cache write failed for {}: {}", key, e.getMessage());
        }
        return value;
    }

    public void evict(String key) {
        if (!props.isEnabled()) return;
        try {
            redis.delete(key);
        } catch (Exception e) {
            log.warn("Redis evict failed for {}: {}", key, e.getMessage());
        }
    }

    public void evictByPattern(String pattern) {
        if (!props.isEnabled()) return;
        try {
            Set<String> keys = redis.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        } catch (Exception e) {
            log.warn("Redis pattern evict failed for {}: {}", pattern, e.getMessage());
        }
    }

    public void setString(String key, String value, Duration ttl) {
        if (!props.isEnabled()) return;
        try {
            redis.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.warn("Redis set failed for {}: {}", key, e.getMessage());
        }
    }

    public boolean hasKey(String key) {
        if (!props.isEnabled()) return false;
        try {
            return Boolean.TRUE.equals(redis.hasKey(key));
        } catch (Exception e) {
            log.warn("Redis hasKey failed for {}: {}", key, e.getMessage());
            return false;
        }
    }

    public Long increment(String key, Duration ttl) {
        if (!props.isEnabled()) return 0L;
        try {
            Long val = redis.opsForValue().increment(key);
            if (val != null && val == 1L) {
                redis.expire(key, ttl);
            }
            return val == null ? 0L : val;
        } catch (Exception e) {
            log.warn("Redis increment failed for {}: {}", key, e.getMessage());
            return 0L;
        }
    }

    public String getString(String key) {
        if (!props.isEnabled()) return null;
        try {
            return redis.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis get failed for {}: {}", key, e.getMessage());
            return null;
        }
    }

    public void delete(String key) {
        evict(key);
    }
}
