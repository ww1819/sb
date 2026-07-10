# MEIS 数据库脚本

本仓库采用 **Flyway 增量迁移** + **源脚本（建表/补字段）** 双轨维护。

## 目录结构

```
db/
├── source/
│   ├── create/              # 建表脚本（幂等，新环境/手工初始化）
│   │   ├── 00_extensions.sql
│   │   └── tenant_tables.sql   # 由 scripts/gen-tenant-create-sql.ps1 从 V1 生成
│   └── patches/
│       └── tenant_column_patches.sql   # 与 R__tenant_schema_sync.sql 同步
meis-tenant/src/main/resources/db/migrations/
├── public/                  # 平台 public schema（Spring Flyway 启动时执行）
└── tenant/                  # 租户 schema（TenantSchemaMigrator）
    ├── V1__tables.sql              # 全量建表 + 默认 COMMENT（唯一建表源）
    ├── V2__extensions.sql          # 索引
    ├── V3__seed_data.sql           # 种子/字典
    ├── V4__comments.sql            # 历史注释（仅首次）
    └── R__tenant_schema_sync.sql   # 老租户补列（每列一条 ALTER）
```

## 约定

1. **新建表**：只改 `V1__tables.sql`；老租户由 `SchemaTableEnsuring` 幂等创建缺表。
2. **已有表加字段**：改 V1 建表语句 + 在 `R__tenant_schema_sync.sql` **每列单独一条** `ADD COLUMN IF NOT EXISTS`。
3. **不要**再新建 V20+；**不要**在补丁里 `CREATE TABLE` 或覆盖注释。

## 运行时迁移

| Schema | 执行方 |
|--------|--------|
| `public` | `meis-tenant` 启动时 `spring.flyway.*` |
| `tenant_*` | Flyway → `SchemaTableEnsuring`（缺表）→ `SchemaCommentFiller`（空注释） |

## 手工工具

```powershell
# 比对业务库与脚本差异
powershell -File scripts/compare-db-schema.ps1 -Schema tenant_demo

# 对指定 schema 执行补字段（幂等）
powershell -File scripts/apply-tenant-patches.ps1 -Schema tenant_demo

# 从 V1 重新生成建表脚本
powershell -File scripts/gen-tenant-create-sql.ps1
```

默认连接：`localhost:5432/meis`，用户 `med`（与 `application.yml` 一致）。
