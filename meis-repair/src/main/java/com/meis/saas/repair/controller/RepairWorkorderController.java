package com.meis.saas.repair.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.tenant.TenantContext;
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

    /** 占用设备、不可再报修的工单状态 */
    private static final Set<String> ACTIVE_STATUSES = Set.of(
            "reported", "dispatching", "pending_accept", "accepted",
            "repairing", "pending_verify", "suspended"
    );

    private final JdbcTemplate jdbc;

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

    @GetMapping("/{id:" + UUID_PATH + "}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        return Result.ok(requireWo(id));
    }

    @GetMapping("/{id:" + UUID_PATH + "}/timeline")
    public Result<Map<String, Object>> timeline(@PathVariable UUID id) {
        Map<String, Object> wo = requireWo(id);
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
    @OperationLog(module = "repair", description = "创建报修工单")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        if (body.get("device_id") == null || String.valueOf(body.get("device_id")).isBlank()) {
            throw new BizException(400, "请选择报修设备");
        }
        if (body.get("fault_description") == null || String.valueOf(body.get("fault_description")).isBlank()) {
            throw new BizException(400, "请填写故障描述");
        }
        UUID id = UUID.randomUUID();
        String woNo = "WO" + System.currentTimeMillis();
        jdbc.update("""
            INSERT INTO repair_workorder (id, wo_no, device_id, device_code, device_name, reporter_id, report_dept_id,
                report_method, report_time, fault_description, urgency_level, status)
            VALUES (?::uuid,?,?::uuid,?,?,?::uuid,?::uuid,?,?::timestamptz,?,?,?)
            """,
                id, woNo, body.get("device_id"), body.get("device_code"), body.get("device_name"),
                blankToNull(body.get("reporter_id")), blankToNull(body.get("report_dept_id")),
                body.getOrDefault("report_method", "web"), body.get("report_time"),
                body.get("fault_description"), body.getOrDefault("urgency_level", "normal"), "reported");
        syncDeviceStatus(body.get("device_id"), "maintenance");
        addEvent(id, "created", null, "reported", null, null, null, null, null, "临床报修", null);
        return Result.ok(requireWo(id));
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

        if ("reported".equals(current)) {
            jdbc.update("""
                UPDATE repair_workorder SET dispatch_started_at = COALESCE(dispatch_started_at, NOW()),
                assigned_engineer_id = ?::uuid, assigned_at = NOW(), assigner_id = ?::uuid,
                status = ?, repair_sub_status = CASE WHEN ? = 'repairing' THEN COALESCE(repair_sub_status, 'internal') ELSE repair_sub_status END,
                repair_start_time = CASE WHEN ? = 'repairing' THEN COALESCE(repair_start_time, NOW()) ELSE repair_start_time END,
                response_time = CASE WHEN ? = 'repairing' THEN COALESCE(response_time, NOW()) ELSE response_time END,
                updated_at = NOW() WHERE id = ?::uuid
                """, engineerId, operatorId(), target, target, target, target, id);
            addEvent(id, "dispatch", current, target, null, startNow ? "internal" : null,
                    engineerId, null, engineerId, str(body.get("remark")), null);
            if (startNow) {
                addEvent(id, "start_repair", current, "repairing", null, "internal",
                        engineerId, null, null, "派工并开始维修", null);
            }
        } else {
            jdbc.update("""
                UPDATE repair_workorder SET assigned_engineer_id = ?::uuid, assigned_at = NOW(), assigner_id = ?::uuid,
                status = ?,
                repair_sub_status = CASE WHEN ? = 'repairing' THEN COALESCE(repair_sub_status, 'internal') ELSE NULL END,
                repair_start_time = CASE WHEN ? = 'repairing' THEN COALESCE(repair_start_time, NOW()) ELSE repair_start_time END,
                accepted_at = CASE WHEN ? = 'repairing' THEN COALESCE(accepted_at, NOW()) ELSE accepted_at END,
                updated_at = NOW() WHERE id = ?::uuid
                """, engineerId, operatorId(), target, target, target, target, id);
            String eventType = Objects.equals(fromEngineer, String.valueOf(engineerId)) ? "dispatch" : "transfer";
            addEvent(id, eventType, current, target, str(wo.get("repair_sub_status")),
                    startNow ? "internal" : null, engineerId, fromEngineer, engineerId, str(body.get("remark")), null);
        }
        return Result.ok(requireWo(id));
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
        jdbc.update("""
            UPDATE repair_workorder SET status = 'repairing', repair_sub_status = ?,
            assigned_engineer_id = COALESCE(?::uuid, assigned_engineer_id),
            repair_start_time = COALESCE(repair_start_time, NOW()),
            response_time = COALESCE(response_time, NOW()),
            accepted_at = COALESCE(accepted_at, NOW()),
            updated_at = NOW() WHERE id = ?::uuid
            """, sub, blankToNull(engineerId), id);
        addEvent(id, "start_repair", current, "repairing", str(wo.get("repair_sub_status")), sub,
                engineerId, null, null, body != null ? str(body.get("remark")) : "开始维修", null);
        syncDeviceStatus(wo.get("device_id"), "maintenance");
        return Result.ok(requireWo(id));
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
        if (startNow) {
            jdbc.update("""
                UPDATE repair_workorder SET status = 'repairing', accepted_at = NOW(),
                repair_start_time = COALESCE(repair_start_time, NOW()),
                response_time = COALESCE(response_time, NOW()),
                repair_sub_status = COALESCE(repair_sub_status, 'internal'),
                updated_at = NOW() WHERE id = ?::uuid
                """, id);
            addEvent(id, "accept", current, "accepted", null, null, wo.get("assigned_engineer_id"), null, null, "接单", null);
            addEvent(id, "start_repair", "accepted", "repairing", null, "internal",
                    wo.get("assigned_engineer_id"), null, null, "接单并开始维修", null);
        } else {
            jdbc.update("""
                UPDATE repair_workorder SET status = 'accepted', accepted_at = NOW(),
                response_time = COALESCE(response_time, NOW()), updated_at = NOW() WHERE id = ?::uuid
                """, id);
            addEvent(id, "accept", current, "accepted", null, null, wo.get("assigned_engineer_id"), null, null, "接单", null);
        }
        return Result.ok(requireWo(id));
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
        jdbc.update("""
            UPDATE repair_workorder SET assigned_engineer_id = ?::uuid, assigned_at = NOW(), assigner_id = ?::uuid,
            status = ?, accepted_at = CASE WHEN ? = 'pending_accept' THEN NULL ELSE accepted_at END,
            updated_at = NOW() WHERE id = ?::uuid
            """, toEngineer, operatorId(), target, target, id);
        addEvent(id, "transfer", current, target, str(wo.get("repair_sub_status")),
                str(wo.get("repair_sub_status")), toEngineer, fromEngineer, toEngineer,
                str(body.get("remark")), null);
        return Result.ok(requireWo(id));
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
        jdbc.update("UPDATE repair_workorder SET repair_sub_status = ?, repair_type = COALESCE(?, repair_type), updated_at = NOW() WHERE id = ?::uuid",
                sub, mapRepairType(sub), id);
        if ("on_site".equals(sub) && wo.get("arrival_time") == null) {
            jdbc.update("UPDATE repair_workorder SET arrival_time = NOW() WHERE id = ?::uuid", id);
        }
        addEvent(id, "sub_status_change", "repairing", "repairing", fromSub, sub,
                wo.get("assigned_engineer_id"), null, null, str(body.get("remark")), null);
        return Result.ok(requireWo(id));
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
        jdbc.update("""
            UPDATE repair_workorder SET solution_description=?, parts_cost=?, labor_cost=?, total_cost=?,
            repair_end_time=NOW(), status=?, repair_sub_status=NULL,
            closed_at = CASE WHEN ? = 'closed' THEN NOW() ELSE closed_at END,
            updated_at=NOW() WHERE id=?::uuid
            """,
                body.get("solution_description"), body.getOrDefault("parts_cost", 0),
                body.getOrDefault("labor_cost", 0), body.getOrDefault("total_cost", 0),
                target, target, id);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts = (List<Map<String, Object>>) body.getOrDefault("spareParts", List.of());
        for (Map<String, Object> p : parts) {
            jdbc.update("INSERT INTO spare_part_usage (id, workorder_id, part_id, quantity, unit_price) VALUES (?::uuid,?::uuid,?::uuid,?,?)",
                    UUID.randomUUID(), id, p.get("part_id"), p.get("quantity"), p.get("unit_price"));
            jdbc.update("INSERT INTO spare_part_transaction (spare_part_id, txn_type, quantity, workorder_id) VALUES (?::uuid,'out',?,?::uuid)",
                    p.get("spare_part_id") != null ? p.get("spare_part_id") : p.get("part_id"), p.get("quantity"), id);
        }
        addEvent(id, "complete", current, target, str(wo.get("repair_sub_status")), null,
                wo.get("assigned_engineer_id"), null, null,
                skipVerify ? "完工直接结案" : "完工提交验收", null);
        if (skipVerify) {
            addEvent(id, "close", target, "closed", null, null, null, null, null, "跳过验收结案", null);
            syncDeviceStatus(wo.get("device_id"), "normal");
        } else {
            addEvent(id, "submit_verify", current, "pending_verify", null, null, null, null, null, "提交验收", null);
            syncDeviceStatus(wo.get("device_id"), "pending_verify");
        }
        return Result.ok(requireWo(id));
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
        if (pass) {
            jdbc.update("""
                UPDATE repair_workorder SET verifier_id=?::uuid, verify_time=NOW(), verify_result=?, verify_comment=?,
                satisfaction_rating=?, satisfaction_comment=?, status='verified', updated_at=NOW() WHERE id=?::uuid
                """,
                    blankToNull(body.get("verifier_id")), result, body.get("verify_comment"),
                    body.get("satisfaction_rating"), body.get("satisfaction_comment"), id);
            jdbc.update("UPDATE repair_workorder SET status='closed', closed_at=NOW(), updated_at=NOW() WHERE id=?::uuid", id);
            addEvent(id, "verify_pass", "pending_verify", "verified", null, null, null, null, null, str(body.get("verify_comment")), null);
            addEvent(id, "close", "verified", "closed", null, null, null, null, null, "验收后关闭", null);
            syncDeviceStatus(wo.get("device_id"), "normal");
        } else {
            jdbc.update("""
                UPDATE repair_workorder SET verifier_id=?::uuid, verify_time=NOW(), verify_result=?, verify_comment=?,
                status='repairing', repair_sub_status=COALESCE(repair_sub_status,'internal'), updated_at=NOW() WHERE id=?::uuid
                """,
                    blankToNull(body.get("verifier_id")), result, body.get("verify_comment"), id);
            addEvent(id, "verify_fail", "pending_verify", "repairing", null, "internal",
                    wo.get("assigned_engineer_id"), null, null, str(body.get("verify_comment")), null);
            syncDeviceStatus(wo.get("device_id"), "maintenance");
        }
        return Result.ok(requireWo(id));
    }

    @PostMapping("/{id:" + UUID_PATH + "}/suspend")
    @Transactional
    @OperationLog(module = "repair", description = "挂起工单")
    public Result<Map<String, Object>> suspend(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> wo = requireWo(id);
        if (!"repairing".equals(str(wo.get("status")))) {
            throw new BizException(400, "仅维修中可挂起");
        }
        jdbc.update("UPDATE repair_workorder SET status='suspended', updated_at=NOW() WHERE id=?::uuid", id);
        addEvent(id, "suspend", "repairing", "suspended", str(wo.get("repair_sub_status")), null,
                wo.get("assigned_engineer_id"), null, null, body != null ? str(body.get("remark")) : null, null);
        return Result.ok(requireWo(id));
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
        jdbc.update("UPDATE repair_workorder SET status='repairing', repair_sub_status=?, updated_at=NOW() WHERE id=?::uuid", sub, id);
        addEvent(id, "resume", "suspended", "repairing", null, sub,
                wo.get("assigned_engineer_id"), null, null, body != null ? str(body.get("remark")) : null, null);
        return Result.ok(requireWo(id));
    }

    @PostMapping("/{id:" + UUID_PATH + "}/cancel")
    @Transactional
    @OperationLog(module = "repair", description = "取消工单")
    public Result<Map<String, Object>> cancel(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> wo = requireWo(id);
        String current = str(wo.get("status"));
        if (Set.of("closed", "cancelled", "verified").contains(current)) {
            throw new BizException(400, "当前状态不可取消");
        }
        jdbc.update("UPDATE repair_workorder SET status='cancelled', closed_at=NOW(), repair_sub_status=NULL, updated_at=NOW() WHERE id=?::uuid", id);
        addEvent(id, "cancel", current, "cancelled", str(wo.get("repair_sub_status")), null,
                null, null, null, body != null ? str(body.get("remark")) : "取消工单", null);
        syncDeviceStatus(wo.get("device_id"), "normal");
        return Result.ok(requireWo(id));
    }

    // ---------- helpers ----------

    private Map<String, Object> requireWo(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM repair_workorder WHERE id = ?::uuid", id);
        if (rows.isEmpty()) throw new BizException(404, "workorder not found");
        return rows.get(0);
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
            case "created" -> "报修提交";
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
