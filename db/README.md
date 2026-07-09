# MEIS 数据库脚本

本仓库采用 **Flyway 增量迁移** + **源脚本（建表/补字段）** 双轨维护。

## 目录结构

```
db/
├── source/
│   ├── create/              # 建表脚本（幂等，新环境/手工初始化）
│   │   ├── 00_extensions.sql
│   │   └── tenant_tables.sql   # 由 scripts/gen-tenant-create-sql.ps1 从 V1 生成
│   └── patches/             # 补字段脚本（已有库增量，可重复执行）
│       └── tenant_column_patches.sql
meis-tenant/src/main/resources/db/migrations/
├── public/                  # 平台 public schema（Spring Flyway 启动时执行）
└── tenant/                  # 租户 schema（TenantSchemaMigrator）
    ├── V1__tables.sql       # 建表 + 列注释
    ├── V2__extensions.sql   # 索引 + 字段扩展（须在种子数据前）
    ├── V3__seed_data.sql    # 种子/演示数据（仅 INSERT/UPDATE）
    ├── V4__comments.sql     # 注释补注
    └── V17__tenant_schema_sync.sql  # 存量库列同步
    └── V18__comment_backfill.sql    # 全量注释补全（存量库）
```

## 执行顺序（重要）

| 顺序 | 内容 | 说明 |
|------|------|------|
| 1 | `V1__tables.sql` | 建表、视图 |
| 2 | `V2__extensions.sql` | 索引、`ALTER TABLE ADD COLUMN` |
| 3 | `V3__seed_data.sql` | 字典/演示数据（依赖上两步完整 schema） |
| 4 | `V4__comments.sql` | 注释补注 |
| 5 | `V17__...` | 存量租户补齐未落库字段 |

**种子数据必须在建表与字段扩展之后执行**，避免 `INSERT/UPDATE` 引用不存在的列。

## 运行时迁移

| Schema | 执行方 |
|--------|--------|
| `public` | `meis-tenant` 启动时 `spring.flyway.*` |
| `tenant_*` | `TenantSchemaMigrator`（启动 + 开户 API） |

存量库若 Flyway 版本号已占用（如旧 V16）但列未创建，由 **V17** 或 `db/source/patches/tenant_column_patches.sql` 补齐。

## 手工工具

```powershell
# 比对业务库与脚本差异
powershell -File scripts/compare-db-schema.ps1 -Schema tenant_demo

# 对指定 schema 执行补字段（幂等）
powershell -File scripts/apply-tenant-patches.ps1 -Schema tenant_demo

# 对指定 schema 补全表/列/索引注释
powershell -File scripts/apply-tenant-comments.ps1 -Schema tenant_demo

# 从迁移脚本重新生成注释补全 SQL
cd scripts && javac ExtractComments.java && java ExtractComments

# 从 V1 重新生成建表脚本
powershell -File scripts/gen-tenant-create-sql.ps1
```

默认连接：`localhost:5432/meis`，用户 `med`（与 `application.yml` 一致）。
