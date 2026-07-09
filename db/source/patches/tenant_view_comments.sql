
-- =============================================================================
-- 视图注释（V1 视图未嵌入 COMMENT ON，在此补全）
-- =============================================================================
COMMENT ON VIEW v_device_full_info IS '设备完整信息视图';
COMMENT ON COLUMN v_device_full_info.id IS '设备主键';
COMMENT ON COLUMN v_device_full_info.device_code IS '设备编码';
COMMENT ON COLUMN v_device_full_info.device_name IS '设备名称';
COMMENT ON COLUMN v_device_full_info.brand IS '品牌';
COMMENT ON COLUMN v_device_full_info.model IS '型号';
COMMENT ON COLUMN v_device_full_info.serial_number IS '出厂序列号';
COMMENT ON COLUMN v_device_full_info.category_name IS '分类名称';
COMMENT ON COLUMN v_device_full_info.manufacturer_name IS '生产厂商';
COMMENT ON COLUMN v_device_full_info.supplier_name IS '供应商';
COMMENT ON COLUMN v_device_full_info.original_value IS '原值';
COMMENT ON COLUMN v_device_full_info.net_value IS '净值';
COMMENT ON COLUMN v_device_full_info.campus_name IS '院区名称';
COMMENT ON COLUMN v_device_full_info.building_name IS '建筑物名称';
COMMENT ON COLUMN v_device_full_info.dept_name IS '科室名称';
COMMENT ON COLUMN v_device_full_info.location_detail IS '位置详情';
COMMENT ON COLUMN v_device_full_info.enable_date IS '启用日期';
COMMENT ON COLUMN v_device_full_info.warranty_end_date IS '保修截止日期';
COMMENT ON COLUMN v_device_full_info.device_status IS '设备状态';
COMMENT ON COLUMN v_device_full_info.risk_level IS '风险等级';
COMMENT ON COLUMN v_device_full_info.is_life_support IS '是否生命支持设备';
COMMENT ON COLUMN v_device_full_info.is_emergency IS '是否应急设备';
COMMENT ON COLUMN v_device_full_info.created_at IS '创建时间';

COMMENT ON VIEW v_device_benefit IS '设备效益分析视图';
COMMENT ON COLUMN v_device_benefit.device_code IS '设备编码';
COMMENT ON COLUMN v_device_benefit.device_name IS '设备名称';
COMMENT ON COLUMN v_device_benefit.dept_id IS '所属科室';
COMMENT ON COLUMN v_device_benefit.dept_name IS '科室名称';
COMMENT ON COLUMN v_device_benefit.summary_year IS '汇总年度';
COMMENT ON COLUMN v_device_benefit.summary_month IS '汇总月份';
COMMENT ON COLUMN v_device_benefit.total_revenue IS '总收入';
COMMENT ON COLUMN v_device_benefit.total_cost IS '总成本';
COMMENT ON COLUMN v_device_benefit.net_profit IS '净利润';
COMMENT ON COLUMN v_device_benefit.profit_rate IS '利润率';
COMMENT ON COLUMN v_device_benefit.utilization_rate IS '使用率';
COMMENT ON COLUMN v_device_benefit.benefit_level IS '效益等级';
