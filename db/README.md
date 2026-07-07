# MEIS 数据库脚本说明

本仓库采用 **Flyway 增量迁移** + **源脚本（建表/补字段）** 双轨维护。

## 目录结构

```
db/
├── migrations/              # Flyway 运行时迁移（勿改已发布版本）
│   ├── public/              # 平台库 public schema（V1…）
│   └── tenant/              # 租户 schema（V1…V13）
├── source/
│   ├── meis_database_design.sql   # 全量设计参考（含最新字段）
│   ├── create/              # 建表脚本（新环境/手工初始化）
│   └── patches/             # 补充字段脚本（已有库增量补列，可重复执行）
└── README.md                # 本文件
```

## 两套脚本的职责

| 类型 | 路径 | 用途 | 是否幂等 |
|------|------|------|----------|
| **Flyway 迁移** | `migrations/tenant/V*.sql` | 应用启动时由 `meis-tenant` 自动执行 | 每版本仅执行一次 |
| **建表脚本** | `source/create/*.sql` | 新租户手工建表、文档对照、离线初始化 | `CREATE TABLE IF NOT EXISTS` |
| **补字段脚本** | `source/patches/*.sql` | 老库手工补列、Flyway 不可用时的兜底 | `ADD COLUMN IF NOT EXISTS` |

## 运行时如何迁移

1. **public schema**：`meis-tenant` 启动时执行 `db/migrations/public`
2. **租户 schema**：创建租户或启动时执行 `db/migrations/tenant`（`TenantFlywayService`）

新建租户会按 V1→V13 顺序执行，**拼音简码** 由 `V13__pinyin_code.sql` 自动添加。

## 新增字段的标准流程

以「拼音简码 `pinyin_code`」为例：

1. **Flyway**：新增 `db/migrations/tenant/V13__pinyin_code.sql`（已存在）
2. **建表脚本**：在 `source/create/org_master_tables.sql` 和 `source/meis_database_design.sql` 的 `CREATE TABLE` 中加入该列
3. **补字段脚本**：在 `source/patches/org_master_column_patches.sql` 和汇总文件 `tenant_column_patches.sql` 中加入 `ALTER TABLE … ADD COLUMN IF NOT EXISTS`
4. **重启** `meis-tenant` 或对新租户触发迁移

> **注意**：已发布的 Flyway 版本（如 V1）**不要修改**，否则校验和会失败。新字段只通过新版本（V13+）或补字段脚本添加。

## 手工执行脚本

### 对指定租户 schema 补字段

```powershell
# 补全部汇总字段（V5–V13 的 ALTER）
.\scripts\apply-tenant-patches.ps1 -Schema tenant_demo

# 仅补科室/供应商/厂商拼音简码
.\scripts\apply-tenant-patches.ps1 -Schema tenant_demo -Patch org_master
```

### 新建库后全量 Flyway（推荐）

由 `meis-tenant` 服务自动完成，无需手工执行建表脚本。

### 仅手工建 org 主数据表（科室/供应商/厂商）

```powershell
.\scripts\apply-tenant-create.ps1 -Schema tenant_demo -Script org_master
```

## 拼音简码相关文件索引

| 文件 | 说明 |
|------|------|
| `migrations/tenant/V13__pinyin_code.sql` | Flyway 正式迁移 |
| `source/create/org_master_tables.sql` | 建表含 `pinyin_code` |
| `source/patches/org_master_column_patches.sql` | 仅 org 三表补列 |
| `source/patches/tenant_column_patches.sql` | 租户全量补列汇总 |

## 导入模板扩展（Excel + 客户自定义列）

自 V14 起，导入模板改为 **Excel（.xlsx）**，并支持按租户扩展字段：

| 表/业务 | business_type | 扩展数据存储 |
|---------|---------------|--------------|
| 设备台账 | `medical_device` | 标准列 + `extension_data` JSONB |
| 科室 | `department` | 标准列 |
| 供应商 | `supplier` | 标准列 |
| 生产厂商 | `manufacturer` | 标准列 |

### 为客户新增扩展列（示例：医院 A 的设备台账多一列「资产编号」）

```sql
INSERT INTO import_template_field
(business_type, profile_code, field_key, field_label, field_type, is_extension, sort_order, remark)
VALUES
('medical_device', 'hospital_a', 'asset_no', '资产编号', 'string', TRUE, 200, '医院A专用字段');
```

绑定租户使用该方案：

```sql
INSERT INTO import_profile_binding (business_type, profile_code)
VALUES ('medical_device', 'hospital_a')
ON CONFLICT (business_type) DO UPDATE SET profile_code = EXCLUDED.profile_code;
```

或通过 API：`POST /api/system/import-template-fields`、`PUT /api/system/import-template-fields/binding`

重启 `meis-tenant` 执行 V14 迁移后，下载的 Excel 模板会自动包含扩展列；导入时扩展列写入 `medical_device.extension_data`。

---

## 前置依赖

执行租户脚本前需已存在：

- 扩展：`uuid-ossp` 或 `pgcrypto`（见 `source/create/00_extensions.sql`）
- 基础表：`campus` 等（通常由 V1 迁移创建）

`org_master_tables.sql` 中 `department` 依赖 `campus`、`building` 表，请确保 V1 已执行或先执行完整基线迁移。
