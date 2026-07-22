package com.meis.saas.common.page;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 列表筛选：逗号分隔多选 → SQL IN（PLT-UI-01）。
 */
public final class FilterCsvSupport {
    private FilterCsvSupport() {}

    public static List<String> split(String csv) {
        if (!StringUtils.hasText(csv)) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    /** UUID 列：单值或多值均走 IN (?::uuid, ...) */
    public static void appendUuidIn(StringBuilder where, List<Object> args, String column, String csv) {
        List<String> parts = split(csv);
        if (parts.isEmpty()) return;
        where.append(" AND ").append(column).append(" IN (");
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) where.append(',');
            where.append("?::uuid");
            args.add(parts.get(i));
        }
        where.append(") ");
    }

    /** 字符串码列：设备状态等 */
    public static void appendStrIn(StringBuilder where, List<Object> args, String column, String csv) {
        List<String> parts = split(csv);
        if (parts.isEmpty()) return;
        where.append(" AND ").append(column).append(" IN (");
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) where.append(',');
            where.append('?');
            args.add(parts.get(i));
        }
        where.append(") ");
    }

    /** 编码/名称/（可选）首拼模糊；与多选 ID 可同时 AND */
    public static void appendCodeNamePinyin(StringBuilder where, List<Object> args,
                                            String codeColumn, String nameColumn, String pinyinColumn,
                                            String keyword) {
        if (!StringUtils.hasText(keyword)) return;
        String kw = "%" + keyword.trim() + "%";
        List<String> cols = new ArrayList<>();
        if (StringUtils.hasText(codeColumn)) cols.add(codeColumn + " ILIKE ?");
        if (StringUtils.hasText(nameColumn)) cols.add(nameColumn + " ILIKE ?");
        if (StringUtils.hasText(pinyinColumn)) cols.add(pinyinColumn + " ILIKE ?");
        if (cols.isEmpty()) return;
        where.append(" AND (").append(String.join(" OR ", cols)).append(") ");
        for (int i = 0; i < cols.size(); i++) args.add(kw);
    }
}
