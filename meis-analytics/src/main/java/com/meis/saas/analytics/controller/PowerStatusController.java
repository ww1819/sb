package com.meis.saas.analytics.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.result.Result;
import com.meis.saas.analytics.service.PowerMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/power/status")
@RequiredArgsConstructor
public class PowerStatusController {
    private final JdbcTemplate jdbc;
    private final PowerMonitorService monitorService;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query,
            @RequestParam(required = false) String workState) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (workState != null && !workState.isBlank()) {
            where.append(" AND s.work_state = ? ");
            args.add(workState);
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String kw = "%" + query.getKeyword().trim() + "%";
            where.append(" AND (s.device_code ILIKE ? OR s.device_name ILIKE ?) ");
            args.add(kw);
            args.add(kw);
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM power_device_status s" + where, Long.class, args.toArray());
        int offset = (query.getPage() - 1) * query.getSize();
        args.add(query.getSize());
        args.add(offset);
        var rows = jdbc.queryForList("""
                SELECT s.*, t.tag_code, t.tag_name, st.station_name, dept.dept_name
                FROM power_device_status s
                LEFT JOIN power_tag t ON t.id = s.tag_id
                LEFT JOIN power_base_station st ON st.id = t.station_id
                LEFT JOIN medical_device d ON d.id = s.device_id
                LEFT JOIN department dept ON dept.id = d.dept_id
                """ + where + " ORDER BY s.collected_at DESC LIMIT ? OFFSET ?", args.toArray());
        return Result.ok(new PageResult<>(rows, total, query.getPage(), query.getSize()));
    }

    @PostMapping("/collect")
    @OperationLog(module = "power", description = "采集电流状态")
    public Result<Map<String, Object>> collect() {
        int count = monitorService.collectSnapshot();
        return Result.ok(Map.of("collected", count));
    }
}
