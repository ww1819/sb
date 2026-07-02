-- MEIS SaaS platform schema (public)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS sys_tenant (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_code VARCHAR(50) UNIQUE NOT NULL,
    tenant_name VARCHAR(200) NOT NULL,
    schema_name VARCHAR(100) UNIQUE NOT NULL,
    hospital_level VARCHAR(50),
    contact_name VARCHAR(100),
    contact_phone VARCHAR(30),
    status VARCHAR(20) DEFAULT 'active',
    expire_at TIMESTAMP WITH TIME ZONE,
    max_users INTEGER DEFAULT 500,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_tenant_subscription (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES sys_tenant(id),
    plan_code VARCHAR(50) NOT NULL,
    plan_name VARCHAR(100),
    start_at TIMESTAMP WITH TIME ZONE,
    end_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20) DEFAULT 'active',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS platform_user (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    real_name VARCHAR(50),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tenant_code ON sys_tenant(tenant_code);

-- demo tenant metadata (schema created by meis-tenant service)
INSERT INTO sys_tenant (id, tenant_code, tenant_name, schema_name, status)
VALUES ('00000000-0000-0000-0000-000000000001', 'demo', '演示医院', 'tenant_demo', 'active')
ON CONFLICT (tenant_code) DO NOTHING;
