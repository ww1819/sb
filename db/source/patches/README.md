# 补列脚本（老租户兜底）

| 文件 | 说明 |
|------|------|
| `tenant_column_patches.sql` | 与 `R__tenant_schema_sync.sql` 同步：**每条语句只 ADD 一个字段** |

**建表**：不在本文件。老租户缺表由运行时 `SchemaTableEnsuring` 幂等执行 V1（`CREATE TABLE IF NOT EXISTS`）。

**注释**：不在本文件。由 `SchemaCommentFiller` 仅补空注释。

```powershell
powershell -File scripts/apply-tenant-patches.ps1 -Schema tenant_demo
```
