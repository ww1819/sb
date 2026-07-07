package com.meis.saas.common.excel;

import com.meis.saas.common.tenant.TenantContext;
import com.meis.saas.common.tenant.TenantInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ImportProfileService {
    private final JdbcTemplate jdbcTemplate;

    public List<ImportFieldDef> resolveFields(String businessType) {
        return resolveFields(businessType, null);
    }

    public List<ImportFieldDef> resolveFields(String businessType, String profileCodeOverride) {
        List<ImportFieldDef> base = ImportFieldRegistry.get(businessType);
        if (base.isEmpty()) return base;
        if (!tableExists()) return sort(new ArrayList<>(base));

        String profileCode = profileCodeOverride != null && !profileCodeOverride.isBlank()
                ? profileCodeOverride.trim()
                : resolveBoundProfile(businessType);

        List<Map<String, Object>> dbRows = jdbcTemplate.queryForList("""
                SELECT profile_code, field_key, field_label, field_type, target_column,
                       required, sort_order, is_extension, remark
                FROM import_template_field
                WHERE business_type = ? AND profile_code IN ('default', ?) AND is_active = TRUE
                ORDER BY sort_order, field_key
                """, businessType, profileCode);

        Map<String, ImportFieldDef> merged = new LinkedHashMap<>();
        for (ImportFieldDef f : base) merged.put(f.getFieldKey(), f);

        for (Map<String, Object> row : dbRows) {
            String key = String.valueOf(row.get("field_key"));
            String rowProfile = String.valueOf(row.get("profile_code"));
            ImportFieldDef fromDb = mapRow(row);

            if (!merged.containsKey(key)) {
                merged.put(key, fromDb);
                continue;
            }
            if (profileCode.equals(rowProfile)) {
                ImportFieldDef existing = merged.get(key);
                merged.put(key, ImportFieldDef.builder()
                        .fieldKey(key)
                        .fieldLabel(fromDb.getFieldLabel())
                        .fieldType(fromDb.getFieldType() != null ? fromDb.getFieldType() : existing.getFieldType())
                        .targetColumn(fromDb.getTargetColumn() != null ? fromDb.getTargetColumn() : existing.getTargetColumn())
                        .required(fromDb.isRequired())
                        .sortOrder(fromDb.getSortOrder())
                        .extension(fromDb.isExtension())
                        .remark(fromDb.getRemark() != null ? fromDb.getRemark() : existing.getRemark())
                        .build());
            }
        }
        return sort(new ArrayList<>(merged.values()));
    }

    public Set<String> standardColumns(String businessType, List<ImportFieldDef> fields) {
        return fields.stream()
                .filter(f -> !f.isExtension())
                .map(ImportFieldDef::effectiveColumn)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private ImportFieldDef mapRow(Map<String, Object> row) {
        return ImportFieldDef.builder()
                .fieldKey(String.valueOf(row.get("field_key")))
                .fieldLabel(String.valueOf(row.get("field_label")))
                .fieldType(String.valueOf(row.get("field_type")))
                .targetColumn(row.get("target_column") == null ? null : String.valueOf(row.get("target_column")))
                .required(Boolean.TRUE.equals(row.get("required")))
                .sortOrder(row.get("sort_order") == null ? 100 : ((Number) row.get("sort_order")).intValue())
                .extension(Boolean.TRUE.equals(row.get("is_extension")))
                .remark(row.get("remark") == null ? null : String.valueOf(row.get("remark")))
                .build();
    }

    private String resolveBoundProfile(String businessType) {
        try {
            List<String> codes = jdbcTemplate.queryForList(
                    "SELECT profile_code FROM import_profile_binding WHERE business_type = ?",
                    String.class, businessType);
            if (!codes.isEmpty() && codes.get(0) != null && !codes.get(0).isBlank()) {
                return codes.get(0).trim();
            }
        } catch (Exception ignored) {
        }
        TenantInfo info = TenantContext.get();
        if (info != null && info.getTenantCode() != null && !info.getTenantCode().isBlank()) {
            return info.getTenantCode().trim();
        }
        return "default";
    }

    private boolean tableExists() {
        try {
            Integer n = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = 'import_template_field'",
                    Integer.class);
            return n != null && n > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static List<ImportFieldDef> sort(List<ImportFieldDef> fields) {
        fields.sort(Comparator.comparingInt(ImportFieldDef::getSortOrder).thenComparing(ImportFieldDef::getFieldKey));
        return fields;
    }
}
