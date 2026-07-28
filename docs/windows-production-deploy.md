# MEIS Windows 生产环境部署与启动

本文档面向 **Windows Server 生产机**（不强制 Docker），按「中间件 → 数据库 → 后端 JAR → 前端静态资源 → 反向代理 → 开机自启」落地。

| 场景 | 文档 |
|------|------|
| Windows **本地开发** | [local-dev-deploy.md](local-dev-deploy.md) |
| **Linux** 生产（Docker / K8s） | [production-deploy.md](production-deploy.md) |
| **Windows** 生产（本文） | 当前文档 |

---

## 一、适用场景与架构

适合：院内机房 **Windows Server 2019/2022**、暂不引入 Docker/K8s、需与现有 Windows 运维体系对齐。

```
用户浏览器
    │
    ├── https://app.医院域名  ──► Nginx / IIS（静态 meis-web）
    │
    └── https://api.医院域名  ──► Nginx / IIS ──► meis-gateway:8080
                                              │
                         ┌────────────────────┼────────────────────┐
                         ▼                    ▼                    ▼
                   meis-auth            meis-system          其余 meis-* JAR
                         │                    │                    │
                         └────────────────────┴────────────────────┘
                                              │
              ┌───────────────────────────────┼───────────────────────────────┐
              ▼                               ▼                               ▼
        PostgreSQL 15                    Memurai/Redis                     MinIO
```

| 组件 | Windows 生产建议 |
|------|------------------|
| JDK 17 | 官方 MSI / zip，固定安装目录 |
| PostgreSQL 15 | Windows 安装包，独立数据盘 |
| Redis | **Memurai**（Windows 原生）或 Redis for Windows |
| MinIO | Windows 二进制 + 数据盘；API **勿占 9000**（若本机还装高拍仪 Eloam） |
| Nacos | 可选；与开发一致时可先关闭注册发现，Gateway 直连 |
| 后端 | 各模块 `*.jar` + **NSSM / WinSW** 注册为 Windows 服务 |
| 前端 | `meis-web` 生产构建产物，由 Nginx/IIS 托管 |
| 反向代理 | Nginx for Windows（推荐）或 IIS + URL Rewrite + ARR |

---

## 二、前置条件

| 项 | 要求 |
|----|------|
| 操作系统 | Windows Server 2019 / 2022（建议 Desktop Experience） |
| 权限 | 本地管理员；可配置防火墙入站规则 |
| CPU / 内存 | 最低 **8C / 16GB**；业务全开建议 **16C / 32GB+** |
| 磁盘 | 系统盘 ≥ 100GB；**数据盘**单独存放 PG / MinIO / 备份 |
| 网络 | 固定 IP；对外开放仅 443（及运维跳板），勿直接暴露 8080–8094 |
| 域名证书 | `app.*`、`api.*` 的 TLS 证书（或院内统一证书） |

建议目录（可按院内规范调整）：

```
D:\meis\
  ├── jdk-17\
  ├── app\                 # 仓库或发布包根目录
  ├── jars\                # 各服务 jar 副本（可选）
  ├── logs\                # 运行日志
  ├── minio-data\
  ├── nginx\
  └── backup\
```

---

## 三、安装中间件

### 3.1 JDK 17

```powershell
& "D:\meis\jdk-17\bin\java.exe" -version
# 建议设置机器级：
# MEIS_JAVA_HOME=D:\meis\jdk-17
```

### 3.2 PostgreSQL 15

1. 安装 PostgreSQL 15，数据目录放到数据盘（如 `E:\PGSQL\data`）。
2. 创建库与应用账号（可用仓库脚本，**生产务必改密码**）：

```powershell
cd D:\meis\app
powershell -File scripts\setup-postgres.ps1 `
  -PostgresPassword '<超管密码>' `
  -DbName meis `
  -AppUser med `
  -AppPassword '<强密码>'
```

| 项 | 生产建议 |
|----|----------|
| 库名 | `meis` |
| 应用用户 | 独立账号，仅授权本库 |
| 网络 | `pg_hba.conf` 仅允许本机或应用网段 |

### 3.3 Memurai / Redis

安装 Memurai，监听 `127.0.0.1:6379`。生产建议设置密码，并在各服务配置中同步。

### 3.4 MinIO（文件上传必装）

```cmd
minio.exe server D:\meis\minio-data --address ":9100" --console-address ":9101"
```

| 项 | 值 |
|----|-----|
| API | `http://127.0.0.1:9100` |
| 控制台 | `http://127.0.0.1:9101`（**勿对公网开放**） |
| 账号 | 首次部署后立即修改默认 `minioadmin` |

> **端口**：本机若安装新良田高拍仪（Eloam），其服务占用 **9000**。MinIO API 请用 **9100**，与开发约定一致，避免 `meis-file` 连错服务。

将 MinIO 注册为 Windows 服务（NSSM 示例见第七节）。

### 3.5 Nacos（可选）

院内若已统一使用 Nacos，可装 2.3+ standalone。若与现网开发模式一致（Gateway 直连各服务），生产可暂不启用。

---

