# meis-tenant 数据库迁移

## 执行方式

| Schema | 执行方 |
|--------|--------|
| `public` | **Spring Boot 自动迁库**（`spring.flyway.*`，启动时） |
| `tenant_*` | **`TenantSchemaMigrator`**（启动时 + 开户 API） |

其他微服务 `spring.flyway.enabled=false`。

## 老租户更新流程

```
SchemaTableEnsuring（幂等建表与索引：V1 + V2）
    → Flyway migrate（R__ 逐列 ADD COLUMN、字典/数据修正）
    → SchemaCommentFiller（仅补空注释）
```

**对老租户执行建表语句没有问题**：`SchemaTableEnsuring` 使用 `CREATE TABLE IF NOT EXISTS`，已有表与数据不会被修改；仅创建缺失的表。已有表缺列由下一步 R__ 的 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` 补全。

## 脚本约定

| 用途 | 文件 |
|------|------|
| **全量建表**（含完整字段 + 默认 COMMENT） | `V1__tables.sql` |
| **已有表补列**（每条 ALTER 只加一列） | `R__tenant_schema_sync.sql` |
| 手工镜像 | `db/source/create/tenant_tables.sql`、`db/source/patches/tenant_column_patches.sql` |

操作规则：

1. **新表**：只写进 `V1__tables.sql`。老租户启动时由 `SchemaTableEnsuring` 幂等建表（字段完整）。
2. **已有表加字段**：
   - 在 `V1` 对应 `CREATE TABLE` 中补上该列（保证新租户完整）
   - 在 `R__` **单独一行** `ALTER TABLE ... ADD COLUMN IF NOT EXISTS ...`（禁止一条语句加多列）
3. **R__ 禁止** `CREATE TABLE` / `CREATE INDEX`；缺表与缺索引由 `SchemaTableEnsuring` 执行 V1/V2 兜底。
4. **不要**再新建 V20+ 版本迁移。
5. **不要**在 R__ 里 `COMMENT ON`（空注释由代码补，已有注释不覆盖）。

## 迁移文件一览

```
tenant/
  V1__tables.sql              # 全量建表（唯一建表源）
  V2__extensions.sql          # 索引
  V3__seed_data.sql           # 种子/字典
  V4__comments.sql            # 历史注释（仅首次）
  R__tenant_schema_sync.sql   # 仅补列（每列一条 ALTER）
```

```powershell
powershell -File scripts/apply-tenant-patches.ps1 -Schema tenant_demo
powershell -File scripts/gen-tenant-create-sql.ps1
```
