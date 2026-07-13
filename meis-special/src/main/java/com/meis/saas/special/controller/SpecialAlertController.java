package com.meis.saas.special.controller;

import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/special/alerts")
@RequiredArgsConstructor
public class SpecialAlertController {
    private final JdbcTemplate jdbc;

    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("license_expiring", jdbc.queryForList("""
            SELECT s.id, s.device_code, s.device_name, s.special_type, s.license_no, s.license_expiry_date, 'license' AS alert_type
            FROM special_device s
            WHERE s.license_expiry_date IS NOT NULL AND s.license_expiry_date <= CURRENT_DATE + INTERVAL '30 days'
            ORDER BY s.license_expiry_date LIMIT 50
            """));
        result.put("lease_expiring", jdbc.queryForList("""
            SELECT l.id, l.device_code, l.device_name, l.lease_end_date, l.monthly_rent, l.status, 'lease' AS alert_type
            FROM leased_device l
            WHERE l.status = 'active' AND l.lease_end_date IS NOT NULL
              AND l.lease_end_date <= CURRENT_DATE + INTERVAL '30 days'
            ORDER BY l.lease_end_date LIMIT 50
            """));
        result.put("test_due", jdbc.queryForList("""
            SELECT ls.id, ls.device_code, ls.device_name, ls.next_test_date, ls.standby_status, 'life_test' AS alert_type
            FROM life_support_device ls
            WHERE ls.next_test_date IS NOT NULL AND ls.next_test_date <= CURRENT_DATE + INTERVAL '30 days'
            ORDER BY ls.next_test_date LIMIT 50
            """));
        result.put("inspection_due", jdbc.queryForList("""
            SELECT s.id, s.device_code, s.device_name, s.next_inspection_date, s.special_type, 'inspection' AS alert_type
            FROM special_device s
            WHERE s.next_inspection_date IS NOT NULL AND s.next_inspection_date <= CURRENT_DATE + INTERVAL '30 days'
            ORDER BY s.next_inspection_date LIMIT 50
            """));
        result.put("total_count",
                ((List<?>) result.get("license_expiring")).size()
                        + ((List<?>) result.get("lease_expiring")).size()
                        + ((List<?>) result.get("test_due")).size()
                        + ((List<?>) result.get("inspection_due")).size());
        return Result.ok(result);
    }
}
