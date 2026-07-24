# meis-tenant 数据库迁移

## 执行方式

| Schema | 执行方 |
|--------|--------|
| `public` | **Spring Boot 自动迁库**（`spring.flyway.*`，启动时） |
| `tenant_*` | **`TenantSchemaMigrator`**（启动时 + 开户 API） |

其他微服务 `spring.flyway.enabled=false`。

## public 脚本结构

```
public/
  V1__tables.sql         # 全量建表 + COMMENT ON（平台表）
  V2__indexes.sql        # 索引
  V3__seed_data.sql      # 一次性种子（冻结）：演示租户、套餐、平台账号
  V4__comments.sql       # 历史注释回填（冻结）
  R__data_fix.sql       # 非菜单数据更正 / 平台表补列（可重复）
  R__menus.sql           # 菜单唯一维护点（sys_menu + 套餐/租户挂接，可重复）
```

**约定**：

1. **新表 / 新字段** → 只改 `V1__tables.sql`（`CREATE TABLE` 须含完整字段）
2. **已有表加列** → `R__data_fix.sql` 单独一行 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`
3. **新菜单 / 菜单调整** → **只改** `R__menus.sql`（`INSERT ON CONFLICT`）；禁止写入 `R__data_fix`、租户脚本或临时 SQL
4. **禁止**再新建 `V5+` / `V20+` 版本化迁移；原 V5–V21 已并入 R__
5. dev 环境配置 `ignore-migration-patterns: *:missing`，删除旧版本脚本后 Flyway 可正常启动

## tenant 脚本结构（字母序 R__）

```
tenant/
  V1__tables.sql           # 全量建表
  V2__indexes.sql          # 索引
  V3__seed_data.sql        # 种子
  V4__comments.sql         # 注释
  R__columns_audit.sql     # 标准七列 / is_deleted（executeInTransaction=false）
  R__columns_biz.sql       # 业务 ALTER / FK
  R__data_fix.sql         # 字典、回填、数据更正
```

## 老租户更新流程

```
ensureDatabaseExtensions（uuid-ossp / pgcrypto）
    → SchemaTableEnsuring（幂等建表与索引：V1 + V2）
    → Flyway migrate（R__columns_audit → R__columns_biz → R__data_fix）
    → SchemaCommentFiller（仅补空注释）
    → TenantSchemaShadowGuard（V1 缺表检测，失败则阻断启动）
```

离线补表：`powershell -File scripts/ensure-tenant-tables.ps1`

**对老租户执行建表语句没有问题**：`SchemaTableEnsuring` 使用 `CREATE TABLE IF NOT EXISTS`，已有表与数据不会被修改；仅创建缺失的表。已有表缺列由下一步 R__ 的 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` 补全。

## 脚本约定（tenant）

| 用途 | 文件 |
|------|------|
| **全量建表**（含完整字段 + 默认 COMMENT） | `tenant/V1__tables.sql` |
| **审计七列 / 软删** | `tenant/R__columns_audit.sql` |
| **已有表业务补列**（每条 ALTER 只加一列） | `tenant/R__columns_biz.sql` |
| **数据更正 / 字典种子** | `tenant/R__data_fix.sql` |
| 可选离线镜像 | `db/source/create/`、`db/source/patches/`（已废弃新补丁） |

操作规则：

1. **新表**：只写进 `tenant/V1__tables.sql`。老租户启动时由 `SchemaTableEnsuring` 幂等建表（字段完整）。
2. **已有表加字段**：
   - 在 `V1` 对应 `CREATE TABLE` 中补上该列（保证新租户完整）
   - 审计七列写 `R__columns_audit.sql`；业务列写 `R__columns_biz.sql`（禁止一条语句加多列）
3. **R__ 禁止** `CREATE TABLE` / `CREATE INDEX`；缺表与缺索引由 `SchemaTableEnsuring` 执行 V1/V2 兜底。
4. **不要**再新建 V20+ 版本迁移；**不要**在 `db/source/patches` 新增功能补丁。
5. **不要**在 R__ 里 `COMMENT ON`（空注释由 `SchemaCommentFiller` 补全）

```powershell
powershell -File scripts/apply-tenant-patches.ps1 -Schema tenant_demo
powershell -File scripts/ensure-tenant-tables.ps1
powershell -File scripts/gen-tenant-create-sql.ps1
```
