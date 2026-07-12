package com.meis.saas.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meis.saas.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 实体级变更记录（附录 T）：字段 diff + 删/提交类精简快照。
 */
@Service
@RequiredArgsConstructor
public class EntityChangeLogService {
    public static final Set<String> TRACKED_TABLES = Set.of(
            "medical_device", "manufacturer", "supplier", "department", "sys_user", "repair_workorder",
            "campus", "building", "warehouse", "asset_category", "medical_device_category",
            "engineer", "fault_type_dict", "finance_category", "unit_dict", "sys_role"
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
        Map<String, Object> snap = compactSnapshot(sanitize(before));
        write(entityType, entityId, "delete", List.of(), snap, null);
    }

    /** submit / withdraw 等业务动作：diff + 精简快照 */
    public void recordAction(String entityType, Object entityId, String action,
                             Map<String, Object> before, Map<String, Object> after, String remark) {
        if (!tracks(entityType)) return;
        List<Map<String, Object>> fields = diff(sanitize(before), sanitize(after));
        Map<String, Object> snap = compactSnapshot(sanitize(after != null ? after : before));
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

    private Map<String, Object> compactSnapshot(Map<String, Object> row) {
        if (row == null || row.isEmpty()) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        int n = 0;
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (SKIP_COMPARE.contains(e.getKey())) continue;
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
