package com.meis.saas.asset.service;

import com.meis.saas.common.asset.DeviceOwnershipWriteback;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.persistence.TableColumnCache;
import com.meis.saas.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * AST-OWN-01/02 + AST-BF-01 + AST-UI-23 + AST-LOC-01（位置真变更/纠错）：设备归属/位置区间。
 */
@Service
@RequiredArgsConstructor
public class DeviceOwnershipPeriodService implements DeviceOwnershipWriteback {
    private static final String OWN_TABLE = "device_ownership_period";
    private static final String LOC_TABLE = "device_location_period";

    private final JdbcTemplate jdbc;

    public List<Map<String, Object>> listByDevice(UUID deviceId, boolean includeDraft) {
        ensureInitialPeriodIfMissing(deviceId);
        StringBuilder sql = new StringBuilder("""
                SELECT * FROM device_ownership_period
                WHERE device_id = ?::uuid
                """);
        sql.append(SoftDeleteSupport.notDeletedClause(jdbc, OWN_TABLE, null));
        if (!includeDraft) {
            sql.append(" AND confirm_status = 'confirmed' ");
        }
        sql.append(" ORDER BY effective_from DESC NULLS LAST, created_at DESC NULLS LAST");
        return jdbc.queryForList(sql.toString(), deviceId);
    }

    public List<Map<String, Object>> listLocationByDevice(UUID deviceId, boolean includeDraft) {
        ensureInitialLocationPeriodIfMissing(deviceId);
        StringBuilder sql = new StringBuilder("""
                SELECT * FROM device_location_period
                WHERE device_id = ?::uuid
                """);
        sql.append(SoftDeleteSupport.notDeletedClause(jdbc, LOC_TABLE, null));
        if (!includeDraft) {
            sql.append(" AND confirm_status = 'confirmed' ");
        }
        sql.append(" ORDER BY effective_from DESC NULLS LAST, created_at DESC NULLS LAST");
        return jdbc.queryForList(sql.toString(), deviceId);
    }

    @Override
    @Transactional
    public void openPeriodFromLedger(UUID deviceId, String changeReason, String sourceMode,
                                     String sourceBizType, UUID sourceBizId, String sourceBizNo) {
        Map<String, Object> device = requireDevice(deviceId);
        Instant now = Instant.now();
        closeOpenOwnershipPeriod(deviceId, now);

        Map<String, Object> body = new LinkedHashMap<>();
        UUID id = UUID.randomUUID();
        body.put("id", id);
        fillOwnershipFromDevice(body, device);
        body.put("effective_from", Timestamp.from(now));
        body.put("effective_to", null);
        body.put("change_reason", changeReason);
        body.put("source_mode", sourceMode);
        body.put("source_biz_type", sourceBizType);
        body.put("source_biz_id", sourceBizId);
        body.put("source_biz_no", sourceBizNo);
        body.put("confirm_status", "confirmed");
        body.put("confirmed_at", Timestamp.from(now));
        fillConfirmActor(body);
        SoftDeleteSupport.applyInsertAudit(jdbc, OWN_TABLE, body);
        insertOwnership(body);
    }

