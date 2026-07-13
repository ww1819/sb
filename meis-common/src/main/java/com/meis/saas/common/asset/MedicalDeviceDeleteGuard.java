package com.meis.saas.common.asset;

import com.meis.saas.common.exception.BizException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 资产台账删除前业务占用校验（附录 P）。 */
public final class MedicalDeviceDeleteGuard {
    private MedicalDeviceDeleteGuard() {}

    private static final List<String> REF_SQL = List.of(
            "SELECT 1 FROM repair_workorder WHERE device_id = ?::uuid AND COALESCE(is_deleted,0)=0 LIMIT 1",
            "SELECT 1 FROM maintenance_plan WHERE device_id = ?::uuid AND COALESCE(is_deleted,0)=0 LIMIT 1",
            "SELECT 1 FROM maintenance_execution_item WHERE device_id = ?::uuid AND COALESCE(is_deleted,0)=0 LIMIT 1",
            "SELECT 1 FROM maintenance_record WHERE device_id = ?::uuid AND COALESCE(is_deleted,0)=0 LIMIT 1",
            "SELECT 1 FROM inspection_plan WHERE device_id = ?::uuid AND COALESCE(is_deleted,0)=0 LIMIT 1",
            "SELECT 1 FROM inspection_execution_item WHERE device_id = ?::uuid AND COALESCE(is_deleted,0)=0 LIMIT 1",
            "SELECT 1 FROM metrology_plan WHERE device_id = ?::uuid AND COALESCE(is_deleted,0)=0 LIMIT 1",
            "SELECT 1 FROM metrology_execution_item WHERE device_id = ?::uuid AND COALESCE(is_deleted,0)=0 LIMIT 1",
            "SELECT 1 FROM metrology_record WHERE device_id = ?::uuid AND COALESCE(is_deleted,0)=0 LIMIT 1",
            "SELECT 1 FROM pm_plan WHERE device_id = ?::uuid AND COALESCE(is_deleted,0)=0 LIMIT 1",
            "SELECT 1 FROM pm_execution_item WHERE device_id = ?::uuid AND COALESCE(is_deleted,0)=0 LIMIT 1",
            "SELECT 1 FROM asset_transfer WHERE device_id = ?::uuid AND COALESCE(is_deleted,0)=0 LIMIT 1",
            "SELECT 1 FROM inventory_check_item WHERE device_id = ?::uuid AND COALESCE(is_deleted,0)=0 LIMIT 1",
            "SELECT 1 FROM device_scrap WHERE device_id = ?::uuid AND COALESCE(is_deleted,0)=0 LIMIT 1",
            "SELECT 1 FROM device_outbound_item WHERE device_id = ?::uuid AND COALESCE(is_deleted,0)=0 LIMIT 1",
            "SELECT 1 FROM device_entry_item WHERE device_id = ?::uuid AND COALESCE(is_deleted,0)=0 LIMIT 1",
            "SELECT 1 FROM device_return_item WHERE device_id = ?::uuid AND COALESCE(is_deleted,0)=0 LIMIT 1",
            "SELECT 1 FROM shared_device_loan WHERE device_id = ?::uuid AND COALESCE(is_deleted,0)=0 LIMIT 1",
            "SELECT 1 FROM adverse_event WHERE device_id = ?::uuid AND COALESCE(is_deleted,0)=0 LIMIT 1",
            "SELECT 1 FROM life_support_device WHERE device_id = ?::uuid AND COALESCE(is_deleted,0)=0 LIMIT 1",
            "SELECT 1 FROM special_device WHERE device_id = ?::uuid AND COALESCE(is_deleted,0)=0 LIMIT 1",
            "SELECT 1 FROM leased_device WHERE device_id = ?::uuid AND COALESCE(is_deleted,0)=0 LIMIT 1",
            "SELECT 1 FROM power_tag WHERE device_id = ?::uuid AND COALESCE(is_deleted,0)=0 LIMIT 1",
            "SELECT 1 FROM risk_assessment WHERE device_id = ?::uuid AND COALESCE(is_deleted,0)=0 LIMIT 1",
            "SELECT 1 FROM performance_test WHERE device_id = ?::uuid AND COALESCE(is_deleted,0)=0 LIMIT 1"
    );

