-- 科室 / 供应商 / 生产厂商 拼音简码（对应 Flyway V13）
-- 幂等，可重复执行

ALTER TABLE department ADD COLUMN IF NOT EXISTS pinyin_code VARCHAR(50);
ALTER TABLE supplier ADD COLUMN IF NOT EXISTS pinyin_code VARCHAR(50);
ALTER TABLE manufacturer ADD COLUMN IF NOT EXISTS pinyin_code VARCHAR(50);

CREATE INDEX IF NOT EXISTS idx_department_pinyin_code ON department(pinyin_code);
CREATE INDEX IF NOT EXISTS idx_supplier_pinyin_code ON supplier(pinyin_code);
CREATE INDEX IF NOT EXISTS idx_manufacturer_pinyin_code ON manufacturer(pinyin_code);
