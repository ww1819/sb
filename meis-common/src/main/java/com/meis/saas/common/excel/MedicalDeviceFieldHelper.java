package com.meis.saas.common.excel;

import com.meis.saas.common.util.PinyinCodeUtil;

import java.util.Map;

public final class MedicalDeviceFieldHelper {
    private MedicalDeviceFieldHelper() {}

    public static void applyDerivedFields(Map<String, Object> body) {
        String acceptance = asString(body.get("acceptance_date"));
        String production = asString(body.get("production_date"));
        Integer lifeYears = asInteger(body.get("service_life_years"));
        Integer period = asInteger(body.get("calibration_period_days"));
        String lastCal = asString(body.get("last_calibration_date"));

        String next = DeviceDateCalculator.nextCalibrationDate(lastCal, period);
        if (next != null) {
            body.put("next_calibration_date", next);
        }

        String expiry = DeviceDateCalculator.serviceExpiryDate(acceptance, production, lifeYears);
        if (expiry != null) {
            body.put("service_expiry_date", expiry);
        }

        // AST-PINYIN-01：简码为空时按设备名称生成首拼
        String name = asString(body.get("device_name"));
        String existingPy = asString(body.get("pinyin_code"));
        if (name != null && existingPy == null) {
            String py = PinyinCodeUtil.toShortCode(name);
            if (!py.isBlank()) {
                body.put("pinyin_code", py);
            }
        }
    }

    private static String asString(Object value) {
        if (value == null) return null;
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static Integer asInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
