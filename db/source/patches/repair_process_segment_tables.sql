-- REP-05：维修进程类型 + 工单进程段 + 段配件明细

CREATE TABLE IF NOT EXISTS repair_process_type (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    type_code VARCHAR(40) NOT NULL UNIQUE,
    type_name VARCHAR(100) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    can_add_parts BOOLEAN NOT NULL DEFAULT FALSE,
    can_engineer_add BOOLEAN NOT NULL DEFAULT FALSE,
    engineer_add_rule VARCHAR(40),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    is_deleted SMALLINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID
);

CREATE TABLE IF NOT EXISTS repair_workorder_segment (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workorder_id UUID NOT NULL REFERENCES repair_workorder(id) ON DELETE CASCADE,
    process_type_id UUID NOT NULL REFERENCES repair_process_type(id),
    user_id UUID REFERENCES sys_user(id),
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMPTZ,
    remark TEXT,
    verify_comment TEXT,
    auto_created BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    is_deleted SMALLINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID
);

CREATE INDEX IF NOT EXISTS idx_wo_segment_wo ON repair_workorder_segment(workorder_id, started_at);
CREATE INDEX IF NOT EXISTS idx_wo_segment_open ON repair_workorder_segment(workorder_id) WHERE ended_at IS NULL;

CREATE TABLE IF NOT EXISTS repair_workorder_segment_part (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    segment_id UUID NOT NULL REFERENCES repair_workorder_segment(id) ON DELETE CASCADE,
    spare_part_id UUID REFERENCES spare_part(id),
    quantity INTEGER NOT NULL DEFAULT 1,
    unit_price DECIMAL(10,2),
    total_price DECIMAL(10,2),
    remark TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    is_deleted SMALLINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID
);

CREATE INDEX IF NOT EXISTS idx_wo_segment_part_seg ON repair_workorder_segment_part(segment_id);

INSERT INTO repair_process_type (type_code, type_name, sort_order, can_add_parts, can_engineer_add, engineer_add_rule)
SELECT v.type_code, v.type_name, v.sort_order, v.can_add_parts, v.can_engineer_add, v.engineer_add_rule
FROM (VALUES
    ('internal', '院内维修中', 1, true, true, NULL),
    ('external', '院外维修中', 2, true, true, NULL),
    ('waiting_parts', '等待配件中', 3, true, true, NULL),
    ('verify_rejected', '拒绝验收', 4, false, false, 'system_only'),
    ('pending_verify', '已维修待验收', 5, false, true, 'verify_rejected_only'),
    ('verified', '已验收', 6, false, false, 'system_only')
) AS v(type_code, type_name, sort_order, can_add_parts, can_engineer_add, engineer_add_rule)
WHERE NOT EXISTS (SELECT 1 FROM repair_process_type LIMIT 1);
