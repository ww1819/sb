package com.meis.saas.common.ops;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/** 运维执行 photos JSONB 序列化（OPS.12 / MT-F-02）。 */
public final class OpsPhotosSupport {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX = 9;

    private OpsPhotosSupport() {}

    public static String toJson(Object raw) {
        try {
            List<String> urls = new ArrayList<>();
            if (raw instanceof List<?> list) {
                for (Object o : list) {
                    if (o == null) continue;
                    String s = o.toString().trim();
                    if (!s.isEmpty()) urls.add(s);
                    if (urls.size() >= MAX) break;
                }
            } else if (raw instanceof String s && !s.isBlank()) {
                if (s.trim().startsWith("[")) {
                    return s;
                }
                urls.add(s.trim());
            }
            return JSON.writeValueAsString(urls);
        } catch (Exception e) {
            return "[]";
        }
    }
}
