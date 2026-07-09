package com.meis.saas.common.excel;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public final class DeviceDateCalculator {
    private DeviceDateCalculator() {}

    static String nextCalibrationDate(String lastCalibrationDate, Integer calibrationPeriodDays) {
        if (lastCalibrationDate == null || calibrationPeriodDays == null || calibrationPeriodDays <= 0) {
            return null;
        }
        LocalDate base = parseDate(lastCalibrationDate);
        if (base == null) return null;
        return base.plusDays(calibrationPeriodDays).toString();
    }

    static String serviceExpiryDate(String acceptanceDate, String productionDate, Integer serviceLifeYears) {
        if (serviceLifeYears == null || serviceLifeYears <= 0) return null;
        String base = acceptanceDate != null && !acceptanceDate.isBlank()
                ? acceptanceDate
                : productionDate;
        if (base == null || base.isBlank()) return null;
        LocalDate date = parseDate(base);
        if (date == null) return null;
        return date.plusYears(serviceLifeYears).toString();
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        if (value.length() >= 10) value = value.substring(0, 10);
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
