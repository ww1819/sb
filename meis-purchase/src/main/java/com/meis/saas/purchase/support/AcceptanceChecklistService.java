package com.meis.saas.purchase.support;

import org.springframework.jdbc.core.JdbcTemplate;

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

    public static void seedDefaultItems(JdbcTemplate jdbc, UUID acceptanceId) {
        var existing = jdbc.queryForList(
                "SELECT 1 FROM purchase_acceptance_item WHERE acceptance_id = ?::uuid LIMIT 1", acceptanceId);
        if (!existing.isEmpty()) return;
        int order = 1;
        for (String[] item : DEFAULT_ITEMS) {
            jdbc.update("""
                INSERT INTO purchase_acceptance_item (id, acceptance_id, item_name, check_standard, check_result, sort_order)
                VALUES (?::uuid, ?::uuid, ?, ?, 'pending', ?)
                """, UUID.randomUUID(), acceptanceId, item[0], item[1], order++);
        }
        seedDefaultMembers(jdbc, acceptanceId);
    }

    private static void seedDefaultMembers(JdbcTemplate jdbc, UUID acceptanceId) {
        var existing = jdbc.queryForList(
                "SELECT 1 FROM purchase_acceptance_member WHERE acceptance_id = ?::uuid LIMIT 1", acceptanceId);
        if (!existing.isEmpty()) return;
        String[][] roles = {
                {"quality", "质控人员"}, {"engineering", "工程人员"},
                {"clinical", "临床代表"}, {"equipment", "设备科"}
        };
        for (String[] r : roles) {
            jdbc.update("""
                INSERT INTO purchase_acceptance_member (id, acceptance_id, member_role, member_name)
                VALUES (?::uuid, ?::uuid, ?, ?)
                """, UUID.randomUUID(), acceptanceId, r[0], r[1]);
        }
    }

    public static void saveItems(JdbcTemplate jdbc, UUID acceptanceId, List<Map<String, Object>> items) {
        jdbc.update("DELETE FROM purchase_acceptance_item WHERE acceptance_id = ?::uuid", acceptanceId);
        for (Map<String, Object> item : items) {
            jdbc.update("""
                INSERT INTO purchase_acceptance_item (id, acceptance_id, item_name, check_standard,
                check_result, is_passed, checker_id, remark, sort_order)
                VALUES (?::uuid,?::uuid,?,?,?,?::uuid,?,?,?)
                """,
                    item.containsKey("id") ? UUID.fromString(item.get("id").toString()) : UUID.randomUUID(),
                    acceptanceId, item.get("item_name"), item.get("check_standard"),
                    item.getOrDefault("check_result", "pending"), item.get("is_passed"),
                    item.get("checker_id"), item.get("remark"), item.getOrDefault("sort_order", 0));
        }
    }

    public static void saveMembers(JdbcTemplate jdbc, UUID acceptanceId, List<Map<String, Object>> members) {
        jdbc.update("DELETE FROM purchase_acceptance_member WHERE acceptance_id = ?::uuid", acceptanceId);
        for (Map<String, Object> m : members) {
            jdbc.update("""
                INSERT INTO purchase_acceptance_member (id, acceptance_id, member_role, user_id,
                member_name, signed_at, signature_url, remark)
                VALUES (?::uuid,?::uuid,?,?::uuid,?,?,?,?)
                """,
                    m.containsKey("id") ? UUID.fromString(m.get("id").toString()) : UUID.randomUUID(),
                    acceptanceId, m.get("member_role"), m.get("user_id"),
                    m.get("member_name"), m.get("signed_at"), m.get("signature_url"), m.get("remark"));
        }
    }

    public static boolean allItemsPassed(JdbcTemplate jdbc, UUID acceptanceId) {
        var failed = jdbc.queryForList("""
            SELECT id FROM purchase_acceptance_item
            WHERE acceptance_id = ?::uuid AND (is_passed IS NOT TRUE OR check_result = 'failed')
            """, acceptanceId);
        return failed.isEmpty();
    }
}
