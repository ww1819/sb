package com.meis.saas.common.excel;

import java.util.List;
import java.util.Locale;

public final class ImportValueParser {
    private ImportValueParser() {}

    public static Boolean parseBoolean(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if (List.of("true", "1", "yes", "y", "是", "启用").contains(v)) return true;
        if (List.of("false", "0", "no", "n", "否", "停用").contains(v)) return false;
        throw new IllegalArgumentException("布尔值无效: " + raw);
    }

    public static Integer parseInteger(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return Integer.parseInt(raw.trim());
    }

    public static Double parseDouble(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return Double.parseDouble(raw.trim());
    }

    public static String require(String raw, String label) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException(label + "不能为空");
        return raw.trim();
    }

    public static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    public static Object cast(String fieldType, String raw) {
        if (raw == null || raw.isBlank()) return null;
        return switch (fieldType == null ? "string" : fieldType) {
            case "boolean" -> parseBoolean(raw);
            case "number" -> {
                try {
                    yield raw.contains(".") ? parseDouble(raw) : parseInteger(raw);
                } catch (NumberFormatException e) {
                    yield parseDouble(raw);
                }
            }
            case "date" -> blankToNull(raw);
            default -> raw.trim();
        };
    }
}
