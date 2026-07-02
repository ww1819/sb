package com.meis.saas.web;

import com.meis.saas.common.web.GenericTableController;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Set;

@RestController
@RequestMapping("/api/qc")
public class DomainController extends GenericTableController {
    private final JdbcTemplate jdbcTemplate;
    private static final Set<String> TABLES = Set.of("risk_assessment","adverse_event","metrology_record","performance_test");
    public DomainController(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }
    @Override protected JdbcTemplate jdbc() { return jdbcTemplate; }
    @Override protected Set<String> tables() { return TABLES; }
}