    public static boolean hasBusinessData(JdbcTemplate jdbc, String deviceId) {
        for (String sql : REF_SQL) {
            try {
                if (!jdbc.queryForList(sql, deviceId).isEmpty()) return true;
            } catch (Exception ignored) {
                // 部分租户可能尚未建齐全部表，忽略单表失败继续检查
            }
        }
        return false;
    }

    public static void assertDeletable(JdbcTemplate jdbc, String deviceId) {
        if (hasBusinessData(jdbc, deviceId)) {
            throw new BizException(400, "该设备已产生业务数据，禁止删除台账");
        }
    }

    public static void enrichCanDelete(JdbcTemplate jdbc, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return;
        List<String> ids = rows.stream()
                .map(r -> r.get("id") == null ? null : r.get("id").toString())
                .filter(id -> id != null && !id.isBlank())
                .toList();
        java.util.Set<String> busy = new java.util.HashSet<>();
        if (!ids.isEmpty()) {
            String inList = ids.stream().map(id -> "'" + id.replace("'", "") + "'::uuid").reduce((a, b) -> a + "," + b).orElse("");
            String sql = """
                    SELECT DISTINCT device_id::text AS id FROM (
                      SELECT device_id FROM repair_workorder WHERE device_id IN (%s) AND COALESCE(is_deleted,0)=0
                      UNION SELECT device_id FROM maintenance_plan WHERE device_id IN (%s) AND COALESCE(is_deleted,0)=0
                      UNION SELECT device_id FROM maintenance_execution_item WHERE device_id IN (%s) AND COALESCE(is_deleted,0)=0
                      UNION SELECT device_id FROM inspection_plan WHERE device_id IN (%s) AND COALESCE(is_deleted,0)=0
                      UNION SELECT device_id FROM inspection_execution_item WHERE device_id IN (%s) AND COALESCE(is_deleted,0)=0
                      UNION SELECT device_id FROM metrology_plan WHERE device_id IN (%s) AND COALESCE(is_deleted,0)=0
                      UNION SELECT device_id FROM metrology_execution_item WHERE device_id IN (%s) AND COALESCE(is_deleted,0)=0
                      UNION SELECT device_id FROM pm_plan WHERE device_id IN (%s) AND COALESCE(is_deleted,0)=0
                      UNION SELECT device_id FROM pm_execution_item WHERE device_id IN (%s) AND COALESCE(is_deleted,0)=0
                      UNION SELECT device_id FROM asset_transfer WHERE device_id IN (%s) AND COALESCE(is_deleted,0)=0
                      UNION SELECT device_id FROM inventory_check_item WHERE device_id IN (%s) AND COALESCE(is_deleted,0)=0
                      UNION SELECT device_id FROM device_scrap WHERE device_id IN (%s) AND COALESCE(is_deleted,0)=0
                      UNION SELECT device_id FROM shared_device_loan WHERE device_id IN (%s) AND COALESCE(is_deleted,0)=0
                      UNION SELECT device_id FROM adverse_event WHERE device_id IN (%s) AND COALESCE(is_deleted,0)=0
                    ) t WHERE device_id IS NOT NULL
                    """.formatted(inList, inList, inList, inList, inList, inList, inList, inList, inList, inList, inList, inList, inList, inList);
            try {
                for (Map<String, Object> r : jdbc.queryForList(sql)) {
                    if (r.get("id") != null) busy.add(r.get("id").toString());
                }
            } catch (Exception e) {
                // 回退逐条检查
                for (String id : ids) {
                    if (hasBusinessData(jdbc, id)) busy.add(id);
                }
            }
        }
        for (Map<String, Object> row : rows) {
            Object id = row.get("id");
            row.put("can_delete", id != null && !busy.contains(id.toString()));
        }
    }

    public static void assertDeletable(JdbcTemplate jdbc, UUID deviceId) {
        assertDeletable(jdbc, deviceId.toString());
    }
}
