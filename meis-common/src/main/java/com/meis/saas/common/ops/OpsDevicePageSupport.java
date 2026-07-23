package com.meis.saas.common.ops;

import com.meis.saas.common.excel.CsvExportHelper;
import com.meis.saas.common.page.FilterCsvSupport;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;

/**
 * 运维设备管理过滤列表（保养/巡检/PM）：台账字段 + 常用检索。
 */
public final class OpsDevicePageSupport {
    private OpsDevicePageSupport() {}

    public record ModuleTables(
            String planItem,
            String executionItem,
            String flagColumn,
            String planTable,
            String executionTable) {}

    public static final ModuleTables MAINTAIN = new ModuleTables(
            "maintenance_plan_item", "maintenance_execution_item", "is_maintain_device",
            "maintenance_plan", "maintenance_execution");
    public static final ModuleTables INSPECT = new ModuleTables(
            "inspection_plan_item", "inspection_execution_item", "is_inspection_device",
            "inspection_plan", "inspection_execution");
    public static final ModuleTables PM = new ModuleTables(
            "pm_plan_item", "pm_execution_item", "is_pm_device",
            "pm_plan", "pm_execution");

    public static PageResult<Map<String, Object>> page(
            JdbcTemplate jdbc,
            ModuleTables mod,
            PageQuery query,
            String deviceStatus,
            String deptId,
            String manageDeptId,
            String deviceCode,
            String deviceName,
            String serialNumber,
            String specification,
            String model,
            String brand,
            Integer dueWithinDays) {
        return page(jdbc, mod, query, deviceStatus, deptId, manageDeptId,
                deviceCode, deviceName, serialNumber, specification, model, brand, dueWithinDays,
                null, null, null, null, null, null, null);
    }

    public static PageResult<Map<String, Object>> page(
            JdbcTemplate jdbc,
            ModuleTables mod,
            PageQuery query,
            String deviceStatus,
            String deptId,
            String manageDeptId,
            String deviceCode,
            String deviceName,
            String serialNumber,
            String specification,
            String model,
            String brand,
            Integer dueWithinDays,
            String categoryId,
            String assetCategoryId,
            String financeCategoryId,
            String categoryKw,
            String assetCategoryKw,
            String financeCategoryKw) {
        return page(jdbc, mod, query, deviceStatus, deptId, manageDeptId,
                deviceCode, deviceName, serialNumber, specification, model, brand, dueWithinDays,
                categoryId, assetCategoryId, financeCategoryId,
                categoryKw, assetCategoryKw, financeCategoryKw, null);
    }

