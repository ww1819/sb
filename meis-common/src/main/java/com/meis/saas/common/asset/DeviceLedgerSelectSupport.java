package com.meis.saas.common.asset;

/**
 * PLT-DEV-LIST-01：从属设备列表联查台账展示字段（读侧 enrich，不落从属表）。
 */
public final class DeviceLedgerSelectSupport {
    private DeviceLedgerSelectSupport() {}

    /**
     * SELECT 片段：依赖别名 d / wh / mfr / sup / pt 已 JOIN。
     * 科室名请用 {@code COALESCE(业务表.dept_name, dept.dept_name) AS dept_name}，避免盖住明细冗余。
     */
    public static final String SELECT_FIELDS = """
            d.brand,
            d.specification,
            d.model,
            d.registration_no,
            d.production_date,
            d.serial_number,
            d.device_status,
            wh.warehouse_name,
            mfr.manufacturer_code,
            mfr.manufacturer_name,
            sup.supplier_code,
            sup.supplier_name,
            CASE WHEN pt.tag_code IS NOT NULL THEN TRUE ELSE FALSE END AS has_power_tag,
            pt.tag_code AS power_tag_code
            """;

    /**
     * JOIN 片段：{@code deviceIdExpr} 如 {@code i.device_id}。
     * 不含软删子句时用 COALESCE(is_deleted,0)=0，避免各环境列差异。
     */
    public static String joins(String deviceIdExpr) {
        return """
                 LEFT JOIN medical_device d ON d.id = %s AND COALESCE(d.is_deleted, 0) = 0
                """.formatted(deviceIdExpr)
                + relatedJoins("d");
    }

    /**
     * 台账已是主表别名（默认 {@code d}）时的关联 JOIN（科室/仓库/厂家/供应商/电流标签）。
     */
    public static String relatedJoins() {
        return relatedJoins("d");
    }

    public static String relatedJoins(String deviceAlias) {
        String a = deviceAlias == null || deviceAlias.isBlank() ? "d" : deviceAlias.trim();
        return """
                 LEFT JOIN department dept ON dept.id = %1$s.dept_id AND COALESCE(dept.is_deleted, 0) = 0
                 LEFT JOIN warehouse wh ON wh.id = %1$s.warehouse_id AND COALESCE(wh.is_deleted, 0) = 0
                 LEFT JOIN manufacturer mfr ON mfr.id = %1$s.manufacturer_id AND COALESCE(mfr.is_deleted, 0) = 0
                 LEFT JOIN supplier sup ON sup.id = %1$s.supplier_id AND COALESCE(sup.is_deleted, 0) = 0
                 LEFT JOIN LATERAL (
                   SELECT p.tag_code
                   FROM power_tag p
                   WHERE p.device_id = %1$s.id AND COALESCE(p.is_deleted, 0) = 0
                   ORDER BY p.updated_at DESC NULLS LAST, p.created_at DESC NULLS LAST
                   LIMIT 1
                 ) pt ON TRUE
                """.formatted(a);
    }
}
