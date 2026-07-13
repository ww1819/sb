package com.meis.saas.analytics.power;

import java.math.BigDecimal;

/**
 * 依据待机电流上下限（mA）判定设备运行状态。
 */
public final class PowerWorkStateResolver {

    private PowerWorkStateResolver() {
    }

    public static String resolve(BigDecimal currentMa, BigDecimal maxMa, BigDecimal minMa) {
        double v = currentMa == null ? 0 : currentMa.doubleValue();
        boolean hasMax = maxMa != null;
        boolean hasMin = minMa != null;

        if (hasMax && hasMin) {
            double max = maxMa.doubleValue();
            double min = minMa.doubleValue();
            if (v > max) {
                return "running";
            }
            if (v == 0 || v < min) {
                return "offline";
            }
            return "idle";
        }
        if (!hasMax && !hasMin) {
            return v > 0 ? "idle" : "offline";
        }
        if (hasMax) {
            double max = maxMa.doubleValue();
            if (v == 0) {
                return "offline";
            }
            if (v > max) {
                return "running";
            }
            return "idle";
        }
        double min = minMa.doubleValue();
        if (v < min) {
            return "offline";
        }
        return "idle";
    }
}
