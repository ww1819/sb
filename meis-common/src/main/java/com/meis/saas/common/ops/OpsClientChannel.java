package com.meis.saas.common.ops;

import java.util.Map;

/** OPS.16.10：多端途径归一（web/app/mp）。 */
public final class OpsClientChannel {
    private OpsClientChannel() {}

    public static String of(Map<String, Object> body) {
        if (body == null) return "web";
        if (body.get("client") != null) return normalize(body.get("client").toString());
        // 报修等历史字段：无 client 时回退 report_method
        if (body.get("report_method") != null) return normalize(body.get("report_method").toString());
        return "web";
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return "web";
        String s = raw.trim().toLowerCase();
        if ("app".equals(s) || "mp".equals(s) || "web".equals(s)) return s;
        if (s.contains("mini") || "wechat".equals(s) || "miniprogram".equals(s)) return "mp";
        if ("phone".equals(s) || "ops_auto".equals(s)) return "web";
        return "web";
    }
}
