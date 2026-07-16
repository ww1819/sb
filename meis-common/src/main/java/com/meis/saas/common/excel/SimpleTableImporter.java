package com.meis.saas.common.excel;

import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.util.PinyinCodeUtil;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;
import java.util.*;

public final class SimpleTableImporter {
    private SimpleTableImporter() {}

    public static ImportResult importRows(JdbcTemplate jdbc, String table, List<Map<String, String>> rows, Set<String> columns) {
        Set<String> dbColumns = loadTableColumns(jdbc, table);
        ImportResult result = new ImportResult();
        int rowNum = 1;
        for (Map<String, String> raw : rows) {
            rowNum++;
            try {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("id", UUID.randomUUID());
                for (String col : columns) {
                    if (!dbColumns.contains(col)) continue;
                    if (!raw.containsKey(col) || raw.get(col).isBlank()) continue;
                    body.put(col, castValue(col, raw.get(col)));
                }
                if (dbColumns.contains("is_active") && !body.containsKey("is_active")) {
                    body.put("is_active", true);
                }
                SoftDeleteSupport.applyInsertAudit(jdbc, table, body);
                fillPinyinIfNeeded(table, body, dbColumns);

                var softDeletedId = SoftDeleteSupport.findSoftDeletedId(jdbc, table, body);
                if (softDeletedId.isPresent()) {
                    String existingId = softDeletedId.get();
                    body.put("id", UUID.fromString(existingId));
                    SoftDeleteSupport.prepareRestore(body, dbColumns);
                    updateRow(jdbc, table, existingId, body, dbColumns);
                    result.addSuccess();
                    continue;
                }

                List<String> cols = new ArrayList<>(body.keySet());
                String colSql = String.join(",", cols);
                String valSql = String.join(",", cols.stream().map(c -> placeholder(c, body.get(c))).toList());
                Object[] args = cols.stream().map(body::get).toArray();
                jdbc.update("INSERT INTO " + table + " (" + colSql + ") VALUES (" + valSql + ")", args);
                result.addSuccess();
            } catch (DataIntegrityViolationException e) {
                result.addError(rowNum, "编码重复或唯一约束冲突");
            } catch (Exception e) {
                result.addError(rowNum, friendlyError(e));
            }
        }
        return result;
    }

    private static void updateRow(JdbcTemplate jdbc, String table, String id, Map<String, Object> body, Set<String> dbColumns) {
        // body 已由 prepareRestore 写入 is_deleted/deleted_*，且剔除了 updated_*；此处跳过审计列避免 SET 重复
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        body.forEach((k, v) -> {
            if (!dbColumns.contains(k) || SoftDeleteSupport.isUpdateSkipColumn(k)) return;
            sets.add(k + " = " + placeholder(k, v));
            args.add(v);
        });
        SoftDeleteSupport.appendUpdateAuditSets(jdbc, dbColumns, sets, args);
        if (sets.isEmpty()) return;
        args.add(UUID.fromString(id));
        jdbc.update("UPDATE " + table + " SET " + String.join(",", sets) + " WHERE id = ?::uuid", args.toArray());
    }

    private static Set<String> loadTableColumns(JdbcTemplate jdbc, String table) {
        List<String> cols = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = ?",
                String.class, table);
        return new HashSet<>(cols);
    }

    private static String placeholder(String col, Object value) {
        if (value instanceof UUID) return "?::uuid";
        if ("id".equals(col) || col.endsWith("_id") || col.endsWith("_by")) return "?::uuid";
        if (value instanceof Boolean) return "?";
        return "?";
    }

    private static Object castValue(String col, String raw) {
        if (col.startsWith("is_")) return ImportValueParser.parseBoolean(raw);
        if ("rating".equals(col) || col.endsWith("_order") || col.endsWith("_years")) return ImportValueParser.parseInteger(raw);
        if ("original_value".equals(col) || "net_value".equals(col)) return ImportValueParser.parseDouble(raw);
        return raw;
    }

    private static void fillPinyinIfNeeded(String table, Map<String, Object> body, Set<String> dbColumns) {
        if (!dbColumns.contains("pinyin_code")) return;
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

    private static String friendlyError(Exception e) {
        Throwable root = e;
        while (root.getCause() != null) root = root.getCause();
        if (root instanceof SQLException sql) {
            String state = sql.getSQLState();
            String msg = sql.getMessage();
            if ("42703".equals(state) && msg != null) {
                return "数据库缺少字段，请执行 Flyway V13 迁移: " + msg;
            }
            if (msg != null && !msg.isBlank()) return msg;
        }
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }
}
