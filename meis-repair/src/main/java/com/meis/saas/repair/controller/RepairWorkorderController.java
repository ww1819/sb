package com.meis.saas.repair.controller;

import com.meis.saas.common.audit.EntityChangeLogService;
import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.persistence.TableColumnCache;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.tenant.TenantContext;
import com.meis.saas.repair.service.RepairWorkorderProcessService;
import com.meis.saas.repair.service.RepairWorkorderProcessService.ProcessRecord;
import com.meis.saas.repair.service.RepairWorkorderSegmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/repair/workorder")
@RequiredArgsConstructor
public class RepairWorkorderController {
    private static final String UUID_PATH =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    /** 占用设备、不可再报修的工单状态（不含草稿） */
    private static final Set<String> ACTIVE_STATUSES = Set.of(
            "reported", "dispatching", "pending_accept", "accepted",
            "repairing", "pending_verify", "suspended", "verify_rejected"
    );

    private static final Set<String> DRAFT_EDITABLE_FIELDS = Set.of(
            "device_id", "device_code", "device_name", "reporter_id", "report_dept_id",
            "report_method", "report_time", "fault_description", "urgency_level",
            "fault_type_id", "remark"
    );

    private static final Set<String> HANDLE_LIST_STATUSES = Set.of(
            "reported", "dispatching", "pending_accept", "accepted",
            "repairing", "suspended", "verify_rejected", "pending_verify", "verified"
    );

    private static final Set<String> VERIFY_LIST_STATUSES = Set.of(
            "pending_verify", "verified", "closed"
    );

    private final JdbcTemplate jdbc;
    private final EntityChangeLogService changeLog;
    private final RepairWorkorderProcessService processService;
    private final RepairWorkorderSegmentService segmentService;

