-- 设备台账扩展字段：规格、注册证、计量与使用年限
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS specification VARCHAR(200);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS registration_no VARCHAR(100);
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS production_date DATE;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS service_life_years INTEGER;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS calibration_period_days INTEGER;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS last_calibration_date DATE;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS next_calibration_date DATE;
ALTER TABLE medical_device ADD COLUMN IF NOT EXISTS service_expiry_date DATE;

CREATE INDEX IF NOT EXISTS idx_device_production_date ON medical_device(production_date);
CREATE INDEX IF NOT EXISTS idx_device_next_calibration ON medical_device(next_calibration_date);
CREATE INDEX IF NOT EXISTS idx_device_service_expiry ON medical_device(service_expiry_date);
