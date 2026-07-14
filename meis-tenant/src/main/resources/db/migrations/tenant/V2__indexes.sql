-- MEIS indexes: CREATE INDEX + COMMENT ON INDEX（槽位 V2__indexes.sql）

CREATE INDEX idx_op_log_user ON sys_operation_log(user_id);
COMMENT ON INDEX idx_op_log_user IS '索引：操作日志.关联用户';

CREATE INDEX idx_op_log_time ON sys_operation_log(created_at DESC);
COMMENT ON INDEX idx_op_log_time IS '索引：操作日志.创建时间';

CREATE INDEX idx_category_parent ON medical_device_category(parent_code);
COMMENT ON INDEX idx_category_parent IS '索引：医疗器械分类.上级分类编码';

CREATE INDEX idx_device_dept ON medical_device(dept_id);
COMMENT ON INDEX idx_device_dept IS '索引：医疗设备台账.所属科室';

CREATE INDEX idx_device_status ON medical_device(device_status);
COMMENT ON INDEX idx_device_status IS '索引：医疗设备台账.设备运行状态';

CREATE INDEX idx_device_category ON medical_device(category_id);
COMMENT ON INDEX idx_device_category IS '索引：医疗设备台账.设备分类';

CREATE INDEX idx_device_enable_date ON medical_device(enable_date);
COMMENT ON INDEX idx_device_enable_date IS '索引：医疗设备台账.启用日期';

CREATE INDEX idx_check_item_device ON inventory_check_item(device_id);
COMMENT ON INDEX idx_check_item_device IS '索引：资产盘点明细.关联设备';

CREATE INDEX idx_wo_device ON repair_workorder(device_id);
COMMENT ON INDEX idx_wo_device IS '索引：维修工单.关联设备';

CREATE INDEX idx_wo_status ON repair_workorder(status);
COMMENT ON INDEX idx_wo_status IS '索引：维修工单.状态';

CREATE INDEX idx_wo_report_time ON repair_workorder(report_time DESC);
COMMENT ON INDEX idx_wo_report_time IS '索引：维修工单.报修时间';

CREATE INDEX idx_wo_assigned_user ON repair_workorder(assigned_user_id);
COMMENT ON INDEX idx_wo_assigned_user IS '索引：维修工单.指派负责人';

CREATE INDEX idx_maint_plan_device ON maintenance_plan(device_id);
COMMENT ON INDEX idx_maint_plan_device IS '索引：保养计划.关联设备';

CREATE INDEX idx_maint_plan_due ON maintenance_plan(next_due_date);
COMMENT ON INDEX idx_maint_plan_due IS '索引：保养计划.下次到期日';

CREATE INDEX idx_maint_record_device ON maintenance_record(device_id);
COMMENT ON INDEX idx_maint_record_device IS '索引：保养执行记录.关联设备';

CREATE INDEX idx_maint_record_time ON maintenance_record(execute_start_time DESC);
COMMENT ON INDEX idx_maint_record_time IS '索引：保养执行记录.执行开始时间';

CREATE INDEX idx_adverse_event_device ON adverse_event(device_id);
COMMENT ON INDEX idx_adverse_event_device IS '索引：adverse event.关联设备';

CREATE INDEX idx_adverse_event_time ON adverse_event(report_time DESC);
COMMENT ON INDEX idx_adverse_event_time IS '索引：adverse event.报修时间';

CREATE INDEX idx_metrology_device ON metrology_record(device_id);
COMMENT ON INDEX idx_metrology_device IS '索引：metrology record.关联设备';

CREATE INDEX idx_metrology_due ON metrology_record(next_due_date);
COMMENT ON INDEX idx_metrology_due IS '索引：metrology record.下次到期日';

CREATE INDEX idx_metrology_type_parent ON metrology_type(parent_id);
COMMENT ON INDEX idx_metrology_type_parent IS '索引：metrology type.上级类型';

CREATE INDEX idx_metrology_type_group ON metrology_type(classification_group);
COMMENT ON INDEX idx_metrology_type_group IS '索引：metrology type.分类维度';

CREATE INDEX idx_perf_test_device ON performance_test(device_id);
COMMENT ON INDEX idx_perf_test_device IS '索引：performance test.关联设备';

CREATE INDEX idx_usage_device ON device_usage_record(device_id);
COMMENT ON INDEX idx_usage_device IS '索引：device usage record.关联设备';

CREATE INDEX idx_usage_date ON device_usage_record(usage_date DESC);
COMMENT ON INDEX idx_usage_date IS '索引：device usage record.usage日期';

CREATE INDEX idx_cost_device ON device_cost_record(device_id);
COMMENT ON INDEX idx_cost_device IS '索引：device cost record.关联设备';

CREATE INDEX idx_cost_date ON device_cost_record(cost_date DESC);
COMMENT ON INDEX idx_cost_date IS '索引：device cost record.cost日期';

CREATE INDEX idx_cost_type ON device_cost_record(cost_type);
COMMENT ON INDEX idx_cost_type IS '索引：device cost record.cost type';

