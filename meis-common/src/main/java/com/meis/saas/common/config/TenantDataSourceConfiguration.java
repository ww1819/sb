package com.meis.saas.common.config;

import com.meis.saas.common.tenant.TenantAwareDataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import javax.sql.DataSource;

@AutoConfiguration(after = DataSourceAutoConfiguration.class)
public class TenantDataSourceConfiguration {
    @org.springframework.context.annotation.Bean
    public static BeanPostProcessor tenantDataSourceWrapper() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if ("dataSource".equals(beanName) && bean instanceof DataSource ds && !(ds instanceof TenantAwareDataSource)) {
                    return new TenantAwareDataSource(ds);
                }
                return bean;
            }
        };
    }
}
