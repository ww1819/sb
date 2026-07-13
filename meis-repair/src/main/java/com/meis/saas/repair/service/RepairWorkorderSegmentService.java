package com.meis.saas.repair.service;

import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.persistence.TableColumnCache;
import com.meis.saas.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RepairWorkorderSegmentService {

    private static final Set<String> SEGMENT_EDITABLE_STATUSES = Set.of(
            "reported", "dispatching", "pending_accept", "accepted",
            "repairing", "suspended", "verify_rejected"
    );

    private static final Map<String, String> TYPE_TO_SUB = Map.of(
            "internal", "internal",
            "external", "external",
            "waiting_parts", "waiting_parts"
    );

    private final JdbcTemplate jdbc;
    private final RepairWorkorderProcessService processService;

    private boolean segmentTableReady() {
        return TableColumnCache.hasTable(jdbc, "repair_workorder_segment");
    }

    public List<Map<String, Object>> listActiveTypes() {
        if (!TableColumnCache.hasTable(jdbc, "repair_process_type")) return List.of();
        String clause = SoftDeleteSupport.notDeletedClause(jdbc, "repair_process_type", null);
        return jdbc.queryForList(
                "SELECT * FROM repair_process_type WHERE is_active = true" + clause + " ORDER BY sort_order, type_name");
    }

    public List<Map<String, Object>> listEngineerAddableTypes(UUID workorderId, String woStatus) {
        if (!segmentTableReady()) return List.of();
        return listActiveTypes().stream()
                .filter(t -> canEngineerAddType(t, woStatus))
                .toList();
    }

    public boolean canEngineerAddType(Map<String, Object> type, String woStatus) {
        if (!Boolean.TRUE.equals(type.get("can_engineer_add"))) return false;
        String rule = str(type.get("engineer_add_rule"));
        if ("system_only".equals(rule)) return false;
        if ("verify_rejected_only".equals(rule)) {
            return "verify_rejected".equals(woStatus);
        }
        return SEGMENT_EDITABLE_STATUSES.contains(woStatus);
    }

    public List<Map<String, Object>> listSegments(UUID workorderId) {
        if (!segmentTableReady()) return List.of();
        String segClause = SoftDeleteSupport.notDeletedClause(jdbc, "repair_workorder_segment", "s");
        String partClause = SoftDeleteSupport.notDeletedClause(jdbc, "repair_workorder_segment_part", "p");
        List<Map<String, Object>> segments = jdbc.queryForList("""
                SELECT s.*, t.type_code, t.type_name, t.can_add_parts,
                       u.real_name AS user_name
                FROM repair_workorder_segment s
                JOIN repair_process_type t ON t.id = s.process_type_id
                LEFT JOIN sys_user u ON u.id = s.user_id
                WHERE s.workorder_id = ?::uuid""" + segClause + """
                ORDER BY s.started_at ASC, s.id ASC
                """, workorderId);
        for (Map<String, Object> seg : segments) {
            UUID segId = UUID.fromString(String.valueOf(seg.get("id")));
            List<Map<String, Object>> parts = jdbc.queryForList("""
                    SELECT p.*, sp.part_code, sp.part_name
                    FROM repair_workorder_segment_part p
                    LEFT JOIN spare_part sp ON sp.id = p.spare_part_id
                    WHERE p.segment_id = ?::uuid""" + partClause + " ORDER BY p.created_at ASC", segId);
            seg.put("parts", parts);
            seg.put("open", seg.get("ended_at") == null);
        }
        return segments;
    }

    @Transactional
    public Map<String, Object> addEngineerSegment(UUID workorderId, Map<String, Object> wo, UUID processTypeId,
                                                  String remark, List<Map<String, Object>> parts) {
        if (!segmentTableReady()) {
            throw new BizException(500, "repair_workorder_segment 表不存在，请重启 meis-tenant 完成迁移");
        }
        String status = str(wo.get("status"));
        if (!SEGMENT_EDITABLE_STATUSES.contains(status)) {
            throw new BizException(400, "当前状态不可添加维修进程: " + status);
        }
        Map<String, Object> type = requireType(processTypeId);
        if (!canEngineerAddType(type, status)) {
            throw new BizException(400, "当前不可添加该进程类型: " + type.get("type_name"));
        }
        Object userId = wo.get("assigned_user_id");
        if (userId == null || String.valueOf(userId).isBlank()) {
            userId = TenantContext.getUserId();
        }
        closeOpenSegment(workorderId, OffsetDateTime.now());
        UUID segmentId = insertSegment(workorderId, processTypeId, userId, remark, null, false);
        applyTypeEffect(workorderId, wo, type, userId);
        if (Boolean.TRUE.equals(type.get("can_add_parts")) && parts != null) {
            saveParts(segmentId, parts, true);
        }
        return loadSegment(segmentId);
    }

    @Transactional
    public Map<String, Object> openSystemSegment(UUID workorderId, String typeCode, Object userId,
                                                 String remark, String verifyComment) {
        if (!segmentTableReady()) return Map.of();
        Map<String, Object> type = requireTypeByCode(typeCode);
        closeOpenSegment(workorderId, OffsetDateTime.now());
        UUID segmentId = insertSegment(workorderId, UUID.fromString(String.valueOf(type.get("id"))),
                userId, remark, verifyComment, true);
        Map<String, Object> wo = jdbc.queryForList(
                "SELECT * FROM repair_workorder WHERE id = ?::uuid", workorderId).stream().findFirst().orElse(Map.of());
        applyTypeEffect(workorderId, wo, type, userId);
        return loadSegment(segmentId);
    }

    @Transactional
    public Map<String, Object> openSegmentByCode(UUID workorderId, String typeCode, Object userId, String remark) {
        if (!segmentTableReady()) return Map.of();
        Map<String, Object> type = requireTypeByCode(typeCode);
        closeOpenSegment(workorderId, OffsetDateTime.now());
        UUID segmentId = insertSegment(workorderId, UUID.fromString(String.valueOf(type.get("id"))),
                userId, remark, null, false);
        Map<String, Object> wo = jdbc.queryForList(
                "SELECT * FROM repair_workorder WHERE id = ?::uuid", workorderId).stream().findFirst().orElse(Map.of());
        applyTypeEffect(workorderId, wo, type, userId);
        return loadSegment(segmentId);
    }

    public void closeOpenSegment(UUID workorderId, OffsetDateTime endedAt) {
        if (!segmentTableReady()) return;
        String clause = SoftDeleteSupport.notDeletedClause(jdbc, "repair_workorder_segment", null);
        jdbc.update("""
                UPDATE repair_workorder_segment SET ended_at = ?::timestamptz, updated_at = NOW()
                WHERE workorder_id = ?::uuid AND ended_at IS NULL""" + clause,
                endedAt, workorderId);
    }

    @Transactional
    public Map<String, Object> addPart(UUID segmentId, Map<String, Object> body) {
        Map<String, Object> seg = loadSegment(segmentId);
        if (!Boolean.TRUE.equals(seg.get("can_add_parts"))) {
            throw new BizException(400, "该进程段不可添加配件");
        }
        if (seg.get("ended_at") != null) {
            throw new BizException(400, "已结束的进程段不可添加配件");
        }
        UUID id = insertPart(segmentId, body);
        return loadPart(id);
    }

    private UUID insertSegment(UUID workorderId, UUID processTypeId, Object userId,
                               String remark, String verifyComment, boolean autoCreated) {
        UUID id = UUID.randomUUID();
        String operator = TenantContext.getUserId();
        jdbc.update("""
                INSERT INTO repair_workorder_segment
                (id, workorder_id, process_type_id, user_id, started_at, remark, verify_comment, auto_created, created_by, updated_by)
                VALUES (?::uuid,?::uuid,?::uuid,?::uuid,NOW(),?,?,?,?,?::uuid,?::uuid)
                """,
                id, workorderId, processTypeId, blankToNull(userId), remark, verifyComment, autoCreated,
                blankToNull(operator), blankToNull(operator));
        return id;
    }

    private void applyTypeEffect(UUID workorderId, Map<String, Object> wo, Map<String, Object> type, Object userId) {
        String code = str(type.get("type_code"));
        String status = str(wo.get("status"));
        switch (code) {
            case "internal", "external", "waiting_parts" -> {
                String sub = TYPE_TO_SUB.get(code);
                String targetStatus = "verify_rejected".equals(status) ? "verify_rejected" : "repairing";
                if (Set.of("reported", "dispatching", "pending_accept", "accepted").contains(status)) {
                    targetStatus = "repairing";
                }
                processService.syncWorkorderState(workorderId, targetStatus, sub, userId);
            }
            case "pending_verify" -> {
                processService.syncWorkorderState(workorderId, "pending_verify", null, null);
                syncDeviceStatus(wo.get("device_id"), "pending_verify");
            }
            case "verify_rejected" -> {
                processService.syncWorkorderState(workorderId, "verify_rejected", null, null);
                syncDeviceStatus(wo.get("device_id"), "maintenance");
            }
            case "verified" -> syncDeviceStatus(wo.get("device_id"), "normal");
            default -> { }
        }
    }

    private void saveParts(UUID segmentId, List<Map<String, Object>> parts, boolean validateType) {
        for (Map<String, Object> p : parts) {
            insertPart(segmentId, p);
        }
    }

    private UUID insertPart(UUID segmentId, Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        Object sparePartId = body.get("spare_part_id") != null ? body.get("spare_part_id") : body.get("part_id");
        Object qty = body.getOrDefault("quantity", 1);
        Object unitPrice = body.get("unit_price");
        BigDecimal total = calcTotal(qty, unitPrice);
        String operator = TenantContext.getUserId();
        jdbc.update("""
                INSERT INTO repair_workorder_segment_part
                (id, segment_id, spare_part_id, quantity, unit_price, total_price, remark, created_by, updated_by)
                VALUES (?::uuid,?::uuid,?::uuid,?,?,?,?,?::uuid,?::uuid)
                """,
                id, segmentId, blankToNull(sparePartId), qty, unitPrice, total,
                blankToNull(body.get("remark")), blankToNull(operator), blankToNull(operator));
        return id;
    }

    private Map<String, Object> loadSegment(UUID segmentId) {
        String clause = SoftDeleteSupport.notDeletedClause(jdbc, "repair_workorder_segment", "s");
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT s.*, t.type_code, t.type_name, t.can_add_parts
                FROM repair_workorder_segment s
                JOIN repair_process_type t ON t.id = s.process_type_id
                WHERE s.id = ?::uuid""" + clause, segmentId);
        if (rows.isEmpty()) throw new BizException(404, "进程段不存在");
        return rows.get(0);
    }

    private Map<String, Object> loadPart(UUID partId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM repair_workorder_segment_part WHERE id = ?::uuid", partId);
        if (rows.isEmpty()) throw new BizException(404, "配件明细不存在");
        return rows.get(0);
    }

    private Map<String, Object> requireType(UUID id) {
        String clause = SoftDeleteSupport.notDeletedClause(jdbc, "repair_process_type", null);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM repair_process_type WHERE id = ?::uuid" + clause, id);
        if (rows.isEmpty()) throw new BizException(404, "进程类型不存在");
        return rows.get(0);
    }

    private Map<String, Object> requireTypeByCode(String code) {
        String clause = SoftDeleteSupport.notDeletedClause(jdbc, "repair_process_type", null);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM repair_process_type WHERE type_code = ?" + clause, code);
        if (rows.isEmpty()) throw new BizException(404, "进程类型不存在: " + code);
        return rows.get(0);
    }

    private void syncDeviceStatus(Object deviceId, String status) {
        if (deviceId == null || String.valueOf(deviceId).isBlank()) return;
        jdbc.update("UPDATE medical_device SET device_status = ?, updated_at = NOW() WHERE id = ?::uuid", status, deviceId);
    }

    private static BigDecimal calcTotal(Object qty, Object unitPrice) {
        if (qty == null || unitPrice == null) return null;
        return new BigDecimal(String.valueOf(unitPrice)).multiply(new BigDecimal(String.valueOf(qty)));
    }

    private static Object blankToNull(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
