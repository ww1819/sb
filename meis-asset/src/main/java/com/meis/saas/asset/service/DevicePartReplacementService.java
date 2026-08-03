package com.meis.saas.asset.service;

import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.persistence.TableColumnCache;
import com.meis.saas.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * AST-PART-01 / AST-BF-01：非维修配件更换（草稿→确认）。
 */
@Service
@RequiredArgsConstructor
public class DevicePartReplacementService {
    private static final String TABLE = "device_part_replacement";

    private final JdbcTemplate jdbc;

    public List<Map<String, Object>> listByDevice(UUID deviceId, boolean includeDraft) {
        requireDevice(deviceId);
        StringBuilder sql = new StringBuilder("""
                SELECT * FROM device_part_replacement
                WHERE device_id = ?::uuid
                """);
        sql.append(SoftDeleteSupport.notDeletedClause(jdbc, TABLE, null));
        if (!includeDraft) {
            sql.append(" AND confirm_status = 'confirmed' ");
        }
        sql.append(" ORDER BY replaced_at DESC NULLS LAST, created_at DESC NULLS LAST");
        return jdbc.queryForList(sql.toString(), deviceId);
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body) {
        UUID deviceId = parseUuid(body.get("device_id"));
        if (deviceId == null) throw new BizException(400, "请指定设备");
        Map<String, Object> device = requireDevice(deviceId);
        Instant replacedAt = parseInstant(body.get("replaced_at"));
        if (replacedAt == null) throw new BizException(400, "请填写更换时间");

        Map<String, Object> row = new LinkedHashMap<>();
        UUID id = UUID.randomUUID();
        row.put("id", id);
        row.put("device_id", deviceId);
        row.put("device_code", device.get("device_code"));
        row.put("device_name", device.get("device_name"));
        fillPartSnapshot(row, body);
        fillSupplierSnapshot(row, body);
        BigDecimal qty = parseAmount(body.get("quantity"), "数量");
        BigDecimal unitPrice = body.get("unit_price") != null
                ? parseAmount(body.get("unit_price"), "单价") : null;
        BigDecimal total = body.get("total_price") != null
                ? parseAmount(body.get("total_price"), "金额")
                : (qty != null && unitPrice != null ? qty.multiply(unitPrice) : null);
        row.put("quantity", qty);
        row.put("unit_price", unitPrice);
        row.put("total_price", total);
        row.put("replaced_at", Timestamp.from(replacedAt));
        row.put("source_mode", firstNonBlank(str(body.get("source_mode")), "manual_backfill"));
        row.put("confirm_status", "draft");
        row.put("remark", body.get("remark"));
        if (body.get("created_by") != null) row.put("created_by", body.get("created_by"));
        if (body.get("created_by_name") != null) row.put("created_by_name", body.get("created_by_name"));
        SoftDeleteSupport.applyInsertAudit(jdbc, TABLE, row);
        insert(row);
        return load(id);
    }

