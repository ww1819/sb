# meis-tenant 数据库迁移

## 执行方式

| Schema | 执行方 |
|--------|--------|
| `public` | **Spring Boot 自动迁库**（`spring.flyway.*`，启动时） |
| `tenant_*` | **`TenantSchemaMigrator`**（启动时 + 开户 API） |

其他微服务 `spring.flyway.enabled=false`。

## 老租户更新流程

```
Flyway migrate（含 R__ 逐列补字段）
    → SchemaTableEnsuring（幂等执行 V1/V2：没有的表创建，已有表跳过）
    → SchemaCommentFiller（仅补空注释）
```

## 脚本约定

| 用途 | 文件 |
|------|------|
| **全量建表**（含完整字段 + 默认 COMMENT） | `V1__tables.sql` |
| **已有表补列**（每条 ALTER 只加一列） | `R__tenant_schema_sync.sql` |
| 手工镜像 | `db/source/create/tenant_tables.sql`、`db/source/patches/tenant_column_patches.sql` |

操作规则：

1. **新表**：只写进 `V1__tables.sql`。老租户下次启动时由 `SchemaTableEnsuring` 用 `CREATE TABLE IF NOT EXISTS` 创建（字段完整）。
2. **已有表加字段**：
   - 在 `V1` 对应 `CREATE TABLE` 中补上该列（保证新租户完整）
   - 在 `R__` **单独一行** `ALTER TABLE ... ADD COLUMN IF NOT EXISTS ...`（禁止一条语句加多列）
3. **不要**在 R__ 里 `CREATE TABLE`，**不要**再新建 V20+。
4. **不要**在 R__ 里 `COMMENT ON`（空注释由代码补，已有注释不覆盖）。

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
