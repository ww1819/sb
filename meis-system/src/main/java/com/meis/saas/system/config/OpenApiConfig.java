package com.meis.saas.system.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI meisOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("MEIS SaaS API")
                .description("医院设备固定资产管理系统 OpenAPI 聚合入口（各微服务独立文档见 /v3/api-docs）")
                .version("1.0.0"));
    }
}
