package com.meis.saas.common.excel;

import com.meis.saas.common.persistence.SoftDeleteSupport;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

/**
 * 设备分类（medical_device_category）导入：
 * - 仅需「分类编码」「分类名称」两列时可自动推导 parent_code / level / full_path
 * - 国标 68 码：4/6/8 位一层（6801 / 680101 / 68010101）；Excel 数字丢前导零时自动补齐奇数位
 * - 按编码长度升序写入，保证上级先入库；已存在则更新（便于重导）
 */
public final class MedicalDeviceCategoryImporter {
    private MedicalDeviceCategoryImporter() {}

    public static ImportResult importRows(JdbcTemplate jdbc, List<Map<String, String>> rawRows) {
        ImportResult result = new ImportResult();
        List<RowData> rows = normalize(rawRows);
        rows.sort(Comparator
                .comparingInt((RowData r) -> r.code.length())
                .thenComparing(r -> r.code));

        Map<String, String> codeToName = new LinkedHashMap<>();
        for (RowData r : rows) {
            codeToName.put(r.code, r.name);
        }

        Set<String> dbColumns = loadColumns(jdbc);
        int excelRowHint = 1;
        for (RowData r : rows) {
            excelRowHint++;
            try {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("category_code", r.code);
                body.put("category_name", r.name);
                body.put("parent_code", r.parentCode);
                body.put("level", r.level);
                body.put("full_path", buildFullPath(r.code, codeToName));
                body.put("sort_order", r.sortOrder);
                body.put("is_active", true);

                Optional<String> existingId = findActiveId(jdbc, r.code);
                if (existingId.isPresent()) {
                    SoftDeleteSupport.prepareRestore(body, dbColumns);
                    body.remove("id");
                    updateRow(jdbc, existingId.get(), body, dbColumns);
                } else {
                    var softDeletedId = SoftDeleteSupport.findSoftDeletedId(jdbc, "medical_device_category", body);
                    if (softDeletedId.isPresent()) {
                        SoftDeleteSupport.prepareRestore(body, dbColumns);
                        body.put("id", UUID.fromString(softDeletedId.get()));
                        updateRow(jdbc, softDeletedId.get(), body, dbColumns);
                    } else {
                        body.put("id", UUID.randomUUID());
                        SoftDeleteSupport.applyInsertAudit(jdbc, "medical_device_category", body);
                        insertRow(jdbc, body, dbColumns);
                    }
                }
                result.addSuccess();
            } catch (Exception e) {
                result.addError(excelRowHint, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
        }
        return result;
    }

    static List<RowData> normalize(List<Map<String, String>> rawRows) {
        List<RowData> out = new ArrayList<>();
        int idx = 0;
        for (Map<String, String> raw : rawRows) {
            idx++;
            String code = firstNonBlank(raw,
                    "category_code", "标准分类代码", "分类编码", "分类编码(68码)", "编码", "代码");
            String name = firstNonBlank(raw,
                    "category_name", "分类名称", "名称", "名称/项目");
            // 无标准表头：若有行号三列则取 2、3 列
            if (isBlank(code) || isBlank(name)) {
                List<String> vals = new ArrayList<>(raw.values());
                if (vals.size() >= 3 && vals.get(0).matches("\\d+")) {
                    if (isBlank(code)) code = vals.get(1);
                    if (isBlank(name)) name = vals.get(2);
                } else if (vals.size() >= 2) {
                    if (isBlank(code)) code = vals.get(0);
                    if (isBlank(name)) name = vals.get(1);
                }
            }
            if (isBlank(code) || isBlank(name)) {
                continue;
            }
            code = normalizeCode(code.trim());
            name = name.trim();
            // 国标 68 码：4 / 6 / 8 位
            if (!code.matches("\\d{4,8}") || code.length() % 2 != 0) {
                continue;
            }
            String parent = firstNonBlank(raw, "parent_code", "上级编码", "上级分类编码");
            if (!isBlank(parent) && normalizeCode(parent.trim()).equals(code)) {
                parent = null;
            }
            if (isBlank(parent)) {
                // 顶级为 4 位（如 6801）；其余去掉末 2 位为上级
                parent = code.length() > 4 ? code.substring(0, code.length() - 2) : null;
            } else {
                parent = normalizeCode(parent.trim());
                if (parent != null && parent.isEmpty()) parent = null;
            }
            int level = code.length() / 2 - 1; // 4→1, 6→2, 8→3
            Integer sort = null;
            String sortRaw = firstNonBlank(raw, "sort_order", "排序", "行号");
            if (!isBlank(sortRaw)) {
                try {
                    sort = Integer.parseInt(sortRaw.trim());
                } catch (NumberFormatException ignored) {
                    sort = idx;
                }
            } else {
                sort = idx;
            }
            out.add(new RowData(code, name, parent, level, sort));
        }
        return out;
    }

    /** Excel 数字单元格丢前导零：补齐为偶数位，最长 8 位（68 三级码） */
    static String normalizeCode(String raw) {
        if (raw == null) return null;
        String c = raw.trim();
        if (c.endsWith(".0")) c = c.substring(0, c.length() - 2);
        if (c.matches("\\d+")) {
            while (c.length() % 2 == 1) {
                c = "0" + c;
            }
            if (c.length() > 8) {
                c = c.substring(0, 8);
            }
        }
        return c;
    }

    private static String buildFullPath(String code, Map<String, String> codeToName) {
        List<String> parts = new ArrayList<>();
        for (int len = 4; len <= code.length(); len += 2) {
            String seg = code.substring(0, len);
            String n = codeToName.get(seg);
            if (n != null && !n.isBlank()) parts.add(n);
        }
        return String.join("/", parts);
    }

    private static Optional<String> findActiveId(JdbcTemplate jdbc, String code) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT id::text AS id FROM medical_device_category
                WHERE category_code = ? AND COALESCE(is_deleted, 0) = 0 LIMIT 1
                """,
                code);
        if (rows.isEmpty()) return Optional.empty();
        return Optional.of(String.valueOf(rows.get(0).get("id")));
    }

    private static void insertRow(JdbcTemplate jdbc, Map<String, Object> body, Set<String> dbColumns) {
        List<String> cols = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        body.forEach((k, v) -> {
            if (!dbColumns.contains(k) || v == null) return;
            cols.add(k);
            args.add(v);
        });
        String placeholders = String.join(",", cols.stream().map(c -> placeholder(c)).toList());
        jdbc.update("INSERT INTO medical_device_category (" + String.join(",", cols) + ") VALUES (" + placeholders + ")",
                args.toArray());
    }

    private static void updateRow(JdbcTemplate jdbc, String id, Map<String, Object> body, Set<String> dbColumns) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        body.forEach((k, v) -> {
            if (!dbColumns.contains(k) || SoftDeleteSupport.isUpdateSkipColumn(k)) return;
            sets.add(k + " = " + placeholder(k));
            args.add(v);
        });
        SoftDeleteSupport.appendUpdateAuditSets(jdbc, dbColumns, sets, args);
        if (sets.isEmpty()) return;
        args.add(UUID.fromString(id));
        jdbc.update("UPDATE medical_device_category SET " + String.join(",", sets) + " WHERE id = ?::uuid", args.toArray());
    }

    private static String placeholder(String col) {
        if ("id".equals(col) || col.endsWith("_id") || col.endsWith("_by")) return "?::uuid";
        return "?";
    }

    private static Set<String> loadColumns(JdbcTemplate jdbc) {
        return new HashSet<>(jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'medical_device_category'",
                String.class));
    }

    private static String firstNonBlank(Map<String, String> raw, String... keys) {
        for (String k : keys) {
            for (Map.Entry<String, String> e : raw.entrySet()) {
                if (e.getKey() != null && normalizeKey(e.getKey()).equals(normalizeKey(k))) {
                    if (!isBlank(e.getValue())) return e.getValue();
                }
            }
        }
        return null;
    }

    private static String normalizeKey(String s) {
        return s == null ? "" : s.trim().replace("\uFEFF", "");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    record RowData(String code, String name, String parentCode, int level, int sortOrder) {}
}
