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
        boolean hasConfirmedBy = TableColumnCache.hasColumn(jdbc, "repair_workorder_segment", "confirmed_by");
        String confirmerJoin = hasConfirmedBy
                ? " LEFT JOIN sys_user cu ON cu.id = s.confirmed_by"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "sys_user", "cu")
                : "";
        String confirmerSelect = hasConfirmedBy ? ", cu.real_name AS confirmed_by_name" : "";
        List<Map<String, Object>> segments = jdbc.queryForList("""
                SELECT s.*, t.type_code, t.type_name, t.can_add_parts,
                       u.real_name AS user_name""" + confirmerSelect + """
                FROM repair_workorder_segment s
                JOIN repair_process_type t ON t.id = s.process_type_id"""
                + SoftDeleteSupport.notDeletedClause(jdbc, "repair_process_type", "t") + """
                LEFT JOIN sys_user u ON u.id = s.user_id"""
                + SoftDeleteSupport.notDeletedClause(jdbc, "sys_user", "u")
                + confirmerJoin + """
                WHERE s.workorder_id = ?::uuid""" + segClause + """
                ORDER BY s.started_at ASC, s.id ASC
                """, workorderId);
        boolean hasSupplierId = TableColumnCache.hasColumn(jdbc, "repair_workorder_segment_part", "supplier_id");
        String supplierJoin = hasSupplierId
                ? " LEFT JOIN supplier sup ON sup.id = p.supplier_id"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "supplier", "sup")
                : "";
        String supplierSelect = hasSupplierId ? ", sup.supplier_name" : "";
        for (Map<String, Object> seg : segments) {
            UUID segId = UUID.fromString(String.valueOf(seg.get("id")));
            List<Map<String, Object>> parts = jdbc.queryForList("""
                    SELECT p.*, sp.part_code, sp.part_name""" + supplierSelect + """
                    FROM repair_workorder_segment_part p
                    LEFT JOIN spare_part sp ON sp.id = p.spare_part_id"""
                    + SoftDeleteSupport.notDeletedClause(jdbc, "spare_part", "sp")
                    + supplierJoin
                    + " WHERE p.segment_id = ?::uuid" + partClause + " ORDER BY p.created_at ASC", segId);
            seg.put("parts", parts);
            seg.put("open", seg.get("ended_at") == null);
            enrichSegmentConfirmed(seg);
            attachSegmentUsers(seg, segId);
        }
        return segments;
    }

    /**
     * 添加工程师进程段；engineers 每项含 user_id/userId、可选 work_content/workContent、is_primary/isPrimary。
     * 仅传 ID 时可用 {@link #engineersFromUserIds(List)} 转换。
     */
    @Transactional
    public Map<String, Object> addEngineerSegment(UUID workorderId, Map<String, Object> wo, UUID processTypeId,
                                                  String remark, List<Map<String, Object>> parts,
                                                  List<Map<String, Object>> engineers,
                                                  OffsetDateTime startedAt, OffsetDateTime endedAt) {
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
        List<Map<String, Object>> engineerRows = normalizeEngineers(engineers);
        if (engineerRows.isEmpty()) {
            Object fallback = blankToNull(wo.get("assigned_user_id"));
            if (fallback == null) fallback = TenantContext.getUserId();
            if (fallback != null && !String.valueOf(fallback).isBlank()) {
                engineerRows = engineersFromUserIds(List.of(String.valueOf(fallback)));
            }
        }
        if (engineerRows.isEmpty()) {
            throw new BizException(400, "请选择维修工程师");
        }
        for (Map<String, Object> eng : engineerRows) {
            assertIsRepairEngineer(eng.get("user_id"));
        }
        String primaryUserId = resolvePrimaryUserId(engineerRows);

        OffsetDateTime start = startedAt != null ? startedAt : OffsetDateTime.now();
        if (endedAt != null && endedAt.isBefore(start)) {
            throw new BizException(400, "结束时间不能早于开始时间");
        }

        closeOpenSegment(workorderId, start);
        UUID segmentId = insertSegment(workorderId, processTypeId, primaryUserId, remark, null, false, start, endedAt, wo);
        saveSegmentUsers(segmentId, engineerRows);
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
        Map<String, Object> wo = loadWorkorder(workorderId);
        closeOpenSegment(workorderId, OffsetDateTime.now());
        if ("pending_verify".equals(typeCode)) {
            confirmAllUnconfirmed(workorderId);
        }
        UUID segmentId = insertSegment(workorderId, UUID.fromString(String.valueOf(type.get("id"))),
                userId, remark, verifyComment, true, OffsetDateTime.now(), null, wo);
        if (userId != null) {
            saveSegmentUsersByIds(segmentId, List.of(String.valueOf(userId)));
        }
        applyTypeEffect(workorderId, wo, type, userId);
        return loadSegment(segmentId);
    }

    @Transactional
    public Map<String, Object> openSegmentByCode(UUID workorderId, String typeCode, Object userId, String remark) {
        if (!segmentTableReady()) return Map.of();
        Map<String, Object> type = requireTypeByCode(typeCode);
        Map<String, Object> wo = loadWorkorder(workorderId);
        closeOpenSegment(workorderId, OffsetDateTime.now());
        UUID segmentId = insertSegment(workorderId, UUID.fromString(String.valueOf(type.get("id"))),
                userId, remark, null, false, OffsetDateTime.now(), null, wo);
        if (userId != null) {
            saveSegmentUsersByIds(segmentId, List.of(String.valueOf(userId)));
        }
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
        assertSegmentEditable(seg);
        UUID id = insertPart(segmentId, body);
        return loadPart(id);
    }

    /**
     * 确认固化进程段。已手写确认的拒绝重复确认；系统自动段（auto_created）视为已固化，幂等返回。
     */
    @Transactional
    public Map<String, Object> confirmSegment(UUID workorderId, UUID segmentId) {
        if (!segmentTableReady()) {
            throw new BizException(500, "repair_workorder_segment 表不存在，请重启 meis-tenant 完成迁移");
        }
        Map<String, Object> seg = loadSegment(segmentId);
        assertSegmentBelongs(workorderId, seg);
        if (toBool(seg.get("auto_created"))) {
            return seg;
        }
        if (seg.get("confirmed_at") != null) {
            throw new BizException(400, "该进程段已确认");
        }
        if (!TableColumnCache.hasColumn(jdbc, "repair_workorder_segment", "confirmed_at")) {
            throw new BizException(500, "confirmed_at 列不存在，请重启 meis-tenant 完成迁移");
        }
        String operator = TenantContext.getUserId();
        if (operator == null || operator.isBlank()) {
            throw new BizException(401, "未登录");
        }
        jdbc.update("""
                UPDATE repair_workorder_segment
                SET confirmed_at = NOW(), confirmed_by = ?::uuid,
                    ended_at = COALESCE(ended_at, NOW()),
                    updated_at = NOW(), updated_by = ?::uuid
                WHERE id = ?::uuid AND workorder_id = ?::uuid AND confirmed_at IS NULL
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "repair_workorder_segment", null),
                operator, operator, segmentId, workorderId);
        return loadSegment(segmentId);
    }

    /**
     * 对本工单所有未确认且非 auto_created 的段执行与手工确认同等落库
     *（confirmed_at/confirmed_by；空 ended_at 补 NOW）。
     */
    public void confirmAllUnconfirmed(UUID workorderId) {
        if (!segmentTableReady()) return;
        if (!TableColumnCache.hasColumn(jdbc, "repair_workorder_segment", "confirmed_at")) {
            throw new BizException(500, "confirmed_at 列不存在，请重启 meis-tenant 完成迁移");
        }
        String operator = TenantContext.getUserId();
        if (operator == null || operator.isBlank()) {
            throw new BizException(401, "未登录");
        }
        jdbc.update("""
                UPDATE repair_workorder_segment
                SET confirmed_at = NOW(), confirmed_by = ?::uuid,
                    ended_at = COALESCE(ended_at, NOW()),
                    updated_at = NOW(), updated_by = ?::uuid
                WHERE workorder_id = ?::uuid
                  AND confirmed_at IS NULL
                  AND COALESCE(auto_created, false) = false
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "repair_workorder_segment", null),
                operator, operator, workorderId);
    }

    /**
     * 更新未确认进程段：备注、起止时间、工程师全量替换。不重开上一段。
     */
    @Transactional
    public Map<String, Object> updateSegment(UUID workorderId, UUID segmentId, Map<String, Object> body,
                                             List<Map<String, Object>> engineers) {
        if (!segmentTableReady()) {
            throw new BizException(500, "repair_workorder_segment 表不存在，请重启 meis-tenant 完成迁移");
        }
        Map<String, Object> seg = loadSegment(segmentId);
        assertSegmentBelongs(workorderId, seg);
        assertSegmentEditable(seg);

        String remark = seg.get("remark") != null ? String.valueOf(seg.get("remark")) : null;
        if (body != null && body.containsKey("remark")) {
            remark = body.get("remark") == null ? null : String.valueOf(body.get("remark"));
        }

        OffsetDateTime startedAt = asOffsetDateTime(seg.get("started_at"));
        OffsetDateTime endedAt = asOffsetDateTime(seg.get("ended_at"));
        if (body != null) {
            if (body.containsKey("started_at") || body.containsKey("startedAt")) {
                Object raw = body.get("started_at") != null ? body.get("started_at") : body.get("startedAt");
                startedAt = parseDateTime(raw);
            }
            if (body.containsKey("ended_at") || body.containsKey("endedAt")) {
                Object raw = body.get("ended_at") != null ? body.get("ended_at") : body.get("endedAt");
                endedAt = parseDateTime(raw);
            }
        }
        if (startedAt == null) {
            throw new BizException(400, "开始时间不能为空");
        }
        if (endedAt != null && endedAt.isBefore(startedAt)) {
            throw new BizException(400, "结束时间不能早于开始时间");
        }

        List<Map<String, Object>> engineerRows = engineers != null
                ? normalizeEngineers(engineers)
                : null;
        String primaryUserId = null;
        if (engineerRows != null) {
            if (engineerRows.isEmpty()) {
                throw new BizException(400, "请至少保留一名维修工程师");
            }
            for (Map<String, Object> eng : engineerRows) {
                assertIsRepairEngineer(eng.get("user_id"));
            }
            primaryUserId = resolvePrimaryUserId(engineerRows);
        }

        String operator = TenantContext.getUserId();
        if (primaryUserId != null) {
            jdbc.update("""
                    UPDATE repair_workorder_segment
                    SET remark = ?, started_at = ?::timestamptz, ended_at = ?::timestamptz,
                        user_id = ?::uuid, updated_at = NOW(), updated_by = ?::uuid
                    WHERE id = ?::uuid AND workorder_id = ?::uuid
                    """ + SoftDeleteSupport.notDeletedClause(jdbc, "repair_workorder_segment", null),
                    remark, startedAt, endedAt, primaryUserId, blankToNull(operator), segmentId, workorderId);
            replaceSegmentUsers(segmentId, engineerRows);
        } else {
            jdbc.update("""
                    UPDATE repair_workorder_segment
                    SET remark = ?, started_at = ?::timestamptz, ended_at = ?::timestamptz,
                        updated_at = NOW(), updated_by = ?::uuid
                    WHERE id = ?::uuid AND workorder_id = ?::uuid
                    """ + SoftDeleteSupport.notDeletedClause(jdbc, "repair_workorder_segment", null),
                    remark, startedAt, endedAt, blankToNull(operator), segmentId, workorderId);
        }
        return loadSegment(segmentId);
    }

    /**
     * 软删未确认进程段；不清空上一段 ended_at。
     */
    @Transactional
    public void deleteSegment(UUID workorderId, UUID segmentId) {
        if (!segmentTableReady()) {
            throw new BizException(500, "repair_workorder_segment 表不存在，请重启 meis-tenant 完成迁移");
        }
        Map<String, Object> seg = loadSegment(segmentId);
        assertSegmentBelongs(workorderId, seg);
        assertSegmentEditable(seg);
        SoftDeleteSupport.softDelete(jdbc, "repair_workorder_segment", segmentId.toString());
    }

    @Transactional
    public Map<String, Object> updatePart(UUID segmentId, UUID partId, Map<String, Object> body) {
        Map<String, Object> seg = loadSegment(segmentId);
        assertSegmentEditable(seg);
        Map<String, Object> part = loadPart(partId);
        if (!Objects.equals(String.valueOf(segmentId), String.valueOf(part.get("segment_id")))) {
            throw new BizException(404, "配件明细不属于该进程段");
        }
        Object qty = body != null && body.containsKey("quantity")
                ? body.get("quantity") : part.get("quantity");
        Object unitPrice = part.get("unit_price");
        if (body != null) {
            if (body.containsKey("unit_price")) unitPrice = body.get("unit_price");
            else if (body.containsKey("unitPrice")) unitPrice = body.get("unitPrice");
        }
        Object remark = part.get("remark");
        if (body != null && body.containsKey("remark")) {
            remark = blankToNull(body.get("remark"));
        }
        BigDecimal total = calcTotal(qty, unitPrice);
        String operator = TenantContext.getUserId();
        boolean hasSupplierId = TableColumnCache.hasColumn(jdbc, "repair_workorder_segment_part", "supplier_id");
        if (hasSupplierId) {
            Object supplierId = part.get("supplier_id");
            if (body != null && (body.containsKey("supplier_id") || body.containsKey("supplierId"))) {
                supplierId = body.get("supplier_id") != null ? body.get("supplier_id") : body.get("supplierId");
            }
            jdbc.update("""
                    UPDATE repair_workorder_segment_part
                    SET quantity = ?, unit_price = ?, total_price = ?, supplier_id = ?::uuid,
                        remark = ?, updated_at = NOW(), updated_by = ?::uuid
                    WHERE id = ?::uuid AND segment_id = ?::uuid
                    """ + SoftDeleteSupport.notDeletedClause(jdbc, "repair_workorder_segment_part", null),
                    qty, unitPrice, total, blankToNull(supplierId), remark, blankToNull(operator),
                    partId, segmentId);
        } else {
            jdbc.update("""
                    UPDATE repair_workorder_segment_part
                    SET quantity = ?, unit_price = ?, total_price = ?,
                        remark = ?, updated_at = NOW(), updated_by = ?::uuid
                    WHERE id = ?::uuid AND segment_id = ?::uuid
                    """ + SoftDeleteSupport.notDeletedClause(jdbc, "repair_workorder_segment_part", null),
                    qty, unitPrice, total, remark, blankToNull(operator), partId, segmentId);
        }
        return loadPart(partId);
    }

    @Transactional
    public void deletePart(UUID segmentId, UUID partId) {
        Map<String, Object> seg = loadSegment(segmentId);
        assertSegmentEditable(seg);
        Map<String, Object> part = loadPart(partId);
        if (!Objects.equals(String.valueOf(segmentId), String.valueOf(part.get("segment_id")))) {
            throw new BizException(404, "配件明细不属于该进程段");
        }
        SoftDeleteSupport.softDelete(jdbc, "repair_workorder_segment_part", partId.toString());
    }

    /**
     * 提交验收前：所有已结束（ended_at 非空）的历史段须已确认；当前开放段可未确认。
     */
    public void assertAllHistoricalConfirmed(UUID workorderId) {
        if (!segmentTableReady()) return;
        List<Map<String, Object>> segments = listSegments(workorderId);
        for (Map<String, Object> seg : segments) {
            if (seg.get("ended_at") == null) continue;
            if (!isSegmentConfirmed(seg)) {
                String name = str(seg.get("type_name"));
                if (name.isBlank()) name = str(seg.get("type_code"));
                throw new BizException(400, "存在未确认的历史进程段，请先确认后再提交验收"
                        + (name.isBlank() ? "" : "（" + name + "）"));
            }
        }
    }

    public static boolean isSegmentConfirmed(Map<String, Object> seg) {
        if (seg == null) return false;
        if (seg.get("confirmed_at") != null) return true;
        Object confirmed = seg.get("confirmed");
        if (confirmed instanceof Boolean b) return b;
        return toBool(seg.get("auto_created"));
    }

    private void assertSegmentBelongs(UUID workorderId, Map<String, Object> seg) {
        if (!Objects.equals(String.valueOf(workorderId), String.valueOf(seg.get("workorder_id")))) {
            throw new BizException(404, "进程段不属于该工单");
        }
    }

    private void assertSegmentEditable(Map<String, Object> seg) {
        if (isSegmentConfirmed(seg)) {
            throw new BizException(400, "已确认的进程段不可修改");
        }
    }

    private void enrichSegmentConfirmed(Map<String, Object> seg) {
        boolean confirmed = seg.get("confirmed_at") != null || toBool(seg.get("auto_created"));
        seg.put("confirmed", confirmed);
    }

    private Map<String, Object> loadWorkorder(UUID workorderId) {
        return jdbc.queryForList(
                "SELECT * FROM repair_workorder WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "repair_workorder", null), workorderId)
                .stream().findFirst().orElse(Map.of());
    }

    private UUID insertSegment(UUID workorderId, UUID processTypeId, Object userId,
                               String remark, String verifyComment, boolean autoCreated,
                               OffsetDateTime startedAt, OffsetDateTime endedAt,
                               Map<String, Object> wo) {
        UUID id = UUID.randomUUID();
        String operator = TenantContext.getUserId();
        OffsetDateTime start = startedAt != null ? startedAt : OffsetDateTime.now();
        Map<String, Object> snap = wo != null ? wo : Map.of();
        boolean hasDeviceId = TableColumnCache.hasColumn(jdbc, "repair_workorder_segment", "device_id");
        boolean hasDeviceCode = TableColumnCache.hasColumn(jdbc, "repair_workorder_segment", "device_code");
        boolean hasDeviceName = TableColumnCache.hasColumn(jdbc, "repair_workorder_segment", "device_name");

        StringBuilder cols = new StringBuilder(
                "id, workorder_id, process_type_id, user_id, started_at, ended_at, remark, verify_comment, auto_created");
        StringBuilder placeholders = new StringBuilder(
                "?::uuid,?::uuid,?::uuid,?::uuid,?::timestamptz,?::timestamptz,?,?,?");
        List<Object> args = new ArrayList<>();
        args.add(id);
        args.add(workorderId);
        args.add(processTypeId);
        args.add(blankToNull(userId));
        args.add(start);
        args.add(endedAt);
        args.add(remark);
        args.add(verifyComment);
        args.add(autoCreated);
        if (hasDeviceId) {
            cols.append(", device_id");
            placeholders.append(",?::uuid");
            args.add(blankToNull(snap.get("device_id")));
        }
        if (hasDeviceCode) {
            cols.append(", device_code");
            placeholders.append(",?");
            args.add(blankToNull(snap.get("device_code")));
        }
        if (hasDeviceName) {
            cols.append(", device_name");
            placeholders.append(",?");
            args.add(blankToNull(snap.get("device_name")));
        }
        cols.append(", created_by, updated_by");
        placeholders.append(",?::uuid,?::uuid");
        args.add(blankToNull(operator));
        args.add(blankToNull(operator));
        jdbc.update("INSERT INTO repair_workorder_segment (" + cols + ") VALUES (" + placeholders + ")",
                args.toArray());
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
        Object unitPrice = body.get("unit_price") != null ? body.get("unit_price") : body.get("unitPrice");
        BigDecimal total = calcTotal(qty, unitPrice);
        String operator = TenantContext.getUserId();
        Map<String, Object> deviceSnap = loadDeviceSnapBySegment(segmentId);
        boolean hasDeviceId = TableColumnCache.hasColumn(jdbc, "repair_workorder_segment_part", "device_id");
        boolean hasDeviceCode = TableColumnCache.hasColumn(jdbc, "repair_workorder_segment_part", "device_code");
        boolean hasDeviceName = TableColumnCache.hasColumn(jdbc, "repair_workorder_segment_part", "device_name");
        boolean hasSupplierId = TableColumnCache.hasColumn(jdbc, "repair_workorder_segment_part", "supplier_id");

        StringBuilder cols = new StringBuilder(
                "id, segment_id, spare_part_id, quantity, unit_price, total_price, remark");
        StringBuilder placeholders = new StringBuilder("?::uuid,?::uuid,?::uuid,?,?,?,?");
        List<Object> args = new ArrayList<>();
        args.add(id);
        args.add(segmentId);
        args.add(blankToNull(sparePartId));
        args.add(qty);
        args.add(unitPrice);
        args.add(total);
        args.add(blankToNull(body.get("remark")));
        if (hasSupplierId) {
            Object supplierId = body.get("supplier_id") != null ? body.get("supplier_id") : body.get("supplierId");
            cols.append(", supplier_id");
            placeholders.append(",?::uuid");
            args.add(blankToNull(supplierId));
        }
        if (hasDeviceId) {
            cols.append(", device_id");
            placeholders.append(",?::uuid");
            args.add(blankToNull(deviceSnap.get("device_id")));
        }
        if (hasDeviceCode) {
            cols.append(", device_code");
            placeholders.append(",?");
            args.add(blankToNull(deviceSnap.get("device_code")));
        }
        if (hasDeviceName) {
            cols.append(", device_name");
            placeholders.append(",?");
            args.add(blankToNull(deviceSnap.get("device_name")));
        }
        cols.append(", created_by, updated_by");
        placeholders.append(",?::uuid,?::uuid");
        args.add(blankToNull(operator));
        args.add(blankToNull(operator));
        jdbc.update("INSERT INTO repair_workorder_segment_part (" + cols + ") VALUES (" + placeholders + ")",
                args.toArray());
        return id;
    }

    /** 经 segment → workorder 取设备冗余快照。 */
    private Map<String, Object> loadDeviceSnapBySegment(UUID segmentId) {
        String segClause = SoftDeleteSupport.notDeletedClause(jdbc, "repair_workorder_segment", "s");
        String woClause = SoftDeleteSupport.notDeletedClause(jdbc, "repair_workorder", "w");
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT w.device_id, w.device_code, w.device_name
                FROM repair_workorder_segment s
                JOIN repair_workorder w ON w.id = s.workorder_id""" + woClause + """
                WHERE s.id = ?::uuid""" + segClause, segmentId);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private Map<String, Object> loadSegment(UUID segmentId) {
        String clause = SoftDeleteSupport.notDeletedClause(jdbc, "repair_workorder_segment", "s");
        boolean hasConfirmedBy = TableColumnCache.hasColumn(jdbc, "repair_workorder_segment", "confirmed_by");
        String confirmerJoin = hasConfirmedBy
                ? " LEFT JOIN sys_user cu ON cu.id = s.confirmed_by"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "sys_user", "cu")
                : "";
        String confirmerSelect = hasConfirmedBy ? ", cu.real_name AS confirmed_by_name" : "";
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT s.*, t.type_code, t.type_name, t.can_add_parts""" + confirmerSelect + """
                FROM repair_workorder_segment s
                JOIN repair_process_type t ON t.id = s.process_type_id"""
                + SoftDeleteSupport.notDeletedClause(jdbc, "repair_process_type", "t")
                + confirmerJoin + """
                WHERE s.id = ?::uuid""" + clause, segmentId);
        if (rows.isEmpty()) throw new BizException(404, "进程段不存在");
        Map<String, Object> seg = rows.get(0);
        UUID segId = UUID.fromString(String.valueOf(seg.get("id")));
        enrichSegmentConfirmed(seg);
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
        boolean hasWorkContent = TableColumnCache.hasColumn(jdbc, "repair_workorder_segment_user", "work_content");
        String workContentSelect = hasWorkContent ? ", su.work_content" : "";
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT su.user_id, su.is_primary, u.real_name AS user_name""" + workContentSelect + """
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
        seg.put("engineers", rows);
    }

    /** 旧接口：仅 userId 列表，委托到带 work_content 的保存。 */
    private void saveSegmentUsersByIds(UUID segmentId, List<String> userIds) {
        saveSegmentUsers(segmentId, engineersFromUserIds(userIds));
    }

    /**
     * 全量替换段工程师：先软删不在新列表中的成员，再 upsert 新列表。
     */
    private void replaceSegmentUsers(UUID segmentId, List<Map<String, Object>> engineers) {
        if (!TableColumnCache.hasTable(jdbc, "repair_workorder_segment_user")) return;
        List<Map<String, Object>> rows = normalizeEngineers(engineers);
        if (rows.isEmpty()) {
            throw new BizException(400, "请至少保留一名维修工程师");
        }
        Set<String> keep = new LinkedHashSet<>();
        for (Map<String, Object> eng : rows) {
            keep.add(str(eng.get("user_id")));
        }
        String clause = SoftDeleteSupport.notDeletedClause(jdbc, "repair_workorder_segment_user", null);
        List<Map<String, Object>> existing = jdbc.queryForList(
                "SELECT id, user_id FROM repair_workorder_segment_user WHERE segment_id = ?::uuid" + clause,
                segmentId);
        for (Map<String, Object> ex : existing) {
            String uid = str(ex.get("user_id"));
            if (!keep.contains(uid)) {
                SoftDeleteSupport.softDelete(jdbc, "repair_workorder_segment_user", String.valueOf(ex.get("id")));
            }
        }
        saveSegmentUsers(segmentId, rows);
    }

    private void saveSegmentUsers(UUID segmentId, List<Map<String, Object>> engineers) {
        if (!TableColumnCache.hasTable(jdbc, "repair_workorder_segment_user")) return;
        List<Map<String, Object>> rows = normalizeEngineers(engineers);
        if (rows.isEmpty()) return;
        boolean hasWorkContent = TableColumnCache.hasColumn(jdbc, "repair_workorder_segment_user", "work_content");
        String operator = TenantContext.getUserId();
        boolean anyPrimary = rows.stream().anyMatch(r -> toBool(r.get("is_primary")));
        int i = 0;
        for (Map<String, Object> eng : rows) {
            String uid = str(eng.get("user_id"));
            if (uid.isBlank()) continue;
            boolean primary = anyPrimary ? toBool(eng.get("is_primary")) : i == 0;
            Object workContent = blankToNull(eng.get("work_content"));
            if (hasWorkContent) {
                jdbc.update("""
                        INSERT INTO repair_workorder_segment_user
                        (id, segment_id, user_id, is_primary, work_content, created_by, updated_by)
                        VALUES (?::uuid,?::uuid,?::uuid,?,?,?::uuid,?::uuid)
                        ON CONFLICT (segment_id, user_id) DO UPDATE
                        SET is_primary = EXCLUDED.is_primary,
                            work_content = EXCLUDED.work_content,
                            updated_at = NOW(), is_deleted = 0, deleted_at = NULL
                        """,
                        UUID.randomUUID(), segmentId, uid, primary, workContent,
                        blankToNull(operator), blankToNull(operator));
            } else {
                jdbc.update("""
                        INSERT INTO repair_workorder_segment_user
                        (id, segment_id, user_id, is_primary, created_by, updated_by)
                        VALUES (?::uuid,?::uuid,?::uuid,?,?::uuid,?::uuid)
                        ON CONFLICT (segment_id, user_id) DO UPDATE
                        SET is_primary = EXCLUDED.is_primary, updated_at = NOW(), is_deleted = 0, deleted_at = NULL
                        """,
                        UUID.randomUUID(), segmentId, uid, primary,
                        blankToNull(operator), blankToNull(operator));
            }
            i++;
        }
    }

    /** 将纯 userId 列表转为工程师行（无工作内容）。 */
    public static List<Map<String, Object>> engineersFromUserIds(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) return new ArrayList<>();
        List<Map<String, Object>> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        int i = 0;
        for (String s : userIds) {
            if (s == null || s.isBlank() || "null".equalsIgnoreCase(s)) continue;
            String uid = s.trim();
            if (!seen.add(uid)) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("user_id", uid);
            row.put("is_primary", i == 0);
            out.add(row);
            i++;
        }
        return out;
    }

    private static List<Map<String, Object>> normalizeEngineers(List<Map<String, Object>> raw) {
        if (raw == null || raw.isEmpty()) return new ArrayList<>();
        List<Map<String, Object>> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (Map<String, Object> eng : raw) {
            if (eng == null) continue;
            Object uid = eng.get("user_id") != null ? eng.get("user_id") : eng.get("userId");
            if (uid == null || String.valueOf(uid).isBlank() || "null".equalsIgnoreCase(String.valueOf(uid))) {
                continue;
            }
            String id = String.valueOf(uid).trim();
            if (!seen.add(id)) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("user_id", id);
            Object wc = eng.get("work_content") != null ? eng.get("work_content") : eng.get("workContent");
            if (wc != null) row.put("work_content", String.valueOf(wc));
            Object primary = eng.get("is_primary") != null ? eng.get("is_primary") : eng.get("isPrimary");
            if (primary != null) row.put("is_primary", primary);
            out.add(row);
        }
        return out;
    }

    private static String resolvePrimaryUserId(List<Map<String, Object>> engineers) {
        for (Map<String, Object> eng : engineers) {
            if (toBool(eng.get("is_primary"))) {
                return str(eng.get("user_id"));
            }
        }
        return str(engineers.get(0).get("user_id"));
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

    private static OffsetDateTime asOffsetDateTime(Object raw) {
        if (raw == null) return null;
        if (raw instanceof OffsetDateTime odt) return odt;
        if (raw instanceof java.time.Instant instant) {
            return instant.atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime();
        }
        if (raw instanceof java.sql.Timestamp ts) {
            return ts.toInstant().atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime();
        }
        if (raw instanceof java.time.LocalDateTime ldt) {
            return ldt.atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime();
        }
        return parseDateTime(raw);
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
