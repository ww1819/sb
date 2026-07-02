package com.meis.saas.common.flyway;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import javax.sql.DataSource;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantFlywayService {
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Value("${meis.flyway.tenant-locations:classpath:db/migrations/tenant}")
    private String tenantLocations;

    public void createSchema(String schemaName) {
        if (!schemaName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) throw new IllegalArgumentException("bad schema");
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
    }

    public void migrate(String schemaName) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schemaName)
                .defaultSchema(schemaName)
                .locations(tenantLocations)
                .baselineOnMigrate(true)
                .load();
        safeMigrate(flyway, schemaName);
    }

    public void migratePublic(String publicLocations) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas("public")
                .defaultSchema("public")
                .locations(publicLocations)
                .baselineOnMigrate(true)
                .load();
        safeMigrate(flyway, "public");
    }

    private void safeMigrate(Flyway flyway, String label) {
        try {
            flyway.migrate();
        } catch (FlywayValidateException e) {
            log.warn("Flyway migrate failed for {}, repairing and retrying: {}", label, e.getMessage());
            flyway.repair();
            flyway.migrate();
        } catch (RuntimeException e) {
            if (e.getClass().getName().contains("FlywayMigrate")) {
                log.warn("Flyway migrate failed for {}, repairing and retrying: {}", label, e.getMessage());
                flyway.repair();
                flyway.migrate();
            } else {
                throw e;
            }
        }
        log.info("Migrated schema {}", label);
    }
}
