# meis-tenant 数据库迁移

## 执行方式

| Schema | 执行方 |
|--------|--------|
| `public` | **Spring Boot 自动迁库**（`spring.flyway.*`，启动时） |
| `tenant_*` | **`TenantSchemaMigrator`**（启动时 + 开户 API；多 schema 无法由单次 Spring Flyway 覆盖） |

其他微服务 `spring.flyway.enabled=false`。

## 迁移文件

```
src/main/resources/db/migrations/
├── public/     → spring.flyway.locations
└── tenant/     → meis.flyway.tenant-locations
```

V1 建表+COMMENT ON · V2 索引与字段扩展 · V3 种子 · V4 注释补注 · V17 存量库列同步 · V18 全量注释补全

`scripts/setup-postgres.ps1` 仅建空库；`scripts/compare-db-schema.ps1` 比对业务库与脚本。
