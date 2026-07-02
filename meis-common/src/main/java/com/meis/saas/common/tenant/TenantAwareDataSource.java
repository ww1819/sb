package com.meis.saas.common.tenant;

import org.springframework.jdbc.datasource.DelegatingDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

public class TenantAwareDataSource extends DelegatingDataSource {
    public TenantAwareDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return prepare(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return prepare(super.getConnection(username, password));
    }

    private Connection prepare(Connection conn) throws SQLException {
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
        return conn;
    }
}
