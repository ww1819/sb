package com.meis.saas.common.excel;

import lombok.Builder;
import lombok.Value;

/**
 * 导入模板字段定义。标准字段由 {@link ImportFieldRegistry} 内置；
 * 客户扩展字段可写入 import_template_field 表合并进来。
 */
@Value
@Builder
public class ImportFieldDef {
    String fieldKey;
    String fieldLabel;
    String fieldType;
    /** 物理列名；扩展字段可为 null（写入 extension_data） */
    String targetColumn;
    boolean required;
    int sortOrder;
    boolean extension;
    String remark;

    public String effectiveColumn() {
        if (extension) return null;
        return targetColumn != null && !targetColumn.isBlank() ? targetColumn : fieldKey;
    }
}
