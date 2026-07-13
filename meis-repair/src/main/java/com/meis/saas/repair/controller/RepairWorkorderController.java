package com.meis.saas.repair.controller;

import com.meis.saas.common.audit.EntityChangeLogService;
import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.tenant.TenantContext;
import com.meis.saas.repair.service.RepairWorkorderProcessService;
import com.meis.saas.repair.service.RepairWorkorderProcessService.ProcessRecord;
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
            "repairing", "pending_verify", "suspended"
    );

    private static final Set<String> DRAFT_EDITABLE_FIELDS = Set.of(
            "device_id", "device_code", "device_name", "reporter_id", "report_dept_id",
            "report_method", "report_time", "fault_description", "urgency_level",
            "fault_type_id", "remark"
    );

    private final JdbcTemplate jdbc;
    private final EntityChangeLogService changeLog;
    private final RepairWorkorderProcessService processService;

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
                LEFT JOIN department dept ON dept.id = d.dept_id
                WHERE d.is_active = true
                  AND COALESCE(d.device_status, '') NOT IN ('maintenance', 'pending_verify', 'scrap')
                  AND d.id NOT IN (
                      SELECT device_id FROM repair_workorder
                      WHERE device_id IS NOT NULL
                        AND status IN ('reported','dispatching','pending_accept','accepted','repairing','pending_verify','suspended')
                  )
                """);
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
            @RequestParam(required = false) String status) {
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
        if (status != null && !status.isBlank()) {
            where.append(" AND status = ? ");
            args.add(status);
        } else if (mode != null && !mode.isBlank()) {
            switch (mode) {
                case "apply" -> where.append(" AND status IN ('draft','reported','dispatching') ");
                case "handle" -> where.append(" AND status IN ('dispatching','pending_accept','accepted','repairing','suspended') ");
                case "verify" -> where.append(" AND status = 'pending_verify' ");
                default -> { }
            }
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

    @GetMapping("/{id:" + UUID_PATH + "}/timeline")
    public Result<Map<String, Object>> timeline(@PathVariable UUID id) {
        Map<String, Object> wo = loadWorkorder(id);
        List<Map<String, Object>> events = jdbc.queryForList(
                "SELECT * FROM repair_workorder_event WHERE workorder_id = ?::uuid ORDER BY created_at ASC, id ASC", id);

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
        jdbc.update("""
            INSERT INTO repair_workorder (id, wo_no, device_id, device_code, device_name, reporter_id, report_dept_id,
                report_method, report_time, fault_description, urgency_level, fault_type_id, remark, status)
            VALUES (?::uuid,?,?::uuid,?,?,?::uuid,?::uuid,?,?::timestamptz,?,?,?::uuid,?,?)
            """,
                id, woNo, body.get("device_id"), body.get("device_code"), body.get("device_name"),
                blankToNull(body.get("reporter_id")), blankToNull(body.get("report_dept_id")),
                body.getOrDefault("report_method", "web"), body.get("report_time"),
                body.get("fault_description"), body.getOrDefault("urgency_level", "normal"),
                blankToNull(body.get("fault_type_id")), blankToNull(body.get("remark")), "draft");
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
        Object engineerId = body.get("engineerId") != null ? body.get("engineerId") : body.get("engineer_id");
        if (engineerId == null || String.valueOf(engineerId).isBlank()) {
            throw new BizException(400, "请选择工程师");
        }
        boolean startNow = Boolean.TRUE.equals(body.get("startRepair")) || Boolean.TRUE.equals(body.get("start_repair"));
        String target = startNow ? "repairing" : "pending_accept";
        String fromEngineer = str(wo.get("assigned_engineer_id"));
        String fromSub = str(wo.get("repair_sub_status"));
        String remark = str(body.get("remark"));

        if ("reported".equals(current)) {
            processService.insertProcess(id, ProcessRecord.builder("dispatch")
                    .fromStatus(current).toStatus(target)
                    .toSubStatus(startNow ? "internal" : null)
                    .engineerId(engineerId).toEngineerId(engineerId).operatorId(operatorId())
                    .remark(remark).build());
            if (startNow) {
                processService.insertProcess(id, ProcessRecord.builder("start_repair")
                        .fromStatus(current).toStatus("repairing").toSubStatus("internal")
                        .engineerId(engineerId).operatorId(operatorId()).remark("派工并开始维修").build());
            }
            addEvent(id, "dispatch", current, target, null, startNow ? "internal" : null,
                    engineerId, null, engineerId, remark, null);
            if (startNow) {
                addEvent(id, "start_repair", current, "repairing", null, "internal",
                        engineerId, null, null, "派工并开始维修", null);
            }
        } else {
            String eventType = Objects.equals(fromEngineer, String.valueOf(engineerId)) ? "dispatch" : "transfer";
            processService.insertProcess(id, ProcessRecord.builder(eventType)
                    .fromStatus(current).toStatus(target)
                    .fromSubStatus(fromSub).toSubStatus(startNow ? "internal" : null)
                    .engineerId(engineerId).fromEngineerId(fromEngineer).toEngineerId(engineerId).operatorId(operatorId())
                    .remark(remark).build());
            addEvent(id, eventType, current, target, fromSub,
                    startNow ? "internal" : null, engineerId, fromEngineer, engineerId, remark, null);
        }
        processService.syncWorkorderState(id, target, startNow ? "internal" : null, engineerId);
        if (startNow) {
            syncDeviceStatus(wo.get("device_id"), "maintenance");
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
        Object engineerId = body != null && body.get("engineerId") != null ? body.get("engineerId")
                : (body != null && body.get("engineer_id") != null ? body.get("engineer_id") : wo.get("assigned_engineer_id"));
        String sub = body != null && body.get("repair_sub_status") != null
                ? String.valueOf(body.get("repair_sub_status")) : "internal";
        String remark = body != null ? str(body.get("remark")) : "开始维修";
        processService.insertProcess(id, ProcessRecord.builder("start_repair")
                .fromStatus(current).toStatus("repairing")
                .fromSubStatus(str(wo.get("repair_sub_status"))).toSubStatus(sub)
                .engineerId(engineerId).operatorId(operatorId()).remark(remark).build());
        processService.syncWorkorderState(id, "repairing", sub, engineerId);
        addEvent(id, "start_repair", current, "repairing", str(wo.get("repair_sub_status")), sub,
                engineerId, null, null, remark, null);
        syncDeviceStatus(wo.get("device_id"), "maintenance");
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
        boolean startNow = body == null || !Boolean.FALSE.equals(body.get("startRepair"));
        Object engineerId = wo.get("assigned_engineer_id");
        if (startNow) {
            processService.insertProcess(id, ProcessRecord.builder("accept")
                    .fromStatus(current).toStatus("accepted")
                    .engineerId(engineerId).operatorId(operatorId()).remark("接单").build());
            processService.insertProcess(id, ProcessRecord.builder("start_repair")
                    .fromStatus("accepted").toStatus("repairing").toSubStatus("internal")
                    .engineerId(engineerId).operatorId(operatorId()).remark("接单并开始维修").build());
            processService.syncWorkorderState(id, "repairing", "internal", null);
            addEvent(id, "accept", current, "accepted", null, null, engineerId, null, null, "接单", null);
            addEvent(id, "start_repair", "accepted", "repairing", null, "internal",
                    engineerId, null, null, "接单并开始维修", null);
            syncDeviceStatus(wo.get("device_id"), "maintenance");
        } else {
            processService.insertProcess(id, ProcessRecord.builder("accept")
                    .fromStatus(current).toStatus("accepted")
                    .engineerId(engineerId).operatorId(operatorId()).remark("接单").build());
            processService.syncWorkorderState(id, "accepted", null, null);
            addEvent(id, "accept", current, "accepted", null, null, engineerId, null, null, "接单", null);
        }
        return Result.ok(loadWorkorder(id));
    }

    @PostMapping("/{id:" + UUID_PATH + "}/transfer")
    @Transactional
    @OperationLog(module = "repair", description = "转派工程师")
    public Result<Map<String, Object>> transfer(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Map<String, Object> wo = requireWo(id);
        String current = str(wo.get("status"));
        if (!Set.of("dispatching", "pending_accept", "accepted", "repairing").contains(current)) {
            throw new BizException(400, "当前状态不可转派: " + current);
        }
        Object toEngineer = body.get("engineerId") != null ? body.get("engineerId") : body.get("engineer_id");
        if (toEngineer == null || String.valueOf(toEngineer).isBlank()) {
            throw new BizException(400, "请选择转派目标工程师");
        }
        boolean keepRepairing = Boolean.TRUE.equals(body.get("keepRepairing")) || Boolean.TRUE.equals(body.get("keep_repairing"));
        String target = keepRepairing && "repairing".equals(current) ? "repairing" : "pending_accept";
        Object fromEngineer = wo.get("assigned_engineer_id");
        String fromSub = str(wo.get("repair_sub_status"));
        String remark = str(body.get("remark"));
        processService.insertProcess(id, ProcessRecord.builder("transfer")
                .fromStatus(current).toStatus(target)
                .fromSubStatus(fromSub).toSubStatus(keepRepairing ? fromSub : null)
                .engineerId(toEngineer).fromEngineerId(fromEngineer).toEngineerId(toEngineer).operatorId(operatorId())
                .remark(remark).build());
        processService.syncWorkorderState(id, target, keepRepairing ? fromSub : null, toEngineer);
        addEvent(id, "transfer", current, target, fromSub,
                keepRepairing ? fromSub : null, toEngineer, fromEngineer, toEngineer, remark, null);
        return Result.ok(loadWorkorder(id));
    }

    @PostMapping("/{id:" + UUID_PATH + "}/sub-status")
    @Transactional
    @OperationLog(module = "repair", description = "更新维修子状态")
    public Result<Map<String, Object>> updateSubStatus(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Map<String, Object> wo = requireWo(id);
        if (!"repairing".equals(str(wo.get("status")))) {
            throw new BizException(400, "仅维修中可更新子状态");
        }
        String sub = str(body.get("repair_sub_status"));
        if (sub.isBlank()) throw new BizException(400, "请指定子状态");
        String fromSub = str(wo.get("repair_sub_status"));
        String repairType = mapRepairType(sub);
        String extraJson = "on_site".equals(sub)
                ? "{\"repair_type\":\"" + repairType + "\",\"arrival_recorded\":true}" : null;
        processService.insertProcess(id, ProcessRecord.builder("sub_status")
                .fromStatus("repairing").toStatus("repairing")
                .fromSubStatus(fromSub).toSubStatus(sub)
                .engineerId(wo.get("assigned_engineer_id")).operatorId(operatorId())
                .remark(str(body.get("remark"))).extraJson(extraJson).build());
        processService.syncWorkorderState(id, "repairing", sub, null);
        addEvent(id, "sub_status_change", "repairing", "repairing", fromSub, sub,
                wo.get("assigned_engineer_id"), null, null, str(body.get("remark")), null);
        return Result.ok(loadWorkorder(id));
    }

    @PostMapping("/{id:" + UUID_PATH + "}/complete")
    @Transactional
    @OperationLog(module = "repair", description = "维修完成")
    public Result<Map<String, Object>> complete(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Map<String, Object> wo = requireWo(id);
        String current = str(wo.get("status"));
        if (!"repairing".equals(current)) {
            throw new BizException(400, "仅维修中可完工: " + current);
        }
        boolean skipVerify = Boolean.TRUE.equals(body.get("skipVerify")) || Boolean.TRUE.equals(body.get("skip_verify"));
        String target = skipVerify ? "closed" : "pending_verify";
        processService.insertProcess(id, ProcessRecord.builder("complete")
                .fromStatus(current).toStatus(target)
                .fromSubStatus(str(wo.get("repair_sub_status")))
                .engineerId(wo.get("assigned_engineer_id")).operatorId(operatorId())
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
                wo.get("assigned_engineer_id"), null, null,
                skipVerify ? "完工直接结案" : "完工提交验收", null);
        if (skipVerify) {
            processService.insertProcess(id, ProcessRecord.builder("close")
                    .fromStatus(target).toStatus("closed").operatorId(operatorId()).remark("跳过验收结案").build());
            addEvent(id, "close", target, "closed", null, null, null, null, null, "跳过验收结案", null);
            syncDeviceStatus(wo.get("device_id"), "normal");
        } else {
            addEvent(id, "submit_verify", current, "pending_verify", null, null, null, null, null, "提交验收", null);
            syncDeviceStatus(wo.get("device_id"), "pending_verify");
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
        } else {
            processService.insertProcess(id, ProcessRecord.builder("verify_fail")
                    .fromStatus("pending_verify").toStatus("repairing").toSubStatus("internal")
                    .engineerId(wo.get("assigned_engineer_id"))
                    .operatorId(verifierId != null ? verifierId : operatorId())
                    .verifyResult(result).verifyComment(str(body.get("verify_comment")))
                    .remark(str(body.get("verify_comment"))).build());
            processService.syncWorkorderState(id, "repairing", "internal", null);
            addEvent(id, "verify_fail", "pending_verify", "repairing", null, "internal",
                    wo.get("assigned_engineer_id"), null, null, str(body.get("verify_comment")), null);
            syncDeviceStatus(wo.get("device_id"), "maintenance");
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
        String remark = body != null ? str(body.get("remark")) : null;
        processService.insertProcess(id, ProcessRecord.builder("suspend")
                .fromStatus("repairing").toStatus("suspended")
                .fromSubStatus(str(wo.get("repair_sub_status")))
                .engineerId(wo.get("assigned_engineer_id")).operatorId(operatorId())
                .remark(remark).build());
        processService.syncWorkorderState(id, "suspended", str(wo.get("repair_sub_status")), null);
        addEvent(id, "suspend", "repairing", "suspended", str(wo.get("repair_sub_status")), null,
                wo.get("assigned_engineer_id"), null, null, remark, null);
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
        String sub = body != null && body.get("repair_sub_status") != null
                ? str(body.get("repair_sub_status")) : "internal";
        String remark = body != null ? str(body.get("remark")) : null;
        processService.insertProcess(id, ProcessRecord.builder("resume")
                .fromStatus("suspended").toStatus("repairing").toSubStatus(sub)
                .engineerId(wo.get("assigned_engineer_id")).operatorId(operatorId())
                .remark(remark).build());
        processService.syncWorkorderState(id, "repairing", sub, null);
        addEvent(id, "resume", "suspended", "repairing", null, sub,
                wo.get("assigned_engineer_id"), null, null, remark, null);
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
                "SELECT device_status FROM medical_device WHERE id = ?::uuid", deviceId);
        if (device.isEmpty()) throw new BizException(400, "设备不存在");
        String ds = str(device.get(0).get("device_status"));
        if (Set.of("maintenance", "pending_verify", "scrap").contains(ds)) {
            throw new BizException(400, "设备当前不可报修: " + ds);
        }
        String sql = """
                SELECT id FROM repair_workorder
                WHERE device_id = ?::uuid AND status IN ('reported','dispatching','pending_accept','accepted','repairing','pending_verify','suspended')
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
                          String fromSub, String toSub, Object engineerId,
                          Object fromEngineer, Object toEngineer, String remark, String extraJson) {
        jdbc.update("""
            INSERT INTO repair_workorder_event
            (id, workorder_id, event_type, from_status, to_status, from_sub_status, to_sub_status,
             operator_id, engineer_id, from_engineer_id, to_engineer_id, remark, extra_json)
            VALUES (?::uuid,?::uuid,?,?,?,?,?,?::uuid,?::uuid,?::uuid,?::uuid,?,CAST(? AS jsonb))
            """,
                UUID.randomUUID(), woId, type, blankToNull(fromStatus), blankToNull(toStatus),
                blankToNull(fromSub), blankToNull(toSub),
                blankToNull(operatorId()), blankToNull(engineerId),
                blankToNull(fromEngineer), blankToNull(toEngineer),
                blankToNull(remark), extraJson);
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
            case "verify_fail" -> "验收不通过";
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
}