    public static PageResult<Map<String, Object>> page(
            JdbcTemplate jdbc,
            ModuleTables mod,
            PageQuery query,
            String deviceStatus,
            String deptId,
            String manageDeptId,
            String deviceCode,
            String deviceName,
            String serialNumber,
            String specification,
            String model,
            String brand,
            Integer dueWithinDays,
            String categoryId,
            String assetCategoryId,
            String financeCategoryId,
            String categoryKw,
            String assetCategoryKw,
            String financeCategoryKw,
            String ids) {

        String md = SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", "d");
        String pi = SoftDeleteSupport.notDeletedClause(jdbc, mod.planItem(), "pi");
        String ei = SoftDeleteSupport.notDeletedClause(jdbc, mod.executionItem(), "ei");
        String p = SoftDeleteSupport.notDeletedClause(jdbc, mod.planTable(), "p");
        String e = SoftDeleteSupport.notDeletedClause(jdbc, mod.executionTable(), "e");

        StringBuilder where = new StringBuilder(
                " WHERE ("
                        + " EXISTS (SELECT 1 FROM " + mod.planItem() + " pi WHERE pi.device_id = d.id " + pi + ")"
                        + " OR EXISTS (SELECT 1 FROM " + mod.executionItem() + " ei WHERE ei.device_id = d.id " + ei + ")"
                        + " )");
        where.append(md);
        List<Object> args = new ArrayList<>();

        FilterCsvSupport.appendUuidIn(where, args, "d.id", ids);

        if (StringUtils.hasText(query.getKeyword())) {
            String kw = "%" + query.getKeyword().trim() + "%";
            where.append("""
                     AND (d.device_code ILIKE ? OR d.device_name ILIKE ? OR d.serial_number ILIKE ?
                          OR d.specification ILIKE ? OR d.model ILIKE ? OR d.brand ILIKE ?
                          OR d.pinyin_code ILIKE ?)
                    """);
            for (int i = 0; i < 7; i++) args.add(kw);
        }
        appendLike(where, args, "d.device_code", deviceCode);
        FilterCsvSupport.appendCodeNamePinyin(where, args, null, "d.device_name", "d.pinyin_code", deviceName);
        appendLike(where, args, "d.serial_number", serialNumber);
        appendLike(where, args, "d.specification", specification);
        appendLike(where, args, "d.model", model);
        appendLike(where, args, "d.brand", brand);
        FilterCsvSupport.appendUuidIn(where, args, "d.dept_id", deptId);
        FilterCsvSupport.appendUuidIn(where, args, "d.manage_dept_id", manageDeptId);
        FilterCsvSupport.appendStrIn(where, args, "d.device_status", deviceStatus);
        FilterCsvSupport.appendUuidIn(where, args, "d.category_id", categoryId);
        FilterCsvSupport.appendUuidIn(where, args, "d.asset_category_id", assetCategoryId);
        FilterCsvSupport.appendUuidIn(where, args, "d.finance_category_id", financeCategoryId);
        FilterCsvSupport.appendCodeNamePinyin(where, args, "cat.category_code", "cat.category_name", null, categoryKw);
        FilterCsvSupport.appendCodeNamePinyin(where, args, "ac.category_code", "ac.category_name", null, assetCategoryKw);
        FilterCsvSupport.appendCodeNamePinyin(where, args, "fc.finance_code", "fc.finance_name", null, financeCategoryKw);

        if (dueWithinDays != null && dueWithinDays > 0) {
            where.append(" AND EXISTS (SELECT 1 FROM ").append(mod.planItem()).append(" pi")
                    .append(" WHERE pi.device_id = d.id ").append(pi)
                    .append(" AND pi.next_due_date IS NOT NULL")
                    .append(" AND pi.next_due_date <= CURRENT_DATE + ?::int) ");
            args.add(dueWithinDays);
        }

        String fromJoins = """
                LEFT JOIN department dept ON dept.id = d.dept_id
                LEFT JOIN department mgr ON mgr.id = d.manage_dept_id
                LEFT JOIN medical_device_category cat ON cat.id = d.category_id
                LEFT JOIN asset_category ac ON ac.id = d.asset_category_id
                LEFT JOIN finance_category fc ON fc.id = d.finance_category_id
                """;

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM medical_device d " + fromJoins + where, Long.class, args.toArray());
        int offset = (query.getPage() - 1) * query.getSize();
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.getSize());
        pageArgs.add(offset);

        String latestPlan = """
                (SELECT p.plan_no FROM %s pi
                 INNER JOIN %s p ON p.id = pi.plan_id
                 WHERE pi.device_id = d.id %s %s
                 ORDER BY p.created_at DESC NULLS LAST LIMIT 1)
                """.formatted(mod.planItem(), mod.planTable(), pi, p);
        String latestPlanId = """
                (SELECT p.id::text FROM %s pi
                 INNER JOIN %s p ON p.id = pi.plan_id
                 WHERE pi.device_id = d.id %s %s
                 ORDER BY p.created_at DESC NULLS LAST LIMIT 1)
                """.formatted(mod.planItem(), mod.planTable(), pi, p);
        String latestExec = """
                (SELECT e.execution_no FROM %s ei
                 INNER JOIN %s e ON e.id = ei.execution_id
                 WHERE ei.device_id = d.id %s %s
                 ORDER BY e.created_at DESC NULLS LAST LIMIT 1)
                """.formatted(mod.executionItem(), mod.executionTable(), ei, e);
        String latestExecId = """
                (SELECT e.id::text FROM %s ei
                 INNER JOIN %s e ON e.id = ei.execution_id
                 WHERE ei.device_id = d.id %s %s
                 ORDER BY e.created_at DESC NULLS LAST LIMIT 1)
                """.formatted(mod.executionItem(), mod.executionTable(), ei, e);

