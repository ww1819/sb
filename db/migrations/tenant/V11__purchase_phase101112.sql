-- MEIS V11: 采购101112阶段 — 验收专用表单、招标结构化、数据权限报表

-- 验收清单项
CREATE TABLE IF NOT EXISTS purchase_acceptance_item (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    acceptance_id UUID NOT NULL REFERENCES purchase_acceptance(id) ON DELETE CASCADE,
    item_name VARCHAR(200) NOT NULL,
    check_standard VARCHAR(500),
    check_result VARCHAR(20) DEFAULT 'pending',
    is_passed BOOLEAN,
    checker_id UUID REFERENCES sys_user(id),
    remark TEXT,
    sort_order INTEGER DEFAULT 0
);

-- 验收小组成员
CREATE TABLE IF NOT EXISTS purchase_acceptance_member (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    acceptance_id UUID NOT NULL REFERENCES purchase_acceptance(id) ON DELETE CASCADE,
    member_role VARCHAR(30) NOT NULL,
    user_id UUID REFERENCES sys_user(id),
    member_name VARCHAR(100),
    signed_at TIMESTAMP WITH TIME ZONE,
    signature_url VARCHAR(500),
    remark TEXT
);

-- 投标人（结构化）
CREATE TABLE IF NOT EXISTS purchase_bidder (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id UUID NOT NULL REFERENCES purchase_project(id) ON DELETE CASCADE,
    bidder_name VARCHAR(200) NOT NULL,
    bid_amount DECIMAL(15,2),
    contact_person VARCHAR(100),
    contact_phone VARCHAR(50),
    is_winner BOOLEAN DEFAULT false,
    bid_doc_url VARCHAR(500),
    remark TEXT,
    sort_order INTEGER DEFAULT 0
);

-- 质疑投诉记录
CREATE TABLE IF NOT EXISTS purchase_complaint (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id UUID NOT NULL REFERENCES purchase_project(id) ON DELETE CASCADE,
    complaint_date DATE,
    complaint_type VARCHAR(30) DEFAULT 'query',
    complainant VARCHAR(200),
    content TEXT,
    resolution TEXT,
    resolved_at DATE,
    status VARCHAR(20) DEFAULT 'open',
    attachment_url VARCHAR(500)
);

-- 招标过程事件（时间轴）
CREATE TABLE IF NOT EXISTS purchase_project_event (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id UUID NOT NULL REFERENCES purchase_project(id) ON DELETE CASCADE,
    event_type VARCHAR(30) NOT NULL,
    event_date DATE,
    event_title VARCHAR(200),
    event_desc TEXT,
    attachment_url VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_acceptance_item_acc ON purchase_acceptance_item(acceptance_id);
CREATE INDEX IF NOT EXISTS idx_acceptance_member_acc ON purchase_acceptance_member(acceptance_id);
CREATE INDEX IF NOT EXISTS idx_bidder_project ON purchase_bidder(project_id);
CREATE INDEX IF NOT EXISTS idx_complaint_project ON purchase_complaint(project_id);
CREATE INDEX IF NOT EXISTS idx_project_event ON purchase_project_event(project_id);

-- 字典
INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('acceptance_check_result', 'pending', '待检', 'pending', 1),
('acceptance_check_result', 'passed', '合格', 'passed', 2),
('acceptance_check_result', 'failed', '不合格', 'failed', 3),
('acceptance_member_role', 'quality', '质控', 'quality', 1),
('acceptance_member_role', 'engineering', '工程', 'engineering', 2),
('acceptance_member_role', 'clinical', '临床', 'clinical', 3),
('acceptance_member_role', 'equipment', '设备科', 'equipment', 4),
('complaint_type', 'query', '质疑', 'query', 1),
('complaint_type', 'complaint', '投诉', 'complaint', 2),
('complaint_status', 'open', '处理中', 'open', 1),
('complaint_status', 'resolved', '已办结', 'resolved', 2),
('project_event_type', 'notice', '发布公告', 'notice', 1),
('project_event_type', 'bid_open', '开标', 'bid_open', 2),
('project_event_type', 'evaluation', '评标', 'evaluation', 3),
('project_event_type', 'award', '定标', 'award', 4),
('project_event_type', 'contract', '签约', 'contract', 5)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

-- 采购预算报表菜单
INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, sort_order) VALUES
('purchase_report', 'mod_purchase', '预算执行', 'menu', '/purchase/report', 10)
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_package_menu (package_code, menu_code)
SELECT pkg, 'purchase_report' FROM (VALUES ('standard'), ('flagship')) AS p(pkg)
ON CONFLICT DO NOTHING;

INSERT INTO sys_tenant_menu (tenant_id, menu_code)
SELECT '00000000-0000-0000-0000-000000000001', 'purchase_report'
FROM sys_menu WHERE menu_code = 'purchase_report'
ON CONFLICT DO NOTHING;

UPDATE sys_role SET permissions = '{"menus":["mod_purchase","purchase_plan","purchase_project","purchase_contract","purchase_supplier","purchase_category","purchase_acceptance","purchase_manufacturer","purchase_dashboard","purchase_trace","purchase_report"],"buttons":[],"dataScope":"dept"}'::jsonb
WHERE role_code = 'purchase_staff';
