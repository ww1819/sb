package com.meis.saas.common.ops;

import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.*;

/**
 * OPS.16.16：运维计划「纳入设备」申请 / Web 确认。
 * module = maintain | inspect | pm
 */
public final class OpsPlanIncludeSupport {
    private OpsPlanIncludeSupport() {}

    public record ModuleTables(String planTable, String itemTable, String planNoCol, String lastDoneCacheCol) {}

    public static ModuleTables tablesOf(String module) {
        return switch (normalizeModule(module)) {
            case "maintain" -> new ModuleTables("maintenance_plan", "maintenance_plan_item", "plan_no", "last_maintained_at");
            case "inspect" -> new ModuleTables("inspection_plan", "inspection_plan_item", "plan_no", null);
            case "pm" -> new ModuleTables("pm_plan", "pm_plan_item", "plan_no", null);
            default -> throw new BizException(400, "未知运维模块: " + module);
        };
    }

    public static String normalizeModule(String module) {
        if (module == null || module.isBlank()) throw new BizException(400, "module 必填");
        String m = module.trim().toLowerCase(Locale.ROOT);
        if ("maintain".equals(m) || "inspect".equals(m) || "pm".equals(m)) return m;
        throw new BizException(400, "未知运维模块: " + module);
    }

    /** 发起纳入申请（Web/App/MP） */
    public static Map<String, Object> create(
            JdbcTemplate jdbc, String module, Map<String, Object> body) {
        String mod = normalizeModule(module);
        ModuleTables t = tablesOf(mod);
        Object planIdRaw = body.get("plan_id");
        Object deviceIdRaw = body.get("device_id");
        if (planIdRaw == null || planIdRaw.toString().isBlank()) throw new BizException(400, "请选择目标计划");
        if (deviceIdRaw == null || deviceIdRaw.toString().isBlank()) throw new BizException(400, "请选择设备");
        UUID planId = UUID.fromString(planIdRaw.toString());
        UUID deviceId = UUID.fromString(deviceIdRaw.toString());

        var planRows = jdbc.queryForList(
                "SELECT * FROM " + t.planTable() + " WHERE id=?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, t.planTable(), null), planId);
        if (planRows.isEmpty()) throw new BizException(404, "计划不存在");
        Map<String, Object> plan = planRows.get(0);
        if (!"approved".equals(Objects.toString(plan.get("approval_status"), ""))) {
            throw new BizException(400, "仅已审核计划可申请纳入");
        }

        Integer existingItem = jdbc.queryForObject("""
                SELECT COUNT(1)::int FROM %s
                WHERE plan_id=?::uuid AND device_id=?::uuid AND COALESCE(is_deleted,0)=0
                """.formatted(t.itemTable()), Integer.class, planId, deviceId);
        if (existingItem != null && existingItem > 0) {
            throw new BizException(400, "设备已在该计划明细中");
        }

        Integer pending = jdbc.queryForObject("""
                SELECT COUNT(1)::int FROM ops_plan_include_request
                WHERE module=? AND plan_id=?::uuid AND device_id=?::uuid
                  AND status='pending' AND COALESCE(is_deleted,0)=0
                """, Integer.class, mod, planId, deviceId);
        if (pending != null && pending > 0) {
            throw new BizException(400, "该设备对该计划已有待确认申请");
        }

        var deviceRows = jdbc.queryForList(
                "SELECT id, device_code, device_name, dept_id FROM medical_device WHERE id=?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", null), deviceId);
        if (deviceRows.isEmpty()) throw new BizException(404, "设备不存在");
        Map<String, Object> device = deviceRows.get(0);
        String deviceCode = firstNonBlank(body.get("device_code"), device.get("device_code"));
        String deviceName = firstNonBlank(body.get("device_name"), device.get("device_name"));
        Object deptId = body.get("dept_id") != null ? body.get("dept_id") : device.get("dept_id");
        String deptName = resolveDeptName(jdbc, deptId, body.get("dept_name"));

        String planNo = Objects.toString(
                plan.get("plan_no") != null ? plan.get("plan_no") : plan.get("plan_code"), null);
        String userId = TenantContext.getUserId();
        String userName = SoftDeleteSupport.resolveUserDisplayName(jdbc, userId);
        String channel = OpsClientChannel.of(body);
        UUID id = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO ops_plan_include_request (
                  id, module, plan_id, plan_no, device_id, device_code, device_name, dept_id, dept_name,
                  status, remark, create_channel, applicant_id, applicant_name,
                  created_by, created_by_name, updated_by, updated_by_name)
                VALUES (?::uuid,?,?::uuid,?,?::uuid,?,?,?::uuid,?,
                  'pending',?,?,?::uuid,?,
                  ?::uuid,?,?::uuid,?)
                """, id, mod, planId, planNo, deviceId, deviceCode, deviceName, deptId, deptName,
                body.get("remark"), channel, userId, userName,
                userId, userName, userId, userName);

        return load(jdbc, id);
    }

    public static List<Map<String, Object>> listByPlan(JdbcTemplate jdbc, String module, UUID planId) {
        String mod = normalizeModule(module);
        return jdbc.queryForList("""
                SELECT * FROM ops_plan_include_request
                WHERE module=? AND plan_id=?::uuid AND COALESCE(is_deleted,0)=0
                ORDER BY CASE status WHEN 'pending' THEN 0 WHEN 'rejected' THEN 1 ELSE 2 END,
                         created_at DESC
                """, mod, planId);
    }

    /** 可选已审核计划（发起纳入时挑选） */
    public static List<Map<String, Object>> listApprovedPlans(JdbcTemplate jdbc, String module, String keyword) {
        ModuleTables t = tablesOf(module);
        StringBuilder sql = new StringBuilder("""
                SELECT id, plan_no, plan_name, template_id, template_name, approval_status, status,
                       dept_id, cycle_days, next_due_date
                FROM %s
                WHERE approval_status='approved' AND COALESCE(is_deleted,0)=0
                """.formatted(t.planTable()));
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            String kw = "%" + keyword.trim() + "%";
            sql.append(" AND (plan_no ILIKE ? OR plan_name ILIKE ? OR COALESCE(template_name,'') ILIKE ?) ");
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        sql.append(" ORDER BY created_at DESC LIMIT 100");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    public static Map<String, Object> approve(JdbcTemplate jdbc, UUID requestId, Map<String, Object> body) {
        Map<String, Object> req = load(jdbc, requestId);
        if (!"pending".equals(Objects.toString(req.get("status"), ""))) {
            throw new BizException(400, "仅待确认申请可通过");
        }
        String mod = Objects.toString(req.get("module"), "");
        ModuleTables t = tablesOf(mod);
        UUID planId = UUID.fromString(req.get("plan_id").toString());
        UUID deviceId = UUID.fromString(req.get("device_id").toString());

        Integer existingItem = jdbc.queryForObject("""
                SELECT COUNT(1)::int FROM %s
                WHERE plan_id=?::uuid AND device_id=?::uuid AND COALESCE(is_deleted,0)=0
                """.formatted(t.itemTable()), Integer.class, planId, deviceId);
        if (existingItem != null && existingItem > 0) {
            markRejected(jdbc, requestId, "设备已在计划中，自动关闭申请");
            throw new BizException(400, "设备已在该计划明细中");
        }

        var planRows = jdbc.queryForList(
                "SELECT * FROM " + t.planTable() + " WHERE id=?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, t.planTable(), null), planId);
        if (planRows.isEmpty()) throw new BizException(404, "计划不存在");
        Map<String, Object> plan = planRows.get(0);
        if (!"approved".equals(Objects.toString(plan.get("approval_status"), ""))) {
            throw new BizException(400, "计划须为已审核状态");
        }

        int cycleDays = toPositiveInt(plan.get("cycle_days"), 30);
        String nextDue = LocalDate.now().plusDays(cycleDays).toString();
        String planNo = Objects.toString(
                plan.get("plan_no") != null ? plan.get("plan_no") : plan.get("plan_code"), null);
        UUID itemId = UUID.randomUUID();
        Object deptId = req.get("dept_id");
        String deptName = Objects.toString(req.get("dept_name"), null);

        if ("inspect".equals(mod)) {
            jdbc.update("""
                    INSERT INTO inspection_plan_item (id, plan_id, plan_no, device_id, device_code, device_name,
                      dept_id, dept_name, next_due_date, item_status)
                    VALUES (?::uuid,?::uuid,?,?::uuid,?,?,?::uuid,?,?::date,'active')
                    """, itemId, planId, planNo, deviceId, req.get("device_code"), req.get("device_name"),
                    deptId, deptName, nextDue);
        } else if ("pm".equals(mod)) {
            jdbc.update("""
                    INSERT INTO pm_plan_item (id, plan_id, plan_no, device_id, device_code, device_name,
                      dept_id, dept_name, next_due_date, item_status)
                    VALUES (?::uuid,?::uuid,?,?::uuid,?,?,?::uuid,?,?::date,'active')
                    """, itemId, planId, planNo, deviceId, req.get("device_code"), req.get("device_name"),
                    deptId, deptName, nextDue);
        } else {
            jdbc.update("""
                    INSERT INTO maintenance_plan_item (id, plan_id, plan_no, device_id, device_code, device_name,
                      dept_id, dept_name, next_due_date, item_status)
                    VALUES (?::uuid,?::uuid,?,?::uuid,?,?,?::uuid,?,?::date,'active')
                    """, itemId, planId, planNo, deviceId, req.get("device_code"), req.get("device_name"),
                    deptId, deptName, nextDue);
        }

        refreshPlanDueCache(jdbc, t, planId);

        String userId = TenantContext.getUserId();
        String userName = SoftDeleteSupport.resolveUserDisplayName(jdbc, userId);
        jdbc.update("""
                UPDATE ops_plan_include_request SET status='approved',
                  approved_by=?::uuid, approved_by_name=?, approved_at=NOW(),
                  result_plan_item_id=?::uuid,
                  updated_by=?::uuid, updated_by_name=?, updated_at=NOW()
                WHERE id=?::uuid
                """, userId, userName, itemId, userId, userName, requestId);

        Map<String, Object> out = load(jdbc, requestId);
        out.put("plan_item_id", itemId);
        return out;
    }

    public static Map<String, Object> reject(JdbcTemplate jdbc, UUID requestId, Map<String, Object> body) {
        Map<String, Object> req = load(jdbc, requestId);
        if (!"pending".equals(Objects.toString(req.get("status"), ""))) {
            throw new BizException(400, "仅待确认申请可驳回");
        }
        Object reason = body != null ? body.get("reject_reason") : null;
        if (reason == null || reason.toString().isBlank()) {
            throw new BizException(400, "驳回须填写原因");
        }
        markRejected(jdbc, requestId, reason.toString().trim());
        return load(jdbc, requestId);
    }

    private static void markRejected(JdbcTemplate jdbc, UUID requestId, String reason) {
        String userId = TenantContext.getUserId();
        String userName = SoftDeleteSupport.resolveUserDisplayName(jdbc, userId);
        jdbc.update("""
                UPDATE ops_plan_include_request SET status='rejected', reject_reason=?,
                  approved_by=?::uuid, approved_by_name=?, approved_at=NOW(),
                  updated_by=?::uuid, updated_by_name=?, updated_at=NOW()
                WHERE id=?::uuid
                """, reason, userId, userName, userId, userName, requestId);
    }

    public static Map<String, Object> load(JdbcTemplate jdbc, UUID id) {
        var rows = jdbc.queryForList(
                "SELECT * FROM ops_plan_include_request WHERE id=?::uuid AND COALESCE(is_deleted,0)=0", id);
        if (rows.isEmpty()) throw new BizException(404, "纳入申请不存在");
        return new LinkedHashMap<>(rows.get(0));
    }

    private static void refreshPlanDueCache(JdbcTemplate jdbc, ModuleTables t, UUID planId) {
        if (t.lastDoneCacheCol() != null) {
            jdbc.update("""
                    UPDATE %s SET
                      next_due_date = COALESCE(
                        (SELECT MIN(next_due_date) FROM %s
                          WHERE plan_id = ?::uuid AND COALESCE(is_deleted,0)=0 AND next_due_date IS NOT NULL),
                        next_due_date,
                        CURRENT_DATE + COALESCE(cycle_days, 30)
                      ),
                      %s = (SELECT MAX(last_done_date) FROM %s
                        WHERE plan_id = ?::uuid AND COALESCE(is_deleted,0)=0),
                      updated_at = NOW()
                    WHERE id = ?::uuid
                    """.formatted(t.planTable(), t.itemTable(), t.lastDoneCacheCol(), t.itemTable()),
                    planId, planId, planId);
        } else {
            jdbc.update("""
                    UPDATE %s SET
                      next_due_date = COALESCE(
                        (SELECT MIN(next_due_date) FROM %s
                          WHERE plan_id = ?::uuid AND COALESCE(is_deleted,0)=0 AND next_due_date IS NOT NULL),
                        next_due_date,
                        CURRENT_DATE + COALESCE(cycle_days, 30)
                      ),
                      updated_at = NOW()
                    WHERE id = ?::uuid
                    """.formatted(t.planTable(), t.itemTable()), planId, planId);
        }
    }

    private static String resolveDeptName(JdbcTemplate jdbc, Object deptId, Object existing) {
        if (existing != null && !String.valueOf(existing).isBlank()) return String.valueOf(existing);
        if (deptId == null || String.valueOf(deptId).isBlank()) return null;
        var rows = jdbc.queryForList(
                "SELECT dept_name FROM department WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "department", null), deptId);
        return rows.isEmpty() ? null : Objects.toString(rows.get(0).get("dept_name"), null);
    }

    private static String firstNonBlank(Object a, Object b) {
        if (a != null && !a.toString().isBlank()) return a.toString();
        return b == null ? null : b.toString();
    }

    private static int toPositiveInt(Object raw, int defaultVal) {
        if (raw == null) return defaultVal;
        if (raw instanceof Number n) {
            int v = n.intValue();
            return v > 0 ? v : defaultVal;
        }
        try {
            int v = Integer.parseInt(raw.toString().trim());
            return v > 0 ? v : defaultVal;
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
