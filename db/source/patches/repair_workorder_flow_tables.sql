-- 维修工单事件/流程表（老租户缺表兜底，幂等）
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
SET search_path TO tenant_demo, public;

CREATE TABLE IF NOT EXISTS repair_workorder_event (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workorder_id UUID NOT NULL REFERENCES repair_workorder(id) ON DELETE CASCADE,
    event_type VARCHAR(40) NOT NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30),
    from_sub_status VARCHAR(30),
    to_sub_status VARCHAR(30),
    operator_id UUID,
    engineer_id UUID,
    from_engineer_id UUID,
    to_engineer_id UUID,
    remark TEXT,
    extra_json JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS repair_workorder_process (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workorder_id UUID NOT NULL REFERENCES repair_workorder(id) ON DELETE CASCADE,
    action_type VARCHAR(40) NOT NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30),
    from_sub_status VARCHAR(30),
    to_sub_status VARCHAR(30),
    engineer_id UUID,
    from_engineer_id UUID,
    to_engineer_id UUID,
    operator_id UUID,
    solution_description TEXT,
    labor_cost DECIMAL(10,2),
    parts_cost DECIMAL(10,2),
    total_cost DECIMAL(10,2),
    verify_result VARCHAR(20),
    verify_comment TEXT,
    satisfaction_rating INTEGER,
    satisfaction_comment TEXT,
    skip_verify BOOLEAN,
    remark TEXT,
    extra_json JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    is_deleted SMALLINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by UUID
);

CREATE INDEX IF NOT EXISTS idx_wo_event_wo ON repair_workorder_event(workorder_id, created_at);
CREATE INDEX IF NOT EXISTS idx_wo_process_wo ON repair_workorder_process(workorder_id, created_at);

INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order)
SELECT 'wo_status', 'draft', '未提交', 'draft', 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict WHERE dict_type = 'wo_status' AND dict_code = 'draft'
);

INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order)
SELECT 'wo_status', 'verify_rejected', '拒绝验收', 'verify_rejected', 7
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict WHERE dict_type = 'wo_status' AND dict_code = 'verify_rejected'
);
