-- MEIS public: CREATE TABLE + COMMENT ON (visible in database catalog)

-- MEIS SaaS platform schema (public)
-- UUID 生成扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
-- 加密/随机数扩展
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
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    credit_code VARCHAR(50),
    package_code VARCHAR(50) DEFAULT 'standard'
);
COMMENT ON TABLE sys_tenant IS '租户';
COMMENT ON COLUMN sys_tenant.id IS '主键';
COMMENT ON COLUMN sys_tenant.tenant_code IS '租户编码';
COMMENT ON COLUMN sys_tenant.tenant_name IS '租户名称';
COMMENT ON COLUMN sys_tenant.schema_name IS '数据库Schema名';
COMMENT ON COLUMN sys_tenant.hospital_level IS '医院等级';
COMMENT ON COLUMN sys_tenant.contact_name IS '联系人';
COMMENT ON COLUMN sys_tenant.contact_phone IS '联系电话';
COMMENT ON COLUMN sys_tenant.status IS '状态';
COMMENT ON COLUMN sys_tenant.expire_at IS '租户到期时间';
COMMENT ON COLUMN sys_tenant.max_users IS '最大用户数';
COMMENT ON COLUMN sys_tenant.created_at IS '创建时间';
COMMENT ON COLUMN sys_tenant.updated_at IS '更新时间';
COMMENT ON COLUMN sys_tenant.credit_code IS '统一社会信用代码';
COMMENT ON COLUMN sys_tenant.package_code IS '功能套餐编码';

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
COMMENT ON TABLE sys_tenant_subscription IS '租户订阅';
COMMENT ON COLUMN sys_tenant_subscription.id IS '主键';
COMMENT ON COLUMN sys_tenant_subscription.tenant_id IS '关联租户';
COMMENT ON COLUMN sys_tenant_subscription.plan_code IS '订阅计划编码';
COMMENT ON COLUMN sys_tenant_subscription.plan_name IS '计划名称';
COMMENT ON COLUMN sys_tenant_subscription.start_at IS '生效时间';
COMMENT ON COLUMN sys_tenant_subscription.end_at IS '失效时间';
COMMENT ON COLUMN sys_tenant_subscription.status IS '状态';
COMMENT ON COLUMN sys_tenant_subscription.created_at IS '创建时间';

CREATE TABLE IF NOT EXISTS platform_user (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    real_name VARCHAR(50),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE platform_user IS '平台管理员';
COMMENT ON COLUMN platform_user.id IS '主键';
COMMENT ON COLUMN platform_user.username IS '登录用户名';
COMMENT ON COLUMN platform_user.password_hash IS '密码哈希';
COMMENT ON COLUMN platform_user.real_name IS '真实姓名';
COMMENT ON COLUMN platform_user.is_active IS '是否启用';
COMMENT ON COLUMN platform_user.created_at IS '创建时间';

CREATE TABLE IF NOT EXISTS sys_menu (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    menu_code VARCHAR(100) UNIQUE NOT NULL,
    parent_code VARCHAR(100),
    menu_name VARCHAR(100) NOT NULL,
    menu_type VARCHAR(20) NOT NULL DEFAULT 'menu',
    path VARCHAR(200),
    icon VARCHAR(50),
    sort_order INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE sys_menu IS '菜单目录';
COMMENT ON COLUMN sys_menu.id IS '主键';
COMMENT ON COLUMN sys_menu.menu_code IS '菜单编码';
COMMENT ON COLUMN sys_menu.parent_code IS '上级分类编码';
COMMENT ON COLUMN sys_menu.menu_name IS '菜单名称';
COMMENT ON COLUMN sys_menu.menu_type IS '菜单类型';
COMMENT ON COLUMN sys_menu.path IS '前端路由路径';
COMMENT ON COLUMN sys_menu.icon IS '菜单图标';
COMMENT ON COLUMN sys_menu.sort_order IS '排序号';
COMMENT ON COLUMN sys_menu.is_active IS '是否启用';
COMMENT ON COLUMN sys_menu.created_at IS '创建时间';

CREATE TABLE IF NOT EXISTS sys_tenant_menu (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES sys_tenant(id) ON DELETE CASCADE,
    menu_code VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, menu_code)
);
COMMENT ON TABLE sys_tenant_menu IS '租户菜单授权';
COMMENT ON COLUMN sys_tenant_menu.id IS '主键';
COMMENT ON COLUMN sys_tenant_menu.tenant_id IS '关联租户';
COMMENT ON COLUMN sys_tenant_menu.menu_code IS '菜单编码';
COMMENT ON COLUMN sys_tenant_menu.created_at IS '创建时间';

CREATE TABLE IF NOT EXISTS sys_package (
    package_code VARCHAR(50) PRIMARY KEY,
    package_name VARCHAR(100) NOT NULL,
    max_users INTEGER DEFAULT 500,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE
);
COMMENT ON TABLE sys_package IS '功能套餐';
COMMENT ON COLUMN sys_package.package_code IS '功能套餐编码';
COMMENT ON COLUMN sys_package.package_name IS 'package名称';
COMMENT ON COLUMN sys_package.max_users IS '最大用户数';
COMMENT ON COLUMN sys_package.description IS '描述';
COMMENT ON COLUMN sys_package.is_active IS '是否启用';

CREATE TABLE IF NOT EXISTS sys_package_menu (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    package_code VARCHAR(50) NOT NULL REFERENCES sys_package(package_code) ON DELETE CASCADE,
    menu_code VARCHAR(100) NOT NULL,
    UNIQUE(package_code, menu_code)
);
COMMENT ON TABLE sys_package_menu IS '套餐菜单';
COMMENT ON COLUMN sys_package_menu.id IS '主键';
COMMENT ON COLUMN sys_package_menu.package_code IS '功能套餐编码';
COMMENT ON COLUMN sys_package_menu.menu_code IS '菜单编码';
