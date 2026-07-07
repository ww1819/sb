-- MEIS V12: 采购131415阶段 — 看板预警快照去重

CREATE TABLE IF NOT EXISTS purchase_alert_snapshot (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    alert_key VARCHAR(80) NOT NULL UNIQUE,
    alert_type VARCHAR(30) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message TEXT,
    level VARCHAR(20) DEFAULT 'warning',
    ref_code VARCHAR(60),
    notified_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_purchase_alert_type ON purchase_alert_snapshot(alert_type);
