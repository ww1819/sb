# 建表脚本

本目录提供 **可重复执行** 的建表 SQL，字段定义与 `source/meis_database_design.sql` 及最新 Flyway 版本对齐。

## 文件说明

| 文件 | 说明 |
|------|------|
| `00_extensions.sql` | PostgreSQL 扩展（uuid） |
| `org_master_tables.sql` | 科室、供应商、生产厂商（含拼音简码） |

## 执行顺序

1. `00_extensions.sql`
2. 确保 V1 基线表（campus、building 等）已存在
3. `org_master_tables.sql`

完整租户 schema 请优先使用 Flyway：`db/migrations/tenant/V1__meis_core.sql` 起。

## 手工执行示例

```powershell
$env:PGPASSWORD = 'med123456'
psql -U med -h localhost -d meis -v ON_ERROR_STOP=1 `
  -c "SET search_path TO tenant_demo, public;" `
  -f db/source/create/00_extensions.sql `
  -f db/source/create/org_master_tables.sql
```

或使用 `scripts/apply-tenant-create.ps1`。
