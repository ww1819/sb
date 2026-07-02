package com.meis.saas.common.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "meis.jwt")
public class JwtProperties {
    private String secret = "meis-saas-jwt-secret-change-in-production-256bits";
    private long expirationMs = 86400000L;
    private String issuer = "meis-saas";
}
