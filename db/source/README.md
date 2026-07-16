# db/source

权威 Flyway 脚本位于：

```
meis-tenant/src/main/resources/db/migrations/{public,tenant}/
```

槽位固定（**禁止**再新增 `V5+` / `V20+` / 按功能零散 patch 文件）：

| Schema | 建表 | 索引 | 种子 | 注释 | 补列 / 数据 |
|--------|------|------|------|------|-------------|
| public | `V1__tables.sql` | `V2__indexes.sql` | `V3__seed_data.sql`（冻结） | `V4__comments.sql`（冻结） | `R__data_fix.sql` |
| tenant | `V1__tables.sql` | `V2__indexes.sql` | `V3__seed_data.sql` | `V4__comments.sql` | `R__columns_audit.sql` → `R__columns_biz.sql` → `R__data_fix.sql` |

本目录（`create/`、`patches/`）仅为可选离线镜像或历史留存，**不要**在此新增按功能的补丁 SQL；需要改库结构或数据时，只改进上述 Flyway 槽位。镜像脚本可按需从权威文件重新生成。