    @Transactional
    public Map<String, Object> updateDraft(UUID id, Map<String, Object> body) {
        Map<String, Object> existing = load(id);
        if (!"draft".equals(String.valueOf(existing.get("confirm_status")))) {
            throw new BizException(400, "仅待确认草稿可修改");
        }
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (body.containsKey("spare_part_id") || body.containsKey("part_code") || body.containsKey("part_name")) {
            Map<String, Object> snap = new LinkedHashMap<>();
            fillPartSnapshot(snap, body);
            sets.add("spare_part_id = ?::uuid");
            args.add(snap.get("spare_part_id"));
            sets.add("part_code = ?");
            args.add(snap.get("part_code"));
            sets.add("part_name = ?");
            args.add(snap.get("part_name"));
            sets.add("part_specification = ?");
            args.add(snap.get("part_specification"));
            sets.add("part_model = ?");
            args.add(snap.get("part_model"));
        }
        if (body.containsKey("supplier_id") || body.containsKey("supplier_name")) {
            Map<String, Object> snap = new LinkedHashMap<>();
            fillSupplierSnapshot(snap, body);
            sets.add("supplier_id = ?::uuid");
            args.add(snap.get("supplier_id"));
            sets.add("supplier_name = ?");
            args.add(snap.get("supplier_name"));
        }
        if (body.containsKey("quantity")) {
            sets.add("quantity = ?");
            args.add(parseAmount(body.get("quantity"), "数量"));
        }
        if (body.containsKey("unit_price")) {
            sets.add("unit_price = ?");
            args.add(body.get("unit_price") == null ? null : parseAmount(body.get("unit_price"), "单价"));
        }
        if (body.containsKey("total_price")) {
            sets.add("total_price = ?");
            args.add(body.get("total_price") == null ? null : parseAmount(body.get("total_price"), "金额"));
        } else if (body.containsKey("quantity") || body.containsKey("unit_price")) {
            BigDecimal qty = body.containsKey("quantity")
                    ? parseAmount(body.get("quantity"), "数量")
                    : toBd(existing.get("quantity"));
            BigDecimal up = body.containsKey("unit_price")
                    ? (body.get("unit_price") == null ? null : parseAmount(body.get("unit_price"), "单价"))
                    : toBd(existing.get("unit_price"));
            if (qty != null && up != null) {
                sets.add("total_price = ?");
                args.add(qty.multiply(up));
            }
        }
        if (body.containsKey("replaced_at")) {
            Instant replacedAt = parseInstant(body.get("replaced_at"));
            if (replacedAt == null) throw new BizException(400, "请填写更换时间");
            sets.add("replaced_at = ?::timestamptz");
            args.add(Timestamp.from(replacedAt));
        }
        if (body.containsKey("remark")) {
            sets.add("remark = ?");
            args.add(body.get("remark"));
        }
        if (sets.isEmpty()) {
            return existing;
        }
        SoftDeleteSupport.appendUpdateAuditSets(jdbc, TableColumnCache.columns(jdbc, TABLE), sets, args);
        args.add(id);
        jdbc.update("UPDATE device_part_replacement SET " + String.join(", ", sets)
                + " WHERE id = ?::uuid"
                + SoftDeleteSupport.notDeletedClause(jdbc, TABLE, null), args.toArray());
        return load(id);
    }

    @Transactional
    public void deleteDraft(UUID id) {
        Map<String, Object> existing = load(id);
        if (!"draft".equals(String.valueOf(existing.get("confirm_status")))) {
            throw new BizException(400, "仅待确认草稿可删除");
        }
        SoftDeleteSupport.softDelete(jdbc, TABLE, id.toString());
    }

    @Transactional
    public Map<String, Object> confirm(UUID id) {
        Map<String, Object> existing = load(id);
        if (!"draft".equals(String.valueOf(existing.get("confirm_status")))) {
            throw new BizException(400, "仅待确认草稿可确认生效");
        }
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        sets.add("confirm_status = 'confirmed'");
        sets.add("confirmed_at = NOW()");
        UUID userId = currentUserUuid();
        sets.add("confirmed_by = ?::uuid");
        args.add(userId);
        sets.add("confirmed_by_name = ?");
        args.add(SoftDeleteSupport.resolveUserDisplayName(jdbc, userId));
        SoftDeleteSupport.appendUpdateAuditSets(jdbc, TableColumnCache.columns(jdbc, TABLE), sets, args);
        args.add(id);
        jdbc.update("UPDATE device_part_replacement SET " + String.join(", ", sets)
                + " WHERE id = ?::uuid"
                + SoftDeleteSupport.notDeletedClause(jdbc, TABLE, null), args.toArray());
        return load(id);
    }

    private void fillPartSnapshot(Map<String, Object> row, Map<String, Object> body) {
        UUID partId = parseUuid(body.get("spare_part_id"));
        row.put("spare_part_id", partId);
        if (partId != null) {
            var parts = jdbc.queryForList(
                    "SELECT part_code, part_name, specification, model FROM spare_part WHERE id = ?::uuid", partId);
            if (!parts.isEmpty()) {
                row.put("part_code", firstNonBlank(str(body.get("part_code")), str(parts.get(0).get("part_code"))));
                row.put("part_name", firstNonBlank(str(body.get("part_name")), str(parts.get(0).get("part_name"))));
                row.put("part_specification", firstNonBlank(str(body.get("part_specification")),
                        str(parts.get(0).get("specification"))));
                row.put("part_model", firstNonBlank(str(body.get("part_model")), str(parts.get(0).get("model"))));
                return;
            }
        }
        row.put("part_code", body.get("part_code"));
        row.put("part_name", body.get("part_name"));
        row.put("part_specification", body.get("part_specification"));
        row.put("part_model", body.get("part_model"));
        if (row.get("part_name") == null || String.valueOf(row.get("part_name")).isBlank()) {
            throw new BizException(400, "请填写配件名称或选择备件");
        }
    }

