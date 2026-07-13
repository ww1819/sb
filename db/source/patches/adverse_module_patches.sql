-- 模块9：不良事件 — 字典种子（幂等）

INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('adverse_event_type', 'malfunction', '设备故障', 'malfunction', 1),
('adverse_event_type', 'injury', '人员伤害', 'injury', 2),
('adverse_event_type', 'misuse', '使用不当', 'misuse', 3),
('adverse_event_type', 'quality', '质量问题', 'quality', 4),
('adverse_event_type', 'other', '其他', 'other', 5),
('adverse_severity', 'minor', '轻微', 'minor', 1),
('adverse_severity', 'moderate', '一般', 'moderate', 2),
('adverse_severity', 'serious', '严重', 'serious', 3),
('adverse_severity', 'critical', '重大', 'critical', 4),
('adverse_status', 'reported', '已上报', 'reported', 1),
('adverse_status', 'handling', '处理中', 'handling', 2),
('adverse_status', 'reviewed', '已审核', 'reviewed', 3),
('adverse_status', 'closed', '已结案', 'closed', 4)
ON CONFLICT (dict_type, dict_code) DO NOTHING;
