package com.meis.saas.common.ops;

import com.meis.saas.common.page.FilterCsvSupport;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 运维设备管理过滤列表（保养/巡检/PM）：台账字段 + 常用检索。
 */
public final class OpsDevicePageSupport {
    private OpsDevicePageSupport() {}

    public record ModuleTables(String planItem, String executionItem, String flagColumn) {}

    public static final ModuleTables MAINTAIN = new ModuleTables(
            "maintenance_plan_item", "maintenance_execution_item", "is_maintain_device");
    public static final ModuleTables INSPECT = new ModuleTables(
            "inspection_plan_item", "inspection_execution_item", "is_inspection_device");
    public static final ModuleTables PM = new ModuleTables(
            "pm_plan_item", "pm_execution_item", "is_pm_device");

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
                null, null, null, null, null, null);
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

        String md = SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", "d");
        String pi = SoftDeleteSupport.notDeletedClause(jdbc, mod.planItem(), "pi");
        String ei = SoftDeleteSupport.notDeletedClause(jdbc, mod.executionItem(), "ei");

        StringBuilder where = new StringBuilder(
                " WHERE ("
                        + " EXISTS (SELECT 1 FROM " + mod.planItem() + " pi WHERE pi.device_id = d.id " + pi + ")"
                        + " OR EXISTS (SELECT 1 FROM " + mod.executionItem() + " ei WHERE ei.device_id = d.id " + ei + ")"
                        + " )");
        where.append(md);
        List<Object> args = new ArrayList<>();

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
                       (SELECT MIN(pi.next_due_date) FROM %s pi WHERE pi.device_id = d.id %s) AS next_due_date
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
                fromJoins,
                where);

        var rows = jdbc.queryForList(sql, pageArgs.toArray());
        return new PageResult<>(rows, total != null ? total : 0, query.getPage(), query.getSize());
    }

    private static void appendLike(StringBuilder where, List<Object> args, String col, String value) {
        if (!StringUtils.hasText(value)) return;
        where.append(" AND ").append(col).append(" ILIKE ? ");
        args.add("%" + value.trim() + "%");
    }
}
