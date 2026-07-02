package com.meis.saas.api.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class LoginResponse {
    private String token;
    private String userId;
    private String username;
    private String realName;
    private String tenantId;
    private String tenantCode;
    private String schemaName;
    private List<String> roles;
    private Map<String, Object> permissions;
    /** platform | tenant */
    private String userType;
}
