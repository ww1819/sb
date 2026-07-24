# MEIS 本地开发部署与启动

本文档面向 **Windows 物理机本地开发**（无 Docker Desktop）。涵盖软件安装、中间件配置、数据库初始化、后端/前端构建与启动、验证与排错。

生产环境请阅读：[production-deploy.md](production-deploy.md)

---

## 一、环境要求

| 软件 | 版本 | 本机默认路径 / 说明 |
|------|------|---------------------|
| JDK | 17 | `C:\Program Files\Java\jdk-17` |
| Maven | 3.9+ | 官网 zip 或 IDEA 自带 |
| Node.js | 20 LTS | [nodejs.org](https://nodejs.org) |
| PostgreSQL | 15 | `E:\PGSQL`，超管 `postgres` / `aspt` |
| Memurai | 最新 | Windows 下 Redis 替代，默认 `6379` |
| Nacos | 2.3 | zip 单机（开发可关闭） |
| MinIO | 最新 | Windows 二进制，**可选**（仅文件上传时需要） |
| Git | 最新 | 版本管理 |

---

## 二、安装与验证

### 2.1 JDK 17

```powershell
& "C:\Program Files\Java\jdk-17\bin\java.exe" -version
```

构建脚本使用 **会话级** `JAVA_HOME`，不修改系统环境变量。

### 2.2 Maven

```powershell
mvn -version
```

### 2.3 Node.js

```powershell
node -v
npm -v
```

### 2.4 PostgreSQL

确认服务已启动，超级用户可连接：

```powershell
$env:PGPASSWORD='aspt'
& "E:\PGSQL\bin\psql.exe" -U postgres -h localhost -c "SELECT version();"
```

### 2.5 Memurai / Redis

安装后确认 `localhost:6379` 可访问（默认无密码）。

### 2.6 MinIO（可选，文件上传时再装）

本地开发可 **跳过**。仅在使用设备附件、文件上传、高拍仪「采用并上传」时需要。

> **端口注意（PLT-CAM）**：新良田 Eloam 本机服务固定占 **9000**。MinIO **勿再占用 9000**，否则 `meis-file` 会连到高拍仪并报 `Non-XML response … 404`。本地 API 用 **9100**。

```cmd
minio server D:\minio-data --address ":9100" --console-address ":9101"
```

- API：`http://localhost:9100`（`meis-file` 默认 `MINIO_ENDPOINT`）
- 控制台：`http://localhost:9101`
- 默认账号：`minioadmin` / `minioadmin`

### 2.7 Nacos（可选）

开发模式默认 **关闭 Nacos 注册发现**，可跳过。若需启用：

```cmd
cd nacos\bin
startup.cmd -m standalone
```

控制台：`http://localhost:8848/nacos`

---

## 三、创建应用数据库

在仓库根目录执行：

```powershell
powershell -File scripts\setup-postgres.ps1 -PostgresPassword aspt -DbName meis
```

| 项 | 值 |
|----|-----|
| 数据库 | `meis` |
| 应用用户 | `med` |
| 应用密码 | `med123456` |

**说明**：`meis` 与演示库 `sb` 分离；业务数据在 `meis` 库内通过 Schema 多租户隔离。本脚本**不**建表、不跑 Flyway，结构由 `meis-tenant` 启动时自动补齐。

---

## 四、数据库结构（自动迁库）

`scripts/setup-postgres.ps1` **仅**创建空库与应用账号，不建表。

| 范围 | 执行方式 |
|------|----------|
| `public` 平台表 | **Spring Flyway**（`meis-tenant` 的 `spring.flyway.enabled=true`，启动时自动跑 `db/migrations/public`） |
| 各租户 `tenant_*` | `TenantSchemaMigrator`（同一服务启动时 + 开户时；多 schema 需单独迁库） |

启动 `meis-tenant` 即可（`start.ps1` 或开发面板），无需额外迁移脚本。失败时服务 fail-fast 退出。

结构变更：修改 `db/migrations` 后重新编译并重启 `meis-tenant`。

---

## 五、构建

```powershell
powershell -File scripts\build.ps1
```

等价于：会话级 JDK17 + `mvn package -DskipTests` +（可选）`meis-web` 生产构建。

---

## 六、启动后端微服务

### 6.1 一键启动

双击 `scripts\start.bat`，或：

```powershell
powershell -File scripts\start.ps1 -Profile dev
```

### 6.2 一键停止

双击 `scripts\stop.bat`，或：

```powershell
powershell -File scripts\stop.ps1
```

### 6.3 一键重启（推荐）

双击 `scripts\restart.bat`（只重启），或 `scripts\restart-build.bat`（重新编译后重启）。

```powershell
powershell -File scripts\restart.ps1 -Profile dev
powershell -File scripts\restart.ps1 -Profile dev -Build
```

### 6.4 仅编译

双击 `scripts\build.bat`

### 6.5 启动顺序（手动时参考）

| 顺序 | 服务 | 端口 | 说明 |
|------|------|------|------|
| 1 | meis-tenant | 8082 | 迁移 + 租户 Schema |
| 2 | meis-auth | 8081 | 登录 |
| 3 | meis-system ~ meis-integration | 8083–8094 | 业务微服务 |
| 4 | meis-gateway | 8080 | API 入口 |

### 6.6 端口一览

| 服务 | 端口 |
|------|------|
| meis-gateway | 8080 |
| meis-auth | 8081 |
| meis-tenant | 8082 |
| meis-system | 8083 |
| meis-purchase | 8084 |
| meis-asset | 8085 |
| meis-repair | 8086 |
| meis-maintain | 8087 |
| meis-qc | 8088 |
| meis-maintenance-contract | 8089 |
| meis-special | 8090 |
| meis-analytics | 8091 |
| meis-file | 8092 |
| meis-notification | 8093 |
| meis-integration | 8094 |
| meis-web (Vite) | 5173 |

### 6.7 环境变量（可选覆盖）

| 变量 | 默认值 |
|------|--------|
| `POSTGRES_HOST` | localhost |
| `POSTGRES_PORT` | 5432 |
| `POSTGRES_DB` | meis |
| `POSTGRES_USER` | med |
| `POSTGRES_PASSWORD` | med123456 |
| `NACOS_SERVER` | localhost:8848 |
| `MINIO_ENDPOINT` | http://localhost:9100（勿与 Eloam 抢 9000） |
| `MINIO_ACCESS_KEY` | minioadmin |
| `MINIO_SECRET_KEY` | minioadmin |

---

## 七、启动前端

```powershell
cd meis-web
npm install
npm run dev
```

- 开发地址：`http://localhost:5173`
- Vite 已将 `/api` 代理到 `http://localhost:8080`

### Electron 桌面壳（可选）

```powershell
cd meis-desktop
npm install
npm run dev
```

需先启动 `meis-web` 开发服务器（5173）。

### Flutter 移动端（可选）

```bash
cd meis-mobile
flutter pub get
flutter run
```

默认 API：`http://localhost:8080/api`（真机需改为本机 IP）。

---

## 八、登录验证

| 项 | 值 |
|----|-----|
| 医院编码 | `demo` |
| 用户名 | `admin` |
| 密码 | `admin123` |

| 入口 | 地址 |
|------|------|
| Web 管理端 | http://localhost:5173 |
| API 网关 | http://localhost:8080 |
| 登录接口 | `POST /api/auth/login` |

命令行快速验证：

```powershell
Invoke-RestMethod -Uri 'http://localhost:8081/api/auth/login' -Method POST `
  -ContentType 'application/json' `
  -Body '{"tenantCode":"demo","username":"admin","password":"admin123"}'
```

---

## 九、多租户说明（开发）

- 平台元数据：`public.sys_tenant`
- 业务数据：`tenant_{code}`（演示为 `tenant_demo`）
- 登录后 JWT 携带 `schemaName`；网关透传 `X-Tenant-Schema` 至各微服务

---

## 十、常见问题

| 问题 | 处理 |
|------|------|
| Maven 找不到 JDK | 使用 `scripts\build.ps1`，勿单独改系统 `JAVA_HOME` |
| `Missing *.jar` | 先执行 `scripts\build.ps1` |
| 网关 503 / 404 | 确认微服务已启动；dev 模式检查 `meis-gateway` 的 `application-dev.yml` |
| 登录失败 | 先启动 `meis-tenant` 完成迁移；确认 `tenant_demo.sys_user` 存在 |
| `meis-tenant` 启动即退出 | 查看日志中的 Flyway 错误；迁库失败会主动 fail-fast，避免结构不全继续运行 |
| 文件上传失败 | 确认 MinIO 已启动且 `meis-file` 配置正确 |
| Flyway 冲突 | 仅 `meis-tenant` 执行迁移；其他服务已关闭 `spring.flyway.enabled`；结构变更只改 V1/V2/V3 三文件 |

---

## 十一、日常开发流程

```powershell
# 1. 确保 PG、Redis 已运行（MinIO 可选）
# 2. 改代码后：双击 scripts\restart-build.bat
# 4. 前端热更新
cd meis-web && npm run dev
```
