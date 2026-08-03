package com.meis.saas.asset.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.page.FilterCsvSupport;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.tenant.TenantContext;
import com.meis.saas.common.workflow.ApprovalInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/asset/scrap")
@RequiredArgsConstructor
public class DeviceScrapController {
    private final JdbcTemplate jdbc;
    private final ApprovalInstanceService approvalService;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String approval_status,
            @RequestParam(required = false) String scrap_type,
            @RequestParam(required = false) String scrap_no,
            @RequestParam(required = false) String device_code,
            @RequestParam(required = false) String device_name) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            where.append(" AND (scrap_no ILIKE ? OR device_code ILIKE ? OR device_name ILIKE ?) ");
            String kw = "%" + query.getKeyword().trim() + "%";
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        if (scrap_no != null && !scrap_no.isBlank()) {
            where.append(" AND scrap_no ILIKE ? ");
            args.add("%" + scrap_no.trim() + "%");
        }
        if (device_code != null && !device_code.isBlank()) {
            where.append(" AND device_code ILIKE ? ");
            args.add("%" + device_code.trim() + "%");
        }
        if (device_name != null && !device_name.isBlank()) {
            where.append(" AND device_name ILIKE ? ");
            args.add("%" + device_name.trim() + "%");
        }
        FilterCsvSupport.appendStrIn(where, args, "status", status);
        FilterCsvSupport.appendStrIn(where, args, "approval_status", approval_status);
        FilterCsvSupport.appendStrIn(where, args, "scrap_type", scrap_type);
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "device_scrap", null));
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM device_scrap" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.limit());
        pageArgs.add(query.offset());
        var rows = jdbc.queryForList(
                "SELECT * FROM device_scrap" + where + " ORDER BY created_at DESC NULLS LAST LIMIT ? OFFSET ?",
                pageArgs.toArray());
        return Result.ok(PageResult.of(rows, total != null ? total : 0, query.getPage(), query.getSize()));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList(
                "SELECT * FROM device_scrap WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "device_scrap", null), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        return Result.ok(rows.get(0));
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "asset", description = "保存报废单")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = body.containsKey("id") ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = !jdbc.queryForList("SELECT 1 FROM device_scrap WHERE id = ?::uuid", id).isEmpty();
        if (exists) {
            assertMutable(id);
            String applicantName = resolveName(body.get("applicant_id"));
            String evaluatorName = resolveName(body.get("evaluator_id"));
            jdbc.update("""
                UPDATE device_scrap SET device_id=?::uuid, device_code=?, device_name=?, scrap_reason=?, scrap_type=?,
                application_date=?, evaluator_id=?::uuid, evaluator_name=COALESCE(?, evaluator_name),
                evaluation_result=?, residual_value=?, disposal_method=?, disposal_destination=?, disposal_proof_url=?,
                disposal_date=?, remark=?, applicant_name=COALESCE(?, applicant_name), updated_at=NOW()
                WHERE id=?::uuid
                """, body.get("device_id"), body.get("device_code"), body.get("device_name"), body.get("scrap_reason"),
                    body.get("scrap_type"), body.get("application_date"), body.get("evaluator_id"), evaluatorName,
                    body.get("evaluation_result"), body.get("residual_value"), body.get("disposal_method"),
                    body.get("disposal_destination"), body.get("disposal_proof_url"),
                    body.get("disposal_date"), body.get("remark"), applicantName, id);
        } else {
            String userId = TenantContext.getUserId();
            Object applicantId = body.get("applicant_id") != null ? body.get("applicant_id") : userId;
            String applicantName = resolveName(applicantId);
            jdbc.update("""
                INSERT INTO device_scrap (id, scrap_no, device_id, device_code, device_name, scrap_reason, scrap_type,
                applicant_id, applicant_name, application_date, status, approval_status) VALUES (?::uuid,?,?::uuid,?,?,?,?,?::uuid,?,?,?,?)
                """, id, body.getOrDefault("scrap_no", "SC" + System.currentTimeMillis()), body.get("device_id"),
                    body.get("device_code"), body.get("device_name"), body.get("scrap_reason"), body.get("scrap_type"),
                    applicantId, applicantName, body.get("application_date"), "draft", "draft");
        }
        return get(id);
    }

    @PostMapping("/{id}/evaluate")
    @OperationLog(module = "asset", description = "报废评估")
    public Result<Map<String, Object>> evaluate(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        assertMutable(id);
        Object evaluatorId = body.get("evaluator_id");
        if (evaluatorId == null || evaluatorId.toString().isBlank()) {
            String userId = TenantContext.getUserId();
            evaluatorId = userId;
        }
        String evaluatorName = resolveName(evaluatorId);
        jdbc.update("""
                UPDATE device_scrap SET evaluator_id=?::uuid, evaluator_name=?, evaluation_result=?, residual_value=?,
                updated_at=NOW() WHERE id=?::uuid
                """, evaluatorId, evaluatorName, body.get("evaluation_result"), body.get("residual_value"), id);
        return get(id);
    }

    @PostMapping("/{id}/submit")
    @OperationLog(module = "asset", description = "提交报废审批")
    public Result<Map<String, Object>> submit(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        var row = load(id);
        String status = String.valueOf(row.get("status"));
        if (!"draft".equals(status) && !"rejected".equals(status)) {
            throw new BizException(400, "仅草稿或已驳回单据可提交审批");
        }
        if (row.get("device_id") == null) throw new BizException(400, "请先选择设备");
        if (row.get("scrap_reason") == null || String.valueOf(row.get("scrap_reason")).isBlank()) {
            throw new BizException(400, "请填写报废原因");
        }
        UUID applicantId = body.get("applicantId") != null
                ? UUID.fromString(body.get("applicantId").toString())
                : (TenantContext.getUserId() != null ? UUID.fromString(TenantContext.getUserId()) : null);
        if (applicantId == null) throw new BizException(400, "缺少申请人");
        String applicantName = SoftDeleteSupport.resolveUserDisplayName(jdbc, applicantId);
        jdbc.update("UPDATE device_scrap SET applicant_id=?::uuid, applicant_name=?, updated_at=NOW() WHERE id=?::uuid",
                applicantId, applicantName, id);
        approvalService.submit("device_scrap", id, row.get("scrap_no").toString(),
                "设备报废 " + row.get("scrap_no"), applicantId, 0);
        return get(id);
    }

    @PostMapping("/{id}/dispose")
    @Transactional
    @OperationLog(module = "asset", description = "报废处置")
    public Result<Map<String, Object>> dispose(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        var row = load(id);
        String status = String.valueOf(row.get("status"));
        if (!"approved".equals(status)) {
            throw new BizException(400, "仅已批准的报废单可处置归档");
        }
        Object method = body.get("disposal_method");
        if (method == null || method.toString().isBlank()) {
            throw new BizException(400, "请填写处置方式");
        }
        if (row.get("device_id") != null) {
            jdbc.update("UPDATE medical_device SET device_status = 'scrap', updated_at = NOW() WHERE id = ?::uuid",
                    row.get("device_id"));
        }
        jdbc.update("""
                UPDATE device_scrap SET disposal_method=?, disposal_destination=?, disposal_proof_url=?,
                disposal_date=?, status='disposed', updated_at=NOW() WHERE id=?::uuid
                """, method, body.get("disposal_destination"), body.get("disposal_proof_url"),
                body.get("disposal_date"), id);
        return get(id);
    }

    private Map<String, Object> load(UUID id) {
        var rows = jdbc.queryForList(
                "SELECT * FROM device_scrap WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "device_scrap", null), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        return rows.get(0);
    }

    private void assertMutable(UUID id) {
        var row = load(id);
        String status = String.valueOf(row.get("status"));
        if ("approved".equals(status) || "disposed".equals(status) || "pending".equals(status)) {
            throw new BizException(400, "审批中或已完结的报废单不可修改");
        }
    }

    private String resolveName(Object userId) {
        if (userId == null || userId.toString().isBlank()) return null;
        try {
            return SoftDeleteSupport.resolveUserDisplayName(jdbc, UUID.fromString(userId.toString()));
        } catch (Exception e) {
            return null;
        }
    }
}
