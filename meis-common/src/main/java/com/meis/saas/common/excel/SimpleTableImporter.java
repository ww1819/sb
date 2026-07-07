package com.meis.saas.common.excel;

import com.meis.saas.common.util.PinyinCodeUtil;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

public final class SimpleTableImporter {
    private SimpleTableImporter() {}

    public static ImportResult importRows(JdbcTemplate jdbc, String table, List<Map<String, String>> rows, Set<String> columns) {
        ImportResult result = new ImportResult();
        int rowNum = 1;
        for (Map<String, String> raw : rows) {
            rowNum++;
            try {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("id", UUID.randomUUID().toString());
                for (String col : columns) {
                    if (!raw.containsKey(col) || raw.get(col).isBlank()) continue;
                    body.put(col, castValue(col, raw.get(col)));
                }
                if (!body.containsKey("is_active")) body.put("is_active", true);
                fillPinyinIfNeeded(table, body);
                String cols = String.join(",", body.keySet());
                String vals = String.join(",", body.keySet().stream().map(k -> "?").toList());
                jdbc.update("INSERT INTO " + table + " (" + cols + ") VALUES (" + vals + ")", body.values().toArray());
                result.addSuccess();
            } catch (DataIntegrityViolationException e) {
                result.addError(rowNum, "编码重复或唯一约束冲突");
            } catch (Exception e) {
                result.addError(rowNum, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
        }
        return result;
    }

    private static Object castValue(String col, String raw) {
        if (col.startsWith("is_")) return ImportValueParser.parseBoolean(raw);
        if ("rating".equals(col) || col.endsWith("_order") || col.endsWith("_years")) return ImportValueParser.parseInteger(raw);
        if ("original_value".equals(col) || "net_value".equals(col)) return ImportValueParser.parseDouble(raw);
        return raw;
    }

    private static void fillPinyinIfNeeded(String table, Map<String, Object> body) {
        String nameCol = switch (table) {
            case "supplier" -> "supplier_name";
            case "manufacturer" -> "manufacturer_name";
            default -> null;
        };
        if (nameCol == null) return;
        Object existing = body.get("pinyin_code");
        if (existing != null && !existing.toString().isBlank()) return;
        Object name = body.get(nameCol);
        if (name != null && !name.toString().isBlank()) {
            body.put("pinyin_code", PinyinCodeUtil.toShortCode(name.toString()));
        }
    }
}
