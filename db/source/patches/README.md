# 补充字段脚本（Patches）

本目录存放 **幂等** 的 `ALTER TABLE … ADD COLUMN IF NOT EXISTS` 脚本，用于：

- 已有数据库在 Flyway 未跑完时的手工补列
- 生产环境紧急 hotfix（事后需补对应 Flyway 版本）

## 文件说明

| 文件 | 对应 Flyway | 说明 |
|------|-------------|------|
| `org_master_column_patches.sql` | V13 | 科室/供应商/厂商 `pinyin_code` |
| `tenant_column_patches.sql` | V5–V13 | 租户全量补列汇总（仅 ALTER，不含种子数据） |

## 执行

```powershell
.\scripts\apply-tenant-patches.ps1 -Schema tenant_demo
.\scripts\apply-tenant-patches.ps1 -Schema tenant_demo -Patch org_master
```

## 维护约定

新增字段时：

1. 先加 Flyway `migrations/tenant/V{n}__*.sql`
2. 同步更新 `source/create/` 中建表定义
3. 在本目录对应分类文件及 `tenant_column_patches.sql` 追加 `ADD COLUMN IF NOT EXISTS`