CREATE INDEX idx_benefit_device ON device_benefit_summary(device_id);
COMMENT ON INDEX idx_benefit_device IS '索引：device benefit summary.关联设备';

CREATE INDEX idx_benefit_period ON device_benefit_summary(summary_year, summary_month);
COMMENT ON INDEX idx_benefit_period IS '索引：device benefit summary.summary year';

CREATE INDEX idx_dict_type ON sys_dict(dict_type);
COMMENT ON INDEX idx_dict_type IS '索引：sys dict.dict type';

CREATE INDEX idx_notification_user ON sys_notification USING GIN(target_users);
COMMENT ON INDEX idx_notification_user IS '索引：sys notification.通知目标用户列表';

CREATE INDEX idx_notification_created ON sys_notification(created_at DESC);
COMMENT ON INDEX idx_notification_created IS '索引：sys notification.创建时间';

CREATE INDEX IF NOT EXISTS idx_purchase_acceptance_status ON purchase_acceptance(acceptance_status);
COMMENT ON INDEX idx_purchase_acceptance_status IS '索引：安装验收.安装验收状态';

CREATE INDEX IF NOT EXISTS idx_acceptance_member_acc ON purchase_acceptance_member(acceptance_id);
COMMENT ON INDEX idx_acceptance_member_acc IS '索引：purchase acceptance member.安装验收单';

CREATE INDEX IF NOT EXISTS idx_bidder_project ON purchase_bidder(project_id);
COMMENT ON INDEX idx_bidder_project IS '索引：purchase bidder.采购项目';

CREATE INDEX IF NOT EXISTS idx_complaint_project ON purchase_complaint(project_id);
COMMENT ON INDEX idx_complaint_project IS '索引：purchase complaint.采购项目';

CREATE INDEX IF NOT EXISTS idx_project_event ON purchase_project_event(project_id);
COMMENT ON INDEX idx_project_event IS '索引：purchase project event.采购项目';

CREATE INDEX IF NOT EXISTS idx_purchase_alert_type ON purchase_alert_snapshot(alert_type);
COMMENT ON INDEX idx_purchase_alert_type IS '索引：purchase alert snapshot.alert type';

CREATE INDEX IF NOT EXISTS idx_supplier_pinyin_code ON supplier(pinyin_code);
COMMENT ON INDEX idx_supplier_pinyin_code IS '索引：供应商.拼音简码（检索）';

CREATE INDEX IF NOT EXISTS idx_manufacturer_pinyin_code ON manufacturer(pinyin_code);
COMMENT ON INDEX idx_manufacturer_pinyin_code IS '索引：生产厂商.拼音简码（检索）';

CREATE INDEX IF NOT EXISTS idx_device_next_calibration ON medical_device(next_calibration_date);
COMMENT ON INDEX idx_device_next_calibration IS '索引：医疗设备台账.下次检定日期';

CREATE INDEX IF NOT EXISTS idx_device_service_expiry ON medical_device(service_expiry_date);
COMMENT ON INDEX idx_device_service_expiry IS '索引：医疗设备台账.使用年限到期日';

CREATE INDEX IF NOT EXISTS idx_wo_event_wo ON repair_workorder_event(workorder_id, created_at);
COMMENT ON INDEX idx_wo_event_wo IS '索引：维修工单事件.工单+时间';

CREATE INDEX IF NOT EXISTS idx_wo_process_wo ON repair_workorder_process(workorder_id, created_at);
COMMENT ON INDEX idx_wo_process_wo IS '索引：维修工单流程记录.工单+时间';

CREATE INDEX IF NOT EXISTS idx_power_reading_tag_read_at ON power_current_reading(tag_id, read_at DESC);
COMMENT ON INDEX idx_power_reading_tag_read_at IS '索引：电流读数.标签+读取时间';

CREATE INDEX IF NOT EXISTS idx_power_reading_station_read_at ON power_current_reading(station_id, read_at DESC);
COMMENT ON INDEX idx_power_reading_station_read_at IS '索引：电流读数.基站+读取时间';

CREATE INDEX IF NOT EXISTS idx_power_tag_bind_log_tag ON power_tag_bind_log(tag_id, bound_at DESC);
COMMENT ON INDEX idx_power_tag_bind_log_tag IS '索引：标签绑定历史.标签+绑定时间';

CREATE INDEX IF NOT EXISTS idx_sys_entity_change_log_entity
    ON sys_entity_change_log (entity_type, entity_id, created_at DESC);
COMMENT ON INDEX idx_sys_entity_change_log_entity IS '索引：实体变更记录.类型+实体+时间';

CREATE INDEX IF NOT EXISTS idx_wo_segment_wo ON repair_workorder_segment(workorder_id, started_at);
COMMENT ON INDEX idx_wo_segment_wo IS '索引：维修进程段.工单+开始时间';

CREATE INDEX IF NOT EXISTS idx_wo_segment_part_seg ON repair_workorder_segment_part(segment_id);
COMMENT ON INDEX idx_wo_segment_part_seg IS '索引：维修进程段配件.关联进程段';
