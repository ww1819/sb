# MEIS 医院设备固定资产管理系统 SaaS

Schema 多租户医院设备全生命周期管理平台。

## 结构

- `meis-common` / `meis-api`：公共库与 DTO
- `meis-gateway` ~ `meis-integration`：微服务
- `meis-web`：Vue3 管理端
- `meis-mobile`：Flutter 骨架
- `meis-desktop`：Electron 壳
- `meis-tenant`：租户与 **Flyway 数据库迁移**（`src/main/resources/db/migrations`）
- `docs/`：安装与部署文档
- `deploy/`：Docker/K8s/Jenkins

## 文档

| 文档 | 说明 |
|------|------|
| [docs/local-dev-deploy.md](docs/local-dev-deploy.md) | **本地开发**部署与启动（Windows 原生） |
| [docs/production-deploy.md](docs/production-deploy.md) | **生产环境**部署与启动（Docker/K8s） |
| [docs/architecture.md](docs/architecture.md) | 架构说明 |
| [docs/user-manual.md](docs/user-manual.md) | 用户手册骨架 |

## 快速开始（本地）

见 [docs/local-dev-deploy.md](docs/local-dev-deploy.md)

```powershell
powershell -File scripts\setup-postgres.ps1 -DbName meis
powershell -File scripts\build.ps1
powershell -File scripts\start.ps1 -Profile dev
cd meis-web && npm install && npm run dev
```

演示：`demo` / `admin` / `admin123`
