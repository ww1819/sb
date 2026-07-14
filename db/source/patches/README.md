# patches（已废弃 / DEPRECATED）

本目录为**历史手工补丁**留存，**不再作为新变更入口**。

- 权威脚本：`meis-tenant/src/main/resources/db/migrations/{public,tenant}/` 固定槽位
- **禁止**再新增按功能的 patch SQL（如 `*_module_patches.sql`）
- 现有 `.sql` 暂不批量删除（未与 DB 同步时风险高）；内容仅供对照/应急

新工作请写入对应 Flyway 槽位：

| 变更类型 | 写入 |
|----------|------|
| 新表 / 全量字段 | `tenant/V1__tables.sql` 或 `public/V1__tables.sql` |
| 索引 | `V2__indexes.sql` |
| 审计七列 / `is_deleted` | `tenant/R__columns_audit.sql` |
| 业务补列 / FK | `tenant/R__columns_biz.sql` |
| 字典 / 数据更正 / 菜单 | `R__data_fix.sql`（public 或 tenant） |