    @GetMapping("/devices/candidates")
    public Result<List<Map<String, Object>>> deviceCandidates(
            @RequestParam(required = false) String deptName,
            @RequestParam(required = false) String deviceName,
            @RequestParam(required = false) String specification,
            @RequestParam(required = false) String deviceCode,
            @RequestParam(required = false) String financialCode,
            @RequestParam(required = false) String serialNumber) {
        StringBuilder sql = new StringBuilder("""
                SELECT d.id, d.device_code, d.device_name, d.specification, d.serial_number,
                       d.financial_code, d.dept_id, d.device_status, dept.dept_name
                FROM medical_device d
                LEFT JOIN department dept ON dept.id = d.dept_id""");
        sql.append(SoftDeleteSupport.notDeletedClause(jdbc, "department", "dept"));
        sql.append("""
                WHERE d.is_active = true
                  AND COALESCE(d.device_status, '') NOT IN ('maintenance', 'pending_verify', 'scrap')
                  AND d.id NOT IN (
                      SELECT device_id FROM repair_workorder
                      WHERE device_id IS NOT NULL
                        AND status IN ('reported','dispatching','pending_accept','accepted','repairing','pending_verify','suspended','verify_rejected')
                """);
        sql.append(SoftDeleteSupport.notDeletedClause(jdbc, "repair_workorder", null));
        sql.append(")");
        sql.append(SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", "d"));
        List<Object> args = new ArrayList<>();
        appendIlike(sql, args, "dept.dept_name", deptName);
        appendIlike(sql, args, "d.device_name", deviceName);
        appendIlike(sql, args, "d.specification", specification);
        appendIlike(sql, args, "d.device_code", deviceCode);
        appendIlike(sql, args, "d.financial_code", financialCode);
        appendIlike(sql, args, "d.serial_number", serialNumber);
        sql.append(" ORDER BY d.device_code LIMIT 500");
        return Result.ok(jdbc.queryForList(sql.toString(), args.toArray()));
    }

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(
            PageQuery query,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String statuses,
            @RequestParam(required = false) String urgencyLevel,
            @RequestParam(required = false) String reportDeptId,
            @RequestParam(required = false) String assignedUserId,
            @RequestParam(required = false) String assignment,
            @RequestParam(required = false) String reportTimeFrom,
            @RequestParam(required = false) String reportTimeTo) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "repair_workorder", null));
        List<Object> args = new ArrayList<>();

        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String kw = "%" + query.getKeyword().trim() + "%";
            where.append("""
                     AND (wo_no ILIKE ? OR device_code ILIKE ? OR device_name ILIKE ? OR fault_description ILIKE ?)
                    """);
            args.add(kw);
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        appendStatusFilter(where, args, mode, status, statuses);
        if (urgencyLevel != null && !urgencyLevel.isBlank()) {
            where.append(" AND urgency_level = ? ");
            args.add(urgencyLevel);
        }
        if (reportDeptId != null && !reportDeptId.isBlank()) {
            where.append(" AND report_dept_id = ?::uuid ");
            args.add(reportDeptId);
        }
        if (assignedUserId != null && !assignedUserId.isBlank()) {
            where.append(" AND assigned_user_id = ?::uuid ");
            args.add(assignedUserId);
        } else if ("unassigned".equalsIgnoreCase(assignment)) {
            where.append(" AND assigned_user_id IS NULL ");
        } else if ("assigned".equalsIgnoreCase(assignment)) {
            where.append(" AND assigned_user_id IS NOT NULL ");
        }
        if (reportTimeFrom != null && !reportTimeFrom.isBlank()) {
            where.append(" AND report_time >= ?::date ");
            args.add(reportTimeFrom);
        }
        if (reportTimeTo != null && !reportTimeTo.isBlank()) {
            where.append(" AND report_time < (?::date + INTERVAL '1 day') ");
            args.add(reportTimeTo);
        }

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM repair_workorder" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.limit());
        pageArgs.add(query.offset());
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM repair_workorder" + where + " ORDER BY report_time DESC NULLS LAST, created_at DESC LIMIT ? OFFSET ?",
                pageArgs.toArray());
        processService.enrichWorkorders(rows);
        return Result.ok(PageResult.of(rows, total != null ? total : 0, query.getPage(), query.getSize()));
    }

    @GetMapping("/{id:" + UUID_PATH + "}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        return Result.ok(loadWorkorder(id));
    }

    @GetMapping("/{id:" + UUID_PATH + "}/process")
    public Result<List<Map<String, Object>>> listProcess(@PathVariable UUID id) {
        requireWo(id);
        return Result.ok(processService.listByWorkorder(id));
    }

    @GetMapping("/{id:" + UUID_PATH + "}/segments")
    public Result<List<Map<String, Object>>> listSegments(@PathVariable UUID id) {
        requireWo(id);
        return Result.ok(segmentService.listSegments(id));
    }

    @PostMapping("/{id:" + UUID_PATH + "}/segments")
    @Transactional
    @OperationLog(module = "repair", description = "添加维修进程段")
    public Result<Map<String, Object>> addSegment(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Map<String, Object> wo = requireWo(id);
        assertRepairEngineer();
        if (!isUnassigned(wo)) {
            assertAssignedOwner(wo);
        }
        Object rawTypeId = body.get("processTypeId") != null ? body.get("processTypeId") : body.get("process_type_id");
        if (rawTypeId == null || String.valueOf(rawTypeId).isBlank()) {
            throw new BizException(400, "请选择进程类型");
        }
        UUID typeId = UUID.fromString(String.valueOf(rawTypeId));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts = (List<Map<String, Object>>) body.getOrDefault("parts", List.of());
        List<Map<String, Object>> engineers = resolveEngineers(body);
        OffsetDateTime startedAt = RepairWorkorderSegmentService.parseDateTime(
                body.get("startedAt") != null ? body.get("startedAt") : body.get("started_at"));
        OffsetDateTime endedAt = null;
        Object enableEnd = body.get("enableEndedAt") != null ? body.get("enableEndedAt") : body.get("enable_ended_at");
        Object rawEnded = body.get("endedAt") != null ? body.get("endedAt") : body.get("ended_at");
        if (Boolean.TRUE.equals(enableEnd) || "true".equalsIgnoreCase(String.valueOf(enableEnd))
                || (rawEnded != null && !String.valueOf(rawEnded).isBlank())) {
            endedAt = RepairWorkorderSegmentService.parseDateTime(rawEnded);
            if (endedAt == null) {
                throw new BizException(400, "请填写结束时间");
            }
        }
        Map<String, Object> seg = segmentService.addEngineerSegment(
                id, wo, typeId, str(body.get("remark")), parts, engineers, startedAt, endedAt);
        addEvent(id, "add_segment", str(wo.get("status")), str(loadWorkorder(id).get("status")),
                null, null, seg.get("user_id"), null, null,
                "添加进程段: " + seg.get("type_name"), null);
        return Result.ok(seg);
    }

    @PutMapping("/{id:" + UUID_PATH + "}/segments/{segmentId:" + UUID_PATH + "}")
    @Transactional
    @OperationLog(module = "repair", description = "编辑维修进程段")
    public Result<Map<String, Object>> updateSegment(@PathVariable UUID id,
                                                     @PathVariable UUID segmentId,
                                                     @RequestBody Map<String, Object> body) {
        Map<String, Object> wo = requireWo(id);
        if ("draft".equals(str(wo.get("status")))) {
            throw new BizException(400, "草稿工单不可编辑进程段");
        }
        assertRepairEngineer();
        List<Map<String, Object>> engineers = resolveEngineers(body);
        Map<String, Object> seg = segmentService.updateSegment(id, segmentId, body, engineers);
        addEvent(id, "update_segment", str(wo.get("status")), str(wo.get("status")),
                null, null, seg.get("user_id"), null, null,
                "编辑进程段: " + seg.get("type_name"), null);
        return Result.ok(seg);
    }

    @DeleteMapping("/{id:" + UUID_PATH + "}/segments/{segmentId:" + UUID_PATH + "}")
    @Transactional
    @OperationLog(module = "repair", description = "删除维修进程段")
    public Result<Void> deleteSegment(@PathVariable UUID id, @PathVariable UUID segmentId) {
        Map<String, Object> wo = requireWo(id);
        if ("draft".equals(str(wo.get("status")))) {
            throw new BizException(400, "草稿工单不可删除进程段");
        }
        assertRepairEngineer();
        Map<String, Object> before = segmentService.listSegments(id).stream()
                .filter(s -> segmentId.toString().equals(String.valueOf(s.get("id"))))
                .findFirst().orElse(null);
        segmentService.deleteSegment(id, segmentId);
        addEvent(id, "delete_segment", str(wo.get("status")), str(wo.get("status")),
                null, null, before != null ? before.get("user_id") : null, null, null,
                "删除进程段" + (before != null && before.get("type_name") != null ? ": " + before.get("type_name") : ""), null);
        return Result.ok();
    }

    @PostMapping("/{id:" + UUID_PATH + "}/segments/{segmentId:" + UUID_PATH + "}/confirm")
    @Transactional
    @OperationLog(module = "repair", description = "确认维修进程段")
    public Result<Map<String, Object>> confirmSegment(@PathVariable UUID id, @PathVariable UUID segmentId) {
        Map<String, Object> wo = requireWo(id);
        if ("draft".equals(str(wo.get("status")))) {
            throw new BizException(400, "草稿工单不可确认进程段");
        }
        String uid = operatorId();
        if (uid == null || uid.isBlank()) {
            throw new BizException(401, "未登录");
        }
        Map<String, Object> seg = segmentService.confirmSegment(id, segmentId);
        addEvent(id, "confirm_segment", str(wo.get("status")), str(wo.get("status")),
                null, null, seg.get("user_id"), null, null,
                "确认进程段: " + seg.get("type_name"), null);
        return Result.ok(seg);
    }

    @PostMapping("/{id:" + UUID_PATH + "}/segments/{segmentId:" + UUID_PATH + "}/parts")
    @Transactional
    @OperationLog(module = "repair", description = "进程段添加配件")
    public Result<Map<String, Object>> addSegmentPart(@PathVariable UUID id,
                                                       @PathVariable UUID segmentId,
                                                       @RequestBody Map<String, Object> body) {
        requireWo(id);
        assertRepairEngineer();
        assertAssignedOwner(requireWo(id));
        return Result.ok(segmentService.addPart(segmentId, body));
    }

    @PutMapping("/{id:" + UUID_PATH + "}/segments/{segmentId:" + UUID_PATH + "}/parts/{partId:" + UUID_PATH + "}")
    @Transactional
    @OperationLog(module = "repair", description = "编辑进程段配件")
    public Result<Map<String, Object>> updateSegmentPart(@PathVariable UUID id,
                                                         @PathVariable UUID segmentId,
                                                         @PathVariable UUID partId,
                                                         @RequestBody Map<String, Object> body) {
        requireWo(id);
        assertRepairEngineer();
        assertAssignedOwner(requireWo(id));
        return Result.ok(segmentService.updatePart(segmentId, partId, body));
    }

    @DeleteMapping("/{id:" + UUID_PATH + "}/segments/{segmentId:" + UUID_PATH + "}/parts/{partId:" + UUID_PATH + "}")
    @Transactional
    @OperationLog(module = "repair", description = "删除进程段配件")
    public Result<Void> deleteSegmentPart(@PathVariable UUID id,
                                          @PathVariable UUID segmentId,
                                          @PathVariable UUID partId) {
        requireWo(id);
        assertRepairEngineer();
        assertAssignedOwner(requireWo(id));
        segmentService.deletePart(segmentId, partId);
        return Result.ok();
    }

    @GetMapping("/{id:" + UUID_PATH + "}/timeline")
    public Result<Map<String, Object>> timeline(@PathVariable UUID id) {
        Map<String, Object> wo = loadWorkorder(id);
        List<Map<String, Object>> events = jdbc.queryForList(
                "SELECT * FROM repair_workorder_event WHERE workorder_id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "repair_workorder_event", null)
                        + " ORDER BY created_at ASC, id ASC", id);

        List<Map<String, Object>> milestones = buildMilestones(wo, events);
        List<Map<String, Object>> segments = buildSubStatusSegments(events);
        Map<String, Object> summary = buildSummary(wo, events);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("summary", summary);
        data.put("milestones", milestones);
        data.put("segments", segments);
        data.put("events", enrichEvents(events));
        return Result.ok(data);
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "repair", description = "保存报修草稿")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        if (body.get("device_id") == null || String.valueOf(body.get("device_id")).isBlank()) {
            throw new BizException(400, "请选择报修设备");
        }
        if (body.get("fault_description") == null || String.valueOf(body.get("fault_description")).isBlank()) {
            throw new BizException(400, "请填写故障描述");
        }
        assertDeviceAvailable(body.get("device_id"), null);
        UUID id = UUID.randomUUID();
        String woNo = "WO" + System.currentTimeMillis();
        Object reporterId = blankToNull(body.get("reporter_id"));
        String reporterName = SoftDeleteSupport.resolveUserDisplayName(jdbc, reporterId);
        if (TableColumnCache.hasColumn(jdbc, "repair_workorder", "reporter_name")) {
            jdbc.update("""
                INSERT INTO repair_workorder (id, wo_no, device_id, device_code, device_name, reporter_id, reporter_name, report_dept_id,
                    report_method, report_time, fault_description, urgency_level, fault_type_id, remark, status)
                VALUES (?::uuid,?,?::uuid,?,?,?::uuid,?,?::uuid,?,?::timestamptz,?,?,?::uuid,?,?)
                """,
                    id, woNo, body.get("device_id"), body.get("device_code"), body.get("device_name"),
                    reporterId, reporterName, blankToNull(body.get("report_dept_id")),
                    body.getOrDefault("report_method", "web"), body.get("report_time"),
                    body.get("fault_description"), body.getOrDefault("urgency_level", "normal"),
                    blankToNull(body.get("fault_type_id")), blankToNull(body.get("remark")), "draft");
        } else {
            jdbc.update("""
                INSERT INTO repair_workorder (id, wo_no, device_id, device_code, device_name, reporter_id, report_dept_id,
                    report_method, report_time, fault_description, urgency_level, fault_type_id, remark, status)
                VALUES (?::uuid,?,?::uuid,?,?,?::uuid,?::uuid,?,?::timestamptz,?,?,?::uuid,?,?)
                """,
                    id, woNo, body.get("device_id"), body.get("device_code"), body.get("device_name"),
                    reporterId, blankToNull(body.get("report_dept_id")),
                    body.getOrDefault("report_method", "web"), body.get("report_time"),
                    body.get("fault_description"), body.getOrDefault("urgency_level", "normal"),
                    blankToNull(body.get("fault_type_id")), blankToNull(body.get("remark")), "draft");
        }
        Map<String, Object> row = requireWo(id);
        addEvent(id, "created", null, "draft", null, null, null, null, null, "保存草稿", null);
        changeLog.recordCreate("repair_workorder", id, row);
        return Result.ok(row);
    }

    @PutMapping("/{id:" + UUID_PATH + "}")
    @Transactional
    @OperationLog(module = "repair", description = "修改报修草稿")
    public Result<Map<String, Object>> update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Map<String, Object> before = requireWo(id);
        if (!"draft".equals(str(before.get("status")))) {
            throw new BizException(400, "仅未提交的报修单可修改");
        }
        Object deviceId = body.containsKey("device_id") ? body.get("device_id") : before.get("device_id");
        if (deviceId == null || String.valueOf(deviceId).isBlank()) {
            throw new BizException(400, "请选择报修设备");
        }
        Object fault = body.containsKey("fault_description") ? body.get("fault_description") : before.get("fault_description");
        if (fault == null || String.valueOf(fault).isBlank()) {
            throw new BizException(400, "请填写故障描述");
        }
        assertDeviceAvailable(deviceId, id);
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        for (String col : DRAFT_EDITABLE_FIELDS) {
            if (!body.containsKey(col)) continue;
            Object v = body.get(col);
            if ("device_id".equals(col) || col.endsWith("_id")) {
                sets.add(col + " = ?::uuid");
                args.add(blankToNull(v));
                if ("reporter_id".equals(col) && TableColumnCache.hasColumn(jdbc, "repair_workorder", "reporter_name")) {
                    sets.add("reporter_name = ?");
                    args.add(SoftDeleteSupport.resolveUserDisplayName(jdbc, v));
                }
            } else if ("report_time".equals(col)) {
                sets.add(col + " = ?::timestamptz");
                args.add(v);
            } else {
                sets.add(col + " = ?");
                args.add(v);
            }
        }
        if (sets.isEmpty()) return Result.ok(before);
        sets.add("updated_at = NOW()");
        args.add(id);
        jdbc.update("UPDATE repair_workorder SET " + String.join(", ", sets) + " WHERE id = ?::uuid", args.toArray());
        Map<String, Object> after = requireWo(id);
        addEvent(id, "update", "draft", "draft", null, null, null, null, null, "修改草稿", null);
        changeLog.recordUpdate("repair_workorder", id, before, after);
        return Result.ok(after);
    }

    @PostMapping("/{id:" + UUID_PATH + "}/submit")
    @Transactional
    @OperationLog(module = "repair", description = "提交报修")
    public Result<Map<String, Object>> submit(@PathVariable UUID id) {
        Map<String, Object> before = requireWo(id);
        if (!"draft".equals(str(before.get("status")))) {
            throw new BizException(400, "仅未提交草稿可提交");
        }
        assertDeviceAvailable(before.get("device_id"), id);
        jdbc.update("""
            UPDATE repair_workorder SET status = 'reported',
            report_time = COALESCE(report_time, NOW()), updated_at = NOW()
            WHERE id = ?::uuid
            """, id);
        syncDeviceStatus(before.get("device_id"), "maintenance");
        Map<String, Object> after = requireWo(id);
        addEvent(id, "submit", "draft", "reported", null, null, null, null, null, "提交报修", null);
        changeLog.recordAction("repair_workorder", id, "submit", before, after, "提交报修");
        return Result.ok(after);
    }

    @PostMapping("/{id:" + UUID_PATH + "}/withdraw")
    @Transactional
    @OperationLog(module = "repair", description = "撤回报修")
    public Result<Map<String, Object>> withdraw(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> before = requireWo(id);
        assertWithdrawable(before);
        jdbc.update("""
            UPDATE repair_workorder SET status = 'draft', updated_at = NOW()
            WHERE id = ?::uuid
            """, id);
        syncDeviceStatus(before.get("device_id"), "normal");
        Map<String, Object> after = requireWo(id);
        String remark = body != null ? str(body.get("remark")) : "撤回报修";
        if (remark.isBlank()) remark = "撤回报修";
        addEvent(id, "withdraw", "reported", "draft", null, null, null, null, null, remark, null);
        changeLog.recordAction("repair_workorder", id, "withdraw", before, after, remark);
        return Result.ok(after);
    }

    @DeleteMapping("/{id:" + UUID_PATH + "}")
    @Transactional
    @OperationLog(module = "repair", description = "删除报修草稿")
    public Result<Void> delete(@PathVariable UUID id) {
        Map<String, Object> before = requireWo(id);
        if (!"draft".equals(str(before.get("status")))) {
            throw new BizException(400, "仅未提交的报修单可删除");
        }
        addEvent(id, "delete", "draft", "draft", null, null, null, null, null, "删除草稿", null);
        changeLog.recordDelete("repair_workorder", id, before);
        SoftDeleteSupport.softDelete(jdbc, "repair_workorder", id.toString());
        return Result.ok();
    }

    @PostMapping("/{id:" + UUID_PATH + "}/dispatch")
    @Transactional
    @OperationLog(module = "repair", description = "派工")
    public Result<Map<String, Object>> dispatch(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Map<String, Object> wo = requireWo(id);
        String current = str(wo.get("status"));
        if (!Set.of("reported", "dispatching", "pending_accept", "accepted", "repairing").contains(current)) {
            throw new BizException(400, "当前状态不可派工: " + current);
        }
        if (Set.of("reported", "dispatching").contains(current) && !isUnassigned(wo)) {
            throw new BizException(400, "工单已指派负责人，请使用转派或由负责人接单");
        }
        Object userId = resolveUserId(body);
        if (userId == null || String.valueOf(userId).isBlank()) {
            throw new BizException(400, "请选择工程师");
        }
        boolean startNow = Boolean.TRUE.equals(body.get("startRepair")) || Boolean.TRUE.equals(body.get("start_repair"));
        String target = startNow ? "repairing" : "pending_accept";
        String fromUser = str(wo.get("assigned_user_id"));
        String fromSub = str(wo.get("repair_sub_status"));
        String remark = str(body.get("remark"));

        if ("reported".equals(current)) {
            processService.insertProcess(id, ProcessRecord.builder("dispatch")
                    .fromStatus(current).toStatus(target)
                    .toSubStatus(startNow ? "internal" : null)
                    .userId(userId).toUserId(userId).operatorId(operatorId())
                    .remark(remark).build());
            if (startNow) {
                processService.insertProcess(id, ProcessRecord.builder("start_repair")
                        .fromStatus(current).toStatus("repairing").toSubStatus("internal")
                        .userId(userId).operatorId(operatorId()).remark("派工并开始维修").build());
            }
            addEvent(id, "dispatch", current, target, null, startNow ? "internal" : null,
                    userId, null, userId, remark, null);
            if (startNow) {
                addEvent(id, "start_repair", current, "repairing", null, "internal",
                        userId, null, null, "派工并开始维修", null);
            }
        } else {
            String eventType = Objects.equals(fromUser, String.valueOf(userId)) ? "dispatch" : "transfer";
            processService.insertProcess(id, ProcessRecord.builder(eventType)
                    .fromStatus(current).toStatus(target)
                    .fromSubStatus(fromSub).toSubStatus(startNow ? "internal" : null)
                    .userId(userId).fromUserId(fromUser).toUserId(userId).operatorId(operatorId())
                    .remark(remark).build());
            addEvent(id, eventType, current, target, fromSub,
                    startNow ? "internal" : null, userId, fromUser, userId, remark, null);
        }
        processService.syncWorkorderState(id, target, startNow ? "internal" : null, userId);
        if (startNow) {
            syncDeviceStatus(wo.get("device_id"), "maintenance");
            segmentService.openSegmentByCode(id, "internal", userId, remark.isBlank() ? "派工并开始维修" : remark);
        }
        return Result.ok(loadWorkorder(id));
    }

    @PostMapping("/{id:" + UUID_PATH + "}/start-repair")
    @Transactional
    @OperationLog(module = "repair", description = "开始维修")
    public Result<Map<String, Object>> startRepair(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> wo = requireWo(id);
        String current = str(wo.get("status"));
        if (!Set.of("reported", "dispatching", "pending_accept", "accepted").contains(current)) {
            throw new BizException(400, "当前状态不可开始维修: " + current);
        }
        assertRepairEngineer();
        assertAssignedOwner(wo);
        Object userId = body != null ? resolveUserId(body) : null;
        if (userId == null || String.valueOf(userId).isBlank()) {
            userId = wo.get("assigned_user_id");
        }
        String sub = body != null && body.get("repair_sub_status") != null
                ? String.valueOf(body.get("repair_sub_status")) : "internal";
        String remark = body != null ? str(body.get("remark")) : "开始维修";
        processService.insertProcess(id, ProcessRecord.builder("start_repair")
                .fromStatus(current).toStatus("repairing")
                .fromSubStatus(str(wo.get("repair_sub_status"))).toSubStatus(sub)
                .userId(userId).operatorId(operatorId()).remark(remark).build());
        processService.syncWorkorderState(id, "repairing", sub, userId);
        addEvent(id, "start_repair", current, "repairing", str(wo.get("repair_sub_status")), sub,
                userId, null, null, remark, null);
        syncDeviceStatus(wo.get("device_id"), "maintenance");
        segmentService.openSegmentByCode(id, "internal", userId, remark);
        return Result.ok(loadWorkorder(id));
    }

    @PostMapping("/{id:" + UUID_PATH + "}/accept")
    @Transactional
    @OperationLog(module = "repair", description = "工程师接单")
    public Result<Map<String, Object>> accept(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> wo = requireWo(id);
        String current = str(wo.get("status"));
        if (!Set.of("pending_accept", "dispatching").contains(current)) {
            throw new BizException(400, "当前状态不可接单: " + current);
        }
        assertRepairEngineer();
        assertAssignedOwner(wo);
        boolean startNow = body == null || !Boolean.FALSE.equals(body.get("startRepair"));
        Object userId = wo.get("assigned_user_id");
        if (startNow) {
            processService.insertProcess(id, ProcessRecord.builder("accept")
                    .fromStatus(current).toStatus("accepted")
                    .userId(userId).operatorId(operatorId()).remark("接单").build());
            processService.insertProcess(id, ProcessRecord.builder("start_repair")
                    .fromStatus("accepted").toStatus("repairing").toSubStatus("internal")
                    .userId(userId).operatorId(operatorId()).remark("接单并开始维修").build());
            processService.syncWorkorderState(id, "repairing", "internal", null);
            addEvent(id, "accept", current, "accepted", null, null, userId, null, null, "接单", null);
            addEvent(id, "start_repair", "accepted", "repairing", null, "internal",
                    userId, null, null, "接单并开始维修", null);
            syncDeviceStatus(wo.get("device_id"), "maintenance");
            segmentService.openSegmentByCode(id, "internal", userId, "接单并开始维修");
        } else {
            processService.insertProcess(id, ProcessRecord.builder("accept")
                    .fromStatus(current).toStatus("accepted")
                    .userId(userId).operatorId(operatorId()).remark("接单").build());
            processService.syncWorkorderState(id, "accepted", null, null);
            addEvent(id, "accept", current, "accepted", null, null, userId, null, null, "接单", null);
        }
        return Result.ok(loadWorkorder(id));
    }

    @PostMapping("/{id:" + UUID_PATH + "}/grab")
    @Transactional
    @OperationLog(module = "repair", description = "工程师抢单")
    public Result<Map<String, Object>> grab(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        assertRepairEngineer();
        Map<String, Object> wo = requireWo(id);
        String current = str(wo.get("status"));
        if (!Set.of("reported", "dispatching").contains(current)) {
            throw new BizException(400, "仅待派单工单可抢单: " + current);
        }
        if (!isUnassigned(wo)) {
            throw new BizException(400, "工单已指派负责人，不可抢单");
        }
        Object userId = operatorId();
        String remark = body != null ? str(body.get("remark")) : "工程师抢单";
        if (remark.isBlank()) remark = "工程师抢单";

        int updated;
        if (TableColumnCache.hasColumn(jdbc, "repair_workorder", "assigned_user_name")) {
            updated = jdbc.update("""
                    UPDATE repair_workorder
                    SET status = 'repairing', assigned_user_id = ?::uuid, assigned_user_name = ?,
                        repair_sub_status = 'internal', updated_at = NOW()
                    WHERE id = ?::uuid AND status IN ('reported','dispatching') AND assigned_user_id IS NULL
                    """, userId, SoftDeleteSupport.resolveUserDisplayName(jdbc, userId), id);
        } else {
            updated = jdbc.update("""
                    UPDATE repair_workorder
                    SET status = 'repairing', assigned_user_id = ?::uuid, repair_sub_status = 'internal', updated_at = NOW()
                    WHERE id = ?::uuid AND status IN ('reported','dispatching') AND assigned_user_id IS NULL
                    """, userId, id);
        }
        if (updated == 0) {
            throw new BizException(409, "工单已被他人抢单或已派单，请刷新后重试");
        }

        processService.insertProcess(id, ProcessRecord.builder("grab")
                .fromStatus(current).toStatus("repairing").toSubStatus("internal")
                .userId(userId).toUserId(userId).operatorId(userId).remark(remark).build());
        processService.insertProcess(id, ProcessRecord.builder("start_repair")
                .fromStatus(current).toStatus("repairing").toSubStatus("internal")
                .userId(userId).operatorId(userId).remark("抢单并开始维修").build());
        addEvent(id, "grab", current, "repairing", null, "internal", userId, null, userId, remark, null);
        addEvent(id, "start_repair", current, "repairing", null, "internal", userId, null, null, "抢单并开始维修", null);
        syncDeviceStatus(wo.get("device_id"), "maintenance");
        segmentService.openSegmentByCode(id, "internal", userId, remark);
        return Result.ok(loadWorkorder(id));
    }

    @PostMapping("/{id:" + UUID_PATH + "}/transfer")
    @Transactional
    @OperationLog(module = "repair", description = "转派工程师")
    public Result<Map<String, Object>> transfer(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Map<String, Object> wo = requireWo(id);
        String current = str(wo.get("status"));
        if (!Set.of("dispatching", "pending_accept", "accepted", "repairing", "verify_rejected").contains(current)) {
            throw new BizException(400, "当前状态不可转派: " + current);
        }
        assertRepairEngineer();
        assertAssignedOwner(wo);
        Object toUser = resolveUserId(body);
        if (toUser == null || String.valueOf(toUser).isBlank()) {
            throw new BizException(400, "请选择转派目标工程师");
        }
        boolean keepRepairing = Boolean.TRUE.equals(body.get("keepRepairing")) || Boolean.TRUE.equals(body.get("keep_repairing"));
        String target = keepRepairing && Set.of("repairing", "verify_rejected").contains(current) ? current : "pending_accept";
        Object fromUser = wo.get("assigned_user_id");
        String fromSub = str(wo.get("repair_sub_status"));
        String remark = str(body.get("remark"));
        processService.insertProcess(id, ProcessRecord.builder("transfer")
                .fromStatus(current).toStatus(target)
                .fromSubStatus(fromSub).toSubStatus(keepRepairing ? fromSub : null)
                .userId(toUser).fromUserId(fromUser).toUserId(toUser).operatorId(operatorId())
                .remark(remark).build());
        processService.syncWorkorderState(id, target, keepRepairing ? fromSub : null, toUser);
        addEvent(id, "transfer", current, target, fromSub,
                keepRepairing ? fromSub : null, toUser, fromUser, toUser, remark, null);
        return Result.ok(loadWorkorder(id));
    }

    @PostMapping("/{id:" + UUID_PATH + "}/sub-status")
    @Transactional
    @OperationLog(module = "repair", description = "更新维修子状态")
    public Result<Map<String, Object>> updateSubStatus(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Map<String, Object> wo = requireWo(id);
        String current = str(wo.get("status"));
        if (!Set.of("repairing", "verify_rejected").contains(current)) {
            throw new BizException(400, "仅维修中或拒绝验收后可更新子状态");
        }
        assertRepairEngineer();
        assertAssignedOwner(wo);
        String sub = str(body.get("repair_sub_status"));
        if (sub.isBlank()) throw new BizException(400, "请指定子状态");
        String fromSub = str(wo.get("repair_sub_status"));
        String repairType = mapRepairType(sub);
        String extraJson = "on_site".equals(sub)
                ? "{\"repair_type\":\"" + repairType + "\",\"arrival_recorded\":true}" : null;
        processService.insertProcess(id, ProcessRecord.builder("sub_status")
                .fromStatus(current).toStatus(current)
                .fromSubStatus(fromSub).toSubStatus(sub)
                .userId(wo.get("assigned_user_id")).operatorId(operatorId())
                .remark(str(body.get("remark"))).extraJson(extraJson).build());
        processService.syncWorkorderState(id, current, sub, null);
        addEvent(id, "sub_status_change", current, current, fromSub, sub,
                wo.get("assigned_user_id"), null, null, str(body.get("remark")), null);
        return Result.ok(loadWorkorder(id));
    }

    @PostMapping("/{id:" + UUID_PATH + "}/complete")
    @Transactional
    @OperationLog(module = "repair", description = "维修完成")
    public Result<Map<String, Object>> complete(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Map<String, Object> wo = requireWo(id);
        String current = str(wo.get("status"));
        if (!Set.of("repairing", "verify_rejected").contains(current)) {
            throw new BizException(400, "仅维修中或拒绝验收后可完工: " + current);
        }
        assertRepairEngineer();
        assertAssignedOwner(wo);
        boolean skipVerify = Boolean.TRUE.equals(body.get("skipVerify")) || Boolean.TRUE.equals(body.get("skip_verify"));
        if (skipVerify && "verify_rejected".equals(current)) {
            throw new BizException(400, "拒绝验收返修后不可跳过验收直接结案");
        }
        if (!skipVerify) {
            segmentService.assertAllHistoricalConfirmed(id);
        }
        String target = skipVerify ? "closed" : "pending_verify";
        processService.insertProcess(id, ProcessRecord.builder("complete")
                .fromStatus(current).toStatus(target)
                .fromSubStatus(str(wo.get("repair_sub_status")))
                .userId(wo.get("assigned_user_id")).operatorId(operatorId())
                .solutionDescription(str(body.get("solution_description")))
                .laborCost(body.getOrDefault("labor_cost", 0))
                .partsCost(body.getOrDefault("parts_cost", 0))
                .totalCost(body.getOrDefault("total_cost", 0))
                .skipVerify(skipVerify)
                .remark(skipVerify ? "完工直接结案" : "完工提交验收").build());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts = (List<Map<String, Object>>) body.getOrDefault("spareParts", List.of());
        for (Map<String, Object> p : parts) {
            jdbc.update("INSERT INTO spare_part_usage (id, workorder_id, part_id, quantity, unit_price) VALUES (?::uuid,?::uuid,?::uuid,?,?)",
                    UUID.randomUUID(), id, p.get("part_id"), p.get("quantity"), p.get("unit_price"));
            jdbc.update("INSERT INTO spare_part_transaction (spare_part_id, txn_type, quantity, workorder_id) VALUES (?::uuid,'out',?,?::uuid)",
                    p.get("spare_part_id") != null ? p.get("spare_part_id") : p.get("part_id"), p.get("quantity"), id);
        }
        processService.syncWorkorderState(id, target, null, null);
        addEvent(id, "complete", current, target, str(wo.get("repair_sub_status")), null,
                wo.get("assigned_user_id"), null, null,
                skipVerify ? "完工直接结案" : "完工提交验收", null);
        if (skipVerify) {
            processService.insertProcess(id, ProcessRecord.builder("close")
                    .fromStatus(target).toStatus("closed").operatorId(operatorId()).remark("跳过验收结案").build());
            addEvent(id, "close", target, "closed", null, null, null, null, null, "跳过验收结案", null);
            syncDeviceStatus(wo.get("device_id"), "normal");
        } else {
            addEvent(id, "submit_verify", current, "pending_verify", null, null, null, null, null, "提交验收", null);
            syncDeviceStatus(wo.get("device_id"), "pending_verify");
            segmentService.openSystemSegment(id, "pending_verify", wo.get("assigned_user_id"), "完工提交验收", null);
        }
        return Result.ok(loadWorkorder(id));
    }

    @PostMapping("/{id:" + UUID_PATH + "}/verify")
    @Transactional
    @OperationLog(module = "repair", description = "验收评价")
    public Result<Map<String, Object>> verify(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Map<String, Object> wo = requireWo(id);
        if (!"pending_verify".equals(str(wo.get("status")))) {
            throw new BizException(400, "仅待验收工单可验收");
        }
        String result = str(body.getOrDefault("verify_result", "pass"));
        boolean pass = !"fail".equalsIgnoreCase(result);
        Object verifierId = blankToNull(body.get("verifier_id"));
        if (pass) {
            processService.insertProcess(id, ProcessRecord.builder("verify_pass")
                    .fromStatus("pending_verify").toStatus("verified")
                    .operatorId(verifierId != null ? verifierId : operatorId())
                    .verifyResult(result).verifyComment(str(body.get("verify_comment")))
                    .satisfactionRating(body.get("satisfaction_rating"))
                    .satisfactionComment(str(body.get("satisfaction_comment")))
                    .remark(str(body.get("verify_comment"))).build());
            processService.insertProcess(id, ProcessRecord.builder("close")
                    .fromStatus("verified").toStatus("closed").operatorId(operatorId()).remark("验收后关闭").build());
            processService.syncWorkorderState(id, "closed", null, null);
            addEvent(id, "verify_pass", "pending_verify", "verified", null, null, null, null, null, str(body.get("verify_comment")), null);
            addEvent(id, "close", "verified", "closed", null, null, null, null, null, "验收后关闭", null);
            syncDeviceStatus(wo.get("device_id"), "normal");
            segmentService.openSystemSegment(id, "verified", verifierId, "验收通过", null);
        } else {
            String comment = str(body.get("verify_comment"));
            if (comment.isBlank()) {
                throw new BizException(400, "请填写拒绝验收原因");
            }
            processService.insertProcess(id, ProcessRecord.builder("verify_fail")
                    .fromStatus("pending_verify").toStatus("verify_rejected")
                    .userId(wo.get("assigned_user_id"))
                    .operatorId(verifierId != null ? verifierId : operatorId())
                    .verifyResult(result).verifyComment(comment)
                    .remark(comment).build());
            processService.syncWorkorderState(id, "verify_rejected", null, null);
            addEvent(id, "verify_fail", "pending_verify", "verify_rejected", null, null,
                    wo.get("assigned_user_id"), null, null, comment, null);
            syncDeviceStatus(wo.get("device_id"), "maintenance");
            segmentService.openSystemSegment(id, "verify_rejected", wo.get("assigned_user_id"), comment, comment);
        }
        return Result.ok(loadWorkorder(id));
    }

    @PostMapping("/{id:" + UUID_PATH + "}/suspend")
    @Transactional
    @OperationLog(module = "repair", description = "挂起工单")
    public Result<Map<String, Object>> suspend(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> wo = requireWo(id);
        if (!"repairing".equals(str(wo.get("status")))) {
            throw new BizException(400, "仅维修中可挂起");
        }
        assertRepairEngineer();
        assertAssignedOwner(wo);
        String remark = body != null ? str(body.get("remark")) : null;
        processService.insertProcess(id, ProcessRecord.builder("suspend")
                .fromStatus("repairing").toStatus("suspended")
                .fromSubStatus(str(wo.get("repair_sub_status")))
                .userId(wo.get("assigned_user_id")).operatorId(operatorId())
                .remark(remark).build());
        processService.syncWorkorderState(id, "suspended", str(wo.get("repair_sub_status")), null);
        addEvent(id, "suspend", "repairing", "suspended", str(wo.get("repair_sub_status")), null,
                wo.get("assigned_user_id"), null, null, remark, null);
        return Result.ok(loadWorkorder(id));
    }

    @PostMapping("/{id:" + UUID_PATH + "}/resume")
    @Transactional
    @OperationLog(module = "repair", description = "恢复工单")
    public Result<Map<String, Object>> resume(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> wo = requireWo(id);
        if (!"suspended".equals(str(wo.get("status")))) {
            throw new BizException(400, "仅挂起工单可恢复");
        }
        assertRepairEngineer();
        assertAssignedOwner(wo);
        String sub = body != null && body.get("repair_sub_status") != null
                ? str(body.get("repair_sub_status")) : "internal";
        String remark = body != null ? str(body.get("remark")) : null;
        processService.insertProcess(id, ProcessRecord.builder("resume")
                .fromStatus("suspended").toStatus("repairing").toSubStatus(sub)
                .userId(wo.get("assigned_user_id")).operatorId(operatorId())
                .remark(remark).build());
        processService.syncWorkorderState(id, "repairing", sub, null);
        addEvent(id, "resume", "suspended", "repairing", null, sub,
                wo.get("assigned_user_id"), null, null, remark, null);
        return Result.ok(loadWorkorder(id));
    }

    @PostMapping("/{id:" + UUID_PATH + "}/cancel")
    @Transactional
    @OperationLog(module = "repair", description = "取消工单")
    public Result<Map<String, Object>> cancel(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> wo = requireWo(id);
        String current = str(wo.get("status"));
        if ("draft".equals(current)) {
            throw new BizException(400, "草稿请直接删除，无需取消");
        }
        if (Set.of("closed", "cancelled", "verified").contains(current)) {
            throw new BizException(400, "当前状态不可取消");
        }
        String remark = body != null ? str(body.get("remark")) : "取消工单";
        processService.insertProcess(id, ProcessRecord.builder("cancel")
                .fromStatus(current).toStatus("cancelled")
                .fromSubStatus(str(wo.get("repair_sub_status")))
                .operatorId(operatorId()).remark(remark).build());
        processService.syncWorkorderState(id, "cancelled", null, null);
        addEvent(id, "cancel", current, "cancelled", str(wo.get("repair_sub_status")), null,
                null, null, null, remark, null);
        syncDeviceStatus(wo.get("device_id"), "normal");
        changeLog.recordAction("repair_workorder", id, "cancel", wo, requireWo(id), remark);
        return Result.ok(loadWorkorder(id));
    }

    // ---------- helpers ----------

    private Map<String, Object> requireWo(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM repair_workorder WHERE id = ?::uuid "
                        + SoftDeleteSupport.notDeletedClause(jdbc, "repair_workorder", null), id);
        if (rows.isEmpty()) throw new BizException(404, "workorder not found");
        return rows.get(0);
    }

    private Map<String, Object> loadWorkorder(UUID id) {
        Map<String, Object> wo = requireWo(id);
        processService.enrichWorkorder(wo);
        return wo;
    }

    private void assertWithdrawable(Map<String, Object> wo) {
        if (!"reported".equals(str(wo.get("status")))) {
            throw new BizException(400, "仅已提交且尚未派单/响应的报修可撤回");
        }
        UUID id = UUID.fromString(String.valueOf(wo.get("id")));
        if (processService.hasWorkflowStarted(id)) {
            throw new BizException(400, "已进入派单或维修流程，不可撤回，如需作废请使用取消");
        }
    }

    private void assertDeviceAvailable(Object deviceId, UUID excludeWoId) {
        if (deviceId == null || String.valueOf(deviceId).isBlank()) return;
        List<Map<String, Object>> device = jdbc.queryForList(
                "SELECT device_status FROM medical_device WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", null), deviceId);
        if (device.isEmpty()) throw new BizException(400, "设备不存在");
        String ds = str(device.get(0).get("device_status"));
        if (Set.of("maintenance", "pending_verify", "scrap").contains(ds)) {
            throw new BizException(400, "设备当前不可报修: " + ds);
        }
        String sql = """
                SELECT id FROM repair_workorder
                WHERE device_id = ?::uuid AND status IN ('reported','dispatching','pending_accept','accepted','repairing','pending_verify','suspended','verify_rejected')
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "repair_workorder", null);
        List<Object> args = new ArrayList<>();
        args.add(deviceId);
        if (excludeWoId != null) {
            sql += " AND id <> ?::uuid";
            args.add(excludeWoId);
        }
        List<Map<String, Object>> busy = jdbc.queryForList(sql, args.toArray());
        if (!busy.isEmpty()) {
            throw new BizException(400, "该设备已有进行中的报修单");
        }
    }

    private static boolean isBlankObj(Object v) {
        return v == null || String.valueOf(v).isBlank() || "null".equalsIgnoreCase(String.valueOf(v));
    }

    private void syncDeviceStatus(Object deviceId, String status) {
        if (deviceId == null || String.valueOf(deviceId).isBlank()) return;
        jdbc.update("UPDATE medical_device SET device_status = ?, updated_at = NOW() WHERE id = ?::uuid", status, deviceId);
    }

    private void addEvent(UUID woId, String type, String fromStatus, String toStatus,
                          String fromSub, String toSub, Object userId,
                          Object fromUser, Object toUser, String remark, String extraJson) {
        Map<String, Object> wo = loadWorkorder(woId);
        boolean hasDevice = TableColumnCache.hasColumn(jdbc, "repair_workorder_event", "device_id");
        boolean hasNames = TableColumnCache.hasColumn(jdbc, "repair_workorder_event", "operator_name");
        Object opId = blankToNull(operatorId());
        String opName = SoftDeleteSupport.resolveUserDisplayName(jdbc, opId);
        String userName = SoftDeleteSupport.resolveUserDisplayName(jdbc, userId);
        String fromUserName = SoftDeleteSupport.resolveUserDisplayName(jdbc, fromUser);
        String toUserName = SoftDeleteSupport.resolveUserDisplayName(jdbc, toUser);
        if (hasDevice && hasNames) {
            jdbc.update("""
                INSERT INTO repair_workorder_event
                (id, workorder_id, event_type, from_status, to_status, from_sub_status, to_sub_status,
                 operator_id, user_id, from_user_id, to_user_id,
                 operator_name, user_name, from_user_name, to_user_name,
                 remark, extra_json, device_id, device_code, device_name)
                VALUES (?::uuid,?::uuid,?,?,?,?,?,?::uuid,?::uuid,?::uuid,?::uuid,
                        ?,?,?,?,
                        ?,CAST(? AS jsonb),?::uuid,?,?)
                """,
                    UUID.randomUUID(), woId, type, blankToNull(fromStatus), blankToNull(toStatus),
                    blankToNull(fromSub), blankToNull(toSub),
                    opId, blankToNull(userId), blankToNull(fromUser), blankToNull(toUser),
                    opName, userName, fromUserName, toUserName,
                    blankToNull(remark), extraJson,
                    blankToNull(wo.get("device_id")), blankToNull(wo.get("device_code")), blankToNull(wo.get("device_name")));
        } else if (hasDevice) {
            jdbc.update("""
                INSERT INTO repair_workorder_event
                (id, workorder_id, event_type, from_status, to_status, from_sub_status, to_sub_status,
                 operator_id, user_id, from_user_id, to_user_id, remark, extra_json,
                 device_id, device_code, device_name)
                VALUES (?::uuid,?::uuid,?,?,?,?,?,?::uuid,?::uuid,?::uuid,?::uuid,?,CAST(? AS jsonb),
                        ?::uuid,?,?)
                """,
                    UUID.randomUUID(), woId, type, blankToNull(fromStatus), blankToNull(toStatus),
                    blankToNull(fromSub), blankToNull(toSub),
                    opId, blankToNull(userId), blankToNull(fromUser), blankToNull(toUser),
                    blankToNull(remark), extraJson,
                    blankToNull(wo.get("device_id")), blankToNull(wo.get("device_code")), blankToNull(wo.get("device_name")));
        } else if (hasNames) {
            jdbc.update("""
                INSERT INTO repair_workorder_event
                (id, workorder_id, event_type, from_status, to_status, from_sub_status, to_sub_status,
                 operator_id, user_id, from_user_id, to_user_id,
                 operator_name, user_name, from_user_name, to_user_name,
                 remark, extra_json)
                VALUES (?::uuid,?::uuid,?,?,?,?,?,?::uuid,?::uuid,?::uuid,?::uuid,
                        ?,?,?,?,
                        ?,CAST(? AS jsonb))
                """,
                    UUID.randomUUID(), woId, type, blankToNull(fromStatus), blankToNull(toStatus),
                    blankToNull(fromSub), blankToNull(toSub),
                    opId, blankToNull(userId), blankToNull(fromUser), blankToNull(toUser),
                    opName, userName, fromUserName, toUserName,
                    blankToNull(remark), extraJson);
        } else {
            jdbc.update("""
                INSERT INTO repair_workorder_event
                (id, workorder_id, event_type, from_status, to_status, from_sub_status, to_sub_status,
                 operator_id, user_id, from_user_id, to_user_id, remark, extra_json)
                VALUES (?::uuid,?::uuid,?,?,?,?,?,?::uuid,?::uuid,?::uuid,?::uuid,?,CAST(? AS jsonb))
                """,
                    UUID.randomUUID(), woId, type, blankToNull(fromStatus), blankToNull(toStatus),
                    blankToNull(fromSub), blankToNull(toSub),
                    opId, blankToNull(userId), blankToNull(fromUser), blankToNull(toUser),
                    blankToNull(remark), extraJson);
        }
    }

    private static Object resolveUserId(Map<String, Object> body) {
        if (body == null) return null;
        for (String key : List.of("userId", "user_id", "engineerId", "engineer_id")) {
            Object v = body.get(key);
            if (v != null && !String.valueOf(v).isBlank()) return v;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> resolveUserIds(Map<String, Object> body) {
        if (body == null) return List.of();
        Object raw = body.get("userIds") != null ? body.get("userIds") : body.get("user_ids");
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                if (o != null && !String.valueOf(o).isBlank()) out.add(String.valueOf(o));
            }
            return out;
        }
        if (raw instanceof String s && !s.isBlank()) {
            return Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
        }
        return List.of();
    }

    /**
     * 解析工程师行：优先 body.engineers[{userId, workContent, isPrimary}]，否则回退 userIds / userId。
     */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> resolveEngineers(Map<String, Object> body) {
        if (body == null) return List.of();
        Object raw = body.get("engineers");
        if (raw instanceof List<?> list && !list.isEmpty()) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    Object uid = m.get("userId") != null ? m.get("userId") : m.get("user_id");
                    if (uid == null || String.valueOf(uid).isBlank()) continue;
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("user_id", String.valueOf(uid));
                    Object wc = m.get("workContent") != null ? m.get("workContent") : m.get("work_content");
                    if (wc != null) row.put("work_content", String.valueOf(wc));
                    Object primary = m.get("isPrimary") != null ? m.get("isPrimary") : m.get("is_primary");
                    if (primary != null) row.put("is_primary", primary);
                    out.add(row);
                } else if (o != null && !String.valueOf(o).isBlank()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("user_id", String.valueOf(o));
                    out.add(row);
                }
            }
            if (!out.isEmpty()) return out;
        }
        Object userId = resolveUserId(body);
        List<String> userIds = resolveUserIds(body);
        if (userIds.isEmpty() && userId != null) {
            userIds = List.of(String.valueOf(userId));
        }
        return RepairWorkorderSegmentService.engineersFromUserIds(userIds);
    }

    private static boolean isUnassigned(Map<String, Object> wo) {
        Object v = wo.get("assigned_user_id");
        return v == null || String.valueOf(v).isBlank() || "null".equalsIgnoreCase(String.valueOf(v));
    }

    private void assertRepairEngineer() {
        String uid = operatorId();
        if (uid == null || uid.isBlank()) {
            throw new BizException(401, "未登录");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT COALESCE(is_repair_engineer, false) AS is_repair_engineer FROM sys_user WHERE id = ?::uuid AND is_active = true"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "sys_user", null),
                uid);
        if (rows.isEmpty() || !Boolean.TRUE.equals(rows.get(0).get("is_repair_engineer"))) {
            throw new BizException(403, "仅维修工程师可执行此操作");
        }
    }

    private void assertAssignedOwner(Map<String, Object> wo) {
        String assigned = str(wo.get("assigned_user_id"));
        String op = str(operatorId());
        if (assigned.isBlank()) {
            throw new BizException(400, "工单尚未指派负责人");
        }
        if (op.isBlank() || !assigned.equals(op)) {
            throw new BizException(403, "仅当前负责人可操作此工单");
        }
    }

    private String operatorId() {
        return TenantContext.getUserId();
    }

    private static void appendIlike(StringBuilder sql, List<Object> args, String col, String val) {
        if (val != null && !val.isBlank()) {
            sql.append(" AND ").append(col).append(" ILIKE ? ");
            args.add("%" + val.trim() + "%");
        }
    }

    private static Object blankToNull(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static String mapRepairType(String sub) {
        if ("external".equals(sub)) return "external";
        if ("internal".equals(sub)) return "internal";
        return null;
    }

    private List<Map<String, Object>> enrichEvents(List<Map<String, Object>> events) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> e : events) {
            Map<String, Object> row = new LinkedHashMap<>(e);
            row.put("event_label", eventLabel(str(e.get("event_type"))));
            out.add(row);
        }
        return out;
    }

    private static String eventLabel(String type) {
        return switch (type) {
            case "created" -> "保存草稿";
            case "update" -> "修改草稿";
            case "submit" -> "提交报修";
            case "withdraw" -> "撤回报修";
            case "delete" -> "删除草稿";
            case "dispatch" -> "派工";
            case "grab" -> "抢单";
            case "add_segment" -> "添加进程段";
            case "update_segment" -> "编辑进程段";
            case "delete_segment" -> "删除进程段";
            case "confirm_segment" -> "确认进程段";
            case "transfer" -> "转派";
            case "accept" -> "接单";
            case "reject" -> "退单";
            case "start_repair" -> "开始维修";
            case "sub_status_change" -> "子状态变更";
            case "suspend" -> "挂起";
            case "resume" -> "恢复";
            case "complete" -> "维修完工";
            case "submit_verify" -> "提交验收";
            case "verify_pass" -> "验收通过";
            case "verify_fail" -> "拒绝验收";
            case "close" -> "工单关闭";
            case "cancel" -> "取消";
            default -> type;
        };
    }

    private List<Map<String, Object>> buildMilestones(Map<String, Object> wo, List<Map<String, Object>> events) {
        String status = str(wo.get("status"));
        boolean skippedDispatch = events.stream().anyMatch(e -> "start_repair".equals(str(e.get("event_type"))))
                && events.stream().noneMatch(e -> "dispatch".equals(str(e.get("event_type"))));
        boolean skippedVerify = "closed".equals(status)
                && events.stream().anyMatch(e -> "close".equals(str(e.get("event_type")))
                && str(e.get("remark")).contains("跳过验收"));

        List<Map<String, Object>> list = new ArrayList<>();
        list.add(milestone("reported", "报修提交", wo.get("report_time"), false, null));
        list.add(milestone("dispatch", "派单/接单",
                firstNonNull(wo.get("dispatch_started_at"), wo.get("assigned_at"), wo.get("accepted_at")),
                skippedDispatch, skippedDispatch ? "工程师直修，已跳过派单" : null));
        list.add(milestone("repairing", "开始维修", wo.get("repair_start_time"), false, null));
        list.add(milestone("complete", "维修结束", wo.get("repair_end_time"), false, null));
        list.add(milestone("verify", "科室验收", wo.get("verify_time"),
                skippedVerify || ("closed".equals(status) && wo.get("verify_time") == null && wo.get("repair_end_time") != null),
                skippedVerify ? "已跳过验收直接结案" : null));
        list.add(milestone("closed", "工单关闭",
                firstNonNull(wo.get("closed_at"), "closed".equals(status) || "cancelled".equals(status) ? wo.get("updated_at") : null),
                false, null));
        return list;
    }

    private Map<String, Object> milestone(String key, String label, Object at, boolean skipped, String skipReason) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("at", at);
        m.put("skipped", skipped);
        m.put("skipReason", skipReason);
        m.put("done", at != null || skipped);
        return m;
    }

    private List<Map<String, Object>> buildSubStatusSegments(List<Map<String, Object>> events) {
        List<Map<String, Object>> segments = new ArrayList<>();
        OffsetDateTime segStart = null;
        String currentSub = null;
        for (Map<String, Object> e : events) {
            String type = str(e.get("event_type"));
            OffsetDateTime at = toOffset(e.get("created_at"));
            if ("start_repair".equals(type) || "resume".equals(type)) {
                currentSub = str(e.get("to_sub_status"));
                if (currentSub.isBlank()) currentSub = "internal";
                segStart = at;
            } else if ("sub_status_change".equals(type) && segStart != null) {
                segments.add(segment(currentSub, segStart, at, str(e.get("remark"))));
                currentSub = str(e.get("to_sub_status"));
                segStart = at;
            } else if (("complete".equals(type) || "suspend".equals(type) || "cancel".equals(type)) && segStart != null) {
                segments.add(segment(currentSub, segStart, at, null));
                segStart = null;
                currentSub = null;
            }
        }
        if (segStart != null && currentSub != null) {
            segments.add(segment(currentSub, segStart, OffsetDateTime.now(), null));
        }
        return segments;
    }

    private Map<String, Object> segment(String sub, OffsetDateTime start, OffsetDateTime end, String remark) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("type", "repairing");
        s.put("subStatus", sub);
        s.put("subStatusLabel", subStatusLabel(sub));
        s.put("start", start);
        s.put("end", end);
        s.put("minutes", start != null && end != null ? Duration.between(start, end).toMinutes() : 0);
        s.put("remark", remark);
        return s;
    }

    private static String subStatusLabel(String sub) {
        return switch (sub) {
            case "internal" -> "院内维修";
            case "external" -> "院外维修";
            case "waiting_parts" -> "等待配件";
            case "waiting_approval" -> "待审批";
            case "on_site" -> "已到场";
            case "diagnosing" -> "诊断中";
            case "testing" -> "调试中";
            default -> sub;
        };
    }

    private Map<String, Object> buildSummary(Map<String, Object> wo, List<Map<String, Object>> events) {
        OffsetDateTime report = toOffset(wo.get("report_time"));
        OffsetDateTime repairStart = toOffset(wo.get("repair_start_time"));
        OffsetDateTime repairEnd = toOffset(wo.get("repair_end_time"));
        OffsetDateTime verify = toOffset(wo.get("verify_time"));
        OffsetDateTime closed = toOffset(firstNonNull(wo.get("closed_at"),
                Set.of("closed", "cancelled").contains(str(wo.get("status"))) ? wo.get("updated_at") : null));
        OffsetDateTime response = toOffset(firstNonNull(wo.get("accepted_at"), wo.get("response_time"), repairStart));
        OffsetDateTime now = OffsetDateTime.now();

        Map<String, Object> s = new LinkedHashMap<>();
        s.put("currentStatus", wo.get("status"));
        s.put("currentSubStatus", wo.get("repair_sub_status"));
        s.put("downtimeMinutes", minutesBetween(report, closed != null ? closed : now));
        s.put("responseMinutes", minutesBetween(report, response));
        s.put("repairMinutes", minutesBetween(repairStart, repairEnd != null ? repairEnd : ("repairing".equals(str(wo.get("status"))) ? now : null)));
        s.put("pendingVerifyMinutes", minutesBetween(repairEnd, verify != null ? verify : ("pending_verify".equals(str(wo.get("status"))) ? now : null)));
        s.put("waitingPartsMinutes", sumSubMinutes(events, "waiting_parts"));
        return s;
    }

    private long sumSubMinutes(List<Map<String, Object>> events, String sub) {
        return buildSubStatusSegments(events).stream()
                .filter(seg -> sub.equals(str(seg.get("subStatus"))))
                .mapToLong(seg -> ((Number) seg.getOrDefault("minutes", 0)).longValue())
                .sum();
    }

    private static long minutesBetween(OffsetDateTime a, OffsetDateTime b) {
        if (a == null || b == null) return 0;
        return Math.max(0, Duration.between(a, b).toMinutes());
    }

    private static OffsetDateTime toOffset(Object v) {
        if (v == null) return null;
        if (v instanceof OffsetDateTime o) return o;
        if (v instanceof java.sql.Timestamp ts) return ts.toInstant().atOffset(java.time.ZoneOffset.ofHours(8));
        try {
            return OffsetDateTime.parse(String.valueOf(v).replace(" ", "T"));
        } catch (Exception e) {
            return null;
        }
    }

    private static Object firstNonNull(Object... vals) {
        for (Object v : vals) if (v != null) return v;
        return null;
    }

    private static Set<String> modeStatusScope(String mode) {
        if (mode == null || mode.isBlank()) return Set.of();
        return switch (mode) {
            case "handle" -> HANDLE_LIST_STATUSES;
            case "verify" -> VERIFY_LIST_STATUSES;
            default -> Set.of();
        };
    }

    private static List<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static void appendStatusFilter(StringBuilder where, List<Object> args,
                                           String mode, String status, String statuses) {
        List<String> selected = !parseCsv(statuses).isEmpty()
                ? parseCsv(statuses)
                : (status != null && !status.isBlank() ? List.of(status.trim()) : List.of());
        Set<String> scope = modeStatusScope(mode);
        List<String> effective;
        if (!selected.isEmpty()) {
            if (scope.isEmpty()) {
                effective = selected;
            } else {
                effective = selected.stream().filter(scope::contains).distinct().toList();
            }
        } else if (!scope.isEmpty()) {
            effective = new ArrayList<>(scope);
        } else {
            return;
        }
        if (effective.isEmpty()) {
            where.append(" AND 1=0 ");
            return;
        }
        where.append(" AND status IN (");
        where.append(String.join(",", Collections.nCopies(effective.size(), "?")));
        where.append(") ");
        args.addAll(effective);
    }
}
