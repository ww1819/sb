-- 科室 / 供应商 / 生产厂商 建表脚本（含拼音简码）
-- 依赖：campus、building 表（V1 基线）
-- 幂等：CREATE TABLE IF NOT EXISTS

-- 1.3 科室表
CREATE TABLE IF NOT EXISTS department (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    dept_code VARCHAR(3) UNIQUE NOT NULL,
    dept_name VARCHAR(100) NOT NULL,
    pinyin_code VARCHAR(50),
    parent_id UUID REFERENCES department(id),
    campus_id UUID REFERENCES campus(id),
    building_id UUID REFERENCES building(id),
    floor_number INTEGER,
    room_number VARCHAR(20),
    manager_id UUID,
    contact_phone VARCHAR(20),
    is_clinical BOOLEAN DEFAULT FALSE,
    sort_order INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_department_pinyin_code ON department(pinyin_code);

-- 2.2 供应商表
CREATE TABLE IF NOT EXISTS supplier (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    supplier_code VARCHAR(20) UNIQUE NOT NULL,
    supplier_name VARCHAR(200) NOT NULL,
    pinyin_code VARCHAR(50),
    unified_social_credit_code VARCHAR(18),
    legal_representative VARCHAR(50),
    contact_person VARCHAR(50),
    contact_phone VARCHAR(20),
    address TEXT,
    bank_account VARCHAR(50),
    bank_name VARCHAR(100),
    qualification_files JSONB,
    rating INTEGER,
    is_authorized BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_supplier_pinyin_code ON supplier(pinyin_code);

-- 2.3 生产厂商表
CREATE TABLE IF NOT EXISTS manufacturer (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    manufacturer_code VARCHAR(20) UNIQUE NOT NULL,
    manufacturer_name VARCHAR(200) NOT NULL,
    pinyin_code VARCHAR(50),
    country VARCHAR(50),
    is_domestic BOOLEAN,
    contact_phone VARCHAR(20),
    website VARCHAR(200),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_manufacturer_pinyin_code ON manufacturer(pinyin_code);
