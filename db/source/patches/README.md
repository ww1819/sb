# 补字段脚本

| `tenant_column_patches.sql` | 字段扩展（`ADD COLUMN IF NOT EXISTS`） |
| `tenant_comment_backfill.sql` | **全量注释补全**（从 V1/V2/V4/V17 + 视图注释提取，1152 条） |
| `tenant_view_comments.sql` | 视图注释（已合并进 `tenant_comment_backfill.sql`，可单独执行） |

```powershell
# 补字段
powershell -File scripts/apply-tenant-patches.ps1 -Schema tenant_demo

# 补注释（幂等）
powershell -File scripts/apply-tenant-comments.ps1 -Schema tenant_demo
```

与 Flyway `V18__comment_backfill.sql` 内容一致；存量租户若未跑到 V18，可手工执行上述脚本。
