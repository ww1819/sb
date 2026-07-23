package com.meis.saas.qc.controller;

import com.meis.saas.common.audit.DocChangeLogService;
import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.ops.OpsAutoRepairSupport;
import com.meis.saas.common.ops.OpsClientChannel;
import com.meis.saas.common.ops.OpsExecutionItemSupport;
import com.meis.saas.common.ops.OpsPhotosSupport;
import com.meis.saas.common.ops.OpsPlanExecutionSupport;
import com.meis.saas.common.page.FilterCsvSupport;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.tenant.TenantContext;
import com.meis.saas.qc.inspect.InspectionExecutionGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/inspect/execution")
@RequiredArgsConstructor
public class InspectionExecutionController {
    private final JdbcTemplate jdbc;
    private final InspectionExecutionGenerator generator;
    private final DocChangeLogService docLog;

    private static final Set<String> EDITABLE = Set.of("draft", "in_progress", "pending");

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(
            PageQuery query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String source_type,
            @RequestParam(required = false) String execution_kind,
            @RequestParam(required = false) String create_channel,
            @RequestParam(required = false) String planned_dateFrom,
            @RequestParam(required = false) String planned_dateTo) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "inspection_execution", "e"));
        List<Object> args = new ArrayList<>();
        FilterCsvSupport.appendStrIn(where, args, "e.status", status);
        FilterCsvSupport.appendStrIn(where, args, "e.source_type", source_type);
        FilterCsvSupport.appendStrIn(where, args, "e.execution_kind", execution_kind);
        FilterCsvSupport.appendStrIn(where, args, "e.create_channel", create_channel);
        if (planned_dateFrom != null && !planned_dateFrom.isBlank()) {
            where.append(" AND e.planned_date >= ?::date ");
            args.add(planned_dateFrom.trim());
        }
        if (planned_dateTo != null && !planned_dateTo.isBlank()) {
            where.append(" AND e.planned_date <= ?::date ");
            args.add(planned_dateTo.trim());
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String kw = "%" + query.getKeyword().trim() + "%";
            where.append("""
                     AND (e.execution_no ILIKE ? OR e.plan_no ILIKE ? OR e.remark ILIKE ?
                          OR COALESCE(e.template_name,'') ILIKE ? OR COALESCE(e.executor_name,'') ILIKE ?
                          OR COALESCE(e.created_by_name,'') ILIKE ?)
                    """);
            args.add(kw);
            args.add(kw);
            args.add(kw);
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inspection_execution e" + where, Long.class, args.toArray());
        int offset = (query.getPage() - 1) * query.getSize();
        args.add(query.getSize());
        args.add(offset);
        var rows = jdbc.queryForList("""
                SELECT e.*, COALESCE(e.template_name, t.template_name) AS template_name,
                       it.type_name AS inspection_type_name,
                       EXISTS (
                         SELECT 1 FROM inspection_execution_item ei
                         WHERE ei.execution_id = e.id AND COALESCE(ei.is_deleted,0)=0
                           AND (ei.status IN ('completed','confirmed')
                                OR ei.end_time IS NOT NULL
                                OR ei.executor_id IS NOT NULL
                                OR (ei.overall_result IS NOT NULL AND TRIM(ei.overall_result) <> ''))
                       ) AS has_execution_record
                FROM inspection_execution e
                LEFT JOIN inspection_template t ON t.id = e.template_id
                LEFT JOIN inspection_type it ON it.id = e.inspection_type_id
                """ + where + " ORDER BY e.created_at DESC LIMIT ? OFFSET ?", args.toArray());
        return Result.ok(new PageResult<>(rows, total, query.getPage(), query.getSize()));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList("""
                SELECT e.*, COALESCE(e.template_name, t.template_name) AS template_name,
                       it.type_name AS inspection_type_name
                FROM inspection_execution e
                LEFT JOIN inspection_template t ON t.id = e.template_id
                LEFT JOIN inspection_type it ON it.id = e.inspection_type_id
                WHERE e.id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_execution", "e"), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        Map<String, Object> result = new LinkedHashMap<>(rows.get(0));
        var items = jdbc.queryForList("""
                SELECT ei.*, COALESCE(ei.dept_name, d.dept_name) AS dept_name
                FROM inspection_execution_item ei
                LEFT JOIN department d ON d.id = ei.dept_id
                WHERE ei.execution_id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_execution_item", "ei")
                + " ORDER BY ei.created_at", id);
        for (Map<String, Object> item : items) {
            UUID itemId = (UUID) item.get("id");
            item.put("results", jdbc.queryForList(
                    "SELECT * FROM inspection_execution_result WHERE execution_item_id = ?::uuid "
                            + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_execution_result", null)
                            + " ORDER BY sort_order NULLS LAST, created_at",
                    itemId));
        }
        result.put("items", items);
        return Result.ok(result);
    }

    @PostMapping("/ad-hoc")
    @Transactional
    @OperationLog(module = "inspect", description = "无计划直开巡检执行单")
    public Result<Map<String, Object>> adHoc(@RequestBody Map<String, Object> body) {
        var created = generator.createAdHoc(body);
        return get(UUID.fromString(created.get("id").toString()));
    }

    @PutMapping("/{id}")
    @Transactional
    @OperationLog(module = "inspect", description = "更新巡检执行单头表")
    public Result<Map<String, Object>> updateHeader(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        assertEditable(id);
        var old = jdbc.queryForList(
                "SELECT * FROM inspection_execution WHERE id=?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_execution", null), id);
        if (old.isEmpty()) throw new BizException(404, "not found");
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        String client = clientOf(body);
        String docNo = execNo(id);
        track(sets, args, old.get(0), body, "remark", id, docNo, "header", id, client);
        if (body.containsKey("execute_start_time")) {
            sets.add("execute_start_time = CAST(? AS timestamptz)");
            args.add(body.get("execute_start_time"));
            docLog.fieldChange("inspect", "execution", id, docNo, "header", id,
                    "execute_start_time", old.get(0).get("execute_start_time"), body.get("execute_start_time"), client);
        }
        if (body.containsKey("execute_end_time")) {
            sets.add("execute_end_time = CAST(? AS timestamptz)");
            args.add(body.get("execute_end_time"));
            docLog.fieldChange("inspect", "execution", id, docNo, "header", id,
                    "execute_end_time", old.get(0).get("execute_end_time"), body.get("execute_end_time"), client);
        }
        if (sets.isEmpty()) return get(id);
        sets.add("updated_at=NOW()");
        args.add(id);
        jdbc.update("UPDATE inspection_execution SET " + String.join(", ", sets) + " WHERE id=?::uuid", args.toArray());
        return get(id);
    }

    @GetMapping("/by-device/{deviceId}")
    public Result<List<Map<String, Object>>> byDevice(@PathVariable UUID deviceId,
            @RequestParam(required = false, defaultValue = "true") boolean openOnly) {
        String statusFilter = openOnly
                ? " AND e.status IN ('draft','in_progress','pending') "
                : "";
        return Result.ok(jdbc.queryForList("""
                SELECT ei.*, e.execution_no, e.status AS execution_status, e.source_type, e.plan_no
                FROM inspection_execution_item ei
                INNER JOIN inspection_execution e ON e.id = ei.execution_id
                WHERE ei.device_id = ?::uuid
                """ + statusFilter
                + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_execution_item", "ei")
                + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_execution", "e")
                + " ORDER BY e.created_at DESC", deviceId));
    }

    @PostMapping("/{id}/start")
    @Transactional
    @OperationLog(module = "inspect", description = "开始巡检执行")
    public Result<Map<String, Object>> start(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        assertEditable(id);
        String userId = body != null && body.get("executor_id") != null
                ? body.get("executor_id").toString() : TenantContext.getUserId();
        String name = SoftDeleteSupport.resolveUserDisplayName(jdbc, userId);
        jdbc.update("""
                UPDATE inspection_execution SET status='in_progress', executor_id=?::uuid, executor_name=?,
                execute_start_time=COALESCE(execute_start_time, NOW()), updated_at=NOW()
                WHERE id=?::uuid AND status IN ('draft','pending','in_progress')
                """, userId, name, id);
        jdbc.update("""
                UPDATE inspection_execution_item SET status='in_progress',
                executor_id=COALESCE(executor_id, ?::uuid), executor_name=COALESCE(executor_name, ?),
                start_time=COALESCE(start_time, NOW()), updated_at=NOW()
                WHERE execution_id=?::uuid AND status='pending'
                """, userId, name, id);
        docLog.event("inspect", "execution", id, execNo(id), "start", clientOf(body), null);
        return get(id);
    }

    @PatchMapping("/item/{itemId}")
    @Transactional
    @OperationLog(module = "inspect", description = "更新巡检执行明细")
    public Result<Map<String, Object>> patchItem(@PathVariable UUID itemId, @RequestBody Map<String, Object> body) {
        var rows = jdbc.queryForList(
                "SELECT * FROM inspection_execution_item WHERE id=?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_execution_item", null), itemId);
        if (rows.isEmpty()) throw new BizException(404, "明细不存在");
        Map<String, Object> old = rows.get(0);
        UUID execId = (UUID) old.get("execution_id");
        assertEditable(execId);
        if ("confirmed".equals(String.valueOf(old.get("status")))) {
            throw new BizException(400, "明细已确认，不可再修改");
        }
        int expected = body.containsKey("row_version")
                ? ((Number) body.get("row_version")).intValue()
                : ((Number) old.getOrDefault("row_version", 1)).intValue();
        String client = clientOf(body);
        String execNo = Objects.toString(old.get("execution_no"), execNo(execId));

        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        track(sets, args, old, body, "overall_result", execId, execNo, "item", itemId, client);
        track(sets, args, old, body, "remark", execId, execNo, "item", itemId, client);
        track(sets, args, old, body, "issues_found", execId, execNo, "item", itemId, client);
        track(sets, args, old, body, "status", execId, execNo, "item", itemId, client);
        track(sets, args, old, body, "signature_url", execId, execNo, "item", itemId, client);
        if (body.containsKey("photos")) {
            sets.add("photos = CAST(? AS jsonb)");
            args.add(OpsPhotosSupport.toJson(body.get("photos")));
            docLog.fieldChange("inspect", "execution", execId, execNo, "item", itemId,
                    "photos", old.get("photos"), body.get("photos"), client);
        }
        if (body.containsKey("executor_id")) {
            sets.add("executor_id = ?::uuid");
            args.add(body.get("executor_id"));
            String nm = SoftDeleteSupport.resolveUserDisplayName(jdbc, body.get("executor_id"));
            sets.add("executor_name = ?");
            args.add(nm);
            docLog.fieldChange("inspect", "execution", execId, execNo, "item", itemId,
                    "executor_id", old.get("executor_id"), body.get("executor_id"), client);
        }
        if (body.containsKey("start_time")) {
            sets.add("start_time = ?");
            args.add(body.get("start_time"));
        }
        if (body.containsKey("end_time")) {
            sets.add("end_time = ?");
            args.add(body.get("end_time"));
        }
        if (sets.isEmpty()) throw new BizException(400, "无更新字段");
        sets.add("row_version = COALESCE(row_version,1) + 1");
        sets.add("updated_at = NOW()");
        args.add(itemId);
        args.add(expected);
        int n = jdbc.update("UPDATE inspection_execution_item SET " + String.join(", ", sets)
                + " WHERE id=?::uuid AND COALESCE(row_version,1)=?", args.toArray());
        if (n == 0) throw new BizException(409, "明细已被他人修改，请刷新后重试");
        return get(execId);
    }

    @PatchMapping("/result/{resultId}")
    @Transactional
    @OperationLog(module = "inspect", description = "更新巡检执行内容结果")
    public Result<Map<String, Object>> patchResult(@PathVariable UUID resultId, @RequestBody Map<String, Object> body) {
        var rows = jdbc.queryForList(
                "SELECT r.*, ei.execution_id, ei.execution_no FROM inspection_execution_result r "
                        + "INNER JOIN inspection_execution_item ei ON ei.id = r.execution_item_id "
                        + "WHERE r.id=?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_execution_result", "r"), resultId);
        if (rows.isEmpty()) throw new BizException(404, "内容不存在");
        Map<String, Object> old = rows.get(0);
        UUID execId = (UUID) old.get("execution_id");
        assertEditable(execId);
        int expected = body.containsKey("row_version")
                ? ((Number) body.get("row_version")).intValue()
                : ((Number) old.getOrDefault("row_version", 1)).intValue();
        String client = clientOf(body);
        String execNo = Objects.toString(old.get("execution_no"), execNo(execId));

        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        track(sets, args, old, body, "result_value", execId, execNo, "result", resultId, client);
        track(sets, args, old, body, "result_status", execId, execNo, "result", resultId, client);
        track(sets, args, old, body, "remark", execId, execNo, "result", resultId, client);
        if (body.containsKey("photos")) {
            sets.add("photos = CAST(? AS jsonb)");
            args.add(OpsPhotosSupport.toJson(body.get("photos")));
            docLog.fieldChange("inspect", "execution", execId, execNo, "result", resultId,
                    "photos", old.get("photos"), body.get("photos"), client);
        }
        if (sets.isEmpty()) throw new BizException(400, "无更新字段");
        sets.add("row_version = COALESCE(row_version,1) + 1");
        sets.add("updated_at = NOW()");
        args.add(resultId);
        args.add(expected);
        int n = jdbc.update("UPDATE inspection_execution_result SET " + String.join(", ", sets)
                + " WHERE id=?::uuid AND COALESCE(row_version,1)=?", args.toArray());
        if (n == 0) throw new BizException(409, "内容已被他人修改，请刷新后重试");
        return get(execId);
    }

    @PostMapping("/item/{itemId}/complete")
    @Transactional
    @OperationLog(module = "inspect", description = "完成巡检设备项")
    public Result<Map<String, Object>> completeItem(@PathVariable UUID itemId, @RequestBody Map<String, Object> body) {
        var itemRows = jdbc.queryForList(
                "SELECT * FROM inspection_execution_item WHERE id=?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_execution_item", null), itemId);
        if (itemRows.isEmpty()) throw new BizException(404, "明细不存在");
        Map<String, Object> item = itemRows.get(0);
        UUID execId = (UUID) item.get("execution_id");
        assertEditable(execId);
        if ("confirmed".equals(String.valueOf(item.get("status")))) {
            throw new BizException(400, "明细已确认，不可再修改");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) body.getOrDefault("results", List.of());
        for (Map<String, Object> r : results) {
            if (r.get("id") != null) {
                if (r.containsKey("photos")) {
                    jdbc.update("""
                        UPDATE inspection_execution_result SET result_value=?, result_status=?, remark=?,
                        photos=CAST(? AS jsonb), row_version=COALESCE(row_version,1)+1, updated_at=NOW()
                        WHERE id=?::uuid
                        """, r.get("result_value"), r.getOrDefault("result_status", "pass"), r.get("remark"),
                            OpsPhotosSupport.toJson(r.get("photos")), r.get("id"));
                } else {
                    jdbc.update("""
                        UPDATE inspection_execution_result SET result_value=?, result_status=?, remark=?,
                        row_version=COALESCE(row_version,1)+1, updated_at=NOW()
                        WHERE id=?::uuid
                        """, r.get("result_value"), r.getOrDefault("result_status", "pass"), r.get("remark"), r.get("id"));
                }
            }
        }
        String channel = OpsClientChannel.of(body);
        String userId = TenantContext.getUserId();
        String name = SoftDeleteSupport.resolveUserDisplayName(jdbc, userId);
        if (body.containsKey("photos") || body.containsKey("signature_url")) {
            jdbc.update("""
                    UPDATE inspection_execution_item SET status='completed', overall_result=?, remark=?,
                    photos=COALESCE(CAST(? AS jsonb), photos), signature_url=COALESCE(?, signature_url),
                    execution_channel=?, end_time=COALESCE(end_time, NOW()),
                    executor_id=COALESCE(executor_id, ?::uuid), executor_name=COALESCE(executor_name, ?),
                    row_version=COALESCE(row_version,1)+1, updated_at=NOW()
                    WHERE id=?::uuid
                    """, body.getOrDefault("overall_result", "pass"), body.get("remark"),
                    body.containsKey("photos") ? OpsPhotosSupport.toJson(body.get("photos")) : null,
                    body.get("signature_url"), channel, userId, name, itemId);
        } else {
            jdbc.update("""
                    UPDATE inspection_execution_item SET status='completed', overall_result=?, remark=?,
                    execution_channel=?, end_time=COALESCE(end_time, NOW()),
                    executor_id=COALESCE(executor_id, ?::uuid), executor_name=COALESCE(executor_name, ?),
                    row_version=COALESCE(row_version,1)+1, updated_at=NOW()
                    WHERE id=?::uuid
                    """, body.getOrDefault("overall_result", "pass"), body.get("remark"), channel, userId, name, itemId);
        }

        docLog.event("inspect", "execution", execId, execNo(execId), "complete_item", clientOf(body), itemId.toString());
        var failed = jdbc.queryForList("""
                SELECT item_name, result_value, remark FROM inspection_execution_result
                WHERE execution_item_id=?::uuid AND result_status='fail'
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_execution_result", null), itemId);
        if (!failed.isEmpty()) {
            OpsAutoRepairSupport.tryCreateFromFail(jdbc, docLog, "inspect", execId, execNo(execId),
                    itemId, item.get("device_id"), failed);
        }
        return get(execId);
    }

    @PostMapping("/item/{itemId}/confirm")
    @Transactional
    @OperationLog(module = "inspect", description = "确认巡检执行明细")
    public Result<Map<String, Object>> confirmItem(@PathVariable UUID itemId, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> item = OpsExecutionItemSupport.loadItem(jdbc, "inspection_execution_item", itemId);
        UUID execId = (UUID) item.get("execution_id");
        OpsExecutionItemSupport.confirmItem(jdbc, docLog, "inspect",
                "inspection_execution", "inspection_execution_item",
                itemId, body != null ? body : Map.of(), execNo(execId));
        return get(execId);
    }

    @DeleteMapping("/item/{itemId}")
    @Transactional
    @OperationLog(module = "inspect", description = "删除巡检执行明细")
    public Result<Map<String, Object>> deleteItem(
            @PathVariable UUID itemId,
            @RequestParam(required = false) String client,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> item = OpsExecutionItemSupport.loadItem(jdbc, "inspection_execution_item", itemId);
        UUID execId = (UUID) item.get("execution_id");
        Map<String, Object> payload = body != null ? new LinkedHashMap<>(body) : new LinkedHashMap<>();
        if (client != null && !payload.containsKey("client")) payload.put("client", client);
        OpsExecutionItemSupport.deleteItem(jdbc, docLog, "inspect",
                "inspection_execution", "inspection_execution_item",
                itemId, payload, execNo(execId));
        return get(execId);
    }

    @PostMapping("/{id}/submit")
    @Transactional
    @OperationLog(module = "inspect", description = "提交巡检执行单")
    public Result<Map<String, Object>> submit(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        String st = statusOf(id);
        if (!EDITABLE.contains(st) && !"in_progress".equals(st)) {
            throw new BizException(400, "当前状态不可提交");
        }
        assertAllItemsCompleted(id);
        String channel = OpsClientChannel.of(body);
        String userId = TenantContext.getUserId();
        String name = SoftDeleteSupport.resolveUserDisplayName(jdbc, userId);
        jdbc.update("""
                UPDATE inspection_execution_item SET status='confirmed', confirm_channel=?,
                confirmed_by=COALESCE(confirmed_by, ?::uuid), confirmed_by_name=COALESCE(confirmed_by_name, ?),
                confirmed_at=COALESCE(confirmed_at, NOW()),
                row_version=COALESCE(row_version,1)+1, updated_at=NOW()
                WHERE execution_id=?::uuid AND COALESCE(is_deleted,0)=0 AND status='completed'
                """, channel, userId, name, id);
        jdbc.update("""
                UPDATE inspection_execution SET status='submitted', submitter_id=?::uuid, submitter_name=?,
                submitted_at=NOW(), execute_end_time=COALESCE(execute_end_time, NOW()),
                submit_channel=?, updated_at=NOW()
                WHERE id=?::uuid
                """, userId, name, channel, id);
        docLog.event("inspect", "execution", id, execNo(id), "submit", clientOf(body), null);
        return get(id);
    }

    @PostMapping("/{id}/withdraw")
    @Transactional
    @OperationLog(module = "inspect", description = "撤回巡检执行单")
    public Result<Map<String, Object>> withdraw(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        if (!"submitted".equals(statusOf(id))) {
            throw new BizException(400, "仅已提交可撤回");
        }
        jdbc.update("""
                UPDATE inspection_execution_item SET status='completed', confirm_channel=NULL,
                confirmed_by=NULL, confirmed_by_name=NULL, confirmed_at=NULL,
                row_version=COALESCE(row_version,1)+1, updated_at=NOW()
                WHERE execution_id=?::uuid AND COALESCE(is_deleted,0)=0 AND status='confirmed'
                """, id);
        jdbc.update("""
                UPDATE inspection_execution SET status='in_progress', submitter_id=NULL, submitter_name=NULL,
                submitted_at=NULL, submit_channel=NULL, updated_at=NOW() WHERE id=?::uuid
                """, id);
        docLog.event("inspect", "execution", id, execNo(id), "withdraw", clientOf(body), null);
        return get(id);
    }

    @PostMapping("/{id}/audit")
    @Transactional
    @OperationLog(module = "inspect", description = "审核巡检执行单")
    public Result<Map<String, Object>> audit(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String st = statusOf(id);
        if ("audited".equals(st) || "cancelled".equals(st)) {
            throw new BizException(400, "当前状态不可审核");
        }
        assertAllItemsConfirmed(id);
        String action = String.valueOf(body.getOrDefault("action", "approve"));
        if ("reject".equals(action)) {
            throw new BizException(400, "执行单不支持驳回");
        }
        String userId = TenantContext.getUserId();
        String name = SoftDeleteSupport.resolveUserDisplayName(jdbc, userId);
        String channel = OpsClientChannel.of(body);
        jdbc.update("""
                UPDATE inspection_execution SET status='audited', auditor_id=?::uuid, auditor_name=?,
                audited_at=NOW(), audit_comment=?, audit_channel=?, updated_at=NOW() WHERE id=?::uuid
                """, userId, name, body.get("audit_comment"), channel, id);
        updatePlansAfterAudit(id);
        docLog.event("inspect", "execution", id, execNo(id), "audit_approve", clientOf(body), null);
        return get(id);
    }

    @DeleteMapping("/{id}")
    @Transactional
    @OperationLog(module = "inspect", description = "删除巡检执行单")
    public Result<Void> deleteExecution(
            @PathVariable UUID id,
            @RequestParam(required = false) String client,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> payload = body != null ? new LinkedHashMap<>(body) : new LinkedHashMap<>();
        if (client != null && !payload.containsKey("client")) payload.put("client", client);
        OpsExecutionItemSupport.deleteExecution(jdbc, docLog, "inspect",
                "inspection_execution", "inspection_execution_item",
                id, payload, execNo(id));
        return Result.ok();
    }

    @GetMapping("/{id}/change-logs")
    public Result<List<Map<String, Object>>> changeLogs(@PathVariable UUID id) {
        return Result.ok(jdbc.queryForList("""
                SELECT * FROM sys_doc_change_log
                WHERE module='inspect' AND doc_type='execution' AND doc_id=?::uuid
                ORDER BY created_at DESC
                """, id));
    }

    private void updatePlansAfterAudit(UUID execId) {
        OpsPlanExecutionSupport.updatePlansAfterAudit(
                jdbc, "inspection_execution", "inspection_execution_item",
                "inspection_plan", "inspection_plan_item", "last_inspected_at", execId);
    }

    private void assertEditable(UUID execId) {
        String st = statusOf(execId);
        if ("submitted".equals(st) || "audited".equals(st) || "cancelled".equals(st)) {
            throw new BizException(400, "已提交/已审核不可修改，请先撤回（若允许）");
        }
    }

    private String statusOf(UUID id) {
        var rows = jdbc.queryForList(
                "SELECT status FROM inspection_execution WHERE id=?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "inspection_execution", null), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        return Objects.toString(rows.get(0).get("status"), "");
    }

    private String execNo(UUID id) {
        var rows = jdbc.queryForList("SELECT execution_no FROM inspection_execution WHERE id=?::uuid", id);
        return rows.isEmpty() ? null : Objects.toString(rows.get(0).get("execution_no"), null);
    }

    private void track(List<String> sets, List<Object> args, Map<String, Object> old,
                       Map<String, Object> body, String field, UUID docId, String docNo,
                       String entityType, UUID entityId, String client) {
        if (!body.containsKey(field)) return;
        sets.add(field + " = ?");
        args.add(body.get(field));
        docLog.fieldChange("inspect", "execution", docId, docNo, entityType, entityId,
                field, old.get(field), body.get(field), client);
    }

    private static String clientOf(Map<String, Object> body) {
        return OpsClientChannel.of(body);
    }

    private void assertAllItemsCompleted(UUID execId) {
        Integer pending = jdbc.queryForObject("""
                SELECT COUNT(1)::int FROM inspection_execution_item
                WHERE execution_id=?::uuid AND COALESCE(is_deleted,0)=0 AND status <> 'completed'
                """, Integer.class, execId);
        if (pending != null && pending > 0) {
            throw new BizException(400, "存在未完成明细，不可提交");
        }
        Integer total = jdbc.queryForObject("""
                SELECT COUNT(1)::int FROM inspection_execution_item
                WHERE execution_id=?::uuid AND COALESCE(is_deleted,0)=0
                """, Integer.class, execId);
        if (total == null || total == 0) {
            throw new BizException(400, "无设备明细，不可提交");
        }
    }

    private void assertAllItemsConfirmed(UUID execId) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(1)::int FROM inspection_execution_item
                WHERE execution_id=?::uuid AND COALESCE(is_deleted,0)=0 AND status <> 'confirmed'
                """, Integer.class, execId);
        if (n != null && n > 0) {
            throw new BizException(400, "存在未确认明细，不可审核");
        }
    }
}
