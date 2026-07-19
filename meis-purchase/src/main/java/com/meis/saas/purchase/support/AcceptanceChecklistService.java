package com.meis.saas.purchase.support;

import com.meis.saas.common.persistence.SoftDeleteSupport;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AcceptanceChecklistService {
    private AcceptanceChecklistService() {}

    private static final List<String[]> DEFAULT_ITEMS = List.of(
            new String[]{"外观完好性检查", "设备外观无破损、锈蚀，标识清晰"},
            new String[]{"随机资料核对", "说明书、合格证、保修卡、注册证齐全"},
            new String[]{"开机自检", "通电自检正常，无报警"},
            new String[]{"基本性能测试", "主要功能参数符合合同及标书要求"},
            new String[]{"安全性能检查", "接地、漏电保护等安全项合格"},
            new String[]{"安装环境确认", "安装位置、水电条件满足要求"}
    );

    private static final List<String[]> DEFAULT_PARAMS = List.of(
            new String[]{"开箱前", "箱体是否有损坏、破坏、碰撞等说明"},
            new String[]{"开箱后", "技术资料（含说明书、合格证、图纸、保修卡、装箱清单）"},
            new String[]{"开箱后", "清点物件（物件的齐全或缺漏情况描述）"}
    );

    public static void seedDefaultItems(JdbcTemplate jdbc, UUID acceptanceId) {
        var existing = jdbc.queryForList(
                "SELECT 1 FROM purchase_acceptance_item WHERE acceptance_id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_acceptance_item", null) + " LIMIT 1",
                acceptanceId);
        if (!existing.isEmpty()) {
            seedDefaultMembers(jdbc, acceptanceId);
            return;
        }
        int order = 1;
        for (String[] item : DEFAULT_ITEMS) {
            jdbc.update("""
                INSERT INTO purchase_acceptance_item (id, acceptance_id, item_name, check_standard, check_result, sort_order)
                VALUES (?::uuid, ?::uuid, ?, ?, 'pending', ?)
                """, UUID.randomUUID(), acceptanceId, item[0], item[1], order++);
        }
        seedDefaultMembers(jdbc, acceptanceId);
    }

    public static void seedDefaultMembers(JdbcTemplate jdbc, UUID acceptanceId) {
        var existing = jdbc.queryForList(
                "SELECT 1 FROM purchase_acceptance_member WHERE acceptance_id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_acceptance_member", null) + " LIMIT 1",
                acceptanceId);
        if (!existing.isEmpty()) return;
        int order = 1;
        for (String[] p : DEFAULT_PARAMS) {
            jdbc.update("""
                INSERT INTO purchase_acceptance_member (id, acceptance_id, member_role, member_name, acceptance_content)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), acceptanceId, String.valueOf(order++), p[0], p[1]);
        }
    }

    public static void saveItems(JdbcTemplate jdbc, UUID acceptanceId, List<Map<String, Object>> items) {
        jdbc.update("DELETE FROM purchase_acceptance_item WHERE acceptance_id = ?::uuid", acceptanceId);
        int order = 1;
        for (Map<String, Object> item : items) {
            jdbc.update("""
                INSERT INTO purchase_acceptance_item (id, acceptance_id, item_name, check_standard,
                check_result, is_passed, checker_id, remark, sort_order)
                VALUES (?,?,?,?,?,?,?,?,?)
                """,
                    parseUuid(item.get("id")) != null ? parseUuid(item.get("id")) : UUID.randomUUID(),
                    acceptanceId, item.get("item_name"), item.get("check_standard"),
                    item.getOrDefault("check_result", "pending"), item.get("is_passed"),
                    parseUuid(item.get("checker_id")), item.get("remark"),
                    item.getOrDefault("sort_order", order++));
        }
    }

    public static void saveMembers(JdbcTemplate jdbc, UUID acceptanceId, List<Map<String, Object>> members) {
        jdbc.update("DELETE FROM purchase_acceptance_member WHERE acceptance_id = ?::uuid", acceptanceId);
        for (Map<String, Object> m : members) {
            String paramNo = blankToNull(m.get("member_role"));
            String project = blankToNull(m.get("member_name"));
            String content = blankToNull(m.get("acceptance_content"));
            String result = blankToNull(m.get("acceptance_result"));
            String remark = blankToNull(m.get("remark"));
            if (paramNo == null && project == null && content == null && result == null && remark == null) {
                continue;
            }
            if (paramNo == null) paramNo = "";
            jdbc.update("""
                INSERT INTO purchase_acceptance_member (id, acceptance_id, member_role, user_id,
                member_name, acceptance_content, acceptance_result, signed_at, signature_url, remark)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """,
                    parseUuid(m.get("id")) != null ? parseUuid(m.get("id")) : UUID.randomUUID(),
                    acceptanceId, paramNo, parseUuid(m.get("user_id")),
                    project, content, result,
                    m.get("signed_at"), m.get("signature_url"), remark);
        }
    }

    public static void saveDevices(JdbcTemplate jdbc, UUID acceptanceId, List<Map<String, Object>> devices) {
        var ctx = com.meis.saas.common.rbac.PermissionInterceptor.CTX.get();
        UUID actorId = null;
        String actorName = null;
        if (ctx != null && ctx.getUserId() != null && !ctx.getUserId().isBlank()) {
            actorId = UUID.fromString(ctx.getUserId());
            actorName = SoftDeleteSupport.resolveUserDisplayName(jdbc, actorId);
        }
        jdbc.update("""
                UPDATE purchase_acceptance_device
                SET is_deleted = 1, deleted_at = NOW(), deleted_by = ?, deleted_by_name = ?,
                    updated_at = NOW(), updated_by = ?, updated_by_name = ?
                WHERE acceptance_id = ? AND is_deleted = 0
                """, actorId, actorName, actorId, actorName, acceptanceId);
        int order = 1;
        for (Map<String, Object> row : devices) {
            String deviceName = blankToNull(row.get("device_name"));
            if (deviceName == null) continue;
            UUID manufacturerId = parseUuid(row.get("manufacturer_id"));
            String manufacturerName = blankToNull(row.get("manufacturer_name"));
            if (manufacturerId != null) {
                var mf = jdbc.queryForList(
                        "SELECT manufacturer_name FROM manufacturer WHERE id = ?"
                                + SoftDeleteSupport.notDeletedClause(jdbc, "manufacturer", null),
                        manufacturerId);
                if (!mf.isEmpty()) {
                    manufacturerName = blankToNull(mf.get(0).get("manufacturer_name"));
                }
            }
            Object qty = toDecimalOrNull(row.get("quantity"));
            Object price = toDecimalOrNull(row.get("unit_price"));
            Object amount = toDecimalOrNull(row.get("amount"));
            if (amount == null) {
                Double qn = toDouble(qty);
                Double pn = toDouble(price);
                if (qn != null && pn != null) {
                    amount = Math.round(qn * pn * 100.0) / 100.0;
                }
            }
            jdbc.update("""
                    INSERT INTO purchase_acceptance_device (
                        id, acceptance_id, device_name, specification, brand, quantity, unit_price, amount,
                        manufacturer_id, manufacturer_name, sort_order,
                        created_at, updated_at, created_by, created_by_name, updated_by, updated_by_name, is_deleted
                    ) VALUES (
                        ?, ?, ?, ?, ?, ?, ?, ?,
                        ?, ?, ?,
                        NOW(), NOW(), ?, ?, ?, ?, 0
                    )
                    """,
                    UUID.randomUUID(), acceptanceId, deviceName,
                    blankToNull(row.get("specification")),
                    blankToNull(row.get("brand")),
                    qty, price, amount,
                    manufacturerId, manufacturerName, order++,
                    actorId, actorName, actorId, actorName);
        }
    }

    public static void copyContractDevices(JdbcTemplate jdbc, UUID acceptanceId, UUID contractId) {
        var items = jdbc.queryForList("""
                SELECT device_name, specification, brand, quantity, unit_price, amount,
                       manufacturer_id, manufacturer_name
                FROM purchase_contract_item
                WHERE contract_id = ?
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract_item", null) + """
                ORDER BY sort_order ASC NULLS LAST, created_at ASC NULLS LAST
                """, contractId);
        if (items.isEmpty()) return;
        saveDevices(jdbc, acceptanceId, items);
    }

    public static boolean allItemsPassed(JdbcTemplate jdbc, UUID acceptanceId) {
        var failed = jdbc.queryForList("""
            SELECT id FROM purchase_acceptance_item
            WHERE acceptance_id = ?::uuid AND (is_passed IS NOT TRUE OR check_result = 'failed')
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_acceptance_item", null), acceptanceId);
        return failed.isEmpty();
    }

    private static UUID parseUuid(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String blankToNull(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static Object toDecimalOrNull(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        String s = v.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double toDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(v.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