    @Transactional
    public void correctCurrent(UUID deviceId, UUID deptId, UUID warehouseId, UUID campusId) {
        requireDevice(deviceId);
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (campusId != null) {
            sets.add("campus_id = ?::uuid");
            args.add(campusId);
        }
        if (warehouseId != null) {
            sets.add("warehouse_id = ?::uuid");
            args.add(warehouseId);
            sets.add("dept_id = NULL");
        }
        if (deptId != null) {
            sets.add("dept_id = ?::uuid");
            args.add(deptId);
            sets.add("warehouse_id = NULL");
        }
        if (sets.isEmpty()) {
            throw new BizException(400, "请至少指定院区、仓库或科室之一");
        }
        SoftDeleteSupport.appendUpdateAuditSets(jdbc, TableColumnCache.columns(jdbc, "medical_device"), sets, args);
        args.add(deviceId);
        jdbc.update("UPDATE medical_device SET " + String.join(", ", sets)
                + " WHERE id = ?::uuid"
                + SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", null), args.toArray());

        Map<String, Object> refreshed = requireDevice(deviceId);
        var open = findOpenOwnershipPeriod(deviceId);
        if (open.isEmpty()) {
            // 无当前开放段时先落初始段，再按纠错改快照（不额外新开变更段）
            ensureInitialPeriodIfMissing(deviceId);
            open = findOpenOwnershipPeriod(deviceId);
        }
        if (!open.isEmpty()) {
            patchOpenOwnershipSnapshot(UUID.fromString(open.get(0).get("id").toString()), refreshed, "manual_correct");
        }
    }

    @Transactional
    public void changeDept(UUID deviceId, UUID newDeptId, boolean realTransfer) {
        if (newDeptId == null) {
            throw new BizException(400, "请选择所属科室");
        }
        requireDevice(deviceId);
        requireDept(newDeptId);
        if (realTransfer) {
            List<String> sets = new ArrayList<>();
            List<Object> args = new ArrayList<>();
            sets.add("dept_id = ?::uuid");
            args.add(newDeptId);
            sets.add("warehouse_id = NULL");
            SoftDeleteSupport.appendUpdateAuditSets(jdbc, TableColumnCache.columns(jdbc, "medical_device"), sets, args);
            args.add(deviceId);
            jdbc.update("UPDATE medical_device SET " + String.join(", ", sets)
                    + " WHERE id = ?::uuid"
                    + SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", null), args.toArray());
            openPeriodFromLedger(deviceId, "manual_transfer", "manual_transfer", null, null, null);
        } else {
            correctCurrent(deviceId, newDeptId, null, null);
        }
    }

    @Transactional
    public void changeLocation(UUID deviceId, String locationFloor, String roomNumber,
                               String locationDetail, boolean realTransfer) {
        requireDevice(deviceId);
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        sets.add("location_floor = ?");
        args.add(blankToNull(locationFloor));
        sets.add("room_number = ?");
        args.add(blankToNull(roomNumber));
        if (locationDetail != null) {
            sets.add("location_detail = ?");
            args.add(blankToNull(locationDetail));
        }
        SoftDeleteSupport.appendUpdateAuditSets(jdbc, TableColumnCache.columns(jdbc, "medical_device"), sets, args);
        args.add(deviceId);
        jdbc.update("UPDATE medical_device SET " + String.join(", ", sets)
                + " WHERE id = ?::uuid"
                + SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", null), args.toArray());

        Map<String, Object> refreshed = requireDevice(deviceId);
        if (realTransfer) {
            Instant now = Instant.now();
            closeOpenLocationPeriod(deviceId, now);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("id", UUID.randomUUID());
            fillLocationFromDevice(body, refreshed);
            body.put("effective_from", Timestamp.from(now));
            body.put("effective_to", null);
            body.put("change_reason", "manual_transfer");
            body.put("source_mode", "manual_transfer");
            body.put("confirm_status", "confirmed");
            body.put("confirmed_at", Timestamp.from(now));
            fillConfirmActor(body);
            SoftDeleteSupport.applyInsertAudit(jdbc, LOC_TABLE, body);
            insertLocation(body);
        } else {
            var open = findOpenLocationPeriod(deviceId);
            if (open.isEmpty()) {
                Instant now = Instant.now();
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("id", UUID.randomUUID());
                fillLocationFromDevice(body, refreshed);
                body.put("effective_from", Timestamp.from(now));
                body.put("effective_to", null);
                body.put("change_reason", "manual_correct");
                body.put("source_mode", "manual_transfer");
                body.put("confirm_status", "confirmed");
                body.put("confirmed_at", Timestamp.from(now));
                fillConfirmActor(body);
                SoftDeleteSupport.applyInsertAudit(jdbc, LOC_TABLE, body);
                insertLocation(body);
            } else {
                UUID periodId = UUID.fromString(open.get(0).get("id").toString());
                List<String> pSets = new ArrayList<>();
                List<Object> pArgs = new ArrayList<>();
                pSets.add("location_floor = ?");
                pArgs.add(refreshed.get("location_floor"));
                pSets.add("room_number = ?");
                pArgs.add(refreshed.get("room_number"));
                pSets.add("location_detail = ?");
                pArgs.add(refreshed.get("location_detail"));
                pSets.add("building_id = ?::uuid");
                pArgs.add(refreshed.get("building_id"));
                pSets.add("building_name = ?");
                pArgs.add(resolveBuildingName(refreshed.get("building_id")));
                pSets.add("campus_id = ?::uuid");
                pArgs.add(refreshed.get("campus_id"));
                pSets.add("campus_name = ?");
                pArgs.add(resolveCampusName(refreshed.get("campus_id")));
                pSets.add("change_reason = ?");
                pArgs.add("manual_correct");
                SoftDeleteSupport.appendUpdateAuditSets(jdbc, TableColumnCache.columns(jdbc, LOC_TABLE), pSets, pArgs);
                pArgs.add(periodId);
                jdbc.update("UPDATE device_location_period SET " + String.join(", ", pSets)
                        + " WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, LOC_TABLE, null), pArgs.toArray());
            }
        }
    }

    @Transactional
    public Map<String, Object> createBackfill(Map<String, Object> body) {
        UUID deviceId = parseUuid(body.get("device_id"));
        if (deviceId == null) throw new BizException(400, "请指定设备");
        Map<String, Object> device = requireDevice(deviceId);
        Instant from = parseInstant(body.get("effective_from"));
        Instant to = parseInstant(body.get("effective_to"));
        if (from == null || to == null) {
            throw new BizException(400, "补录归属历史须填写闭合起止时间");
        }
        if (!to.isAfter(from)) {
            throw new BizException(400, "结束时间必须晚于开始时间");
        }
        assertNoConfirmedOverlap(deviceId, from, to, null);

        Map<String, Object> row = new LinkedHashMap<>();
        UUID id = UUID.randomUUID();
        row.put("id", id);
        fillOwnershipFromDevice(row, device);
        // 允许覆盖快照字段
        applyOwnershipBodyOverrides(row, body);
        row.put("effective_from", Timestamp.from(from));
        row.put("effective_to", Timestamp.from(to));
        row.put("change_reason", firstNonBlank(str(body.get("change_reason")), "manual_backfill"));
        row.put("source_mode", firstNonBlank(str(body.get("source_mode")), "manual_backfill"));
        row.put("source_biz_type", body.get("source_biz_type"));
        row.put("source_biz_id", parseUuid(body.get("source_biz_id")));
        row.put("source_biz_no", body.get("source_biz_no"));
        row.put("confirm_status", "draft");
        row.put("confirmed_at", null);
        row.put("confirmed_by", null);
        row.put("confirmed_by_name", null);
        row.put("remark", body.get("remark"));
        if (body.get("created_by") != null) row.put("created_by", body.get("created_by"));
        if (body.get("created_by_name") != null) row.put("created_by_name", body.get("created_by_name"));
        SoftDeleteSupport.applyInsertAudit(jdbc, OWN_TABLE, row);
        insertOwnership(row);
        return loadOwnership(id);
    }

    @Transactional
    public Map<String, Object> updateDraft(UUID periodId, Map<String, Object> body) {
        Map<String, Object> existing = loadOwnership(periodId);
        if (!"draft".equals(String.valueOf(existing.get("confirm_status")))) {
            throw new BizException(400, "仅待确认草稿可修改");
        }
        UUID deviceId = parseUuid(existing.get("device_id"));
        Instant from = body.containsKey("effective_from")
                ? parseInstant(body.get("effective_from")) : parseInstant(existing.get("effective_from"));
        Instant to = body.containsKey("effective_to")
                ? parseInstant(body.get("effective_to")) : parseInstant(existing.get("effective_to"));
        if (from == null || to == null) {
            throw new BizException(400, "补录归属历史须填写闭合起止时间");
        }
        if (!to.isAfter(from)) {
            throw new BizException(400, "结束时间必须晚于开始时间");
        }
        assertNoConfirmedOverlap(deviceId, from, to, periodId);

        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (body.containsKey("campus_id")) {
            sets.add("campus_id = ?::uuid");
            args.add(parseUuid(body.get("campus_id")));
            sets.add("campus_name = ?");
            args.add(firstNonBlank(str(body.get("campus_name")), resolveCampusName(body.get("campus_id"))));
        }
        if (body.containsKey("warehouse_id")) {
            sets.add("warehouse_id = ?::uuid");
            args.add(parseUuid(body.get("warehouse_id")));
            sets.add("warehouse_name = ?");
            args.add(firstNonBlank(str(body.get("warehouse_name")), resolveWarehouseName(body.get("warehouse_id"))));
        }
        if (body.containsKey("dept_id")) {
            sets.add("dept_id = ?::uuid");
            args.add(parseUuid(body.get("dept_id")));
            sets.add("dept_name = ?");
            args.add(firstNonBlank(str(body.get("dept_name")), resolveDeptName(body.get("dept_id"))));
        }
        if (body.containsKey("owner_type") || body.containsKey("warehouse_id") || body.containsKey("dept_id")) {
            String ownerType = firstNonBlank(str(body.get("owner_type")), str(existing.get("owner_type")));
            Object wh = body.containsKey("warehouse_id") ? body.get("warehouse_id") : existing.get("warehouse_id");
            if (ownerType == null || ownerType.isBlank()) {
                ownerType = wh != null ? "warehouse" : "dept";
            }
            sets.add("owner_type = ?");
            args.add(ownerType);
        }
        sets.add("effective_from = ?::timestamptz");
        args.add(Timestamp.from(from));
        sets.add("effective_to = ?::timestamptz");
        args.add(Timestamp.from(to));
        putSet(sets, args, "change_reason", body, existing, false);
        putSet(sets, args, "source_mode", body, existing, false);
        putSet(sets, args, "remark", body, existing, false);
        SoftDeleteSupport.appendUpdateAuditSets(jdbc, TableColumnCache.columns(jdbc, OWN_TABLE), sets, args);
        args.add(periodId);
        jdbc.update("UPDATE device_ownership_period SET " + String.join(", ", sets)
                + " WHERE id = ?::uuid"
                + SoftDeleteSupport.notDeletedClause(jdbc, OWN_TABLE, null), args.toArray());
        return loadOwnership(periodId);
    }

    @Transactional
    public void deleteDraft(UUID periodId) {
        Map<String, Object> existing = loadOwnership(periodId);
        if (!"draft".equals(String.valueOf(existing.get("confirm_status")))) {
            throw new BizException(400, "仅待确认草稿可删除");
        }
        SoftDeleteSupport.softDelete(jdbc, OWN_TABLE, periodId.toString());
    }

    @Transactional
    public Map<String, Object> confirm(UUID periodId) {
        Map<String, Object> existing = loadOwnership(periodId);
        if (!"draft".equals(String.valueOf(existing.get("confirm_status")))) {
            throw new BizException(400, "仅待确认草稿可确认生效");
        }
        UUID deviceId = parseUuid(existing.get("device_id"));
        Instant from = parseInstant(existing.get("effective_from"));
        Instant to = parseInstant(existing.get("effective_to"));
        if (from == null || to == null) {
            throw new BizException(400, "补录区间须为闭合区间才能确认");
        }
        assertNoConfirmedOverlap(deviceId, from, to, periodId);

        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        sets.add("confirm_status = 'confirmed'");
        sets.add("confirmed_at = NOW()");
        UUID userId = currentUserUuid();
        String userName = SoftDeleteSupport.resolveUserDisplayName(jdbc, userId);
        sets.add("confirmed_by = ?::uuid");
        args.add(userId);
        sets.add("confirmed_by_name = ?");
        args.add(userName);
        SoftDeleteSupport.appendUpdateAuditSets(jdbc, TableColumnCache.columns(jdbc, OWN_TABLE), sets, args);
        args.add(periodId);
        jdbc.update("UPDATE device_ownership_period SET " + String.join(", ", sets)
                + " WHERE id = ?::uuid"
                + SoftDeleteSupport.notDeletedClause(jdbc, OWN_TABLE, null), args.toArray());
        return loadOwnership(periodId);
    }

    @Transactional
    public void ensureInitialPeriodIfMissing(UUID deviceId) {
        if (deviceId == null) return;
        Integer cnt = jdbc.queryForObject("""
                SELECT COUNT(*) FROM device_ownership_period
                WHERE device_id = ?::uuid AND confirm_status = 'confirmed'
                """ + SoftDeleteSupport.notDeletedClause(jdbc, OWN_TABLE, null), Integer.class, deviceId);
        if (cnt != null && cnt > 0) return;
        Map<String, Object> device;
        try {
            device = requireDevice(deviceId);
        } catch (BizException e) {
            return;
        }
        Instant from = device.get("created_at") != null
                ? parseInstant(device.get("created_at")) : Instant.now();
        if (from == null) from = Instant.now();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", UUID.randomUUID());
        fillOwnershipFromDevice(body, device);
        body.put("effective_from", Timestamp.from(from));
        body.put("effective_to", null);
        body.put("change_reason", null);
        body.put("source_mode", "system");
        body.put("confirm_status", "confirmed");
        body.put("confirmed_at", Timestamp.from(Instant.now()));
        fillConfirmActor(body);
        SoftDeleteSupport.applyInsertAudit(jdbc, OWN_TABLE, body);
        insertOwnership(body);
    }

    @Transactional
    public void ensureInitialLocationPeriodIfMissing(UUID deviceId) {
        if (deviceId == null) return;
        Integer cnt = jdbc.queryForObject("""
                SELECT COUNT(*) FROM device_location_period
                WHERE device_id = ?::uuid AND confirm_status = 'confirmed'
                """ + SoftDeleteSupport.notDeletedClause(jdbc, LOC_TABLE, null), Integer.class, deviceId);
        if (cnt != null && cnt > 0) return;
        Map<String, Object> device;
        try {
            device = requireDevice(deviceId);
        } catch (BizException e) {
            return;
        }
        Instant from = device.get("created_at") != null
                ? parseInstant(device.get("created_at")) : Instant.now();
        if (from == null) from = Instant.now();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", UUID.randomUUID());
        fillLocationFromDevice(body, device);
        body.put("effective_from", Timestamp.from(from));
        body.put("effective_to", null);
        body.put("change_reason", null);
        body.put("source_mode", "system");
        body.put("confirm_status", "confirmed");
        body.put("confirmed_at", Timestamp.from(Instant.now()));
        fillConfirmActor(body);
        SoftDeleteSupport.applyInsertAudit(jdbc, LOC_TABLE, body);
        insertLocation(body);
    }

    // ---------- helpers ----------

    private void patchOpenOwnershipSnapshot(UUID periodId, Map<String, Object> device, String changeReason) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        sets.add("device_code = ?");
        args.add(device.get("device_code"));
        sets.add("device_name = ?");
        args.add(device.get("device_name"));
        sets.add("campus_id = ?::uuid");
        args.add(device.get("campus_id"));
        sets.add("campus_name = ?");
        args.add(resolveCampusName(device.get("campus_id")));
        String ownerType = device.get("warehouse_id") != null ? "warehouse" : "dept";
        sets.add("owner_type = ?");
        args.add(ownerType);
        sets.add("warehouse_id = ?::uuid");
        args.add(device.get("warehouse_id"));
        sets.add("warehouse_name = ?");
        args.add(resolveWarehouseName(device.get("warehouse_id")));
        sets.add("dept_id = ?::uuid");
        args.add(device.get("dept_id"));
        sets.add("dept_name = ?");
        args.add(resolveDeptName(device.get("dept_id")));
        sets.add("change_reason = ?");
        args.add(changeReason);
        SoftDeleteSupport.appendUpdateAuditSets(jdbc, TableColumnCache.columns(jdbc, OWN_TABLE), sets, args);
        args.add(periodId);
        jdbc.update("UPDATE device_ownership_period SET " + String.join(", ", sets)
                + " WHERE id = ?::uuid"
                + SoftDeleteSupport.notDeletedClause(jdbc, OWN_TABLE, null), args.toArray());
    }

    private void closeOpenOwnershipPeriod(UUID deviceId, Instant at) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        sets.add("effective_to = ?::timestamptz");
        args.add(Timestamp.from(at));
        SoftDeleteSupport.appendUpdateAuditSets(jdbc, TableColumnCache.columns(jdbc, OWN_TABLE), sets, args);
        args.add(deviceId);
        jdbc.update("""
                UPDATE device_ownership_period SET
                """ + String.join(", ", sets) + """
                WHERE device_id = ?::uuid
                  AND effective_to IS NULL
                  AND confirm_status = 'confirmed'
                """ + SoftDeleteSupport.notDeletedClause(jdbc, OWN_TABLE, null), args.toArray());
    }

    private void closeOpenLocationPeriod(UUID deviceId, Instant at) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        sets.add("effective_to = ?::timestamptz");
        args.add(Timestamp.from(at));
        SoftDeleteSupport.appendUpdateAuditSets(jdbc, TableColumnCache.columns(jdbc, LOC_TABLE), sets, args);
        args.add(deviceId);
        jdbc.update("""
                UPDATE device_location_period SET
                """ + String.join(", ", sets) + """
                WHERE device_id = ?::uuid
                  AND effective_to IS NULL
                  AND confirm_status = 'confirmed'
                """ + SoftDeleteSupport.notDeletedClause(jdbc, LOC_TABLE, null), args.toArray());
    }

    private List<Map<String, Object>> findOpenOwnershipPeriod(UUID deviceId) {
        return jdbc.queryForList("""
                SELECT * FROM device_ownership_period
                WHERE device_id = ?::uuid AND effective_to IS NULL AND confirm_status = 'confirmed'
                """ + SoftDeleteSupport.notDeletedClause(jdbc, OWN_TABLE, null) + """
                ORDER BY effective_from DESC LIMIT 1
                """, deviceId);
    }

    private List<Map<String, Object>> findOpenLocationPeriod(UUID deviceId) {
        return jdbc.queryForList("""
                SELECT * FROM device_location_period
                WHERE device_id = ?::uuid AND effective_to IS NULL AND confirm_status = 'confirmed'
                """ + SoftDeleteSupport.notDeletedClause(jdbc, LOC_TABLE, null) + """
                ORDER BY effective_from DESC LIMIT 1
                """, deviceId);
    }

    private void assertNoConfirmedOverlap(UUID deviceId, Instant from, Instant to, UUID excludeId) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT id, effective_to FROM device_ownership_period
                WHERE device_id = ?::uuid AND confirm_status = 'confirmed'
                """);
        args.add(deviceId);
        sql.append(SoftDeleteSupport.notDeletedClause(jdbc, OWN_TABLE, null));
        // 区间 [from, to] 与已确认段重叠（开放段 to 视为 infinity）
        sql.append("""
                 AND effective_from < ?::timestamptz
                 AND ?::timestamptz < COALESCE(effective_to, 'infinity'::timestamptz)
                """);
        args.add(Timestamp.from(to));
        args.add(Timestamp.from(from));
        if (excludeId != null) {
            sql.append(" AND id <> ?::uuid ");
            args.add(excludeId);
        }
        sql.append(" LIMIT 1");
        var rows = jdbc.queryForList(sql.toString(), args.toArray());
        if (!rows.isEmpty()) {
            if (rows.get(0).get("effective_to") == null) {
                throw new BizException(400, "补录不可覆盖当前开放归属段，请使用「变更所属科室」");
            }
            throw new BizException(400, "补录区间与已确认归属历史重叠，请调整起止时间");
        }
    }

    private void fillOwnershipFromDevice(Map<String, Object> body, Map<String, Object> device) {
        body.put("device_id", device.get("id"));
        body.put("device_code", device.get("device_code"));
        body.put("device_name", device.get("device_name"));
        body.put("campus_id", device.get("campus_id"));
        body.put("campus_name", resolveCampusName(device.get("campus_id")));
        Object warehouseId = device.get("warehouse_id");
        Object deptId = device.get("dept_id");
        String ownerType = warehouseId != null ? "warehouse" : "dept";
        body.put("owner_type", ownerType);
        body.put("warehouse_id", warehouseId);
        body.put("warehouse_name", resolveWarehouseName(warehouseId));
        body.put("dept_id", deptId);
        body.put("dept_name", resolveDeptName(deptId));
    }

    private void applyOwnershipBodyOverrides(Map<String, Object> row, Map<String, Object> body) {
        if (body.containsKey("campus_id")) {
            row.put("campus_id", parseUuid(body.get("campus_id")));
            row.put("campus_name", firstNonBlank(str(body.get("campus_name")),
                    resolveCampusName(body.get("campus_id"))));
        }
        if (body.containsKey("warehouse_id")) {
            row.put("warehouse_id", parseUuid(body.get("warehouse_id")));
            row.put("warehouse_name", firstNonBlank(str(body.get("warehouse_name")),
                    resolveWarehouseName(body.get("warehouse_id"))));
        }
        if (body.containsKey("dept_id")) {
            row.put("dept_id", parseUuid(body.get("dept_id")));
            row.put("dept_name", firstNonBlank(str(body.get("dept_name")),
                    resolveDeptName(body.get("dept_id"))));
        }
        if (body.containsKey("owner_type") && hasText(str(body.get("owner_type")))) {
            row.put("owner_type", str(body.get("owner_type")));
        } else {
            row.put("owner_type", row.get("warehouse_id") != null ? "warehouse" : "dept");
        }
        if (body.containsKey("campus_name") && hasText(str(body.get("campus_name")))) {
            row.put("campus_name", body.get("campus_name"));
        }
        if (body.containsKey("warehouse_name") && hasText(str(body.get("warehouse_name")))) {
            row.put("warehouse_name", body.get("warehouse_name"));
        }
        if (body.containsKey("dept_name") && hasText(str(body.get("dept_name")))) {
            row.put("dept_name", body.get("dept_name"));
        }
    }

    private void fillLocationFromDevice(Map<String, Object> body, Map<String, Object> device) {
        body.put("device_id", device.get("id"));
        body.put("device_code", device.get("device_code"));
        body.put("device_name", device.get("device_name"));
        body.put("location_floor", device.get("location_floor"));
        body.put("room_number", device.get("room_number"));
        body.put("location_detail", device.get("location_detail"));
        body.put("building_id", device.get("building_id"));
        body.put("building_name", resolveBuildingName(device.get("building_id")));
        body.put("campus_id", device.get("campus_id"));
        body.put("campus_name", resolveCampusName(device.get("campus_id")));
    }

    private void insertOwnership(Map<String, Object> body) {
        jdbc.update("""
                INSERT INTO device_ownership_period (
                  id, device_id, device_code, device_name, campus_id, campus_name,
                  owner_type, warehouse_id, warehouse_name, dept_id, dept_name,
                  effective_from, effective_to, change_reason, source_mode,
                  source_biz_type, source_biz_id, source_biz_no,
                  confirm_status, confirmed_at, confirmed_by, confirmed_by_name, remark,
                  created_by, updated_by, created_by_name, updated_by_name, is_deleted
                ) VALUES (
                  ?::uuid, ?::uuid, ?, ?, ?::uuid, ?,
                  ?, ?::uuid, ?, ?::uuid, ?,
                  ?::timestamptz, ?::timestamptz, ?, ?,
                  ?, ?::uuid, ?,
                  ?, ?::timestamptz, ?::uuid, ?, ?,
                  ?::uuid, ?::uuid, ?, ?, COALESCE(?::smallint, 0)
                )
                """,
                body.get("id"), body.get("device_id"), body.get("device_code"), body.get("device_name"),
                body.get("campus_id"), body.get("campus_name"),
                body.get("owner_type"), body.get("warehouse_id"), body.get("warehouse_name"),
                body.get("dept_id"), body.get("dept_name"),
                body.get("effective_from"), body.get("effective_to"),
                body.get("change_reason"), body.get("source_mode"),
                body.get("source_biz_type"), body.get("source_biz_id"), body.get("source_biz_no"),
                body.get("confirm_status"), body.get("confirmed_at"),
                body.get("confirmed_by"), body.get("confirmed_by_name"), body.get("remark"),
                body.get("created_by"), body.get("updated_by"),
                body.get("created_by_name"), body.get("updated_by_name"),
                body.get("is_deleted") != null ? body.get("is_deleted") : 0);
    }

    private void insertLocation(Map<String, Object> body) {
        jdbc.update("""
                INSERT INTO device_location_period (
                  id, device_id, device_code, device_name,
                  location_floor, room_number, location_detail,
                  building_id, building_name, campus_id, campus_name,
                  effective_from, effective_to, change_reason, source_mode,
                  source_biz_type, source_biz_id, source_biz_no,
                  confirm_status, confirmed_at, confirmed_by, confirmed_by_name, remark,
                  created_by, updated_by, created_by_name, updated_by_name, is_deleted
                ) VALUES (
                  ?::uuid, ?::uuid, ?, ?,
                  ?, ?, ?,
                  ?::uuid, ?, ?::uuid, ?,
                  ?::timestamptz, ?::timestamptz, ?, ?,
                  ?, ?::uuid, ?,
                  ?, ?::timestamptz, ?::uuid, ?, ?,
                  ?::uuid, ?::uuid, ?, ?, COALESCE(?::smallint, 0)
                )
                """,
                body.get("id"), body.get("device_id"), body.get("device_code"), body.get("device_name"),
                body.get("location_floor"), body.get("room_number"), body.get("location_detail"),
                body.get("building_id"), body.get("building_name"),
                body.get("campus_id"), body.get("campus_name"),
                body.get("effective_from"), body.get("effective_to"),
                body.get("change_reason"), body.get("source_mode"),
                body.get("source_biz_type"), body.get("source_biz_id"), body.get("source_biz_no"),
                body.get("confirm_status"), body.get("confirmed_at"),
                body.get("confirmed_by"), body.get("confirmed_by_name"), body.get("remark"),
                body.get("created_by"), body.get("updated_by"),
                body.get("created_by_name"), body.get("updated_by_name"),
                body.get("is_deleted") != null ? body.get("is_deleted") : 0);
    }

    private Map<String, Object> loadOwnership(UUID id) {
        var rows = jdbc.queryForList(
                "SELECT * FROM device_ownership_period WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, OWN_TABLE, null), id);
        if (rows.isEmpty()) throw new BizException(404, "归属区间不存在");
        return rows.get(0);
    }

    private Map<String, Object> requireDevice(UUID deviceId) {
        var rows = jdbc.queryForList(
                "SELECT * FROM medical_device WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", null), deviceId);
        if (rows.isEmpty()) throw new BizException(404, "设备不存在");
        return rows.get(0);
    }

    private void requireDept(UUID deptId) {
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM department WHERE id = ?::uuid AND COALESCE(is_active, TRUE) = TRUE",
                Integer.class, deptId);
        if (cnt == null || cnt == 0) throw new BizException(400, "科室不存在或已停用");
    }

    private void fillConfirmActor(Map<String, Object> body) {
        UUID userId = currentUserUuid();
        body.put("confirmed_by", userId);
        body.put("confirmed_by_name", SoftDeleteSupport.resolveUserDisplayName(jdbc, userId));
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

    private String resolveCampusName(Object campusId) {
        UUID id = parseUuid(campusId);
        if (id == null) return null;
        var rows = jdbc.queryForList("SELECT campus_name FROM campus WHERE id = ?::uuid", id);
        return rows.isEmpty() ? null : str(rows.get(0).get("campus_name"));
    }

    private String resolveWarehouseName(Object warehouseId) {
        UUID id = parseUuid(warehouseId);
        if (id == null) return null;
        var rows = jdbc.queryForList("SELECT warehouse_name FROM warehouse WHERE id = ?::uuid", id);
        return rows.isEmpty() ? null : str(rows.get(0).get("warehouse_name"));
    }

    private String resolveDeptName(Object deptId) {
        UUID id = parseUuid(deptId);
        if (id == null) return null;
        var rows = jdbc.queryForList("SELECT dept_name FROM department WHERE id = ?::uuid", id);
        return rows.isEmpty() ? null : str(rows.get(0).get("dept_name"));
    }

    private String resolveBuildingName(Object buildingId) {
        UUID id = parseUuid(buildingId);
        if (id == null) return null;
        var rows = jdbc.queryForList("SELECT building_name FROM building WHERE id = ?::uuid", id);
        return rows.isEmpty() ? null : str(rows.get(0).get("building_name"));
    }

    private static void putSet(List<String> sets, List<Object> args, String col,
                               Map<String, Object> body, Map<String, Object> existing, boolean uuid) {
        if (!body.containsKey(col)) return;
        Object v = body.get(col);
        if (uuid) {
            sets.add(col + " = ?::uuid");
            args.add(parseUuid(v));
        } else {
            sets.add(col + " = ?");
            args.add(v);
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

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String firstNonBlank(String a, String b) {
        if (hasText(a)) return a;
        return b;
    }
}
