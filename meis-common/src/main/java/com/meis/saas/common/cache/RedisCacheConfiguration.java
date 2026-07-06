package com.meis.saas.common.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

@Configuration
@ConditionalOnClass(StringRedisTemplate.class)
@EnableConfigurationProperties(MeisCacheProperties.class)
public class RedisCacheConfiguration {

    @Bean
    public LettuceClientConfigurationBuilderCustomizer redisCommandTimeoutCustomizer() {
        return builder -> builder.commandTimeout(Duration.ofSeconds(2));
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
