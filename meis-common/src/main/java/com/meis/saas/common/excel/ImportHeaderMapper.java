package com.meis.saas.common.excel;

import java.util.*;

/**
 * 表头别名映射：支持中文列名、英文字段名、客户自定义 field_label。
 */
public final class ImportHeaderMapper {
    private ImportHeaderMapper() {}

    private static final Map<String, String> GLOBAL_ALIASES = buildGlobalAliases();

    public static Map<String, String> buildLabelMap(List<ImportFieldDef> fields) {
        Map<String, String> map = new LinkedHashMap<>();
        for (ImportFieldDef f : fields) {
            map.put(normalize(f.getFieldLabel()), f.getFieldKey());
            map.put(normalize(f.getFieldKey()), f.getFieldKey());
        }
        GLOBAL_ALIASES.forEach(map::putIfAbsent);
        return map;
    }

    public static List<String> normalizeHeaders(List<String> raw, List<ImportFieldDef> fields) {
        Map<String, String> labelMap = buildLabelMap(fields);
        List<String> out = new ArrayList<>();
        for (String h : raw) {
            if (h == null) { out.add(""); continue; }
            String t = normalize(h);
            out.add(labelMap.getOrDefault(t, t));
        }
        return out;
    }

    public static String normalize(String raw) {
        if (raw == null) return "";
        return raw.trim().replace("\uFEFF", "");
    }

    private static Map<String, String> buildGlobalAliases() {
        Map<String, String> alias = new LinkedHashMap<>();
        for (ImportFieldDef f : ImportFieldRegistry.get(ImportFieldRegistry.DEPARTMENT)) {
            alias.put(normalize(f.getFieldLabel()), f.getFieldKey());
        }
        for (ImportFieldDef f : ImportFieldRegistry.get(ImportFieldRegistry.SUPPLIER)) {
            alias.put(normalize(f.getFieldLabel()), f.getFieldKey());
        }
        for (ImportFieldDef f : ImportFieldRegistry.get(ImportFieldRegistry.MANUFACTURER)) {
            alias.put(normalize(f.getFieldLabel()), f.getFieldKey());
        }
        for (ImportFieldDef f : ImportFieldRegistry.get(ImportFieldRegistry.MEDICAL_DEVICE)) {
            alias.put(normalize(f.getFieldLabel()), f.getFieldKey());
        }
        // 兼容旧模板列名
        alias.put(normalize("设备编码"), "device_code");
        alias.put(normalize("设备名称"), "device_name");
        return alias;
    }
}
