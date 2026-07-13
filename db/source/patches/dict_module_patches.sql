-- 模块1：基础字典 — 仅补列与种子（幂等）
ALTER TABLE supplier ADD COLUMN IF NOT EXISTS pinyin_code VARCHAR(50);
ALTER TABLE manufacturer ADD COLUMN IF NOT EXISTS pinyin_code VARCHAR(50);
ALTER TABLE department ADD COLUMN IF NOT EXISTS pinyin_code VARCHAR(50);
ALTER TABLE warehouse ADD COLUMN IF NOT EXISTS warehouse_type VARCHAR(30) DEFAULT 'device';
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS asset_category_id UUID;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS finance_category_id UUID;

INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('warehouse_type', 'device', '设备库', 'device', 1),
('warehouse_type', 'spare', '备件库', 'spare', 2),
('warehouse_type', 'consumable', '耗材库', 'consumable', 3),
('unit_type', 'quantity', '数量', 'quantity', 1),
('unit_type', 'weight', '重量', 'weight', 2),
('unit_type', 'volume', '体积', 'volume', 3)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

INSERT INTO unit_dict (unit_code, unit_name, unit_type, sort_order) VALUES
('pcs', '个', 'quantity', 1),
('set', '套', 'quantity', 2),
('box', '盒', 'quantity', 3),
('piece', '件', 'quantity', 4),
('unit', '台', 'quantity', 5),
('kg', '千克', 'weight', 10),
('g', '克', 'weight', 11),
('l', '升', 'volume', 20),
('ml', '毫升', 'volume', 21)
ON CONFLICT (unit_code) DO NOTHING;
