-- MEIS V7: 核心业务字段补缺 + 业务字典种子

-- 采购项目：招标标段/评标结果
ALTER TABLE purchase_project ADD COLUMN IF NOT EXISTS bid_sections TEXT;
ALTER TABLE purchase_project ADD COLUMN IF NOT EXISTS bid_evaluation TEXT;

-- 采购合同：安装验收、发票汇总
ALTER TABLE purchase_contract ADD COLUMN IF NOT EXISTS acceptance_status VARCHAR(20) DEFAULT 'pending';
ALTER TABLE purchase_contract ADD COLUMN IF NOT EXISTS invoice_summary TEXT;

-- 保养计划：计划编码、上次保养日、责任科室
ALTER TABLE maintenance_plan ADD COLUMN IF NOT EXISTS plan_code VARCHAR(30);
ALTER TABLE maintenance_plan ADD COLUMN IF NOT EXISTS last_maintained_at DATE;
ALTER TABLE maintenance_plan ADD COLUMN IF NOT EXISTS dept_id UUID REFERENCES department(id);

-- 巡检计划字段对齐
ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS plan_code VARCHAR(30);
ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS plan_name VARCHAR(200);
ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS dept_id UUID REFERENCES department(id);
ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS start_date DATE;
ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS end_date DATE;
ALTER TABLE inspection_plan ADD COLUMN IF NOT EXISTS frequency VARCHAR(30);

-- 巡检记录字段对齐
ALTER TABLE inspection_record ADD COLUMN IF NOT EXISTS record_no VARCHAR(30);
ALTER TABLE inspection_record ADD COLUMN IF NOT EXISTS result_summary TEXT;
ALTER TABLE inspection_record ADD COLUMN IF NOT EXISTS inspect_date DATE;

-- 业务字典种子
INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('project_status', 'draft', '草稿', 'draft', 1),
('project_status', 'bidding', '招标中', 'bidding', 2),
('project_status', 'awarded', '已定标', 'awarded', 3),
('project_status', 'closed', '已关闭', 'closed', 4),
('contract_status', 'active', '生效', 'active', 1),
('contract_status', 'completed', '已完成', 'completed', 2),
('contract_status', 'terminated', '已终止', 'terminated', 3),
('acceptance_status', 'pending', '待验收', 'pending', 1),
('acceptance_status', 'passed', '验收通过', 'passed', 2),
('acceptance_status', 'failed', '验收不通过', 'failed', 3),
('payment_stage', 'advance', '预付款', 'advance', 1),
('payment_stage', 'delivery', '到货款', 'delivery', 2),
('payment_stage', 'acceptance', '验收款', 'acceptance', 3),
('payment_stage', 'warranty', '质保金', 'warranty', 4),
('payment_status', 'pending', '待付款', 'pending', 1),
('payment_status', 'paid', '已付款', 'paid', 2),
('entry_type', 'purchase', '采购入库', 'purchase', 1),
('entry_type', 'donation', '捐赠入库', 'donation', 2),
('entry_type', 'transfer_in', '调拨入库', 'transfer_in', 3),
('entry_status', 'draft', '草稿', 'draft', 1),
('entry_status', 'pending', '待验收', 'pending', 2),
('entry_status', 'completed', '已完成', 'completed', 3),
('outbound_status', 'draft', '草稿', 'draft', 1),
('outbound_status', 'issued', '已发放', 'issued', 2),
('transfer_type', 'dept', '科室内流转', 'dept', 1),
('transfer_type', 'campus', '院区间调拨', 'campus', 2),
('transfer_type', 'external', '院外调出', 'external', 3),
('transfer_status', 'pending', '待审批', 'pending', 1),
('transfer_status', 'approved', '已批准', 'approved', 2),
('transfer_status', 'completed', '已完成', 'completed', 3),
('check_type', 'annual', '年度盘点', 'annual', 1),
('check_type', 'spot', '抽盘', 'spot', 2),
('check_type', 'dept', '科室盘点', 'dept', 3),
('check_status', 'planning', '计划中', 'planning', 1),
('check_status', 'in_progress', '盘点中', 'in_progress', 2),
('check_status', 'completed', '已完成', 'completed', 3),
('condition_status', 'good', '良好', 'good', 1),
('condition_status', 'fair', '一般', 'fair', 2),
('condition_status', 'poor', '较差', 'poor', 3),
('scrap_type', 'obsolete', '技术淘汰', 'obsolete', 1),
('scrap_type', 'damaged', '损坏报废', 'damaged', 2),
('scrap_status', 'draft', '草稿', 'draft', 1),
('scrap_status', 'pending', '审批中', 'pending', 2),
('scrap_status', 'approved', '已批准', 'approved', 3),
('scrap_status', 'disposed', '已处置', 'disposed', 4),
('disposal_method', 'auction', '拍卖', 'auction', 1),
('disposal_method', 'recycle', '回收', 'recycle', 2),
('disposal_method', 'destroy', '销毁', 'destroy', 3),
('inspection_type', 'daily', '日常巡检', 'daily', 1),
('inspection_type', 'special', '专项巡检', 'special', 2),
('inspection_frequency', 'daily', '每日', 'daily', 1),
('inspection_frequency', 'weekly', '每周', 'weekly', 2),
('inspection_frequency', 'monthly', '每月', 'monthly', 3),
('plan_status', 'active', '进行中', 'active', 1),
('plan_status', 'completed', '已完成', 'completed', 2),
('inspection_status', 'pending', '待巡检', 'pending', 1),
('inspection_status', 'completed', '已完成', 'completed', 2),
('report_method', 'web', '网页报修', 'web', 1),
('report_method', 'phone', '电话报修', 'phone', 2),
('report_method', 'app', 'APP报修', 'app', 3),
('wo_status', 'reported', '已报修', 'reported', 1),
('wo_status', 'dispatched', '已派工', 'dispatched', 2),
('wo_status', 'in_progress', '维修中', 'in_progress', 3),
('wo_status', 'completed', '待验收', 'completed', 4),
('wo_status', 'accepted', '已验收', 'accepted', 5),
('wo_status', 'closed', '已关闭', 'closed', 6),
('verify_result', 'pass', '通过', 'pass', 1),
('verify_result', 'fail', '不通过', 'fail', 2),
('maintenance_level', 'daily', '日常保养', 'daily', 1),
('maintenance_level', 'level1', '一级保养', 'level1', 2),
('maintenance_level', 'level2', '二级保养', 'level2', 3),
('cycle_type', 'day', '按天', 'day', 1),
('cycle_type', 'week', '按周', 'week', 2),
('cycle_type', 'month', '按月', 'month', 3),
('cycle_type', 'year', '按年', 'year', 4),
('maintain_plan_status', 'active', '激活', 'active', 1),
('maintain_plan_status', 'paused', '暂停', 'paused', 2),
('maintain_plan_status', 'completed', '完成', 'completed', 3),
('maintain_result', 'pass', '合格', 'pass', 1),
('maintain_result', 'fail', '不合格', 'fail', 2),
('maintain_record_status', 'draft', '草稿', 'draft', 1),
('maintain_record_status', 'submitted', '已提交', 'submitted', 2)
ON CONFLICT (dict_type, dict_code) DO NOTHING;
