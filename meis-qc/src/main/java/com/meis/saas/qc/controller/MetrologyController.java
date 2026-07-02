package com.meis.saas.qc.controller;

import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/qc/metrology")
@RequiredArgsConstructor
public class MetrologyController {
    private final JdbcTemplate jdbc;

    @GetMapping("/due")
    public Result<List<Map<String, Object>>> due() {
        return Result.ok(jdbc.queryForList("""
            SELECT r.*, m.device_code, m.device_name FROM metrology_record r
            JOIN medical_device m ON m.id = r.device_id
            WHERE r.next_due_date <= CURRENT_DATE + 30 ORDER BY r.next_due_date
            """));
    }
}
