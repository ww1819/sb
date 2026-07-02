package com.meis.saas.api.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String tenantCode;
    private String username;
    private String password;
}
