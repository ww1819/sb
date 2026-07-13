package com.meis.saas.common.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/** 公用设备借调计费（附录 N.8.2）。 */
public final class SharedFeeCalculator {
    private SharedFeeCalculator() {}

    public static BigDecimal calculate(
            String feeMode,
            String feeTimeUnit,
            BigDecimal unitPrice,
            Instant billingStart,
            Instant billingEnd) {
        BigDecimal price = unitPrice != null ? unitPrice : BigDecimal.ZERO;
        if ("per_use".equals(feeMode)) {
            return price.setScale(2, RoundingMode.HALF_UP);
        }
        if (!"time".equals(feeMode) || billingStart == null || billingEnd == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        long minutes = Duration.between(billingStart, billingEnd).toMinutes();
        if (minutes < 0) minutes = 0;
        long units = switch (feeTimeUnit != null ? feeTimeUnit : "day") {
            case "hour" -> ceilUnits(minutes, 60);
            case "month" -> ceilUnits(minutes, 30L * 24 * 60);
            default -> ceilUnits(minutes, 24L * 60);
        };
        if (units < 1) units = 1;
        return price.multiply(BigDecimal.valueOf(units)).setScale(2, RoundingMode.HALF_UP);
    }

    private static long ceilUnits(long minutes, long minutesPerUnit) {
        if (minutes <= 0) return 1;
        return (minutes + minutesPerUnit - 1) / minutesPerUnit;
    }
}