        String sql = """
                SELECT d.id, d.device_code, d.device_name, d.brand, d.specification, d.model,
                       d.serial_number, d.device_status, d.risk_level, d.enable_date,
                       d.dept_id, d.manage_dept_id, d.%s AS module_flag,
                       dept.dept_name, mgr.dept_name AS manage_dept_name,
                       cat.category_name,
                       ac.category_name AS asset_category_name,
                       fc.finance_name AS finance_category_name,
                       (SELECT COUNT(*) FROM %s pi WHERE pi.device_id = d.id %s) AS plan_count,
                       (SELECT COUNT(*) FROM %s ei WHERE ei.device_id = d.id %s) AS execution_item_count,
                       (SELECT MIN(pi.next_due_date) FROM %s pi WHERE pi.device_id = d.id %s) AS next_due_date,
                       %s AS latest_plan_no,
                       %s AS latest_plan_id,
                       %s AS latest_execution_no,
                       %s AS latest_execution_id
                FROM medical_device d
                %s
                %s
                ORDER BY next_due_date NULLS LAST, d.device_code
                LIMIT ? OFFSET ?
                """.formatted(
                mod.flagColumn(),
                mod.planItem(), pi,
                mod.executionItem(), ei,
                mod.planItem(), pi,
                latestPlan.trim(),
                latestPlanId.trim(),
                latestExec.trim(),
                latestExecId.trim(),
                fromJoins,
                where);

        var rows = jdbc.queryForList(sql, pageArgs.toArray());
        return new PageResult<>(rows, total != null ? total : 0, query.getPage(), query.getSize());
    }

    /** OPS.16.11：按勾选 ids 或当前筛选导出 */
    public static void export(
            JdbcTemplate jdbc,
            ModuleTables mod,
            HttpServletResponse resp,
            String filename,
            PageQuery query,
            String deviceStatus,
            String deptId,
            String manageDeptId,
            String deviceCode,
            String deviceName,
            String serialNumber,
            String specification,
            String model,
            String brand,
            Integer dueWithinDays,
            String categoryId,
            String assetCategoryId,
            String financeCategoryId,
            String categoryKw,
            String assetCategoryKw,
            String financeCategoryKw,
            String ids) throws IOException {
        PageQuery q = new PageQuery();
        q.setPage(1);
        q.setSize(50_000);
        q.setKeyword(query != null ? query.getKeyword() : null);
        PageResult<Map<String, Object>> result = page(
                jdbc, mod, q, deviceStatus, deptId, manageDeptId,
                deviceCode, deviceName, serialNumber, specification, model, brand, dueWithinDays,
                categoryId, assetCategoryId, financeCategoryId,
                categoryKw, assetCategoryKw, financeCategoryKw, ids);
        String[] headers = {
                "device_code", "device_name", "brand", "specification", "model", "serial_number",
                "category_name", "dept_name", "manage_dept_name", "device_status", "risk_level",
                "enable_date", "next_due_date", "latest_plan_no", "latest_execution_no",
                "plan_count", "execution_item_count"
        };
        CsvExportHelper.writeRows(resp, filename, headers, result.getRecords());
    }

    private static void appendLike(StringBuilder where, List<Object> args, String col, String value) {
        if (!StringUtils.hasText(value)) return;
        where.append(" AND ").append(col).append(" ILIKE ? ");
        args.add("%" + value.trim() + "%");
    }
}
