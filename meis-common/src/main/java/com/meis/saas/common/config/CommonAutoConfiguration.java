package com.meis.saas.common.config;

import com.meis.saas.common.cache.MeisCacheProperties;
import com.meis.saas.common.cache.RedisCacheConfiguration;
import com.meis.saas.common.security.JwtProperties;
import com.meis.saas.common.security.JwtUtil;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ComponentScan(basePackages = "com.meis.saas.common")
@EnableConfigurationProperties({JwtProperties.class, MeisCacheProperties.class})
@Import({MybatisPlusConfig.class, JwtUtil.class, TenantDataSourceConfiguration.class, RedisCacheConfiguration.class})
public class CommonAutoConfiguration {}
