package com.meis.saas.web;

import com.meis.saas.common.web.GenericTableController;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/power")
public class PowerDomainController extends GenericTableController {
    private final JdbcTemplate jdbcTemplate;
    private static final Set<String> TABLES = Set.of(
            "power_base_station", "power_tag", "power_device_status", "power_monitor_record",
            "power_current_reading", "power_tag_bind_log");

    public PowerDomainController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbcTemplate;
    }

    @Override
    protected Set<String> tables() {
        return TABLES;
    }
}
