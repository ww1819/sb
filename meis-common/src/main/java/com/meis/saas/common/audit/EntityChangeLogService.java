package com.meis.saas.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meis.saas.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 实体级变更记录（附录 T）：字段 diff + 删/提交类精简快照。
 * 精简快照字段以 docs/meis-requirements.md 附录 T.5 为准，与 SNAPSHOT_FIELDS 保持同步。
 */
@Service
@RequiredArgsConstructor
public class EntityChangeLogService {
    public static final Set<String> TRACKED_TABLES = Set.of(
            "medical_device", "manufacturer", "supplier", "department", "sys_user", "repair_workorder",
            "campus", "building", "warehouse", "asset_category", "medical_device_category",
            "engineer", "fault_type_dict", "finance_category", "unit_dict", "sys_role"
    );

    /** 附录 T.5：delete/submit/withdraw 精简快照字段（按实体） */
    public static final Map<String, List<String>> SNAPSHOT_FIELDS = Map.ofEntries(
            Map.entry("medical_device", List.of(
                    "device_code", "device_name", "brand", "model", "serial_number",
                    "device_status", "risk_level", "dept_id", "campus_id",
                    "original_value", "enable_date", "is_active")),
            Map.entry("manufacturer", List.of(
                    "manufacturer_code", "manufacturer_name", "pinyin_code", "country",
                    "is_domestic", "contact_phone", "is_active")),
            Map.entry("supplier", List.of(
                    "supplier_code", "supplier_name", "pinyin_code", "contact_person",
                    "contact_phone", "unified_social_credit_code", "is_authorized", "is_active")),
            Map.entry("department", List.of(
                    "dept_code", "dept_name", "pinyin_code", "campus_id", "parent_id",
                    "is_clinical", "sort_order", "is_active")),
            Map.entry("sys_user", List.of(
                    "username", "real_name", "employee_no", "phone", "email",
                    "dept_id", "is_active", "permission_mode")),
            Map.entry("repair_workorder", List.of(
                    "wo_no", "device_id", "device_code", "device_name", "status", "urgency_level",
                    "fault_description", "reporter_id", "report_dept_id", "report_time",
                    "assigned_engineer_id", "repair_sub_status")),
            Map.entry("campus", List.of(
                    "campus_code", "campus_name", "address", "contact_phone", "is_active")),
            Map.entry("building", List.of(
                    "building_code", "building_name", "campus_id", "floor_count", "is_active")),
            Map.entry("warehouse", List.of(
                    "warehouse_code", "warehouse_name", "warehouse_type", "campus_id",
                    "dept_id", "manager_id", "address", "is_active")),
            Map.entry("asset_category", List.of(
                    "category_code", "category_name", "parent_id", "depreciation_years",
                    "residual_rate", "sort_order", "is_active")),
            Map.entry("medical_device_category", List.of(
                    "category_code", "category_name", "parent_code", "level", "sort_order", "is_active")),
            Map.entry("engineer", List.of(
                    "engineer_no", "real_name", "user_id", "specialty", "phone", "is_on_duty")),
            Map.entry("fault_type_dict", List.of(
                    "fault_code", "fault_name", "level", "is_active")),
            Map.entry("finance_category", List.of(
                    "finance_code", "finance_name", "parent_id", "account_subject",
                    "fund_source", "sort_order", "is_active")),
            Map.entry("unit_dict", List.of(
                    "unit_code", "unit_name", "unit_type", "sort_order", "is_active")),
            Map.entry("sys_role", List.of(
                    "role_code", "role_name", "description", "sort_order", "is_active"))
    );

    private static final Set<String> SENSITIVE = Set.of(
            "password", "password_hash", "salt", "token", "access_token", "refresh_token",
            "secret", "api_key", "credential"
    );

    private static final Set<String> SKIP_COMPARE = Set.of(
            "updated_at", "updated_by", "created_at", "created_by",
            "deleted_at", "deleted_by", "is_deleted"
    );

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public boolean tracks(String entityType) {
        return entityType != null && TRACKED_TABLES.contains(entityType);
    }

    public void recordCreate(String entityType, Object entityId, Map<String, Object> after) {
        if (!tracks(entityType)) return;
        List<Map<String, Object>> fields = diff(null, sanitize(after));
        write(entityType, entityId, "create", fields, null, null);
    }

    public void recordUpdate(String entityType, Object entityId,
                             Map<String, Object> before, Map<String, Object> after) {
        if (!tracks(entityType)) return;
        List<Map<String, Object>> fields = diff(sanitize(before), sanitize(after));
        if (fields.isEmpty()) return;
        write(entityType, entityId, "update", fields, null, null);
    }

    public void recordDelete(String entityType, Object entityId, Map<String, Object> before) {
        if (!tracks(entityType)) return;
        Map<String, Object> snap = compactSnapshot(entityType, sanitize(before));
        write(entityType, entityId, "delete", List.of(), snap, null);
    }

