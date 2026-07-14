# MEIS 数据库脚本

权威迁移脚本在 **Flyway 固定槽位**（`meis-tenant/.../db/migrations/`）。`db/source/` 仅为可选离线镜像 / 历史补丁，详见 `db/source/README.md`。

## 目录结构

```
db/source/                          # 非权威；勿新增功能 patch
meis-tenant/src/main/resources/db/migrations/
├── public/
│   ├── V1__tables.sql
│   ├── V2__indexes.sql
│   ├── V3__seed_data.sql           # 冻结
│   ├── V4__comments.sql            # 冻结
│   └── R__data_fix.sql            # 菜单 / 数据更正
└── tenant/
    ├── V1__tables.sql
    ├── V2__indexes.sql
    ├── V3__seed_data.sql
    ├── V4__comments.sql
    ├── R__columns_audit.sql        # 标准七列 / is_deleted
    ├── R__columns_biz.sql          # 业务 ALTER / FK
    └── R__data_fix.sql            # 字典 / 回填 / 数据更正
```

## 约定

1. **新建表**：只改 `V1__tables.sql`；老租户由 `SchemaTableEnsuring` 幂等创建缺表。
2. **已有表加字段**：改 V1 + 对应 `R__columns_audit`（七列）或 `R__columns_biz`（业务列），每列单独一条 `ADD COLUMN IF NOT EXISTS`。
3. **不要**再新建 V5+/V20+；**不要**在 `db/source/patches` 新增功能补丁。

## 运行时迁移

| Schema | 执行方 |
|--------|--------|
| `public` | `meis-tenant` 启动时 `spring.flyway.*` |
| `tenant_*` | `SchemaTableEnsuring`（缺表/索引）→ Flyway R__ → `SchemaCommentFiller` |

## 手工工具

```powershell
powershell -File scripts/ensure-tenant-tables.ps1
powershell -File scripts/gen-tenant-create-sql.ps1
```
