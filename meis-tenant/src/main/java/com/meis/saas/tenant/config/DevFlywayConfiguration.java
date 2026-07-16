package com.meis.saas.tenant.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 本地 dev 环境：迁移脚本合并/变更后，自动 repair 更新 flyway_schema_history 的 checksum，
 * 并忽略已删除的版本化脚本（V5–V19 / V20–V21 已并入 public/R__data_fix.sql）。
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
