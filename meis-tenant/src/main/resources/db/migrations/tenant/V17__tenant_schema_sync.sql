-- 存量租户 schema 同步（V1–V16 合并后，部分版本号已占用但列未落库）
-- 新租户：V1 建表 + V2 扩展已包含下列变更，本脚本可幂等重复执行

ALTER TABLE inventory_check ADD COLUMN IF NOT EXISTS audit_status VARCHAR(20) DEFAULT 'pending';
COMMENT ON COLUMN inventory_check.audit_status IS '审核状态';

UPDATE inventory_check
SET audit_status = 'approved'
WHERE approved_by IS NOT NULL AND COALESCE(audit_status, 'pending') = 'pending';

INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('audit_status', 'pending', '待审核', 'pending', 1),
('audit_status', 'approved', '已审核', 'approved', 2)
ON CONFLICT (dict_type, dict_code) DO NOTHING;
