-- System RBAC: warehouse, user permissions snapshot, button permission dict

CREATE TABLE IF NOT EXISTS warehouse (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    warehouse_code VARCHAR(50) UNIQUE NOT NULL,
    warehouse_name VARCHAR(100) NOT NULL,
    campus_id UUID REFERENCES campus(id),
    dept_id UUID REFERENCES department(id),
    address VARCHAR(500),
    manager_id UUID REFERENCES sys_user(id),
    is_active BOOLEAN DEFAULT TRUE,
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_warehouse_campus ON warehouse(campus_id);

ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS permissions JSONB;
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS permission_mode VARCHAR(20) DEFAULT 'synced';

-- Default warehouse for demo campus
INSERT INTO warehouse (warehouse_code, warehouse_name, campus_id, address)
SELECT 'WH01', '中心库房', c.id, '主院区设备科库房'
FROM campus c
WHERE c.campus_code = 'A'
  AND NOT EXISTS (SELECT 1 FROM warehouse w WHERE w.warehouse_code = 'WH01');

INSERT INTO warehouse (warehouse_code, warehouse_name, campus_id, address)
SELECT 'WH02', '备件库房', c.id, '主院区备件库'
FROM campus c
WHERE c.campus_code = 'A'
  AND NOT EXISTS (SELECT 1 FROM warehouse w WHERE w.warehouse_code = 'WH02');

-- Button permission dict entries
INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order)
SELECT v.dict_type, v.dict_code, v.dict_label, v.dict_value, v.sort_order
FROM (VALUES
    ('button_perm', 'add', '新增', 'add', 1),
    ('button_perm', 'edit', '编辑', 'edit', 2),
    ('button_perm', 'delete', '删除', 'delete', 3),
    ('button_perm', 'export', '导出', 'export', 4),
    ('button_perm', 'import', '导入', 'import', 5),
    ('button_perm', 'approve', '审批', 'approve', 6),
    ('button_perm', 'print', '打印', 'print', 7)
) AS v(dict_type, dict_code, dict_label, dict_value, sort_order)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict d WHERE d.dict_type = v.dict_type AND d.dict_code = v.dict_code
);

-- Copy admin role permissions to admin user
UPDATE sys_user u
SET permissions = r.permissions,
    permission_mode = 'synced',
    updated_at = NOW()
FROM sys_role r
WHERE u.username = 'admin'
  AND r.role_code = 'admin'
  AND u.role_ids IS NOT NULL
  AND r.id = ANY(u.role_ids)
  AND u.permissions IS NULL;

-- Users with role but no permissions: copy from first role
UPDATE sys_user u
SET permissions = r.permissions,
    permission_mode = 'synced',
    updated_at = NOW()
FROM sys_role r
WHERE u.permissions IS NULL
  AND u.role_ids IS NOT NULL
  AND cardinality(u.role_ids) > 0
  AND r.id = u.role_ids[1];

-- Enforce single role: keep first only
UPDATE sys_user
SET role_ids = ARRAY[role_ids[1]]
WHERE role_ids IS NOT NULL AND cardinality(role_ids) > 1;
