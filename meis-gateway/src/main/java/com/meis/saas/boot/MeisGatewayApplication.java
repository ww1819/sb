package com.meis.saas.boot;

import com.meis.saas.common.cache.MeisCacheProperties;
import com.meis.saas.common.cache.RedisCacheConfiguration;
import com.meis.saas.common.security.JwtProperties;
import com.meis.saas.common.security.JwtUtil;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.boot.SpringApplication;

@SpringBootApplication(
        scanBasePackages = {"com.meis.saas.boot", "com.meis.saas.gateway", "com.meis.saas.common.cache"},
        exclude = {
                DataSourceAutoConfiguration.class,
                DataSourceTransactionManagerAutoConfiguration.class,
                FlywayAutoConfiguration.class
        }
)
@EnableConfigurationProperties({JwtProperties.class, MeisCacheProperties.class})
@Import({JwtUtil.class, RedisCacheConfiguration.class})
public class MeisGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(MeisGatewayApplication.class, args);
    }
}
