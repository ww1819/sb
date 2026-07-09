package com.meis.saas.common.web;

import com.meis.saas.common.util.PinyinCodeUtil;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

public final class PinyinCodeBatchUpdater {
    private PinyinCodeBatchUpdater() {}

    public static int updateByIds(JdbcTemplate jdbc, String table, String nameColumn, Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        int count = 0;
        for (UUID id : ids) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT " + nameColumn + " FROM " + table + " WHERE id = ?::uuid", id);
            if (rows.isEmpty()) continue;
            String name = String.valueOf(rows.get(0).get(nameColumn));
            String code = PinyinCodeUtil.toShortCode(name);
            jdbc.update("UPDATE " + table + " SET pinyin_code = ?, updated_at = NOW() WHERE id = ?::uuid", code, id);
            count++;
        }
        return count;
    }

    public static int updateByKeyword(JdbcTemplate jdbc, String table, String nameColumn, String codeColumn, String keyword) {
        String sql = "SELECT id, " + nameColumn + " FROM " + table + " WHERE 1=1";
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql += " AND (" + nameColumn + " ILIKE ? OR " + codeColumn + " ILIKE ? OR COALESCE(pinyin_code,'') ILIKE ?)";
            String kw = "%" + keyword.trim() + "%";
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args.toArray());
        int count = 0;
        for (Map<String, Object> row : rows) {
            String name = String.valueOf(row.get(nameColumn));
            String code = PinyinCodeUtil.toShortCode(name);
            jdbc.update("UPDATE " + table + " SET pinyin_code = ?, updated_at = NOW() WHERE id = ?::uuid",
                    code, row.get("id"));
            count++;
        }
        return count;
    }

    public static Map<String, String> pinyinMeta(String table) {
        return switch (table) {
            case "department" -> Map.of("table", "department", "nameColumn", "dept_name", "codeColumn", "dept_code");
            case "supplier" -> Map.of("table", "supplier", "nameColumn", "supplier_name", "codeColumn", "supplier_code");
            case "manufacturer" -> Map.of("table", "manufacturer", "nameColumn", "manufacturer_name", "codeColumn", "manufacturer_code");
            default -> null;
        };
    }
}