    private void fillSupplierSnapshot(Map<String, Object> row, Map<String, Object> body) {
        UUID supplierId = parseUuid(body.get("supplier_id"));
        row.put("supplier_id", supplierId);
        if (supplierId != null && (body.get("supplier_name") == null
                || String.valueOf(body.get("supplier_name")).isBlank())) {
            var rows = jdbc.queryForList(
                    "SELECT supplier_name FROM supplier WHERE id = ?::uuid AND COALESCE(is_deleted, 0) = 0",
                    supplierId);
            row.put("supplier_name", rows.isEmpty() ? null : rows.get(0).get("supplier_name"));
        } else {
            row.put("supplier_name", body.get("supplier_name"));
        }
    }

    private void insert(Map<String, Object> row) {
        jdbc.update("""
                INSERT INTO device_part_replacement (
                  id, device_id, device_code, device_name,
                  spare_part_id, part_code, part_name, part_specification, part_model,
                  quantity, unit_price, total_price, supplier_id, supplier_name,
                  replaced_at, source_mode, confirm_status, remark,
                  created_by, updated_by, created_by_name, updated_by_name, is_deleted
                ) VALUES (
                  ?::uuid, ?::uuid, ?, ?,
                  ?::uuid, ?, ?, ?, ?,
                  ?, ?, ?, ?::uuid, ?,
                  ?::timestamptz, ?, ?, ?,
                  ?::uuid, ?::uuid, ?, ?, COALESCE(?::smallint, 0)
                )
                """,
                row.get("id"), row.get("device_id"), row.get("device_code"), row.get("device_name"),
                row.get("spare_part_id"), row.get("part_code"), row.get("part_name"),
                row.get("part_specification"), row.get("part_model"),
                row.get("quantity"), row.get("unit_price"), row.get("total_price"),
                row.get("supplier_id"), row.get("supplier_name"),
                row.get("replaced_at"), row.get("source_mode"), row.get("confirm_status"), row.get("remark"),
                row.get("created_by"), row.get("updated_by"),
                row.get("created_by_name"), row.get("updated_by_name"),
                row.get("is_deleted") != null ? row.get("is_deleted") : 0);
    }

    private Map<String, Object> load(UUID id) {
        var rows = jdbc.queryForList(
                "SELECT * FROM device_part_replacement WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, TABLE, null), id);
        if (rows.isEmpty()) throw new BizException(404, "配件更换记录不存在");
        return rows.get(0);
    }

    private Map<String, Object> requireDevice(UUID deviceId) {
        var rows = jdbc.queryForList(
                "SELECT id, device_code, device_name FROM medical_device WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", null), deviceId);
        if (rows.isEmpty()) throw new BizException(404, "设备不存在");
        return rows.get(0);
    }

    private UUID currentUserUuid() {
        String uid = TenantContext.getUserId();
        if (uid == null || uid.isBlank()) return null;
        try {
            return UUID.fromString(uid);
        } catch (Exception e) {
            return null;
        }
    }

    private static UUID parseUuid(Object v) {
        if (v == null) return null;
        if (v instanceof UUID u) return u;
        String s = String.valueOf(v).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return null;
        try {
            return UUID.fromString(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static Instant parseInstant(Object v) {
        if (v == null) return null;
        if (v instanceof Instant i) return i;
        if (v instanceof Timestamp ts) return ts.toInstant();
        if (v instanceof java.util.Date d) return d.toInstant();
        if (v instanceof OffsetDateTime odt) return odt.toInstant();
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return null;
        try {
            return Instant.parse(s);
        } catch (Exception ignored) {
        }
        try {
            return OffsetDateTime.parse(s).toInstant();
        } catch (Exception ignored) {
        }
        try {
            return Timestamp.valueOf(s.replace('T', ' ').substring(0, Math.min(s.length(), 19))).toInstant();
        } catch (Exception e) {
            throw new BizException(400, "时间格式无效: " + s);
        }
    }

    private static BigDecimal parseAmount(Object v, String label) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        try {
            return new BigDecimal(String.valueOf(v).trim());
        } catch (Exception e) {
            throw new BizException(400, label + "格式无效");
        }
    }

    private static BigDecimal toBd(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return b;
    }
}
