package com.meis.saas.qc.controller;

import com.meis.saas.common.web.GenericTableController;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/metrology")
public class MetrologyDomainController extends GenericTableController {
    private final JdbcTemplate jdbcTemplate;
    private static final Set<String> TABLES = Set.of(
            "metrology_category", "metrology_type", "metrology_org", "metrology_template", "metrology_template_item",
            "metrology_plan", "metrology_record", "metrology_execution",
            "metrology_execution_item", "metrology_execution_result");

    public MetrologyDomainController(JdbcTemplate jdbcTemplate) {
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
