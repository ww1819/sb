# 建表脚本

| 文件 | 说明 |
|------|------|
| `00_extensions.sql` | PostgreSQL 扩展（uuid-ossp、pgcrypto） |
| `tenant_tables.sql` | 租户全量建表（由 `scripts/gen-tenant-create-sql.ps1` 从 `V1__tables.sql` 生成） |

执行顺序：`00_extensions.sql` → `tenant_tables.sql` →（升级老库时）`../patches/tenant_column_patches.sql`
