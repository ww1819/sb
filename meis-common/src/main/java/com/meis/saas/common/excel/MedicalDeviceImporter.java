package com.meis.saas.common.excel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

/**
 * 可扩展的设备台账导入：标准列写入 medical_device，扩展列写入 extension_data JSONB。
 */
public final class MedicalDeviceImporter {
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Set<String> UUID_COLUMNS = Set.of(
            "id", "category_id", "manufacturer_id", "supplier_id", "dept_id", "campus_id",
            "building_id", "contract_id", "created_by", "updated_by"
    );
    private static final Set<String> DATE_COLUMNS = Set.of(
            "purchase_date", "acceptance_date", "enable_date", "production_date", "warranty_end_date",
            "last_calibration_date", "next_calibration_date", "service_expiry_date"
    );
    private static final Set<String> JSON_COLUMNS = Set.of("extension_data", "manual_files", "certificate_files");

    private MedicalDeviceImporter() {}

    public static ImportResult importRows(JdbcTemplate jdbc, List<Map<String, String>> rows, List<ImportFieldDef> fields) {
        ImportResult result = new ImportResult();
        Set<String> dbColumns = loadTableColumns(jdbc);
        ImportMasterDataResolver resolver = new ImportMasterDataResolver(jdbc);
        Map<String, ImportFieldDef> fieldByKey = new LinkedHashMap<>();
        for (ImportFieldDef f : fields) fieldByKey.put(f.getFieldKey(), f);

        int rowNum = 1;
        for (Map<String, String> raw : rows) {
            rowNum++;
            try {
                ImportFieldDef codeDef = fieldByKey.get("device_code");
                ImportFieldDef nameDef = fieldByKey.get("device_name");
                String code = ImportValueParser.require(raw.get("device_code"),
                        codeDef != null ? codeDef.getFieldLabel() : "设备编码");
                String name = ImportValueParser.require(raw.get("device_name"),
                        nameDef != null ? nameDef.getFieldLabel() : "设备名称");

                UUID id = UUID.randomUUID();
                Map<String, Object> extension = new LinkedHashMap<>();

                String brand = null, model = null, serialNumber = null, financialCode = null;
                String specification = null, registrationNo = null;
                String deviceStatus = "normal", riskLevel = null, remark = null;
                Double originalValue = null, netValue = null;
                String purchaseDate = null, acceptanceDate = null, enableDate = null, productionDate = null;
                String lastCalibrationDate = null;
                Integer serviceLifeYears = null, calibrationPeriodDays = null;
                Boolean active = true;
                UUID categoryId = null, manufacturerId = null, supplierId = null, deptId = null, campusId = null;

                for (ImportFieldDef field : fields) {
                    String val = raw.get(field.getFieldKey());
                    if (val == null || val.isBlank()) continue;

                    if (field.isExtension()) {
                        extension.put(field.getFieldKey(), ImportValueParser.cast(field.getFieldType(), val));
                        continue;
                    }

                    switch (field.getFieldKey()) {
                        case "device_code", "device_name" -> { /* handled */ }
                        case "manufacturer_code", "manufacturer_name", "supplier_code", "supplier_name",
                             "dept_code", "dept_name" -> { /* resolved after loop */ }
                        case "brand" -> brand = ImportValueParser.blankToNull(val);
                        case "model" -> model = ImportValueParser.blankToNull(val);
                        case "specification" -> specification = ImportValueParser.blankToNull(val);
                        case "registration_no" -> registrationNo = ImportValueParser.blankToNull(val);
                        case "serial_number" -> serialNumber = ImportValueParser.blankToNull(val);
                        case "financial_code" -> financialCode = ImportValueParser.blankToNull(val);
                        case "category_code" -> categoryId = resolver.resolveCategoryByCode(val);
                        case "campus_name" -> campusId = resolver.resolveCampusByName(val);
                        case "original_value" -> originalValue = ImportValueParser.parseDouble(val);
                        case "net_value" -> netValue = ImportValueParser.parseDouble(val);
                        case "device_status" -> deviceStatus = ImportValueParser.blankToNull(val);
                        case "risk_level" -> riskLevel = ImportValueParser.blankToNull(val);
                        case "purchase_date" -> purchaseDate = ImportValueParser.blankToNull(val);
                        case "acceptance_date" -> acceptanceDate = ImportValueParser.blankToNull(val);
                        case "enable_date" -> enableDate = ImportValueParser.blankToNull(val);
                        case "production_date" -> productionDate = ImportValueParser.blankToNull(val);
                        case "service_life_years" -> serviceLifeYears = ImportValueParser.parseInteger(val);
                        case "calibration_period_days" -> calibrationPeriodDays = ImportValueParser.parseInteger(val);
                        case "last_calibration_date" -> lastCalibrationDate = ImportValueParser.blankToNull(val);
                        case "remark" -> remark = ImportValueParser.blankToNull(val);
                        case "is_active" -> active = ImportValueParser.parseBoolean(val);
                        default -> {
                            String col = field.effectiveColumn();
                            if (col != null && isKnownColumn(col)) {
                                // 客户将标准字段映射到其他物理列（target_column）
                            } else {
                                extension.put(field.getFieldKey(), ImportValueParser.cast(field.getFieldType(), val));
                            }
                        }
                    }
                }

                manufacturerId = resolver.resolveManufacturer(
                        ImportValueParser.blankToNull(raw.get("manufacturer_code")),
                        ImportValueParser.blankToNull(raw.get("manufacturer_name")));
                supplierId = resolver.resolveSupplier(
                        ImportValueParser.blankToNull(raw.get("supplier_code")),
                        ImportValueParser.blankToNull(raw.get("supplier_name")));
                deptId = resolver.resolveDepartment(
                        ImportValueParser.blankToNull(raw.get("dept_code")),
                        ImportValueParser.blankToNull(raw.get("dept_name")));

                if (deviceStatus == null) deviceStatus = "normal";
                if (active == null) active = true;

                String nextCalibrationDate = DeviceDateCalculator.nextCalibrationDate(lastCalibrationDate, calibrationPeriodDays);
                String serviceExpiryDate = DeviceDateCalculator.serviceExpiryDate(acceptanceDate, productionDate, serviceLifeYears);

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", id);
                row.put("device_code", code);
                row.put("device_name", name);
                putValue(row, extension, dbColumns, "brand", brand);
                putValue(row, extension, dbColumns, "model", model);
                putValue(row, extension, dbColumns, "specification", specification);
                putValue(row, extension, dbColumns, "registration_no", registrationNo);
                putValue(row, extension, dbColumns, "serial_number", serialNumber);
                putValue(row, extension, dbColumns, "financial_code", financialCode);
                putValue(row, extension, dbColumns, "category_id", categoryId);
                putValue(row, extension, dbColumns, "manufacturer_id", manufacturerId);
                putValue(row, extension, dbColumns, "supplier_id", supplierId);
                putValue(row, extension, dbColumns, "dept_id", deptId);
                putValue(row, extension, dbColumns, "campus_id", campusId);
                putValue(row, extension, dbColumns, "original_value", originalValue);
                putValue(row, extension, dbColumns, "net_value", netValue);
                putValue(row, extension, dbColumns, "device_status", deviceStatus);
                putValue(row, extension, dbColumns, "risk_level", riskLevel);
                putValue(row, extension, dbColumns, "purchase_date", purchaseDate);
                putValue(row, extension, dbColumns, "acceptance_date", acceptanceDate);
                putValue(row, extension, dbColumns, "enable_date", enableDate);
                putValue(row, extension, dbColumns, "production_date", productionDate);
                putValue(row, extension, dbColumns, "service_life_years", serviceLifeYears);
                putValue(row, extension, dbColumns, "calibration_period_days", calibrationPeriodDays);
                putValue(row, extension, dbColumns, "last_calibration_date", lastCalibrationDate);
                putValue(row, extension, dbColumns, "next_calibration_date", nextCalibrationDate);
                putValue(row, extension, dbColumns, "service_expiry_date", serviceExpiryDate);
                putValue(row, extension, dbColumns, "remark", remark);
                putValue(row, extension, dbColumns, "is_active", active);
                if (dbColumns.contains("extension_data")) {
                    row.put("extension_data", toJson(extension));
                }

                insertRow(jdbc, dbColumns, row);
                result.addSuccess();
            } catch (DataIntegrityViolationException e) {
                result.addError(rowNum, "设备编码重复或数据约束冲突");
            } catch (Exception e) {
                result.addError(rowNum, friendlyError(e));
            }
        }
        return result;
    }

