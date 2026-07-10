# 建表脚本

| 文件 | 说明 |
|------|------|
| `00_extensions.sql` | PostgreSQL 扩展（uuid-ossp、pgcrypto） |
| `tenant_tables.sql` | 租户全量建表（由 `scripts/gen-tenant-create-sql.ps1` 从 `V1__tables.sql` 生成） |

**约定**：只维护 Flyway `V1__tables.sql`，再生成本目录文件；不要在本文件手改后忘记回写 V1。

执行顺序（新库）：`00_extensions.sql` → `tenant_tables.sql` →（可选）`../patches/tenant_column_patches.sql`