    /** submit / withdraw 等业务动作：diff + 精简快照 */
    public void recordAction(String entityType, Object entityId, String action,
                             Map<String, Object> before, Map<String, Object> after, String remark) {
        if (!tracks(entityType)) return;
        List<Map<String, Object>> fields = diff(sanitize(before), sanitize(after));
        Map<String, Object> snap = compactSnapshot(entityType, sanitize(after != null ? after : before));
        write(entityType, entityId, action, fields, snap, remark);
    }

    public Map<String, Object> loadRow(String table, Object id) {
        if (id == null || String.valueOf(id).isBlank()) return null;
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM " + table + " WHERE id = ?::uuid", id);
        return rows.isEmpty() ? null : new LinkedHashMap<>(rows.get(0));
    }

    private void write(String entityType, Object entityId, String action,
                       List<Map<String, Object>> changedFields,
                       Map<String, Object> snapshot, String remark) {
        try {
            if ("public".equals(TenantContext.getSchemaName())) return;
            String userId = TenantContext.getUserId();
            String operatorName = null;
            if (userId != null && !userId.isBlank()) {
                List<Map<String, Object>> u = jdbc.queryForList(
                        "SELECT COALESCE(real_name, username) AS name FROM sys_user WHERE id = ?::uuid", userId);
                if (!u.isEmpty()) operatorName = String.valueOf(u.get(0).get("name"));
            }
            jdbc.update("""
                    INSERT INTO sys_entity_change_log
                    (id, entity_type, entity_id, action, changed_fields, snapshot_json, operator_id, operator_name, remark)
                    VALUES (?::uuid, ?, ?::uuid, ?, ?::jsonb, ?::jsonb, ?::uuid, ?, ?)
                    """,
                    UUID.randomUUID(),
                    entityType,
                    String.valueOf(entityId),
                    action,
                    mapper.writeValueAsString(changedFields != null ? changedFields : List.of()),
                    snapshot == null ? null : mapper.writeValueAsString(snapshot),
                    userId,
                    operatorName,
                    remark);
        } catch (Exception ignored) {
            // 审计失败不阻断主业务
        }
    }

    private Map<String, Object> sanitize(Map<String, Object> src) {
        if (src == null) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : src.entrySet()) {
            String k = e.getKey();
            if (k == null) continue;
            String lower = k.toLowerCase(Locale.ROOT);
            if (SENSITIVE.contains(lower) || lower.endsWith("_hash") || lower.contains("password")) continue;
            out.put(k, normalize(e.getValue()));
        }
        return out;
    }

    /** 按附录 T.5 白名单裁剪；未配置清单时回退为有限字段数兜底 */
    private Map<String, Object> compactSnapshot(String entityType, Map<String, Object> row) {
        if (row == null || row.isEmpty()) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        List<String> keys = SNAPSHOT_FIELDS.get(entityType);
        if (keys != null && !keys.isEmpty()) {
            for (String k : keys) {
                if (SKIP_COMPARE.contains(k) || !row.containsKey(k)) continue;
                out.put(k, row.get(k));
            }
            return out.isEmpty() ? null : out;
        }
        int n = 0;
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (SKIP_COMPARE.contains(e.getKey()) || "id".equals(e.getKey())) continue;
            out.put(e.getKey(), e.getValue());
            if (++n >= 40) break;
        }
        return out;
    }

    private List<Map<String, Object>> diff(Map<String, Object> before, Map<String, Object> after) {
        List<Map<String, Object>> changes = new ArrayList<>();
        if (after == null && before == null) return changes;
        if (before == null) {
            for (Map.Entry<String, Object> e : after.entrySet()) {
                if (SKIP_COMPARE.contains(e.getKey())) continue;
                if (isEmpty(e.getValue())) continue;
                changes.add(fieldChange(e.getKey(), null, e.getValue()));
            }
            return changes;
        }
        if (after == null) {
            for (Map.Entry<String, Object> e : before.entrySet()) {
                if (SKIP_COMPARE.contains(e.getKey())) continue;
                changes.add(fieldChange(e.getKey(), e.getValue(), null));
            }
            return changes;
        }
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(before.keySet());
        keys.addAll(after.keySet());
        for (String k : keys) {
            if (SKIP_COMPARE.contains(k)) continue;
            Object ov = before.get(k);
            Object nv = after.get(k);
            if (Objects.equals(stringify(ov), stringify(nv))) continue;
            changes.add(fieldChange(k, ov, nv));
        }
        return changes;
    }

    private Map<String, Object> fieldChange(String field, Object oldVal, Object newVal) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("field", field);
        m.put("label", field);
        m.put("oldValue", stringify(oldVal));
        m.put("newValue", stringify(newVal));
        return m;
    }

    private static Object normalize(Object v) {
        if (v == null) return null;
        if (v instanceof UUID u) return u.toString();
        if (v instanceof java.sql.Array || v instanceof byte[]) return String.valueOf(v);
        return v;
    }

    private static String stringify(Object v) {
        if (v == null) return null;
        if (v instanceof Boolean b) return b ? "true" : "false";
        if (v instanceof Number || v instanceof CharSequence) return String.valueOf(v);
        return String.valueOf(v);
    }

    private static boolean isEmpty(Object v) {
        return v == null || (v instanceof String s && s.isBlank());
    }
}
