package com.meis.saas.common.tenant;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.*;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Properties;

@Intercepts({@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})})
public class TenantSchemaInterceptor implements Interceptor {
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Connection conn = (Connection) invocation.getArgs()[0];
        String schema = TenantContext.getSchemaName();
        if (schema == null || schema.isBlank()) {
            schema = "public";
        }
        try (Statement st = conn.createStatement()) {
            if ("public".equals(schema)) {
                st.execute("SET search_path TO public");
            } else if (schema.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
                st.execute("SET search_path TO " + schema + ", public");
            }
        }
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) { return Plugin.wrap(target, this); }

    @Override
    public void setProperties(Properties properties) {}
}
