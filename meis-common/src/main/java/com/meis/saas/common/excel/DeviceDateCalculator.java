package com.meis.saas.common.excel;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * AST-EXP-01：使用到期推算。
 * <p>默认（客户未指定前）：启用日期优先，其次生产日期，再验收/购置/录入兜底。
 * 严格合规模式（生产优先）预留 {@link #Mode#PRODUCTION_FIRST}，暂不接租户配置。
 */
public final class DeviceDateCalculator {
    private DeviceDateCalculator() {}

    public enum Mode {
        /** 医院运维默认：启用 → 生产 → 验收 → 购置 → 录入 */
        ENABLE_FIRST,
        /** 最严寿命口径：生产 → 启用 → 验收 → 购置 → 录入 */
        PRODUCTION_FIRST
    }

    public record ServiceExpiryResult(String expiryDate, String basis, String basisLabel) {}

    static String nextCalibrationDate(String lastCalibrationDate, Integer calibrationPeriodDays) {
        if (lastCalibrationDate == null || calibrationPeriodDays == null || calibrationPeriodDays <= 0) {
            return null;
        }
        LocalDate base = parseDate(lastCalibrationDate);
        if (base == null) return null;
        return base.plusDays(calibrationPeriodDays).toString();
    }

    /** 兼容旧调用：验收 → 生产（无启用字段时）。 */
    static String serviceExpiryDate(String acceptanceDate, String productionDate, Integer serviceLifeYears) {
        ServiceExpiryResult r = resolveServiceExpiry(
                null, productionDate, acceptanceDate, null, null, serviceLifeYears, Mode.ENABLE_FIRST);
        return r == null ? null : r.expiryDate();
    }

    public static ServiceExpiryResult resolveServiceExpiry(
            String enableDate,
            String productionDate,
            String acceptanceDate,
            String purchaseDate,
            String createdDate,
            Integer serviceLifeYears,
            Mode mode) {
        if (serviceLifeYears == null || serviceLifeYears <= 0) return null;
        Mode m = mode == null ? Mode.ENABLE_FIRST : mode;
        String[][] order = m == Mode.PRODUCTION_FIRST
                ? new String[][]{
                    {productionDate, "production", "生产日期"},
                    {enableDate, "enable", "启用日期"},
                    {acceptanceDate, "acceptance", "验收日期"},
                    {purchaseDate, "purchase", "购置日期"},
                    {createdDate, "created", "录入日期"}
                }
                : new String[][]{
                    {enableDate, "enable", "启用日期"},
                    {productionDate, "production", "生产日期"},
                    {acceptanceDate, "acceptance", "验收日期"},
                    {purchaseDate, "purchase", "购置日期"},
                    {createdDate, "created", "录入日期"}
                };
        for (String[] row : order) {
            LocalDate base = parseDate(row[0]);
            if (base == null) continue;
            return new ServiceExpiryResult(
                    base.plusYears(serviceLifeYears).toString(),
                    row[1],
                    row[2] + "+" + serviceLifeYears + "年");
        }
        return null;
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