## 四、构建与制品

在**构建机**或生产机（需 Maven + Node）执行：

```powershell
cd D:\meis\app
$env:MEIS_JAVA_HOME = 'D:\meis\jdk-17'
powershell -File scripts\build.ps1
```

| 产物 | 路径 |
|------|------|
| 后端 JAR | `meis-*/target/meis-*-1.0.0-SNAPSHOT.jar` |
| 前端静态资源 | `meis-web/dist/` |

也可只在构建机打包，将 JAR + `dist` 拷贝到生产机 `D:\meis\`。

---

## 五、环境变量与安全配置

启动前为会话或「系统环境变量」设置（示例）：

| 变量 | 生产示例 |
|------|----------|
| `MEIS_JAVA_HOME` | `D:\meis\jdk-17` |
| `POSTGRES_HOST` | `127.0.0.1` |
| `POSTGRES_PORT` | `5432` |
| `POSTGRES_DB` | `meis` |
| `POSTGRES_USER` | `med` |
| `POSTGRES_PASSWORD` | **强密码** |
| `MINIO_ENDPOINT` | `http://127.0.0.1:9100` |
| `MINIO_ACCESS_KEY` | 生产密钥 |
| `MINIO_SECRET_KEY` | 生产密钥 |
| `NACOS_SERVER` | `127.0.0.1:8848`（若启用） |

**JWT**：所有微服务 `meis.jwt.secret` 必须一致且与开发默认值不同（至少 32 字节随机串）。可通过各服务 `application.yml` 覆盖，或统一外部化配置。

---

## 六、启动后端服务

### 6.1 启动顺序（必须）

| 顺序 | 服务 | 端口 | 说明 |
|------|------|------|------|
| 1 | PostgreSQL / Memurai / MinIO | — | 中间件就绪 |
| 2 | **meis-tenant** | 8082 | **首次** Flyway + 租户 Schema，失败会 fail-fast |
| 3 | meis-auth | 8081 | 登录 |
| 4 | meis-system … meis-integration | 8083–8094 | 业务服务 |
| 5 | **meis-gateway** | 8080 | API 入口（最后） |

端口一览与开发一致，见 [local-dev-deploy.md §6.6](local-dev-deploy.md)。

### 6.2 使用仓库脚本（运维窗口）

```powershell
cd D:\meis\app
# 生产机建议使用与运维约定的 profile；当前脚本默认 Profile=dev（直连路由）
powershell -File scripts\start.ps1 -Profile dev
powershell -File scripts\status.ps1
powershell -File scripts\stop.ps1
```

> 脚本面向「本机运维窗口」便捷启停。**正式生产**请改为 NSSM/WinSW 服务（见第七节），避免注销桌面会话后进程退出。

### 6.3 手动启动单服务（排错）

```powershell
$java = Join-Path $env:MEIS_JAVA_HOME 'bin\java.exe'
& $java -jar D:\meis\app\meis-tenant\target\meis-tenant-1.0.0-SNAPSHOT.jar
```

确认 `meis-tenant` 日志出现 `Public schema migrated`、演示或正式租户 Schema 就绪后，再启其余服务。

### 6.4 验证 API

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/auth/health
Invoke-RestMethod -Method POST http://127.0.0.1:8080/api/auth/login `
  -ContentType 'application/json' `
  -Body '{"tenantCode":"demo","username":"admin","password":"admin123"}'
```

上线前：**修改或禁用**演示账号 `admin/admin123`。

---

## 七、注册为 Windows 服务（推荐）

