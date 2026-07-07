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

    private MedicalDeviceImporter() {}

    public static ImportResult importRows(JdbcTemplate jdbc, List<Map<String, String>> rows, List<ImportFieldDef> fields) {
        ImportResult result = new ImportResult();
        Lookup lookup = Lookup.load(jdbc);
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
                String deviceStatus = "normal", riskLevel = null, remark = null;
                Double originalValue = null, netValue = null;
                String purchaseDate = null, enableDate = null;
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
                        case "brand" -> brand = ImportValueParser.blankToNull(val);
                        case "model" -> model = ImportValueParser.blankToNull(val);
                        case "serial_number" -> serialNumber = ImportValueParser.blankToNull(val);
                        case "financial_code" -> financialCode = ImportValueParser.blankToNull(val);
                        case "category_code" -> categoryId = lookup.requireId(field.getFieldLabel(), val, lookup.categoryByCode, false);
                        case "manufacturer_code" -> manufacturerId = lookup.requireId(field.getFieldLabel(), val, lookup.manufacturerByCode, false);
                        case "supplier_code" -> supplierId = lookup.requireId(field.getFieldLabel(), val, lookup.supplierByCode, false);
                        case "dept_code" -> deptId = lookup.requireId(field.getFieldLabel(), val, lookup.deptByCode, false);
                        case "campus_name" -> campusId = lookup.requireId(field.getFieldLabel(), val, lookup.campusByName, false);
                        case "original_value" -> originalValue = ImportValueParser.parseDouble(val);
                        case "net_value" -> netValue = ImportValueParser.parseDouble(val);
                        case "device_status" -> deviceStatus = ImportValueParser.blankToNull(val);
                        case "risk_level" -> riskLevel = ImportValueParser.blankToNull(val);
                        case "purchase_date" -> purchaseDate = ImportValueParser.blankToNull(val);
                        case "enable_date" -> enableDate = ImportValueParser.blankToNull(val);
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

                if (deviceStatus == null) deviceStatus = "normal";
                if (active == null) active = true;

                String extensionJson = toJson(extension);
                jdbc.update("""
                        INSERT INTO medical_device (
                          id, device_code, device_name, brand, model, serial_number, financial_code,
                          category_id, manufacturer_id, supplier_id, dept_id, campus_id,
                          original_value, net_value, device_status, risk_level,
                          purchase_date, enable_date, remark, is_active, extension_data
                        ) VALUES (
                          ?::uuid,?,?,?,?,?,?,
                          ?::uuid,?::uuid,?::uuid,?::uuid,?::uuid,
                          ?,?, ?,?,
                          ?::date,?::date,?,?,?::jsonb
                        )
                        """,
                        id, code, name, brand, model, serialNumber, financialCode,
                        categoryId, manufacturerId, supplierId, deptId, campusId,
                        originalValue, netValue, deviceStatus, riskLevel,
                        purchaseDate, enableDate, remark, active, extensionJson);
                result.addSuccess();
            } catch (DataIntegrityViolationException e) {
                result.addError(rowNum, "设备编码重复或数据约束冲突");
            } catch (Exception e) {
                result.addError(rowNum, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
        }
        return result;
    }

    private static boolean isKnownColumn(String col) {
        return Set.of("device_code", "device_name", "brand", "model", "serial_number", "financial_code",
                "original_value", "net_value", "device_status", "risk_level", "purchase_date", "enable_date",
                "remark", "is_active").contains(col);
    }

    private static String toJson(Map<String, Object> map) throws JsonProcessingException {
        if (map.isEmpty()) return "{}";
        return JSON.writeValueAsString(map);
    }

    private record Lookup(
            Map<String, UUID> categoryByCode,
            Map<String, UUID> manufacturerByCode,
            Map<String, UUID> supplierByCode,
            Map<String, UUID> deptByCode,
            Map<String, UUID> campusByName
    ) {
        static Lookup load(JdbcTemplate jdbc) {
            return new Lookup(
                    byColumn(jdbc, "medical_device_category", "category_code"),
                    byColumn(jdbc, "manufacturer", "manufacturer_code"),
                    byColumn(jdbc, "supplier", "supplier_code"),
                    byColumn(jdbc, "department", "dept_code"),
                    byColumn(jdbc, "campus", "campus_name")
            );
        }

        UUID requireId(String label, String raw, Map<String, UUID> map, boolean required) {
            if (raw == null || raw.isBlank()) {
                if (required) throw new IllegalArgumentException(label + "不能为空");
                return null;
            }
            UUID id = map.get(raw.trim());
            if (id == null) throw new IllegalArgumentException(label + "不存在: " + raw);
            return id;
        }

        private static Map<String, UUID> byColumn(JdbcTemplate jdbc, String table, String column) {
            Map<String, UUID> map = new HashMap<>();
            jdbc.queryForList("SELECT id, " + column + " FROM " + table).forEach(r ->
                    map.put(String.valueOf(r.get(column)), (UUID) r.get("id")));
            return map;
        }
    }
}
