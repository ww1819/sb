package com.meis.saas.system.controller;

import com.meis.saas.common.excel.ImportFieldDef;
import com.meis.saas.common.excel.ImportProfileService;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/system/import-template-fields")
@RequiredArgsConstructor
public class ImportTemplateFieldController {
    private final JdbcTemplate jdbc;
    private final ImportProfileService importProfileService;

    @GetMapping
    public Result<List<ImportFieldDef>> list(
            @RequestParam String businessType,
            @RequestParam(required = false) String profile) {
        return Result.ok(importProfileService.resolveFields(businessType, profile));
    }

    /** 为客户新增扩展导入列（写入 import_template_field，并出现在 Excel 模板中） */
    @PostMapping
    public Result<Map<String, Object>> addExtensionField(@RequestBody Map<String, Object> body) {
        String businessType = Objects.toString(body.get("businessType"), "");
        String profileCode = Objects.toString(body.get("profileCode"), "default");
        String fieldKey = Objects.toString(body.get("fieldKey"), "");
        String fieldLabel = Objects.toString(body.get("fieldLabel"), "");
        if (businessType.isBlank() || fieldKey.isBlank() || fieldLabel.isBlank()) {
            return Result.fail(400, "businessType、fieldKey、fieldLabel 不能为空");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO import_template_field
                (id, business_type, profile_code, field_key, field_label, field_type, target_column,
                 required, sort_order, is_extension, is_active, remark)
                VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, TRUE, ?)
                ON CONFLICT (business_type, profile_code, field_key) DO UPDATE SET
                  field_label = EXCLUDED.field_label,
                  field_type = EXCLUDED.field_type,
                  target_column = EXCLUDED.target_column,
                  required = EXCLUDED.required,
                  sort_order = EXCLUDED.sort_order,
                  is_extension = EXCLUDED.is_extension,
                  is_active = TRUE,
                  remark = EXCLUDED.remark,
                  updated_at = NOW()
                """,
                id, businessType, profileCode, fieldKey, fieldLabel,
                body.getOrDefault("fieldType", "string"),
                body.get("targetColumn"),
                Boolean.TRUE.equals(body.get("required")),
                body.get("sortOrder") == null ? 200 : ((Number) body.get("sortOrder")).intValue(),
                body.get("remark"));
        return Result.ok(Map.of("id", id, "fieldKey", fieldKey));
    }

    @PutMapping("/binding")
    public Result<Void> bindProfile(@RequestBody Map<String, Object> body) {
        String businessType = Objects.toString(body.get("businessType"), "");
        String profileCode = Objects.toString(body.get("profileCode"), "default");
        jdbc.update("""
                INSERT INTO import_profile_binding (business_type, profile_code, updated_at)
                VALUES (?, ?, NOW())
                ON CONFLICT (business_type) DO UPDATE SET profile_code = EXCLUDED.profile_code, updated_at = NOW()
                """, businessType, profileCode);
        return Result.ok();
    }
}
