package com.meis.saas.analytics.service;

import com.meis.saas.common.persistence.SoftDeleteSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ScreenDashboardService {
    /** 前端虚拟仓 ID：无 warehouse_id 的设备归入此节点 */
    private static final String UNASSIGNED_WAREHOUSE_ID = "unassigned";
    private static final int DEVICES_PER_WAREHOUSE_LIMIT = 80;

    private final JdbcTemplate jdbc;

    public Map<String, Object> equipmentDashboard() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("kpis", buildKpis());
        data.put("deviceStatus", deviceStatusChart());
        data.put("deptDistribution", deptDistribution());
        data.put("repairTrend", repairTrend());
        data.put("repairDynamic", repairDynamic());
        data.put("qcDueList", qcDueList());
        data.put("benefitTop", benefitTop());
        data.put("powerOverview", powerOverview());
        data.put("purchaseBrief", purchaseBrief());
        return data;
    }

    /** 库房数字孪生专用数据（独立大屏） */
    public Map<String, Object> warehouseTwinDashboard() {
        return warehouseTwin();
    }

    private Map<String, Object> buildKpis() {
        Map<String, Object> k = new LinkedHashMap<>();
        k.put("deviceCount", count("SELECT COUNT(*) FROM medical_device WHERE is_active = true"));
        k.put("openRepairs", count("SELECT COUNT(*) FROM repair_workorder WHERE status NOT IN ('closed','cancelled','verified')"));
        k.put("activeMaintenance", count("SELECT COUNT(*) FROM maintenance_plan WHERE status = 'active'"));
        k.put("alarmDevices", count("SELECT COUNT(*) FROM power_device_status WHERE work_state = 'alarm'"));
        k.put("dueQcCount", count("""
                SELECT COUNT(*) FROM (
                    SELECT id FROM maintenance_plan WHERE status = 'active' AND next_due_date <= CURRENT_DATE + 7
                    UNION ALL
                    SELECT id FROM inspection_plan WHERE status = 'active' AND next_due_date <= CURRENT_DATE + 7
                    UNION ALL
                    SELECT id FROM metrology_plan WHERE status = 'active' AND next_due_date <= CURRENT_DATE + 7
                    UNION ALL
                    SELECT id FROM pm_plan WHERE status = 'active' AND next_due_date <= CURRENT_DATE + 7
                ) t
                """));
        k.put("pendingApprovals", count("SELECT COUNT(*) FROM sys_approval_instance WHERE status = 'pending'"));
        k.put("lifeSupportCount", count("SELECT COUNT(*) FROM medical_device WHERE is_life_support = true AND is_active = true"));
        k.put("totalAssetValue", jdbc.queryForObject(
                "SELECT COALESCE(SUM(original_value),0) FROM medical_device WHERE is_active = true", Number.class));
        return k;
    }

    private List<Map<String, Object>> deviceStatusChart() {
        return jdbc.queryForList(
                "SELECT COALESCE(device_status,'未知') AS name, COUNT(*) AS value FROM medical_device GROUP BY device_status ORDER BY value DESC");
    }

    private List<Map<String, Object>> deptDistribution() {
        return jdbc.queryForList("""
                SELECT COALESCE(d.dept_name,'未分配') AS name, COUNT(m.id) AS value
                FROM medical_device m
                LEFT JOIN department d ON d.id = m.dept_id
                WHERE m.is_active = true
                GROUP BY d.dept_name ORDER BY value DESC LIMIT 12
                """);
    }

    private List<Map<String, Object>> repairTrend() {
        return jdbc.queryForList("""
                SELECT TO_CHAR(created_at,'YYYY-MM') AS month, COUNT(*) AS count
                FROM repair_workorder WHERE created_at > NOW() - INTERVAL '12 months'
                GROUP BY 1 ORDER BY 1
                """);
    }

    private List<Map<String, Object>> repairDynamic() {
        return jdbc.queryForList("""
                SELECT w.wo_no, w.device_name, w.status, w.fault_desc, d.dept_name, w.created_at
                FROM repair_workorder w
                LEFT JOIN department d ON d.id = w.report_dept_id
                WHERE w.status NOT IN ('closed','cancelled')
                ORDER BY w.created_at DESC LIMIT 12
                """);
    }

    private List<Map<String, Object>> qcDueList() {
        return jdbc.queryForList("""
                SELECT * FROM (
                    SELECT '保养' AS qc_type, p.plan_name AS title, d.device_name, p.next_due_date AS due_date
                    FROM maintenance_plan p LEFT JOIN medical_device d ON d.id = p.device_id
                    WHERE p.status = 'active' AND p.next_due_date <= CURRENT_DATE + 7
                    UNION ALL
                    SELECT '巡检', p.plan_name, d.device_name, p.next_due_date
                    FROM inspection_plan p LEFT JOIN medical_device d ON d.id = p.device_id
                    WHERE p.status = 'active' AND p.next_due_date <= CURRENT_DATE + 7
                    UNION ALL
                    SELECT '计量', p.plan_name, d.device_name, p.next_due_date
                    FROM metrology_plan p LEFT JOIN medical_device d ON d.id = p.device_id
                    WHERE p.status = 'active' AND p.next_due_date <= CURRENT_DATE + 7
                    UNION ALL
                    SELECT 'PM', p.plan_name, d.device_name, p.next_due_date
                    FROM pm_plan p LEFT JOIN medical_device d ON d.id = p.device_id
                    WHERE p.status = 'active' AND p.next_due_date <= CURRENT_DATE + 7
                ) t ORDER BY due_date LIMIT 15
                """);
    }

    private List<Map<String, Object>> benefitTop() {
        return jdbc.queryForList("""
                SELECT s.device_code, s.device_name, s.net_profit, s.profit_rate, s.benefit_level, dept.dept_name
                FROM device_benefit_summary s
                LEFT JOIN medical_device d ON d.id = s.device_id
                LEFT JOIN department dept ON dept.id = d.dept_id
                WHERE s.summary_year = EXTRACT(YEAR FROM CURRENT_DATE)::int
                  AND s.summary_month = EXTRACT(MONTH FROM CURRENT_DATE)::int
                ORDER BY s.net_profit DESC NULLS LAST LIMIT 8
                """);
    }

    private Map<String, Object> powerOverview() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("stateCounts", jdbc.queryForList(
                "SELECT work_state AS name, COUNT(*) AS value FROM power_device_status GROUP BY work_state"));
        p.put("todayEnergyKwh", jdbc.queryForObject(
                "SELECT COALESCE(SUM(energy_kwh),0) FROM power_monitor_record WHERE record_date = CURRENT_DATE", Number.class));
        p.put("runningCount", count("SELECT COUNT(*) FROM power_device_status WHERE work_state = 'running'"));
        return p;
    }

    private Map<String, Object> purchaseBrief() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("pendingPlans", count("SELECT COUNT(*) FROM purchase_plan WHERE approval_status IN ('draft','pending')"));
        p.put("activeContracts", count("SELECT COUNT(*) FROM purchase_contract WHERE status = 'active'"));
        p.put("pendingAcceptance", count("SELECT COUNT(*) FROM purchase_acceptance WHERE status IN ('pending','partial')"));
        p.put("monthEntries", count("SELECT COUNT(*) FROM device_entry WHERE created_at >= date_trunc('month', CURRENT_DATE)"));
        return p;
    }

    /**
     * 演示级库房数字孪生数据：仓库列表 + 每仓状态聚合 + 每仓设备样本（上限 80）。
     * 无 warehouse_id 的设备归入虚拟仓「未分配」。
     */
    private Map<String, Object> warehouseTwin() {
        Map<String, Object> twin = new LinkedHashMap<>();
        String whNotDel = SoftDeleteSupport.notDeletedClause(jdbc, "warehouse", "w");
        String mdNotDel = SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", "d");

        List<Map<String, Object>> warehouses = new ArrayList<>();
        Map<String, List<Map<String, Object>>> devicesByWarehouse = new LinkedHashMap<>();

        List<Map<String, Object>> whRows = jdbc.queryForList("""
                SELECT w.id, w.warehouse_code, w.warehouse_name
                FROM warehouse w
                WHERE COALESCE(w.is_active, true) = true
                """ + whNotDel + """
                ORDER BY w.sort_order NULLS LAST, w.warehouse_code
                """);

        for (Map<String, Object> wh : whRows) {
            String id = String.valueOf(wh.get("id"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", id);
            item.put("warehouse_code", wh.get("warehouse_code"));
            item.put("warehouse_name", wh.get("warehouse_name"));
            item.put("virtual", false);
            fillWarehouseDeviceStats(item, devicesByWarehouse, id, false, mdNotDel);
            warehouses.add(item);
        }

        long unassignedCount = count("""
                SELECT COUNT(*) FROM medical_device d
                WHERE d.warehouse_id IS NULL AND COALESCE(d.is_active, true) = true
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", "d"));
        if (unassignedCount > 0 || warehouses.isEmpty()) {
            Map<String, Object> virtual = new LinkedHashMap<>();
            virtual.put("id", UNASSIGNED_WAREHOUSE_ID);
            virtual.put("warehouse_code", "UNASSIGNED");
            virtual.put("warehouse_name", "未分配库房");
            virtual.put("virtual", true);
            fillWarehouseDeviceStats(virtual, devicesByWarehouse, UNASSIGNED_WAREHOUSE_ID, true, mdNotDel);
            warehouses.add(virtual);
        }

        warehouses.sort((a, b) -> Long.compare(
                ((Number) b.getOrDefault("deviceCount", 0L)).longValue(),
                ((Number) a.getOrDefault("deviceCount", 0L)).longValue()));

        twin.put("warehouses", warehouses);
        twin.put("devicesByWarehouse", devicesByWarehouse);
        return twin;
    }

    private void fillWarehouseDeviceStats(
            Map<String, Object> item,
            Map<String, List<Map<String, Object>>> devicesByWarehouse,
            String warehouseId,
            boolean unassigned,
            String mdNotDel) {
        String whFilter = unassigned
                ? " AND d.warehouse_id IS NULL "
                : " AND d.warehouse_id = ?::uuid ";

        String countSql = """
                SELECT COUNT(*) FROM medical_device d
                WHERE COALESCE(d.is_active, true) = true
                """ + whFilter + mdNotDel;
        long deviceCount = unassigned
                ? count(countSql)
                : count(countSql, warehouseId);
        item.put("deviceCount", deviceCount);

        String statusSql = """
                SELECT COALESCE(d.device_status, 'unknown') AS status, COUNT(*) AS cnt
                FROM medical_device d
                WHERE COALESCE(d.is_active, true) = true
                """ + whFilter + mdNotDel + """
                GROUP BY d.device_status
                """;
        List<Map<String, Object>> statusRows = unassigned
                ? jdbc.queryForList(statusSql)
                : jdbc.queryForList(statusSql, warehouseId);
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (Map<String, Object> row : statusRows) {
            statusCounts.put(String.valueOf(row.get("status")), ((Number) row.get("cnt")).longValue());
        }
        item.put("statusCounts", statusCounts);

        String deviceSql = """
                SELECT d.id, d.device_code, d.device_name, d.device_status, dept.dept_name
                FROM medical_device d
                LEFT JOIN department dept ON dept.id = d.dept_id
                WHERE COALESCE(d.is_active, true) = true
                """ + whFilter + mdNotDel + """
                ORDER BY d.updated_at DESC NULLS LAST, d.device_code
                LIMIT ?
                """;
        List<Map<String, Object>> devices = unassigned
                ? jdbc.queryForList(deviceSql, DEVICES_PER_WAREHOUSE_LIMIT)
                : jdbc.queryForList(deviceSql, warehouseId, DEVICES_PER_WAREHOUSE_LIMIT);
        devicesByWarehouse.put(warehouseId, devices);
    }

    private long count(String sql) {
        Long v = jdbc.queryForObject(sql, Long.class);
        return v != null ? v : 0;
    }

    private long count(String sql, Object... args) {
        Long v = jdbc.queryForObject(sql, Long.class, args);
        return v != null ? v : 0;
    }
}
