-- Demo hospital seed (tenant_demo schema)
INSERT INTO campus (campus_code, campus_name) VALUES ('A', '主院区') ON CONFLICT DO NOTHING;
INSERT INTO department (dept_code, dept_name, is_clinical) VALUES ('001', '设备科', false) ON CONFLICT DO NOTHING;

-- admin / admin123 (BCrypt)
INSERT INTO sys_user (username, password_hash, real_name, is_active)
SELECT 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '系统管理员', true
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'admin');

INSERT INTO sys_role (role_code, role_name, permissions)
SELECT 'admin', '管理员', '["*"]'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'admin');
