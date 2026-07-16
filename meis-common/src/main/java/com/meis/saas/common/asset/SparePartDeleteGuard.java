package com.meis.saas.common.asset;

import com.meis.saas.common.exception.BizException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/** 配件档案删除前业务引用校验（附录 U.15.3）。 */
public final class SparePartDeleteGuard {
    private SparePartDeleteGuard() {}

    private static final List<String> REF_SQL = List.of(
            "SELECT 1 FROM repair_workorder_segment_part WHERE spare_part_id = ?::uuid AND COALESCE(is_deleted,0)=0 LIMIT 1",
            "SELECT 1 FROM spare_part_usage WHERE part_id = ?::uuid LIMIT 1",
            "SELECT 1 FROM spare_part_transaction WHERE spare_part_id = ?::uuid LIMIT 1"
    );

    public static boolean hasBusinessData(JdbcTemplate jdbc, String partId) {
        for (String sql : REF_SQL) {
            try {
                if (!jdbc.queryForList(sql, partId).isEmpty()) return true;
            } catch (Exception ignored) {
                // 表可能尚未建齐
            }
        }
        return false;
    }

    public static void assertDeletable(JdbcTemplate jdbc, String partId) {
        if (hasBusinessData(jdbc, partId)) {
            throw new BizException(400, "该配件已被业务引用，禁止删除");
        }
    }
}
