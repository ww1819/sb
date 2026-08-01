package com.meis.saas.common.excel;

import com.meis.saas.common.util.PinyinCodeUtil;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class MedicalDeviceFieldHelper {
    private static final Pattern EXPIRY_REMARK = Pattern.compile(
            "\\[使用到期推算][^\\n]*\\n?", Pattern.UNICODE_CASE);

    private MedicalDeviceFieldHelper() {}

    public static void applyDerivedFields(Map<String, Object> body) {
        applyDerivedFields(body, null);
    }

    /**
     * @param existing 更新时的库中行；创建传 null。推算用「existing ∪ body」合并视图。
     */
    public static void applyDerivedFields(Map<String, Object> body, Map<String, Object> existing) {
        Map<String, Object> ctx = mergeContext(existing, body);

        String lastCal = asString(ctx.get("last_calibration_date"));
        Integer period = asInteger(ctx.get("calibration_period_days"));
        String next = DeviceDateCalculator.nextCalibrationDate(lastCal, period);
        if (next != null) {
            body.put("next_calibration_date", next);
        }

        applyServiceExpiry(body, ctx, existing);

        String name = asString(body.get("device_name"));
        if (name == null && existing != null) name = asString(existing.get("device_name"));
        String existingPy = asString(body.get("pinyin_code"));
        if (existingPy == null && existing != null) existingPy = asString(existing.get("pinyin_code"));
        if (name != null && existingPy == null && !body.containsKey("pinyin_code")) {
            String py = PinyinCodeUtil.toShortCode(name);
            if (!py.isBlank()) {
                body.put("pinyin_code", py);
            }
        }
    }

    private static void applyServiceExpiry(
            Map<String, Object> body, Map<String, Object> ctx, Map<String, Object> existing) {
        Integer lifeYears = asInteger(ctx.get("service_life_years"));
        String created = asString(ctx.get("created_at"));
        if (created == null) created = LocalDate.now().toString();

        DeviceDateCalculator.ServiceExpiryResult r = DeviceDateCalculator.resolveServiceExpiry(
                asString(ctx.get("enable_date")),
                asString(ctx.get("production_date")),
                asString(ctx.get("acceptance_date")),
                asString(ctx.get("purchase_date")),
                created,
                lifeYears,
                DeviceDateCalculator.Mode.ENABLE_FIRST);

        String oldExpiry = existing == null ? null : asString(existing.get("service_expiry_date"));
        String oldBasis = existing == null ? null : asString(existing.get("service_expiry_basis"));

        if (r == null) {
            if (lifeYears == null || lifeYears <= 0) {
                // 年限清空：同步清空推算结果
                if (body.containsKey("service_life_years") && asInteger(body.get("service_life_years")) == null) {
                    body.put("service_expiry_date", null);
                    body.put("service_expiry_basis", null);
                }
            }
            return;
        }

        body.put("service_expiry_date", r.expiryDate());
        body.put("service_expiry_basis", r.basis());

        boolean changed = !r.expiryDate().equals(nullToEmpty(oldExpiry))
                || !r.basis().equals(nullToEmpty(oldBasis));
        if (!changed && existing != null) return;

        String baseRemark = asString(body.containsKey("remark") ? body.get("remark")
                : (existing == null ? null : existing.get("remark")));
        body.put("remark", upsertExpiryRemark(baseRemark, r));
    }

    public static String upsertExpiryRemark(String remark, DeviceDateCalculator.ServiceExpiryResult r) {
        String note = "[使用到期推算] " + r.basisLabel() + " → " + r.expiryDate();
        String cleaned = remark == null ? "" : EXPIRY_REMARK.matcher(remark).replaceAll("").trim();
        if (cleaned.isEmpty()) return note;
        return cleaned + "\n" + note;
    }

    private static Map<String, Object> mergeContext(Map<String, Object> existing, Map<String, Object> body) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        if (existing != null) ctx.putAll(existing);
        if (body != null) {
            for (Map.Entry<String, Object> e : body.entrySet()) {
                ctx.put(e.getKey(), e.getValue());
            }
        }
        return ctx;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String asString(Object value) {
        if (value == null) return null;
        String text = value.toString().trim();
        return text.isEmpty() || "null".equalsIgnoreCase(text) ? null : text;
    }

    private static Integer asInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        String t = value.toString().trim();
        if (t.isEmpty()) return null;
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
