package com.meis.saas.asset.controller;

import com.meis.saas.common.page.FilterCsvSupport;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/asset/medical_device")
@RequiredArgsConstructor
public class MedicalDeviceQueryController {

    private final JdbcTemplate jdbc;

    @GetMapping("/query/page")
    public Result<PageResult<Map<String, Object>>> queryPage(
            PageQuery query,
            @RequestParam(required = false) String campusId,
            @RequestParam(required = false) String deptId,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String assetCategoryId,
            @RequestParam(required = false) String financeCategoryId,
            @RequestParam(required = false) String categoryKw,
            @RequestParam(required = false) String assetCategoryKw,
            @RequestParam(required = false) String financeCategoryKw,
            @RequestParam(required = false) String manufacturerId,
            @RequestParam(required = false) String supplierId,
            @RequestParam(required = false) String warehouseId,
            @RequestParam(required = false) String deviceStatus,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) Boolean isMetrology,
            @RequestParam(required = false) Boolean isMaintainDevice,
            @RequestParam(required = false) Boolean isInspectionDevice,
            @RequestParam(required = false) Boolean isLifeSupport,
            @RequestParam(required = false) Boolean isEmergency) {

        StringBuilder where = new StringBuilder(" WHERE d.is_active IS NOT FALSE ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", "d"));
        List<Object> args = new ArrayList<>();

        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String kw = "%" + query.getKeyword().trim() + "%";
            where.append("""
                     AND (
                       d.device_code ILIKE ? OR d.device_name ILIKE ? OR d.brand ILIKE ?
                       OR d.model ILIKE ? OR d.serial_number ILIKE ? OR d.specification ILIKE ?
                       OR d.registration_no ILIKE ? OR d.financial_code ILIKE ? OR d.pinyin_code ILIKE ?
                     )
                    """);
            for (int i = 0; i < 9; i++) args.add(kw);
        }

        FilterCsvSupport.appendUuidIn(where, args, "d.campus_id", campusId);
        FilterCsvSupport.appendUuidIn(where, args, "d.dept_id", deptId);
        FilterCsvSupport.appendUuidIn(where, args, "d.category_id", categoryId);
        FilterCsvSupport.appendUuidIn(where, args, "d.asset_category_id", assetCategoryId);
        FilterCsvSupport.appendUuidIn(where, args, "d.finance_category_id", financeCategoryId);
        appendEqUuid(where, args, "d.manufacturer_id", manufacturerId);
        appendEqUuid(where, args, "d.supplier_id", supplierId);
        appendEqUuid(where, args, "d.warehouse_id", warehouseId);
        FilterCsvSupport.appendStrIn(where, args, "d.device_status", deviceStatus);
        appendEqStr(where, args, "d.risk_level", riskLevel);
        appendBool(where, args, "d.is_metrology", isMetrology);
        appendBool(where, args, "d.is_maintain_device", isMaintainDevice);
        appendBool(where, args, "d.is_inspection_device", isInspectionDevice);
        appendBool(where, args, "d.is_life_support", isLifeSupport);
        appendBool(where, args, "d.is_emergency", isEmergency);
        FilterCsvSupport.appendCodeNamePinyin(where, args, "cat.category_code", "cat.category_name", null, categoryKw);
        FilterCsvSupport.appendCodeNamePinyin(where, args, "ac.category_code", "ac.category_name", null, assetCategoryKw);
        FilterCsvSupport.appendCodeNamePinyin(where, args, "fc.finance_code", "fc.finance_name", null, financeCategoryKw);

        String from = buildFrom();
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + from + where, Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.limit());
        pageArgs.add(query.offset());

        String select = """
                SELECT d.*,
                       cat.category_name,
                       ac.category_name AS asset_category_name,
                       fc.finance_name,
                       m.manufacturer_name,
                       s.supplier_name,
                       dept.dept_name,
                       camp.campus_name,
                       w.warehouse_name
                """;

        List<Map<String, Object>> rows = jdbc.queryForList(
                select + from + where + " ORDER BY d.created_at DESC NULLS LAST LIMIT ? OFFSET ?",
                pageArgs.toArray());

        return Result.ok(PageResult.of(rows, total != null ? total : 0, query.getPage(), query.getSize()));
    }

    private String buildFrom() {
        return " FROM medical_device d "
                + " LEFT JOIN medical_device_category cat ON cat.id = d.category_id"
                + SoftDeleteSupport.notDeletedClause(jdbc, "medical_device_category", "cat")
                + " LEFT JOIN asset_category ac ON ac.id = d.asset_category_id"
                + SoftDeleteSupport.notDeletedClause(jdbc, "asset_category", "ac")
                + " LEFT JOIN finance_category fc ON fc.id = d.finance_category_id"
                + SoftDeleteSupport.notDeletedClause(jdbc, "finance_category", "fc")
                + " LEFT JOIN manufacturer m ON m.id = d.manufacturer_id"
                + SoftDeleteSupport.notDeletedClause(jdbc, "manufacturer", "m")
                + " LEFT JOIN supplier s ON s.id = d.supplier_id"
                + SoftDeleteSupport.notDeletedClause(jdbc, "supplier", "s")
                + " LEFT JOIN department dept ON dept.id = d.dept_id"
                + SoftDeleteSupport.notDeletedClause(jdbc, "department", "dept")
                + " LEFT JOIN campus camp ON camp.id = d.campus_id"
                + SoftDeleteSupport.notDeletedClause(jdbc, "campus", "camp")
                + " LEFT JOIN warehouse w ON w.id = d.warehouse_id"
                + SoftDeleteSupport.notDeletedClause(jdbc, "warehouse", "w");
    }

    private static void appendEqUuid(StringBuilder where, List<Object> args, String column, String value) {
        if (value != null && !value.isBlank() && !value.contains(",")) {
            where.append(" AND ").append(column).append(" = ?::uuid ");
            args.add(value);
        } else if (value != null && value.contains(",")) {
            FilterCsvSupport.appendUuidIn(where, args, column, value);
        }
    }

    private static void appendEqStr(StringBuilder where, List<Object> args, String column, String value) {
        if (value != null && !value.isBlank()) {
            where.append(" AND ").append(column).append(" = ? ");
            args.add(value);
        }
    }

    private static void appendBool(StringBuilder where, List<Object> args, String column, Boolean value) {
        if (value != null) {
            where.append(" AND ").append(column).append(" = ? ");
            args.add(value);
        }
    }
}
