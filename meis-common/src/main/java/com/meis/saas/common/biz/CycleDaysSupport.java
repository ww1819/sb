package com.meis.saas.common.biz;

import java.util.Locale;
import java.util.Map;

/**
 * 周期类型 + 周期值 → 周期天数（OPS.15.1）。
 * day×1 / week×7 / month×30 / year×365。
 */
public final class CycleDaysSupport {
    private CycleDaysSupport() {}

    public record Cycle(Object type, Object value, Integer days) {}

    public static Integer calc(Object cycleType, Object cycleValue) {
        if (cycleType == null) return null;
        String type = cycleType.toString().trim().toLowerCase(Locale.ROOT);
        if (type.isEmpty()) return null;
        int unit = switch (type) {
            case "day" -> 1;
            case "week" -> 7;
            case "month" -> 30;
            case "year" -> 365;
            default -> -1;
        };
        if (unit < 0) return null;
        Integer value = toPositiveInt(cycleValue);
        if (value == null) return null;
        return value * unit;
    }

    /** 写入 body.cycle_days；算不出则置 null。 */
    public static void applyToBody(Map<String, Object> body) {
        if (body == null) return;
        body.put("cycle_days", calc(body.get("cycle_type"), body.get("cycle_value")));
    }

    /**
     * 从模板生成计划时解析周期：请求体优先，否则模板，再默认；并重算天数。
     */
    public static Cycle resolveFromTemplate(Map<String, Object> body, Map<String, Object> template,
                                           String defaultType, int defaultValue) {
        Object type = firstPresent(body, "cycle_type", template, "cycle_type", defaultType);
        Object value = firstPresent(body, "cycle_value", template, "cycle_value", defaultValue);
        Integer days = calc(type, value);
        if (days == null && template != null && template.get("cycle_days") != null) {
            days = toPositiveInt(template.get("cycle_days"));
        }
        if (days == null) {
            days = calc(defaultType, defaultValue);
            if (days == null) days = defaultValue;
        }
        return new Cycle(type, value, days);
    }

    private static Object firstPresent(Map<String, Object> primary, String key,
                                       Map<String, Object> secondary, String secondaryKey,
                                       Object defaultValue) {
        Object v = primary != null ? primary.get(key) : null;
        if (v != null && !v.toString().isBlank()) return v;
        v = secondary != null ? secondary.get(secondaryKey) : null;
        if (v != null && !v.toString().isBlank()) return v;
        return defaultValue;
    }

    private static Integer toPositiveInt(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number n) {
            int v = n.intValue();
            return v > 0 ? v : null;
        }
        String s = raw.toString().trim();
        if (s.isEmpty()) return null;
        try {
            int v = Integer.parseInt(s);
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
