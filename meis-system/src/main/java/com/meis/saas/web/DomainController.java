package com.meis.saas.web;

import com.meis.saas.common.web.GenericTableController;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Set;

@RestController
@RequestMapping("/api/system")
public class DomainController extends GenericTableController {
    private final JdbcTemplate jdbcTemplate;
    private static final Set<String> TABLES = Set.of("campus","building","department","sys_user","sys_role","sys_operation_log","sys_config","sys_dict","medical_device_category","supplier","manufacturer","sys_approval_flow","sys_approval_node","warehouse");
    public DomainController(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }
    @Override protected JdbcTemplate jdbc() { return jdbcTemplate; }
    @Override protected Set<String> tables() { return TABLES; }
}
