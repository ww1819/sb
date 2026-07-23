package com.meis.saas.common.excel;

import java.util.*;

public final class ImportFieldRegistry {
    private ImportFieldRegistry() {}

    public static final String MEDICAL_DEVICE = "medical_device";
    public static final String DEPARTMENT = "department";
    public static final String SUPPLIER = "supplier";
    public static final String MANUFACTURER = "manufacturer";
    public static final String DEVICE_CATEGORY = "medical_device_category";

    private static final Map<String, List<ImportFieldDef>> DEFAULTS = buildDefaults();

    public static List<ImportFieldDef> get(String businessType) {
        return DEFAULTS.getOrDefault(businessType, List.of());
    }

    public static boolean supports(String businessType) {
        return DEFAULTS.containsKey(businessType);
    }

    private static ImportFieldDef f(String key, String label, String type, String target, boolean req, int order) {
        return ImportFieldDef.builder()
                .fieldKey(key).fieldLabel(label).fieldType(type)
                .targetColumn(target).required(req).sortOrder(order)
                .extension(false).build();
    }

    private static Map<String, List<ImportFieldDef>> buildDefaults() {
        Map<String, List<ImportFieldDef>> map = new LinkedHashMap<>();

        map.put(DEPARTMENT, List.of(
                f("dept_code", "科室编码", "string", "dept_code", true, 10),
                f("dept_name", "科室名称", "string", "dept_name", true, 20),
                f("pinyin_code", "拼音简码", "string", "pinyin_code", false, 30),
                f("parent_dept_code", "上级科室编码", "string", null, false, 40),
                f("campus_name", "院区名称", "string", null, false, 50),
                f("is_clinical", "临床科室", "boolean", "is_clinical", false, 60),
                f("sort_order", "排序", "number", "sort_order", false, 70),
                f("is_active", "启用", "boolean", "is_active", false, 80)
        ));

        map.put(SUPPLIER, List.of(
                f("supplier_code", "供应商编码", "string", "supplier_code", true, 10),
                f("supplier_name", "供应商名称", "string", "supplier_name", true, 20),
                f("pinyin_code", "拼音简码", "string", "pinyin_code", false, 30),
                f("unified_social_credit_code", "统一社会信用代码", "string", "unified_social_credit_code", false, 40),
                f("legal_representative", "法人代表", "string", "legal_representative", false, 50),
                f("contact_person", "联系人", "string", "contact_person", false, 60),
                f("contact_phone", "联系电话", "string", "contact_phone", false, 70),
                f("address", "地址", "string", "address", false, 80),
                f("bank_name", "开户行", "string", "bank_name", false, 90),
                f("bank_account", "银行账号", "string", "bank_account", false, 100),
                f("rating", "评级", "number", "rating", false, 110),
                f("is_authorized", "授权经销商", "boolean", "is_authorized", false, 120),
                f("is_active", "启用", "boolean", "is_active", false, 130)
        ));

        map.put(MANUFACTURER, List.of(
                f("manufacturer_code", "厂商编码", "string", "manufacturer_code", true, 10),
                f("manufacturer_name", "厂商名称", "string", "manufacturer_name", true, 20),
                f("pinyin_code", "拼音简码", "string", "pinyin_code", false, 30),
                f("country", "国家/地区", "string", "country", false, 40),
                f("is_domestic", "国产厂商", "boolean", "is_domestic", false, 50),
                f("contact_phone", "联系电话", "string", "contact_phone", false, 60),
                f("website", "官网", "string", "website", false, 70),
                f("is_active", "启用", "boolean", "is_active", false, 80)
        ));

        map.put(MEDICAL_DEVICE, List.of(
                f("device_code", "资产编码", "string", "device_code", true, 10),
                f("device_name", "资产名称", "string", "device_name", true, 20),
                f("pinyin_code", "拼音简码", "string", "pinyin_code", false, 25),
                f("brand", "品牌", "string", "brand", false, 30),
                f("model", "型号", "string", "model", false, 40),
                f("specification", "规格", "string", "specification", false, 45),
                f("serial_number", "序列号", "string", "serial_number", false, 50),
                f("category_code", "分类编码(68码)", "lookup", null, false, 60),
                f("asset_category_code", "资产分类编码", "lookup", null, false, 62),
                f("finance_category_code", "财务分类编码", "lookup", null, false, 64),
                f("warehouse_code", "库房编码", "lookup", null, false, 66),
                f("manufacturer_code", "生产厂商编码", "lookup", null, false, 70),
                f("manufacturer_name", "生产厂家", "lookup", null, false, 75),
                f("supplier_code", "供应商编码", "lookup", null, false, 80),
                f("supplier_name", "供应商", "lookup", null, false, 85),
                f("dept_code", "使用科室编码", "lookup", null, false, 90),
                f("dept_name", "使用科室", "lookup", null, false, 95),
                f("campus_name", "院区名称", "lookup", null, false, 100),
                f("original_value", "原值", "number", "original_value", false, 110),
                f("net_value", "净值", "number", "net_value", false, 120),
                f("financial_code", "财务编码", "string", "financial_code", false, 130),
                f("device_status", "设备状态", "string", "device_status", false, 140),
                f("risk_level", "风险等级", "string", "risk_level", false, 150),
                f("purchase_date", "购置日期", "date", "purchase_date", false, 160),
                f("acceptance_date", "验收日期", "date", "acceptance_date", false, 165),
                f("production_date", "生产日期", "date", "production_date", false, 170),
                f("service_life_years", "使用年限", "number", "service_life_years", false, 175),
                f("enable_date", "启用日期", "date", "enable_date", false, 180),
                f("is_metrology", "计量设备", "boolean", "is_metrology", false, 182),
                f("is_maintain_device", "保养设备", "boolean", "is_maintain_device", false, 184),
                f("is_inspection_device", "巡检设备", "boolean", "is_inspection_device", false, 186),
                f("remark", "备注", "string", "remark", false, 190),
                f("is_active", "启用", "boolean", "is_active", false, 200)
        ));

        map.put(DEVICE_CATEGORY, List.of(
                f("category_code", "分类编码", "string", "category_code", true, 10),
                f("category_name", "分类名称", "string", "category_name", true, 20),
                f("parent_code", "上级编码", "string", "parent_code", false, 30),
                f("sort_order", "排序", "number", "sort_order", false, 40)
        ));

        return Map.copyOf(map);
    }
}
