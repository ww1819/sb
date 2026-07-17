package com.meis.saas.asset.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.result.Result;
import com.meis.saas.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/asset/inventory")
@RequiredArgsConstructor
public class InventoryCheckController {
    private static final String UUID_PATH =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    private final JdbcTemplate jdbc;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query,
            @RequestParam(required = false) String audit_status) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            where.append(" AND (check_no ILIKE ? OR check_name ILIKE ?) ");
            String kw = "%" + query.getKeyword() + "%";
            args.add(kw);
            args.add(kw);
        }
        if (audit_status != null && !audit_status.isBlank()) {
            where.append(" AND audit_status = ? ");
            args.add(audit_status);
        }
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, "inventory_check", null));
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM inventory_check" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.limit());
        pageArgs.add(query.offset());
        var rows = jdbc.queryForList(
                "SELECT * FROM inventory_check" + where + " ORDER BY created_at DESC NULLS LAST LIMIT ? OFFSET ?",
                pageArgs.toArray());
        return Result.ok(PageResult.of(rows, total != null ? total : 0, query.getPage(), query.getSize()));
    }

    @GetMapping("/devices/candidates")
    public Result<List<Map<String, Object>>> candidateDevices(
            @RequestParam(required = false) String deptId,
            @RequestParam(required = false) String campusId,
            @RequestParam(required = false) String checkId,
            @RequestParam(required = false) List<String> excludeIds) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, device_code, device_name, brand, model, specification,
                       dept_id, campus_id, location_detail, device_status
                FROM medical_device
                WHERE is_active = true
                """);
        sql.append(SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", null));
        List<Object> args = new ArrayList<>();
        if (deptId != null && !deptId.toString().isBlank()) {
            sql.append(" AND dept_id = ?::uuid ");
            args.add(deptId);
        }
        if (campusId != null && !campusId.toString().isBlank()) {
            sql.append(" AND campus_id = ?::uuid ");
            args.add(campusId);
        }
        if (checkId != null && !checkId.toString().isBlank()) {
            sql.append("""
                     AND id NOT IN (
                         SELECT device_id FROM inventory_check_item
                         WHERE check_id = ?::uuid AND device_id IS NOT NULL
                    """);
            sql.append(SoftDeleteSupport.notDeletedClause(jdbc, "inventory_check_item", null));
            sql.append(")");
            args.add(checkId);
        }
        List<String> excludes = excludeIds == null ? List.of()
                : excludeIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
        if (!excludes.isEmpty()) {
            sql.append(" AND id NOT IN (");
            sql.append(String.join(",", Collections.nCopies(excludes.size(), "?::uuid")));
            sql.append(") ");
            args.addAll(excludes);
        }
        sql.append(" ORDER BY device_code LIMIT 500");
        return Result.ok(jdbc.queryForList(sql.toString(), args.toArray()));
    }

    @GetMapping("/{id:" + UUID_PATH + "}")
    public Result<Map<String, Object>> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList(
                "SELECT * FROM inventory_check WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "inventory_check", null), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        Map<String, Object> t = rows.get(0);
        t.put("items", jdbc.queryForList(
                "SELECT * FROM inventory_check_item WHERE check_id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "inventory_check_item", null)
                        + " ORDER BY device_code", id));
        return Result.ok(t);
    }

    @GetMapping("/{id:" + UUID_PATH + "}/reprint-items")
    public Result<List<Map<String, Object>>> reprintItems(@PathVariable UUID id) {
        assertExists(id);
        return Result.ok(jdbc.queryForList("""
                SELECT i.id, i.check_id, i.device_id, i.device_code, i.device_name,
                       i.need_reprint_label, i.label_printed, i.label_print_count,
                       d.specification, d.model, d.serial_number, d.dept_id,
                       d.enable_date, d.acceptance_date, d.purchase_date
                FROM inventory_check_item i
                INNER JOIN medical_device d ON d.id = i.device_id
                WHERE i.check_id = ?::uuid AND i.need_reprint_label = TRUE
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "inventory_check_item", "i")
                + SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", "d")
                + " ORDER BY i.device_code", id));
    }

    @PostMapping
    @Transactional
    @OperationLog(module = "asset", description = "保存盘点任务")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        UUID id = body.containsKey("id") && body.get("id") != null && !body.get("id").toString().isBlank()
                ? UUID.fromString(body.get("id").toString()) : UUID.randomUUID();
        boolean exists = !jdbc.queryForList("SELECT 1 FROM inventory_check WHERE id = ?::uuid", id).isEmpty();
        String userId = TenantContext.getUserId();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.getOrDefault("items", List.of());

        if (exists) {
            assertMutable(id);
            jdbc.update("""
                UPDATE inventory_check SET check_name=?, check_year=?, check_type=?, campus_id=?::uuid, dept_id=?::uuid,
                warehouse_id=?::uuid, start_date=?, end_date=?, checker_id=?::uuid, supervisor_id=?::uuid, remark=?, total_count=?, updated_at=NOW()
                WHERE id=?::uuid
                """, body.get("check_name"), body.get("check_year"), body.get("check_type"), body.get("campus_id"),
                    body.get("dept_id"), body.get("warehouse_id"), body.get("start_date"), body.get("end_date"), body.get("checker_id"),
                    body.get("supervisor_id"), body.get("remark"), items.size(), id);
        } else {
            jdbc.update("""
                INSERT INTO inventory_check (id, check_no, check_name, check_year, check_type, campus_id, dept_id,
                warehouse_id, start_date, end_date, checker_id, supervisor_id, status, audit_status, total_count, created_by)
                VALUES (?::uuid,?,?,?,?,?::uuid,?::uuid,?::uuid,?,?,?::uuid,?::uuid,?,?,?,?::uuid)
                """, id, body.getOrDefault("check_no", "IC" + System.currentTimeMillis()), body.get("check_name"),
                    body.get("check_year"), body.getOrDefault("check_type", "annual"), body.get("campus_id"),
                    body.get("dept_id"), body.get("warehouse_id"), body.get("start_date"), body.get("end_date"), body.get("checker_id"),
                    body.get("supervisor_id"), body.getOrDefault("status", "planning"),
                    body.getOrDefault("audit_status", "pending"), items.size(),
                    userId != null ? UUID.fromString(userId) : null);
        }

        upsertItems(id, items);
        refreshCheckedCount(id);
        return get(id);
    }

    @DeleteMapping("/{id:" + UUID_PATH + "}")
    @Transactional
    @OperationLog(module = "asset", description = "删除盘点任务")
    public Result<Void> delete(@PathVariable UUID id) {
        assertMutable(id);
        jdbc.update("DELETE FROM inventory_check_item WHERE check_id = ?::uuid", id);
        int n = SoftDeleteSupport.softDelete(jdbc, "inventory_check", id.toString());
        if (n == 0) throw new BizException(404, "not found");
        return Result.ok();
    }

    @PostMapping("/{id:" + UUID_PATH + "}/approve")
    @Transactional
    @OperationLog(module = "asset", description = "审核盘点任务")
    public Result<Map<String, Object>> approve(@PathVariable UUID id) {
        var rows = jdbc.queryForList(
                "SELECT audit_status FROM inventory_check WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "inventory_check", null), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
        if ("approved".equals(String.valueOf(rows.get(0).get("audit_status")))) {
            throw new BizException(400, "盘点单已审核");
        }
        String userId = TenantContext.getUserId();
        UUID approver = userId != null ? UUID.fromString(userId) : null;
        jdbc.update("""
                UPDATE inventory_check
                SET audit_status = 'approved', approved_by = ?::uuid, approved_at = NOW(), updated_at = NOW()
                WHERE id = ?::uuid
                """, approver, id);
        return get(id);
    }

    @PostMapping("/{id:" + UUID_PATH + "}/start")
    @OperationLog(module = "asset", description = "开始盘点")
    public Result<Map<String, Object>> start(@PathVariable UUID id) {
        jdbc.update("UPDATE inventory_check SET status = 'in_progress', actual_start_at = NOW(), updated_at = NOW() WHERE id = ?::uuid", id);
        return get(id);
    }

    @PostMapping("/{id:" + UUID_PATH + "}/scan")
    @Transactional
    @OperationLog(module = "asset", description = "扫码盘点")
    public Result<Map<String, Object>> scan(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        assertExists(id);
        Object deviceId = blankToNull(body.get("device_id"));
        String deviceCode = body.get("device_code") != null ? body.get("device_code").toString().trim() : "";
        String deviceName = body.get("device_name") != null ? body.get("device_name").toString() : null;
        if (deviceId == null && !deviceCode.isBlank()) {
            var devices = jdbc.queryForList(
                    "SELECT id, device_code, device_name FROM medical_device WHERE device_code = ?"
                            + SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", null) + " LIMIT 2",
                    deviceCode);
            if (devices.isEmpty()) throw new BizException(404, "未找到设备：" + deviceCode);
            if (devices.size() > 1) throw new BizException(400, "设备编码匹配多条，请联系管理员");
            deviceId = devices.get(0).get("id");
            deviceCode = Objects.toString(devices.get(0).get("device_code"), deviceCode);
            deviceName = Objects.toString(devices.get(0).get("device_name"), deviceName);
        }
        if (deviceId == null) throw new BizException(400, "请提供设备或设备编码");

        var existing = jdbc.queryForList(
                "SELECT id FROM inventory_check_item WHERE check_id = ?::uuid AND device_id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "inventory_check_item", null), id, deviceId);
        if (existing.isEmpty()) {
            throw new BizException(400, "该设备不在本盘点单明细中");
        }
        String userId = TenantContext.getUserId();
        jdbc.update("""
                UPDATE inventory_check_item SET is_found = true,
                actual_location = COALESCE(?, actual_location),
                check_date = NOW(),
                checker_id = COALESCE(?::uuid, checker_id)
                WHERE check_id = ?::uuid AND device_id = ?::uuid
                """, body.get("actual_location"), userId, id, deviceId);
        refreshCheckedCount(id);
        return get(id);
    }

    @PatchMapping("/{id:" + UUID_PATH + "}/items/{itemId:" + UUID_PATH + "}")
    @Transactional
    @OperationLog(module = "asset", description = "更新盘点明细")
    public Result<Map<String, Object>> patchItem(@PathVariable UUID id, @PathVariable UUID itemId,
            @RequestBody Map<String, Object> body) {
        assertExists(id);
        var rows = jdbc.queryForList(
                "SELECT id FROM inventory_check_item WHERE id = ?::uuid AND check_id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "inventory_check_item", null), itemId, id);
        if (rows.isEmpty()) throw new BizException(404, "明细不存在");

        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (body.containsKey("is_found")) {
            sets.add("is_found = ?");
            args.add(asBool(body.get("is_found")));
            if (asBool(body.get("is_found"))) {
                sets.add("check_date = COALESCE(check_date, NOW())");
            }
        }
        if (body.containsKey("need_reprint_label")) {
            sets.add("need_reprint_label = ?");
            args.add(asBool(body.get("need_reprint_label")));
        }
        if (body.containsKey("actual_location")) {
            sets.add("actual_location = ?");
            args.add(body.get("actual_location"));
        }
        if (sets.isEmpty()) throw new BizException(400, "无更新字段");
        args.add(itemId);
        args.add(id);
        jdbc.update("UPDATE inventory_check_item SET " + String.join(", ", sets)
                + " WHERE id = ?::uuid AND check_id = ?::uuid", args.toArray());
        refreshCheckedCount(id);
        return get(id);
    }

    @PostMapping("/{id:" + UUID_PATH + "}/label/print")
    @Transactional
    @OperationLog(module = "asset", description = "盘点补打标签")
    public Result<Map<String, Object>> printLabels(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        var checks = jdbc.queryForList(
                "SELECT id, check_no FROM inventory_check WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "inventory_check", null), id);
        if (checks.isEmpty()) throw new BizException(404, "not found");
        String checkNo = Objects.toString(checks.get(0).get("check_no"), "");

        @SuppressWarnings("unchecked")
        List<Object> rawIds = body.get("item_ids") instanceof List<?> list ? (List<Object>) list : List.of();
        if (rawIds.isEmpty()) throw new BizException(400, "请选择要打印的明细");
        List<UUID> itemIds = rawIds.stream()
                .filter(Objects::nonNull)
                .map(v -> UUID.fromString(v.toString()))
                .distinct()
                .toList();

        String placeholders = String.join(",", Collections.nCopies(itemIds.size(), "?::uuid"));
        List<Object> qArgs = new ArrayList<>();
        qArgs.add(id);
        qArgs.addAll(itemIds);
        var items = jdbc.queryForList(
                "SELECT id, device_id, device_code, device_name FROM inventory_check_item"
                        + " WHERE check_id = ?::uuid AND id IN (" + placeholders + ")"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "inventory_check_item", null),
                qArgs.toArray());
        if (items.size() != itemIds.size()) throw new BizException(400, "部分明细不存在或不属于本盘点单");

        String template = body.get("template_code") != null ? body.get("template_code").toString() : "asset_sticker";
        String remark = body.get("remark") != null ? body.get("remark").toString() : null;
        String userId = TenantContext.getUserId();
        String printedByName = resolveUserName(userId);

        int printed = 0;
        for (Map<String, Object> item : items) {
            Object deviceId = item.get("device_id");
            if (deviceId == null) throw new BizException(400, "明细缺少设备，无法打印");
            String code = Objects.toString(item.get("device_code"), "").trim();
            if (code.isBlank()) {
                var d = jdbc.queryForList(
                        "SELECT device_code, device_name FROM medical_device WHERE id = ?::uuid"
                                + SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", null), deviceId);
                if (d.isEmpty()) throw new BizException(404, "设备不存在");
                code = Objects.toString(d.get(0).get("device_code"), "");
                if (item.get("device_name") == null) item.put("device_name", d.get(0).get("device_name"));
            }
            if (code.isBlank()) throw new BizException(400, "设备编码为空，无法生成标签二维码");

            UUID logId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO device_label_print_log
                    (id, device_id, device_code, device_name, printed_by, printed_by_name, template_code,
                     biz_type, biz_id, biz_no, biz_item_id, remark)
                    VALUES (?::uuid, ?::uuid, ?, ?, ?::uuid, ?, ?, 'inventory_check', ?::uuid, ?, ?::uuid, ?)
                    """, logId, deviceId, code, item.get("device_name"), userId, printedByName, template,
                    id, checkNo, item.get("id"), remark);
            jdbc.update("""
                    UPDATE medical_device SET label_printed = TRUE, qr_code_url = ?, updated_at = NOW()
                    WHERE id = ?::uuid
                    """, code, deviceId);
            jdbc.update("""
                    UPDATE inventory_check_item
                    SET need_reprint_label = FALSE, label_printed = TRUE,
                        label_print_count = COALESCE(label_print_count, 0) + 1
                    WHERE id = ?::uuid
                    """, item.get("id"));
            printed++;
        }
        return Result.ok(Map.of("printed", printed, "check_id", id.toString(), "check_no", checkNo));
    }

    @PostMapping("/{id:" + UUID_PATH + "}/complete")
    @OperationLog(module = "asset", description = "完成盘点")
    public Result<Map<String, Object>> complete(@PathVariable UUID id) {
        jdbc.update("""
                UPDATE inventory_check
                SET status = 'completed', actual_end_at = NOW(), updated_at = NOW()
                WHERE id = ?::uuid
                """, id);
        return get(id);
    }

    private void upsertItems(UUID checkId, List<Map<String, Object>> items) {
        Set<UUID> keep = new LinkedHashSet<>();
        for (Map<String, Object> item : items) {
            UUID itemId = parseUuid(item.get("id")).orElse(UUID.randomUUID());
            keep.add(itemId);
            boolean exists = !jdbc.queryForList(
                    "SELECT 1 FROM inventory_check_item WHERE id = ?::uuid AND check_id = ?::uuid",
                    itemId, checkId).isEmpty();
            Boolean needReprint = item.containsKey("need_reprint_label")
                    ? asBool(item.get("need_reprint_label")) : null;
            if (exists) {
                jdbc.update("""
                        UPDATE inventory_check_item SET
                          device_id = ?::uuid, device_code = ?, device_name = ?,
                          expected_location = ?, actual_location = ?,
                          is_found = ?, is_matched = ?, condition_status = ?, remark = ?,
                          need_reprint_label = COALESCE(?, need_reprint_label),
                          check_date = COALESCE(?, check_date),
                          checker_id = COALESCE(?::uuid, checker_id)
                        WHERE id = ?::uuid AND check_id = ?::uuid
                        """, blankToNull(item.get("device_id")), item.get("device_code"), item.get("device_name"),
                        item.get("expected_location"), item.get("actual_location"),
                        asBool(item.getOrDefault("is_found", false)),
                        asBool(item.getOrDefault("is_matched", false)),
                        item.get("condition_status"), item.get("remark"),
                        needReprint, item.get("check_date"), blankToNull(item.get("checker_id")),
                        itemId, checkId);
            } else {
                jdbc.update("""
                        INSERT INTO inventory_check_item (id, check_id, device_id, device_code, device_name,
                        expected_location, actual_location, is_found, is_matched, need_reprint_label,
                        label_printed, label_print_count, condition_status, check_date, checker_id, remark)
                        VALUES (?::uuid,?::uuid,?::uuid,?,?,?,?,?,?,?,FALSE,0,?,?,?::uuid,?)
                        """, itemId, checkId, blankToNull(item.get("device_id")), item.get("device_code"),
                        item.get("device_name"), item.get("expected_location"), item.get("actual_location"),
                        asBool(item.getOrDefault("is_found", false)),
                        asBool(item.getOrDefault("is_matched", false)),
                        needReprint != null ? needReprint : false,
                        item.get("condition_status"), item.get("check_date"),
                        blankToNull(item.get("checker_id")), item.get("remark"));
            }
        }
        if (keep.isEmpty()) {
            jdbc.update("DELETE FROM inventory_check_item WHERE check_id = ?::uuid", checkId);
        } else {
            String placeholders = String.join(",", Collections.nCopies(keep.size(), "?::uuid"));
            List<Object> args = new ArrayList<>();
            args.add(checkId);
            args.addAll(keep);
            jdbc.update("DELETE FROM inventory_check_item WHERE check_id = ?::uuid AND id NOT IN ("
                    + placeholders + ")", args.toArray());
        }
    }

    private void refreshCheckedCount(UUID id) {
        jdbc.update("""
            UPDATE inventory_check SET checked_count = (
                SELECT COUNT(*) FROM inventory_check_item WHERE check_id = ?::uuid AND is_found = true
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "inventory_check_item", null) + """
            ), updated_at = NOW() WHERE id = ?::uuid
            """, id, id);
    }

    private void assertExists(UUID id) {
        var rows = jdbc.queryForList(
                "SELECT 1 FROM inventory_check WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "inventory_check", null), id);
        if (rows.isEmpty()) throw new BizException(404, "not found");
    }

    private void assertMutable(UUID id) {
        var rows = jdbc.queryForList(
                "SELECT audit_status FROM inventory_check WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "inventory_check", null), id);
        if (!rows.isEmpty() && "approved".equals(String.valueOf(rows.get(0).get("audit_status")))) {
            throw new BizException(400, "已审核的盘点单不可修改或删除");
        }
    }

    private String resolveUserName(String userId) {
        if (userId == null || userId.isBlank()) return null;
        try {
            var rows = jdbc.queryForList(
                    "SELECT COALESCE(NULLIF(TRIM(real_name), ''), username) AS name FROM sys_user WHERE id = ?::uuid",
                    userId);
            if (!rows.isEmpty() && rows.get(0).get("name") != null) {
                return rows.get(0).get("name").toString();
            }
        } catch (Exception ignored) {
            // sys_user 可能暂不可用
        }
        return TenantContext.get() != null ? TenantContext.get().getUsername() : null;
    }

    private static Optional<UUID> parseUuid(Object value) {
        if (value == null) return Optional.empty();
        String s = value.toString().trim();
        if (s.isEmpty()) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(s));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static boolean asBool(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        String s = value.toString().trim();
        return "true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s);
    }

    private static Object blankToNull(Object value) {
        if (value == null) return null;
        if (value instanceof String s && s.isBlank()) return null;
        return value;
    }
}