以 [NSSM](https://nssm.cc/) 为例（WinSW 配置思路相同）。

### 7.1 MinIO

```cmd
nssm install MeisMinio D:\meis\minio\minio.exe
nssm set MeisMinio AppParameters server D:\meis\minio-data --address ":9100" --console-address ":9101"
nssm set MeisMinio AppDirectory D:\meis\minio
nssm set MeisMinio Start SERVICE_AUTO_START
nssm start MeisMinio
```

### 7.2 业务 JAR（每个服务一个）

```cmd
nssm install MeisTenant "D:\meis\jdk-17\bin\java.exe"
nssm set MeisTenant AppParameters -Xms256m -Xmx512m -jar D:\meis\app\meis-tenant\target\meis-tenant-1.0.0-SNAPSHOT.jar
nssm set MeisTenant AppDirectory D:\meis\app\meis-tenant
nssm set MeisTenant AppEnvironmentExtra POSTGRES_PASSWORD=*** MINIO_ENDPOINT=http://127.0.0.1:9100
nssm set MeisTenant AppStdout D:\meis\logs\meis-tenant.out.log
nssm set MeisTenant AppStderr D:\meis\logs\meis-tenant.err.log
nssm set MeisTenant Start SERVICE_AUTO_START
```

建议服务名与启动依赖：

1. `MeisTenant` → 2. `MeisAuth` / 各业务 → 最后 `MeisGateway`  
2. 在服务属性中设置「依赖项」：Gateway 依赖 Auth + Tenant + 关键业务服务。

内存建议（可按实测调整）：Gateway / Auth / Tenant `512m–1g`；资产/维修等业务 `512m–1g`；分析类可更高。

---

## 八、前端与反向代理

### 8.1 构建静态资源

```powershell
cd D:\meis\app\meis-web
npm ci
npm run build
# 将 dist\ 拷到例如 D:\meis\www\
```

### 8.2 Nginx for Windows（推荐）

`api` 反代 Gateway，`app` 托管静态页：

```nginx
# API
server {
    listen 443 ssl;
    server_name api.example.com;
    ssl_certificate     conf/ssl/api.crt;
    ssl_certificate_key conf/ssl/api.key;

    client_max_body_size 50m;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}

# Web
server {
    listen 443 ssl;
    server_name app.example.com;
    ssl_certificate     conf/ssl/app.crt;
    ssl_certificate_key conf/ssl/app.key;

    root D:/meis/www;
    index index.html;
    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

将 Nginx 亦注册为 Windows 服务；防火墙仅放行 **443**（及运维 RDP）。

### 8.3 IIS（备选）

- 静态站点：物理路径指向 `D:\meis\www`，启用 URL Rewrite 将未匹配路径回写 `index.html`（SPA）。
- API：安装 ARR，将 `api` 站点反向代理到 `http://127.0.0.1:8080`。

前端 `vite`/`env` 生产接口地址应指向 `https://api.医院域名`（以仓库现有生产构建配置为准）。

---

## 九、防火墙与网络

| 规则 | 说明 |
|------|------|
| 入站 443 | 对外 Web / API |
| 入站 3389 | 仅运维网段（或走堡垒机） |
| **禁止** 入站 8080–8094、5432、6379、8848、9100/9101 | 仅本机或内网应用访问 |
| 出站 | 按院内策略；通知/集成模块若需外联另开白名单 |

移动端（Flutter App / 小程序）生产环境将 API Base 配为 `https://api.医院域名/api`。

---

## 十、备份与恢复

| 对象 | 建议 |
|------|------|
| PostgreSQL | 每日全备 `pg_dump`（库 `meis`，含全部 `tenant_*` Schema）；保留 ≥ 7 天 |
| MinIO | 同步/备份 `minio-data` 目录 |
| 配置与证书 | 纳入变更管理，勿只存本机 |
| 恢复演练 | 上线后至少做一次还原演练 |

仓库已有辅助脚本（按环境改路径/密码）：

```powershell
powershell -File scripts\backup-db.ps1
powershell -File scripts\restore-db.ps1
```

---

## 十一、升级发布

1. 维护窗口通知；`scripts\stop.ps1` 或停止 Windows 服务（Gateway 可先停）。
2. 备份数据库与 MinIO。
3. 替换 JAR / `www` 静态资源。
4. **先启 `meis-tenant`**，确认迁移成功。
5. 再启其余服务与 Gateway；冒烟：登录、台账列表、文件上传。
6. 结构变更只走 Flyway 固定槽位（见需求文档附录 D），禁止手工改生产表结构。

---

## 十二、安全 checklist

- [ ] 修改所有默认密码（PG、MinIO、演示 `admin`）
- [ ] 统一修改 `meis.jwt.secret`
- [ ] MinIO 控制台不对公网
- [ ] 全站 HTTPS；关闭明文 80 或仅跳转
- [ ] 防火墙收敛端口
- [ ] Windows Update / 防病毒排除策略与日志目录协商
- [ ] 操作审计与备份策略落地

---

## 十三、常见问题

| 现象 | 排查 |
|------|------|
| 登录 401 | `meis-tenant` 是否迁库成功；JWT secret 是否各服务一致；Token 是否过期 |
| 网关 503 / 404 | 对应微服务是否在监听端口；Gateway 路由/直连配置 |
| 文件上传失败 / Non-XML 404 | MinIO 是否在 **9100**；是否误连 Eloam **9000** |
| 服务「启动后消失」 | 用桌面会话跑 jar 会随注销退出 → 改为 NSSM/WinSW |
| `meis-tenant` 立即退出 | 查看 `D:\meis\logs\` 或 NSSM stderr；Flyway 失败会主动退出 |
| 前端白屏 | `try_files` / IIS Rewrite 是否回写 `index.html`；API 域名 CORS/证书 |

### 健康检查与日常监测

完整说明见独立文档：[production-monitoring.md](production-monitoring.md)。

```powershell
powershell -File scripts\status.ps1
powershell -File scripts\health-check.ps1
powershell -File scripts\health-check.ps1 -GatewayUrl 'https://api.example.com'
Invoke-RestMethod https://api.example.com/api/auth/health
```

---

## 十四、与 Linux 生产的选择建议

| 条件 | 建议 |
|------|------|
| 已有 K8s / Docker 运维能力 | 优先 [production-deploy.md](production-deploy.md) |
| 院内标准为 Windows Server、无容器平台 | 使用本文原生部署 |
| 仅开发调试 | [local-dev-deploy.md](local-dev-deploy.md) |

同一套业务 JAR 与 Flyway 脚本；差异主要在进程托管、反向代理与运维习惯。
