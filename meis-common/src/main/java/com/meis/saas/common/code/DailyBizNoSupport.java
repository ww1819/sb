package com.meis.saas.common.code;

import com.meis.saas.common.exception.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 日流水业务单号：{prefix}yyyyMMdd + 4 位序号（如 MP-202607220001）。
 */
public final class DailyBizNoSupport {
    private DailyBizNoSupport() {}

    /**
     * @param prefix 前缀，建议带尾缀 "-"，如 {@code "MP-"}、{@code "ME-"}
     */
    public static String next(JdbcTemplate jdbc, String table, String column, String prefix) {
        if (jdbc == null || !StringUtils.hasText(table) || !StringUtils.hasText(column) || !StringUtils.hasText(prefix)) {
            throw new BizException(500, "单号生成参数无效");
        }
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String fullPrefix = prefix.endsWith("-") ? prefix + day : prefix + "-" + day;
        int expectedLen = fullPrefix.length() + 4;
        Integer maxSeq = jdbc.queryForObject("""
                SELECT COALESCE(MAX(CAST(RIGHT(%s, 4) AS INTEGER)), 0)
                FROM %s
                WHERE %s LIKE ?
                  AND LENGTH(%s) = ?
                  AND RIGHT(%s, 4) ~ '^[0-9]{4}$'
                """.formatted(column, table, column, column, column),
                Integer.class, fullPrefix + "%", expectedLen);
        int seq = (maxSeq != null ? maxSeq : 0) + 1;
        for (int i = 0; i < 30; i++) {
            if (seq + i > 9999) {
                throw new BizException(400, "当日单号已用尽，请明日再试");
            }
            String candidate = fullPrefix + String.format("%04d", seq + i);
            Integer cnt = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                    Integer.class, candidate);
            if (cnt == null || cnt == 0) {
                return candidate;
            }
        }
        throw new BizException(500, "单号生成冲突，请重试");
    }
}
