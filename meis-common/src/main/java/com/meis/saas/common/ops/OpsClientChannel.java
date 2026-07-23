package com.meis.saas.common.ops;

import java.util.Map;

/** OPS.16.10：多端途径归一（web/app/mp）。 */
public final class OpsClientChannel {
    private OpsClientChannel() {}

    public static String of(Map<String, Object> body) {
        if (body == null || body.get("client") == null) return "web";
        return normalize(body.get("client").toString());
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return "web";
        String s = raw.trim().toLowerCase();
        if ("app".equals(s) || "mp".equals(s) || "web".equals(s)) return s;
        if (s.contains("mini") || "wechat".equals(s)) return "mp";
        return "web";
    }
}
