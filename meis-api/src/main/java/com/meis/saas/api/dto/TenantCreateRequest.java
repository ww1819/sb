package com.meis.saas.api.dto;

import lombok.Data;

@Data
public class TenantCreateRequest {
    private String tenantCode;
    private String tenantName;
    private String hospitalLevel;
    private String contactName;
    private String contactPhone;
    /** 开户套餐，默认 standard */
    private String packageCode;
}