    private static void putValue(Map<String, Object> row, Map<String, Object> extension,
                                 Set<String> dbColumns, String column, Object value) {
        if (value == null) return;
        if (dbColumns.contains(column)) {
            row.put(column, value);
        } else {
            extension.put(column, value);
        }
    }

    private static void insertRow(JdbcTemplate jdbc, Set<String> dbColumns, Map<String, Object> row) {
        List<String> cols = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String col = entry.getKey();
            Object val = entry.getValue();
            if (!dbColumns.contains(col) || val == null) continue;
            cols.add(col);
            args.add(val);
        }
        if (cols.isEmpty()) {
            throw new IllegalStateException("没有可写入的字段");
        }
        String colSql = String.join(",", cols);
        String valSql = String.join(",", cols.stream().map(MedicalDeviceImporter::placeholder).toList());
        jdbc.update("INSERT INTO medical_device (" + colSql + ") VALUES (" + valSql + ")", args.toArray());
    }

    private static String placeholder(String col) {
        if (UUID_COLUMNS.contains(col)) return "?::uuid";
        if (DATE_COLUMNS.contains(col)) return "?::date";
        if (JSON_COLUMNS.contains(col)) return "?::jsonb";
        return "?";
    }

    private static Set<String> loadTableColumns(JdbcTemplate jdbc) {
        List<String> cols = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'medical_device'",
                String.class);
        return new HashSet<>(cols);
    }

    private static boolean isKnownColumn(String col) {
        return Set.of(
                "device_code", "device_name", "brand", "model", "specification", "registration_no", "serial_number",
                "financial_code", "original_value", "net_value", "device_status", "risk_level",
                "purchase_date", "acceptance_date", "enable_date", "production_date",
                "service_life_years", "calibration_period_days", "last_calibration_date",
                "next_calibration_date", "service_expiry_date",
                "remark", "is_active"
        ).contains(col);
    }

    private static String friendlyError(Exception e) {
        String msg = e.getMessage();
        if (msg != null && msg.contains("42703")) {
            return "数据库缺少台账扩展字段，请重启 meis-tenant 以执行 Flyway V15 迁移";
        }
        return msg != null ? msg : e.getClass().getSimpleName();
    }

    private static String toJson(Map<String, Object> map) throws JsonProcessingException {
        if (map.isEmpty()) return "{}";
        return JSON.writeValueAsString(map);
    }
}
