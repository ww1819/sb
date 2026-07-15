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
        // 进程类型与 segment 表解耦，避免表短暂缺失时下拉静默「无数据」
        return listActiveTypes().stream()
                .filter(t -> canEngineerAddType(t, woStatus))
                .toList();
    }

    public boolean canEngineerAddType(Map<String, Object> type, String woStatus) {
        if (!toBool(type.get("can_engineer_add"))) return false;
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
                JOIN repair_process_type t ON t.id = s.process_type_id"""
                + SoftDeleteSupport.notDeletedClause(jdbc, "repair_process_type", "t") + """
                LEFT JOIN sys_user u ON u.id = s.user_id"""
                + SoftDeleteSupport.notDeletedClause(jdbc, "sys_user", "u") + """
                WHERE s.workorder_id = ?::uuid""" + segClause + """
                ORDER BY s.started_at ASC, s.id ASC
                """, workorderId);
        for (Map<String, Object> seg : segments) {
            UUID segId = UUID.fromString(String.valueOf(seg.get("id")));
            List<Map<String, Object>> parts = jdbc.queryForList("""
                    SELECT p.*, sp.part_code, sp.part_name
                    FROM repair_workorder_segment_part p
                    LEFT JOIN spare_part sp ON sp.id = p.spare_part_id"""
                    + SoftDeleteSupport.notDeletedClause(jdbc, "spare_part", "sp")
                    + " WHERE p.segment_id = ?::uuid" + partClause + " ORDER BY p.created_at ASC", segId);
            seg.put("parts", parts);
            seg.put("open", seg.get("ended_at") == null);
            attachSegmentUsers(seg, segId);
        }
        return segments;
    }

    @Transactional
    public Map<String, Object> addEngineerSegment(UUID workorderId, Map<String, Object> wo, UUID processTypeId,
                                                  String remark, List<Map<String, Object>> parts,
                                                  List<String> userIds, OffsetDateTime startedAt, OffsetDateTime endedAt) {
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
        List<String> engineers = normalizeUserIds(userIds);
        if (engineers.isEmpty()) {
            Object fallback = blankToNull(wo.get("assigned_user_id"));
            if (fallback == null) fallback = TenantContext.getUserId();
            if (fallback != null && !String.valueOf(fallback).isBlank()) {
                engineers = List.of(String.valueOf(fallback));
            }
        }
        if (engineers.isEmpty()) {
            throw new BizException(400, "请选择维修工程师");
        }
        for (String uid : engineers) {
            assertIsRepairEngineer(uid);
        }
        String primaryUserId = engineers.get(0);

        OffsetDateTime start = startedAt != null ? startedAt : OffsetDateTime.now();
        if (endedAt != null && endedAt.isBefore(start)) {
            throw new BizException(400, "结束时间不能早于开始时间");
        }

        closeOpenSegment(workorderId, start);
        UUID segmentId = insertSegment(workorderId, processTypeId, primaryUserId, remark, null, false, start, endedAt);
        saveSegmentUsers(segmentId, engineers);
        applyTypeEffect(workorderId, wo, type, primaryUserId);
        if (toBool(type.get("can_add_parts")) && parts != null) {
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
                userId, remark, verifyComment, true, OffsetDateTime.now(), null);
        if (userId != null) {
            saveSegmentUsers(segmentId, List.of(String.valueOf(userId)));
        }
        Map<String, Object> wo = jdbc.queryForList(
                "SELECT * FROM repair_workorder WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "repair_workorder", null), workorderId)
                .stream().findFirst().orElse(Map.of());
        applyTypeEffect(workorderId, wo, type, userId);
        return loadSegment(segmentId);
    }

    @Transactional
    public Map<String, Object> openSegmentByCode(UUID workorderId, String typeCode, Object userId, String remark) {
        if (!segmentTableReady()) return Map.of();
        Map<String, Object> type = requireTypeByCode(typeCode);
        closeOpenSegment(workorderId, OffsetDateTime.now());
        UUID segmentId = insertSegment(workorderId, UUID.fromString(String.valueOf(type.get("id"))),
                userId, remark, null, false, OffsetDateTime.now(), null);
        if (userId != null) {
            saveSegmentUsers(segmentId, List.of(String.valueOf(userId)));
        }
        Map<String, Object> wo = jdbc.queryForList(
                "SELECT * FROM repair_workorder WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "repair_workorder", null), workorderId)
                .stream().findFirst().orElse(Map.of());
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
        if (!toBool(seg.get("can_add_parts"))) {
            throw new BizException(400, "该进程段不可添加配件");
        }
        if (seg.get("ended_at") != null) {
            throw new BizException(400, "已结束的进程段不可添加配件");
        }
        UUID id = insertPart(segmentId, body);
        return loadPart(id);
    }

    private UUID insertSegment(UUID workorderId, UUID processTypeId, Object userId,
                               String remark, String verifyComment, boolean autoCreated,
                               OffsetDateTime startedAt, OffsetDateTime endedAt) {
        UUID id = UUID.randomUUID();
        String operator = TenantContext.getUserId();
        OffsetDateTime start = startedAt != null ? startedAt : OffsetDateTime.now();
        jdbc.update("""
                INSERT INTO repair_workorder_segment
                (id, workorder_id, process_type_id, user_id, started_at, ended_at, remark, verify_comment, auto_created, created_by, updated_by)
                VALUES (?::uuid,?::uuid,?::uuid,?::uuid,?::timestamptz,?::timestamptz,?,?,?,?::uuid,?::uuid)
                """,
                id, workorderId, processTypeId, blankToNull(userId), start, endedAt, remark, verifyComment, autoCreated,
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
                JOIN repair_process_type t ON t.id = s.process_type_id"""
                + SoftDeleteSupport.notDeletedClause(jdbc, "repair_process_type", "t") + """
                WHERE s.id = ?::uuid""" + clause, segmentId);
        if (rows.isEmpty()) throw new BizException(404, "进程段不存在");
        Map<String, Object> seg = rows.get(0);
        UUID segId = UUID.fromString(String.valueOf(seg.get("id")));
        attachSegmentUsers(seg, segId);
        return seg;
    }

    private void attachSegmentUsers(Map<String, Object> seg, UUID segmentId) {
        if (!TableColumnCache.hasTable(jdbc, "repair_workorder_segment_user")) {
            Object uid = seg.get("user_id");
            if (uid != null) {
                seg.put("user_ids", List.of(String.valueOf(uid)));
                if (seg.get("user_name") != null) {
                    seg.put("user_names", List.of(String.valueOf(seg.get("user_name"))));
                }
            } else {
                seg.put("user_ids", List.of());
                seg.put("user_names", List.of());
            }
            return;
        }
        String clause = SoftDeleteSupport.notDeletedClause(jdbc, "repair_workorder_segment_user", "su");
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT su.user_id, su.is_primary, u.real_name AS user_name
                FROM repair_workorder_segment_user su
                LEFT JOIN sys_user u ON u.id = su.user_id"""
                + SoftDeleteSupport.notDeletedClause(jdbc, "sys_user", "u") + """
                WHERE su.segment_id = ?::uuid""" + clause + """
                ORDER BY su.is_primary DESC, su.created_at ASC
                """, segmentId);
        if (rows.isEmpty() && seg.get("user_id") != null) {
            seg.put("user_ids", List.of(String.valueOf(seg.get("user_id"))));
            seg.put("user_names", seg.get("user_name") != null
                    ? List.of(String.valueOf(seg.get("user_name"))) : List.of());
            return;
        }
        List<String> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            if (r.get("user_id") != null) ids.add(String.valueOf(r.get("user_id")));
            names.add(r.get("user_name") != null ? String.valueOf(r.get("user_name")) : "");
        }
        seg.put("user_ids", ids);
        seg.put("user_names", names);
        seg.put("users", rows);
    }

    private void saveSegmentUsers(UUID segmentId, List<String> userIds) {
        if (!TableColumnCache.hasTable(jdbc, "repair_workorder_segment_user")) return;
        String operator = TenantContext.getUserId();
        int i = 0;
        for (String uid : userIds) {
            if (uid == null || uid.isBlank()) continue;
            boolean primary = i == 0;
            jdbc.update("""
                    INSERT INTO repair_workorder_segment_user
                    (id, segment_id, user_id, is_primary, created_by, updated_by)
                    VALUES (?::uuid,?::uuid,?::uuid,?,?,?::uuid,?::uuid)
                    ON CONFLICT (segment_id, user_id) DO UPDATE
                    SET is_primary = EXCLUDED.is_primary, updated_at = NOW(), is_deleted = 0, deleted_at = NULL
                    """,
                    UUID.randomUUID(), segmentId, uid, primary, blankToNull(operator), blankToNull(operator));
            i++;
        }
    }

    private static List<String> normalizeUserIds(List<String> raw) {
        if (raw == null || raw.isEmpty()) return new ArrayList<>();
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String s : raw) {
            if (s != null && !s.isBlank() && !"null".equalsIgnoreCase(s)) {
                set.add(s.trim());
            }
        }
        return new ArrayList<>(set);
    }

    private Map<String, Object> loadPart(UUID partId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM repair_workorder_segment_part WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "repair_workorder_segment_part", null), partId);
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

    private void assertIsRepairEngineer(Object userId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT COALESCE(is_repair_engineer, false) AS is_repair_engineer FROM sys_user WHERE id = ?::uuid AND is_active = true"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "sys_user", null),
                userId);
        if (rows.isEmpty() || !toBool(rows.get(0).get("is_repair_engineer"))) {
            throw new BizException(400, "所选人员不是维修工程师");
        }
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

    private static boolean toBool(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        String s = String.valueOf(v).trim();
        return "true".equalsIgnoreCase(s) || "t".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s);
    }

    /** 解析前端传入的时间（支持本地 datetime / ISO） */
    public static OffsetDateTime parseDateTime(Object raw) {
        if (raw == null) return null;
        String s = String.valueOf(raw).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return null;
        try {
            if (s.endsWith("Z") || s.matches(".*[+-]\\d{2}:\\d{2}$")) {
                return OffsetDateTime.parse(s);
            }
        } catch (Exception ignored) {
            // fall through
        }
        String norm = s.replace(' ', 'T');
        if (norm.length() == 16) {
            norm = norm + ":00";
        }
        try {
            return java.time.LocalDateTime.parse(norm)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toOffsetDateTime();
        } catch (Exception e) {
            throw new BizException(400, "时间格式不正确: " + raw);
        }
    }
}
