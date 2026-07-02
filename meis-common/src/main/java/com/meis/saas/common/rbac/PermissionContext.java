package com.meis.saas.common.rbac;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class PermissionContext {
    private String userId;
    private String tenantId;
    private String schemaName;
    private List<String> roles;
    private List<String> menus;
    private List<String> buttons;
    private String dataScope;
    private String deptId;
    private List<String> deptIds;
    private List<String> warehouseIds;

    public boolean hasMenu(String code) {
        return menus != null && (menus.contains("*") || menus.contains(code));
    }

    public boolean hasButton(String code) {
        return buttons != null && (buttons.contains("*") || buttons.contains(code));
    }
}
