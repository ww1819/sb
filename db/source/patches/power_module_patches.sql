-- 模块14：电流监测 — 租户补丁（幂等）

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS power_base_station (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    station_code VARCHAR(50) NOT NULL UNIQUE,
    station_name VARCHAR(200) NOT NULL,
    campus_id UUID REFERENCES campus(id),
    location VARCHAR(200),
    ip_address VARCHAR(50),
    protocol_type VARCHAR(30) DEFAULT 'mqtt',
    status VARCHAR(20) DEFAULT 'online',
    is_active BOOLEAN DEFAULT TRUE,
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS power_tag (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tag_code VARCHAR(50) NOT NULL UNIQUE,
    tag_name VARCHAR(200) NOT NULL,
    device_id UUID REFERENCES medical_device(id),
    station_id UUID REFERENCES power_base_station(id),
    rated_power DECIMAL(10,2),
    install_date DATE,
    is_active BOOLEAN DEFAULT TRUE,
    remark TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS power_device_status (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID REFERENCES medical_device(id),
    tag_id UUID UNIQUE REFERENCES power_tag(id),
    device_code VARCHAR(20),
    device_name VARCHAR(200),
    current_amp DECIMAL(10,3),
    voltage DECIMAL(10,2),
    power_watt DECIMAL(10,2),
    work_state VARCHAR(20) DEFAULT 'offline',
    collected_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS power_monitor_record (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID REFERENCES medical_device(id),
    tag_id UUID REFERENCES power_tag(id),
    device_code VARCHAR(20),
    device_name VARCHAR(200),
    record_date DATE NOT NULL,
    run_hours DECIMAL(8,2) DEFAULT 0,
    idle_hours DECIMAL(8,2) DEFAULT 0,
    offline_hours DECIMAL(8,2) DEFAULT 0,
    avg_current DECIMAL(10,3),
    peak_current DECIMAL(10,3),
    energy_kwh DECIMAL(12,3) DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(device_id, record_date)
);

CREATE INDEX IF NOT EXISTS idx_power_tag_device ON power_tag(device_id);
CREATE INDEX IF NOT EXISTS idx_power_tag_station ON power_tag(station_id);
CREATE INDEX IF NOT EXISTS idx_power_record_date ON power_monitor_record(record_date);

INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order) VALUES
('power_protocol_type', 'mqtt', 'MQTT', 'mqtt', 1),
('power_protocol_type', 'modbus', 'Modbus', 'modbus', 2),
('power_protocol_type', 'http', 'HTTP', 'http', 3),
('power_station_status', 'online', '在线', 'online', 1),
('power_station_status', 'offline', '离线', 'offline', 2),
('power_station_status', 'maintenance', '维护中', 'maintenance', 3),
('power_work_state', 'running', '运行中', 'running', 1),
('power_work_state', 'idle', '待机', 'idle', 2),
('power_work_state', 'offline', '离线', 'offline', 3),
('power_work_state', 'alarm', '告警', 'alarm', 4)
ON CONFLICT (dict_type, dict_code) DO NOTHING;
