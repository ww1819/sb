package com.meis.saas.common.excel;

import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.util.PinyinCodeUtil;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

/**
 * 导入时主数据解析：编码优先，编码为空则按名称匹配，匹配不到则自动新增。
 * 查找仅命中未删除行；自动新增时若唯一键命中软删行则恢复。
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
    private final Map<String, UUID> assetCategoryByCode = new HashMap<>();
    private final Map<String, UUID> financeCategoryByCode = new HashMap<>();
    private final Map<String, UUID> warehouseByCode = new HashMap<>();
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

    public UUID resolveAssetCategoryByCode(String code) {
        if (code == null || code.isBlank()) return null;
        UUID id = assetCategoryByCode.get(code.trim());
        if (id == null) throw new IllegalArgumentException("资产分类编码不存在: " + code);
        return id;
    }

    public UUID resolveFinanceCategoryByCode(String code) {
        if (code == null || code.isBlank()) return null;
        UUID id = financeCategoryByCode.get(code.trim());
        if (id == null) throw new IllegalArgumentException("财务分类编码不存在: " + code);
        return id;
    }

    public UUID resolveWarehouseByCode(String code) {
        if (code == null || code.isBlank()) return null;
        UUID id = warehouseByCode.get(code.trim());
        if (id == null) throw new IllegalArgumentException("库房编码不存在: " + code);
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
            if (id != null) return id;
            id = restoreSoftDeleted("manufacturer", "manufacturer_code", code.trim());
            if (id != null) {
                cacheManufacturer(id, code.trim(), null);
                return id;
            }
            throw new IllegalArgumentException("生产厂商编码不存在: " + code);
        }
        return resolveOrCreateManufacturer(name);
    }

    public UUID resolveSupplier(String code, String name) {
        if (code != null && !code.isBlank()) {
            UUID id = supplierByCode.get(code.trim());
            if (id != null) return id;
            id = restoreSoftDeleted("supplier", "supplier_code", code.trim());
            if (id != null) {
                cacheSupplier(id, code.trim(), null);
                return id;
            }
            throw new IllegalArgumentException("供应商编码不存在: " + code);
        }
        return resolveOrCreateSupplier(name);
    }

    public UUID resolveDepartment(String code, String name) {
        if (code != null && !code.isBlank()) {
            UUID id = deptByCode.get(code.trim());
            if (id != null) return id;
            id = restoreSoftDeleted("department", "dept_code", code.trim());
            if (id != null) {
                cacheDepartment(id, code.trim(), null);
                return id;
            }
            throw new IllegalArgumentException("使用科室编码不存在: " + code);
        }
        return resolveOrCreateDepartment(name);
    }

    private UUID resolveOrCreateManufacturer(String name) {
        if (name == null || name.isBlank()) return null;
        String n = name.trim();
        UUID existing = manufacturerByName.get(n);
        if (existing != null) return existing;

        UUID restored = restoreSoftDeleted("manufacturer", "manufacturer_name", n);
        if (restored != null) {
            cacheManufacturer(restored, null, n);
            return restored;
        }

        String pinyin = PinyinCodeUtil.toShortCode(n);
        String code = uniqueCode("manufacturer", "manufacturer_code", pinyin, 20);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("manufacturer_code", code);
        body.put("manufacturer_name", n);
        body.put("pinyin_code", pinyin);
        body.put("is_active", true);
        var softId = SoftDeleteSupport.prepareCreate(jdbc, "manufacturer", body);
        if (softId.isPresent()) {
            UUID id = UUID.fromString(softId.get());
            jdbc.update("""
                    UPDATE manufacturer SET manufacturer_code=?, manufacturer_name=?, pinyin_code=?, is_active=TRUE,
                    is_deleted=0, deleted_at=NULL, deleted_by=NULL, updated_at=NOW(), updated_by=?::uuid
                    WHERE id=?::uuid
                    """, code, n, pinyin, SoftDeleteSupport.currentUserId(), id);
            cacheManufacturer(id, code, n);
            return id;
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO manufacturer (id, manufacturer_code, manufacturer_name, pinyin_code, is_active, created_by, is_deleted)
                VALUES (?::uuid, ?, ?, ?, TRUE, ?::uuid, 0)
                """, id, code, n, pinyin, SoftDeleteSupport.currentUserId());
        cacheManufacturer(id, code, n);
        return id;
    }

    private UUID resolveOrCreateSupplier(String name) {
        if (name == null || name.isBlank()) return null;
        String n = name.trim();
        UUID existing = supplierByName.get(n);
        if (existing != null) return existing;

        UUID restored = restoreSoftDeleted("supplier", "supplier_name", n);
        if (restored != null) {
            cacheSupplier(restored, null, n);
            return restored;
        }

        String pinyin = PinyinCodeUtil.toShortCode(n);
        String code = uniqueCode("supplier", "supplier_code", pinyin, 20);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("supplier_code", code);
        body.put("supplier_name", n);
        body.put("pinyin_code", pinyin);
        body.put("is_active", true);
        var softId = SoftDeleteSupport.prepareCreate(jdbc, "supplier", body);
        if (softId.isPresent()) {
            UUID id = UUID.fromString(softId.get());
            jdbc.update("""
                    UPDATE supplier SET supplier_code=?, supplier_name=?, pinyin_code=?, is_active=TRUE,
                    is_deleted=0, deleted_at=NULL, deleted_by=NULL, updated_at=NOW(), updated_by=?::uuid
                    WHERE id=?::uuid
                    """, code, n, pinyin, SoftDeleteSupport.currentUserId(), id);
            cacheSupplier(id, code, n);
            return id;
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO supplier (id, supplier_code, supplier_name, pinyin_code, is_active, created_by, is_deleted)
                VALUES (?::uuid, ?, ?, ?, TRUE, ?::uuid, 0)
                """, id, code, n, pinyin, SoftDeleteSupport.currentUserId());
        cacheSupplier(id, code, n);
        return id;
    }

    private UUID resolveOrCreateDepartment(String name) {
        if (name == null || name.isBlank()) return null;
        String n = name.trim();
        UUID existing = deptByName.get(n);
        if (existing != null) return existing;

        UUID restored = restoreSoftDeleted("department", "dept_name", n);
        if (restored != null) {
            cacheDepartment(restored, null, n);
            return restored;
        }

        String pinyin = PinyinCodeUtil.toShortCode(n);
        String code = nextDeptCode();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dept_code", code);
        body.put("dept_name", n);
        body.put("pinyin_code", pinyin);
        body.put("is_active", true);
        var softId = SoftDeleteSupport.prepareCreate(jdbc, "department", body);
        if (softId.isPresent()) {
            UUID id = UUID.fromString(softId.get());
            jdbc.update("""
                    UPDATE department SET dept_code=?, dept_name=?, pinyin_code=?, is_active=TRUE,
                    is_deleted=0, deleted_at=NULL, deleted_by=NULL, updated_at=NOW(), updated_by=?::uuid
                    WHERE id=?::uuid
                    """, code, n, pinyin, SoftDeleteSupport.currentUserId(), id);
            cacheDepartment(id, code, n);
            return id;
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO department (id, dept_code, dept_name, pinyin_code, is_active, created_by, is_deleted)
                VALUES (?::uuid, ?, ?, ?, TRUE, ?::uuid, 0)
                """, id, code, n, pinyin, SoftDeleteSupport.currentUserId());
        cacheDepartment(id, code, n);
        return id;
    }

    private void cacheManufacturer(UUID id, String code, String name) {
        if (code != null) manufacturerByCode.put(code, id);
        if (name != null) manufacturerByName.put(name, id);
        if (code == null || name == null) {
            var rows = jdbc.queryForList("SELECT manufacturer_code, manufacturer_name FROM manufacturer WHERE id = ?::uuid", id);
            if (!rows.isEmpty()) {
                manufacturerByCode.put(String.valueOf(rows.get(0).get("manufacturer_code")), id);
                Object n = rows.get(0).get("manufacturer_name");
                if (n != null) manufacturerByName.put(n.toString().trim(), id);
            }
        }
    }

    private void cacheSupplier(UUID id, String code, String name) {
        if (code != null) supplierByCode.put(code, id);
        if (name != null) supplierByName.put(name, id);
        if (code == null || name == null) {
            var rows = jdbc.queryForList("SELECT supplier_code, supplier_name FROM supplier WHERE id = ?::uuid", id);
            if (!rows.isEmpty()) {
                supplierByCode.put(String.valueOf(rows.get(0).get("supplier_code")), id);
                Object n = rows.get(0).get("supplier_name");
                if (n != null) supplierByName.put(n.toString().trim(), id);
            }
        }
    }

    private void cacheDepartment(UUID id, String code, String name) {
        if (code != null) deptByCode.put(code, id);
        if (name != null) deptByName.put(name, id);
        if (code == null || name == null) {
            var rows = jdbc.queryForList("SELECT dept_code, dept_name FROM department WHERE id = ?::uuid", id);
            if (!rows.isEmpty()) {
                deptByCode.put(String.valueOf(rows.get(0).get("dept_code")), id);
                Object n = rows.get(0).get("dept_name");
                if (n != null) deptByName.put(n.toString().trim(), id);
            }
        }
    }

    /** 按非唯一列（如名称）恢复软删行。 */
    private UUID restoreSoftDeleted(String table, String column, String value) {
        if (!SoftDeleteSupport.supportsSoftDelete(jdbc, table)) return null;
        String deletedWhere = SoftDeleteSupport.hasIsDeleted(jdbc, table) ? "is_deleted = 1" : "deleted_at IS NOT NULL";
        var rows = jdbc.queryForList(
                "SELECT id FROM " + table + " WHERE " + column + " = ? AND " + deletedWhere + " LIMIT 1", value);
        if (rows.isEmpty()) return null;
        UUID id = (UUID) rows.get(0).get("id");
        jdbc.update("UPDATE " + table + " SET is_deleted=0, deleted_at=NULL, deleted_by=NULL, is_active=TRUE, "
                        + "updated_at=NOW(), updated_by=?::uuid WHERE id=?::uuid",
                SoftDeleteSupport.currentUserId(), id);
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
        loadByColumn("asset_category", "category_code", assetCategoryByCode);
        loadByColumn("finance_category", "finance_code", financeCategoryByCode);
        loadByColumn("warehouse", "warehouse_code", warehouseByCode);
        loadByColumn("manufacturer", "manufacturer_code", manufacturerByCode);
        loadByColumn("supplier", "supplier_code", supplierByCode);
        loadByColumn("department", "dept_code", deptByCode);
        loadByColumn("campus", "campus_name", campusByName);
        loadByName("manufacturer", "manufacturer_name", manufacturerByName);
        loadByName("supplier", "supplier_name", supplierByName);
        loadByName("department", "dept_name", deptByName);
    }

    private void loadByColumn(String table, String column, Map<String, UUID> target) {
        jdbc.queryForList("SELECT id, " + column + " FROM " + table + " WHERE 1=1"
                        + SoftDeleteSupport.notDeletedClause(jdbc, table, null))
                .forEach(r -> target.put(String.valueOf(r.get(column)), (UUID) r.get("id")));
    }

    private void loadByName(String table, String nameColumn, Map<String, UUID> target) {
        jdbc.queryForList("SELECT id, " + nameColumn + " FROM " + table + " WHERE 1=1"
                        + SoftDeleteSupport.notDeletedClause(jdbc, table, null))
                .forEach(r -> {
                    Object name = r.get(nameColumn);
                    if (name != null && !name.toString().isBlank()) {
                        target.put(name.toString().trim(), (UUID) r.get("id"));
                    }
                });
    }
}
