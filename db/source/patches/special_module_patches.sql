-- 模块10：特种设备 — 字典种子（幂等）

INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('special_type', 'radiation', '放射辐射类', 'radiation', 1),
('special_type', 'pressure', '压力容器类', 'pressure', 2),
('special_type', 'elevator', '电梯类', 'elevator', 3),
('special_type', 'other', '其他特种', 'other', 4),
('criticality_level', 'critical', '极高', 'critical', 1),
('criticality_level', 'high', '高', 'high', 2),
('criticality_level', 'medium', '中', 'medium', 3),
('standby_status', 'ready', '待用', 'ready', 1),
('standby_status', 'in_use', '使用中', 'in_use', 2),
('standby_status', 'maintenance', '维护中', 'maintenance', 3),
('lease_status', 'active', '租赁中', 'active', 1),
('lease_status', 'expired', '已到期', 'expired', 2),
('lease_status', 'returned', '已退租', 'returned', 3),
('allocation_status', 'pending', '待审批', 'pending', 1),
('allocation_status', 'approved', '已调配', 'approved', 2),
('allocation_status', 'returned', '已归还', 'returned', 3),
('urgency_level', 'normal', '一般', 'normal', 1),
('urgency_level', 'urgent', '紧急', 'urgent', 2),
('urgency_level', 'critical', '特急', 'critical', 3)
ON CONFLICT (dict_type, dict_code) DO NOTHING;
