package com.meis.saas.web;

import com.meis.saas.common.web.GenericTableController;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Set;

@RestController
@RequestMapping("/api/maintain")
public class DomainController extends GenericTableController {
    private final JdbcTemplate jdbcTemplate;
    private static final Set<String> TABLES = Set.of(
            "maintenance_level", "maintenance_template", "maintenance_template_item",
            "maintenance_plan", "maintenance_record", "maintenance_execution",
            "maintenance_execution_item", "maintenance_execution_result",
            "pm_type", "pm_template", "pm_template_item", "pm_plan",
            "pm_execution", "pm_execution_item", "pm_execution_result");
    public DomainController(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }
    @Override protected JdbcTemplate jdbc() { return jdbcTemplate; }
    @Override protected Set<String> tables() { return TABLES; }
}
