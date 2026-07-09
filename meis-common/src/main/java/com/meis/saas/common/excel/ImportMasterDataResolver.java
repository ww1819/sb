package com.meis.saas.common.excel;

import com.meis.saas.common.util.PinyinCodeUtil;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 导入时主数据解析：编码优先，编码为空则按名称匹配，匹配不到则自动新增。
 */
public final class ImportMasterDataResolver {
    private final JdbcTemplate jdbc;
    private final Map<String, UUID> manufacturerByCode = new HashMap<>();
    private final Map<String, UUID> manufacturerByName = new HashMap<>();
    private final Map<String, UUID> supplierByCode = new HashMap<>();
    private final Map<String, UUID> supplierByName = new HashMap<>();
    private final Map<String, UUID> deptByCode = new HashMap<>();
    private final Map<String, UUID> deptByName = new HashMap<>();
    private final Map<String, UUID> categoryByCode = new HashMap<>();
    private final Map<String, UUID> campusByName = new HashMap<>();

    public ImportMasterDataResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        loadLookups();
    }

    public UUID resolveCategoryByCode(String code) {
        if (code == null || code.isBlank()) return null;
        UUID id = categoryByCode.get(code.trim());
        if (id == null) throw new IllegalArgumentException("分类编码不存在: " + code);
        return id;
    }

    public UUID resolveCampusByName(String name) {
        if (name == null || name.isBlank()) return null;
        UUID id = campusByName.get(name.trim());
        if (id == null) throw new IllegalArgumentException("院区名称不存在: " + name);
        return id;
    }

    public UUID resolveManufacturer(String code, String name) {
        if (code != null && !code.isBlank()) {
            UUID id = manufacturerByCode.get(code.trim());
            if (id == null) throw new IllegalArgumentException("生产厂商编码不存在: " + code);
            return id;
        }
        return resolveOrCreateManufacturer(name);
    }

    public UUID resolveSupplier(String code, String name) {
        if (code != null && !code.isBlank()) {
            UUID id = supplierByCode.get(code.trim());
            if (id == null) throw new IllegalArgumentException("供应商编码不存在: " + code);
            return id;
        }
        return resolveOrCreateSupplier(name);
    }

    public UUID resolveDepartment(String code, String name) {
        if (code != null && !code.isBlank()) {
            UUID id = deptByCode.get(code.trim());
            if (id == null) throw new IllegalArgumentException("使用科室编码不存在: " + code);
            return id;
        }
        return resolveOrCreateDepartment(name);
    }

    private UUID resolveOrCreateManufacturer(String name) {
        if (name == null || name.isBlank()) return null;
        String n = name.trim();
        UUID existing = manufacturerByName.get(n);
        if (existing != null) return existing;

        UUID id = UUID.randomUUID();
        String pinyin = PinyinCodeUtil.toShortCode(n);
        String code = uniqueCode("manufacturer", "manufacturer_code", pinyin, 20);
        jdbc.update("""
                INSERT INTO manufacturer (id, manufacturer_code, manufacturer_name, pinyin_code, is_active)
                VALUES (?::uuid, ?, ?, ?, TRUE)
                """, id, code, n, pinyin);
        manufacturerByCode.put(code, id);
        manufacturerByName.put(n, id);
        return id;
    }

    private UUID resolveOrCreateSupplier(String name) {
        if (name == null || name.isBlank()) return null;
        String n = name.trim();
        UUID existing = supplierByName.get(n);
        if (existing != null) return existing;

        UUID id = UUID.randomUUID();
        String pinyin = PinyinCodeUtil.toShortCode(n);
        String code = uniqueCode("supplier", "supplier_code", pinyin, 20);
        jdbc.update("""
                INSERT INTO supplier (id, supplier_code, supplier_name, pinyin_code, is_active)
                VALUES (?::uuid, ?, ?, ?, TRUE)
                """, id, code, n, pinyin);
        supplierByCode.put(code, id);
        supplierByName.put(n, id);
        return id;
    }

    private UUID resolveOrCreateDepartment(String name) {
        if (name == null || name.isBlank()) return null;
        String n = name.trim();
        UUID existing = deptByName.get(n);
        if (existing != null) return existing;

        UUID id = UUID.randomUUID();
        String pinyin = PinyinCodeUtil.toShortCode(n);
        String code = nextDeptCode();
        jdbc.update("""
                INSERT INTO department (id, dept_code, dept_name, pinyin_code, is_active)
                VALUES (?::uuid, ?, ?, ?, TRUE)
                """, id, code, n, pinyin);
        deptByCode.put(code, id);
        deptByName.put(n, id);
        return id;
    }

    private String uniqueCode(String table, String codeColumn, String base, int maxLen) {
        String seed = (base == null || base.isBlank()) ? "X" : base.toUpperCase();
        if (seed.length() > maxLen) seed = seed.substring(0, maxLen);
        String candidate = seed;
        int n = 1;
        while (codeExists(table, codeColumn, candidate)) {
            String suffix = String.valueOf(n++);
            int prefixLen = Math.max(1, maxLen - suffix.length());
            candidate = seed.substring(0, Math.min(seed.length(), prefixLen)) + suffix;
            if (candidate.length() > maxLen) {
                candidate = candidate.substring(0, maxLen);
            }
        }
        return candidate;
    }

    private boolean codeExists(String table, String codeColumn, String code) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + codeColumn + " = ?",
                Integer.class, code);
        return count != null && count > 0;
    }

    private String nextDeptCode() {
        Integer max = jdbc.queryForObject("""
                SELECT COALESCE(MAX(CAST(dept_code AS INTEGER)), 0)
                FROM department
                WHERE dept_code ~ '^[0-9]{1,3}$'
                """, Integer.class);
        int next = (max == null ? 0 : max) + 1;
        if (next > 999) {
            throw new IllegalStateException("自动生成科室编码已达上限(999)");
        }
        return String.format("%03d", next);
    }

    private void loadLookups() {
        loadByColumn("medical_device_category", "category_code", categoryByCode);
        loadByColumn("manufacturer", "manufacturer_code", manufacturerByCode);
        loadByColumn("supplier", "supplier_code", supplierByCode);
        loadByColumn("department", "dept_code", deptByCode);
        loadByColumn("campus", "campus_name", campusByName);
        loadByName("manufacturer", "manufacturer_name", manufacturerByName);
        loadByName("supplier", "supplier_name", supplierByName);
        loadByName("department", "dept_name", deptByName);
    }

    private void loadByColumn(JdbcTemplate jdbc, String table, String column, Map<String, UUID> target) {
        jdbc.queryForList("SELECT id, " + column + " FROM " + table).forEach(r ->
                target.put(String.valueOf(r.get(column)), (UUID) r.get("id")));
    }

    private void loadByColumn(String table, String column, Map<String, UUID> target) {
        loadByColumn(jdbc, table, column, target);
    }

    private void loadByName(String table, String nameColumn, Map<String, UUID> target) {
        jdbc.queryForList("SELECT id, " + nameColumn + " FROM " + table).forEach(r -> {
            Object name = r.get(nameColumn);
            if (name != null && !name.toString().isBlank()) {
                target.put(name.toString().trim(), (UUID) r.get("id"));
            }
        });
    }
}
