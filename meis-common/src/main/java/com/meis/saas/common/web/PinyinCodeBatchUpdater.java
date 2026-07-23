package com.meis.saas.common.web;

import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.persistence.TableColumnCache;
import com.meis.saas.common.util.PinyinCodeUtil;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

public final class PinyinCodeBatchUpdater {
    private PinyinCodeBatchUpdater() {}

    public static int updateByIds(JdbcTemplate jdbc, String table, String nameColumn, Collection<UUID> ids) {
        ensurePinyinColumn(jdbc, table);
        if (ids == null || ids.isEmpty()) return 0;
        int count = 0;
        for (UUID id : ids) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT " + nameColumn + " FROM " + table + " WHERE id = ?::uuid", id);
            if (rows.isEmpty()) continue;
            String name = String.valueOf(rows.get(0).get(nameColumn));
            String code = PinyinCodeUtil.toShortCode(name);
            updatePinyin(jdbc, table, code, id);
            count++;
        }
        return count;
    }

    public static int updateByKeyword(JdbcTemplate jdbc, String table, String nameColumn, String codeColumn, String keyword) {
        ensurePinyinColumn(jdbc, table);
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
            updatePinyin(jdbc, table, code, row.get("id"));
            count++;
        }
        return count;
    }

    private static void ensurePinyinColumn(JdbcTemplate jdbc, String table) {
        if (!TableColumnCache.hasColumn(jdbc, table, "pinyin_code")) {
            throw new BizException(500,
                    "表 " + table + " 缺少 pinyin_code 列，请重启 meis-tenant 完成迁库（R__columns_biz）后再试");
        }
    }

    private static void updatePinyin(JdbcTemplate jdbc, String table, String code, Object id) {
        Set<String> cols = TableColumnCache.columns(jdbc, table);
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        sets.add("pinyin_code = ?");
        args.add(code);
        SoftDeleteSupport.appendUpdateAuditSets(jdbc, cols, sets, args);
        args.add(id);
        jdbc.update("UPDATE " + table + " SET " + String.join(", ", sets) + " WHERE id = ?::uuid", args.toArray());
    }

    public static Map<String, String> pinyinMeta(String table) {
        return switch (table) {
            case "department" -> Map.of("table", "department", "nameColumn", "dept_name", "codeColumn", "dept_code");
            case "supplier" -> Map.of("table", "supplier", "nameColumn", "supplier_name", "codeColumn", "supplier_code");
            case "manufacturer" -> Map.of("table", "manufacturer", "nameColumn", "manufacturer_name", "codeColumn", "manufacturer_code");
            case "spare_part" -> Map.of("table", "spare_part", "nameColumn", "part_name", "codeColumn", "part_code");
            case "medical_device" -> Map.of("table", "medical_device", "nameColumn", "device_name", "codeColumn", "device_code");
            default -> null;
        };
    }
}
