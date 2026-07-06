package com.meis.saas.common.cache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "meis.cache")
public class MeisCacheProperties {
    private boolean enabled = true;
    private Duration userPermTtl = Duration.ofMinutes(5);
    private Duration tenantMenuTtl = Duration.ofMinutes(30);
    private Duration menuTreeTtl = Duration.ofMinutes(30);
    private Duration menuNavTtl = Duration.ofMinutes(10);
    private Duration dictTtl = Duration.ofMinutes(30);
    private Duration orgTtl = Duration.ofMinutes(30);
    private Duration platformMenuTtl = Duration.ofHours(1);
    private int loginMaxAttempts = 5;
    private Duration loginLockTtl = Duration.ofMinutes(15);
}
