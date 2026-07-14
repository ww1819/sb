-- MEIS indexes: CREATE INDEX + COMMENT ON INDEX（槽位 V2__indexes.sql）

CREATE INDEX IF NOT EXISTS idx_tenant_code ON sys_tenant(tenant_code);
COMMENT ON INDEX idx_tenant_code IS '索引：租户.租户编码';

CREATE INDEX IF NOT EXISTS idx_tenant_menu_tenant ON sys_tenant_menu(tenant_id);
COMMENT ON INDEX idx_tenant_menu_tenant IS '索引：租户菜单授权.关联租户';
