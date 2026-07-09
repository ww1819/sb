package com.meis.saas.tenant.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 本地 dev 环境：迁移脚本合并/变更后，自动 repair 更新 flyway_schema_history 的 checksum，
 * 避免因 V1–V16 合并为 V1–V4 导致校验失败、meis-tenant 无法启动。
 */
@Slf4j
@Configuration
@Profile("dev")
public class DevFlywayConfiguration {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            log.info("dev profile: Flyway repair + migrate (public schema)");
            flyway.repair();
            flyway.migrate();
        };
    }
}
