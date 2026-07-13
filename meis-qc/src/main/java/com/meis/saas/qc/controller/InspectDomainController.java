package com.meis.saas.qc.controller;

import com.meis.saas.common.web.GenericTableController;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/inspect")
public class InspectDomainController extends GenericTableController {
    private final JdbcTemplate jdbcTemplate;
    private static final Set<String> TABLES = Set.of(
            "inspection_type", "inspection_template", "inspection_template_item",
            "inspection_plan", "inspection_record", "inspection_record_item",
            "inspection_execution", "inspection_execution_item", "inspection_execution_result");

    public InspectDomainController(JdbcTemplate jdbcTemplate) {
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
