-- =============================================================================
-- MEIS tenant column patches (idempotent)
-- Sync existing business schemas with meis-tenant migrations (V1 + V2 + V17)
-- Usage: powershell -File scripts/apply-tenant-patches.ps1 -Schema tenant_demo
-- =============================================================================

-- inventory_check audit (V17; legacy tenants may have flyway v16 without column)
ALTER TABLE inventory_check ADD COLUMN IF NOT EXISTS audit_status VARCHAR(20) DEFAULT 'pending';
COMMENT ON COLUMN inventory_check.audit_status IS '审核状态';

UPDATE inventory_check
SET audit_status = 'approved'
WHERE approved_by IS NOT NULL AND COALESCE(audit_status, 'pending') = 'pending';

INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('audit_status', 'pending', '待审核', 'pending', 1),
('audit_status', 'approved', '已审核', 'approved', 2)
ON CONFLICT (dict_type, dict_code) DO NOTHING;
