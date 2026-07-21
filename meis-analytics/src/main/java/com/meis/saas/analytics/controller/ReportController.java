package com.meis.saas.analytics.controller;

import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class ReportController {
    private final JdbcTemplate jdbc;

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        long devices = count("SELECT COUNT(*) FROM medical_device");
        long workorders = count("SELECT COUNT(*) FROM repair_workorder WHERE status NOT IN ('closed','cancelled')");
        long plans = count("SELECT COUNT(*) FROM maintenance_plan WHERE status = 'active'");
        long pendingApprovals = count("SELECT COUNT(*) FROM sys_approval_instance WHERE status = 'pending'");
        List<Map<String, Object>> repairTrend = jdbc.queryForList(
                "SELECT TO_CHAR(created_at, 'YYYY-MM') AS month, COUNT(*) AS count FROM repair_workorder WHERE created_at > NOW() - INTERVAL '12 months' GROUP BY 1 ORDER BY 1");
        List<Map<String, Object>> brandTop = jdbc.queryForList(
                "SELECT brand, COUNT(*) AS count FROM medical_device WHERE brand IS NOT NULL GROUP BY brand ORDER BY count DESC LIMIT 10");
        List<Map<String, Object>> deptValue = jdbc.queryForList(
                "SELECT d.dept_name, COALESCE(SUM(m.original_value),0) AS total_value FROM department d LEFT JOIN medical_device m ON m.dept_id = d.id GROUP BY d.dept_name ORDER BY total_value DESC LIMIT 10");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deviceCount", devices);
        data.put("openWorkorders", workorders);
        data.put("activeMaintenancePlans", plans);
        data.put("pendingApprovals", pendingApprovals);
        data.put("repairTrend", repairTrend);
        data.put("brandTop10", brandTop);
        data.put("deptValue", deptValue);
        data.put("deviceStatus", jdbc.queryForList("SELECT device_status, COUNT(*) AS count FROM medical_device GROUP BY device_status"));
        data.put("deviceCategory", jdbc.queryForList(
                "SELECT COALESCE(c.category_name, '未分类') AS category_name, COUNT(*) AS count "
                        + "FROM medical_device m "
                        + "LEFT JOIN medical_device_category c ON c.id = m.category_id "
                        + "GROUP BY COALESCE(c.category_name, '未分类') "
                        + "ORDER BY count DESC LIMIT 8"));
        data.put("usageRate", jdbc.queryForList("SELECT device_status, ROUND(COUNT(*)::numeric / NULLIF((SELECT COUNT(*) FROM medical_device),0) * 100, 2) AS rate FROM medical_device GROUP BY device_status"));
        data.put("importDomestic", jdbc.queryForList("SELECT COALESCE(country_of_origin,'未知') AS country, COUNT(*) AS count FROM medical_device GROUP BY country_of_origin"));
        data.put("ageDistribution", jdbc.queryForList("SELECT CASE WHEN purchase_date > CURRENT_DATE - INTERVAL '3 years' THEN '3年内' WHEN purchase_date > CURRENT_DATE - INTERVAL '5 years' THEN '3-5年' ELSE '5年以上' END AS age_group, COUNT(*) FROM medical_device GROUP BY 1"));
        data.put("newDevices", jdbc.queryForList("SELECT TO_CHAR(created_at,'YYYY-MM') AS month, COUNT(*) AS count FROM medical_device WHERE created_at > NOW() - INTERVAL '12 months' GROUP BY 1 ORDER BY 1"));
        return Result.ok(data);
    }

    @GetMapping("/dashboard/todos")
    public Result<List<Map<String, Object>>> todos() {
        List<Map<String, Object>> todos = new ArrayList<>();
        todos.addAll(jdbc.queryForList(
                "SELECT id, title, business_type, created_at, 'approval' AS todo_type FROM sys_approval_instance WHERE status = 'pending' ORDER BY created_at DESC LIMIT 20"));
        todos.addAll(jdbc.queryForList(
                "SELECT id, wo_no AS title, 'repair' AS business_type, created_at, 'workorder' AS todo_type FROM repair_workorder WHERE status IN ('reported','dispatching','pending_accept','accepted','repairing','pending_verify','suspended') ORDER BY created_at DESC LIMIT 20"));
        return Result.ok(todos);
    }

    private long count(String sql) {
        Long v = jdbc.queryForObject(sql, Long.class);
        return v != null ? v : 0;
    }

    @GetMapping("/reports/device-status")
    public Result<List<Map<String, Object>>> deviceStatusReport() {
        return Result.ok(jdbc.queryForList(
                "SELECT device_status, COUNT(*) AS count FROM medical_device GROUP BY device_status ORDER BY count DESC"));
    }

    @GetMapping("/reports/dept-device")
    public Result<List<Map<String, Object>>> deptDeviceReport() {
        return Result.ok(jdbc.queryForList(
                "SELECT d.dept_name, COUNT(m.id) AS device_count FROM department d LEFT JOIN medical_device m ON m.dept_id = d.id GROUP BY d.dept_name ORDER BY device_count DESC"));
    }

    @GetMapping("/reports/repair-volume")
    public Result<List<Map<String, Object>>> repairVolume() {
        return Result.ok(jdbc.queryForList(
                "SELECT d.dept_name, COUNT(w.id) AS count FROM repair_workorder w LEFT JOIN department d ON d.id = w.report_dept_id GROUP BY d.dept_name ORDER BY count DESC"));
    }

    @GetMapping("/reports/fault-type")
    public Result<List<Map<String, Object>>> faultType() {
        return Result.ok(jdbc.queryForList(
                "SELECT f.fault_name, COUNT(w.id) AS count FROM repair_workorder w JOIN fault_type_dict f ON f.id = w.fault_type_id GROUP BY f.fault_name ORDER BY count DESC"));
    }

    @GetMapping("/reports/repair-cost")
    public Result<List<Map<String, Object>>> repairCost() {
        return Result.ok(jdbc.queryForList(
                "SELECT TO_CHAR(created_at,'YYYY-MM') AS month, COALESCE(SUM(total_cost),0) AS cost FROM repair_workorder GROUP BY 1 ORDER BY 1 DESC LIMIT 12"));
    }

    @GetMapping("/reports/benefit-summary")
    public Result<List<Map<String, Object>>> benefitSummary() {
        return Result.ok(jdbc.queryForList(
                "SELECT summary_year, summary_month, SUM(total_revenue) AS revenue, SUM(total_cost) AS cost, SUM(net_profit) AS profit FROM device_benefit_summary GROUP BY summary_year, summary_month ORDER BY summary_year DESC, summary_month DESC LIMIT 24"));
    }
}
