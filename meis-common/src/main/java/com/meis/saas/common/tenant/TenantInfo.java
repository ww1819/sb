package com.meis.saas.common.tenant;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class TenantInfo {
    private String tenantId;
    private String tenantCode;
    private String schemaName;
    private String userId;
    private String username;
    /** tenant | platform */
    private String userType;
    private List<String> roles;
}
