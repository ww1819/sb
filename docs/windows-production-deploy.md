# MEIS Windows 生产 / 实施环境部署

本文档面向 **Windows Server**（不强制 Docker）。**现场实施优先走 `package\` 现场包**；正式上线再按后文做 NSSM 服务化、HTTPS 与防火墙收敛。

> **脚本分区（2026-07-31）**：仓库 `scripts\` 已拆为 `common\` / `bs\`（后端+Web）/ `app\`（Flutter）；`package\` 同理拆 `common\` / `bs\` / `app\`。根目录旧入口 bat/ps1 仍为转发兼容。详见 `scripts\README.txt`、`package\README.txt`。

| 场景 | 文档 |
|------|------|
| **实施环境快速部署（推荐）** | 本文 [§〇](#〇实施环境部署流程推荐) |
| Windows **本地开发** | [local-dev-deploy.md](local-dev-deploy.md) |
| **Linux** 生产（Docker / K8s） | [production-deploy.md](production-deploy.md) |
| Windows **正式生产加固** | 本文 [§一](#一适用场景与架构) 及以后 |

---

## 〇、实施环境部署流程（推荐）

适合：院内试运行 / 实施验收 / 短期演示。目标是 **少步骤、可双击、可拷走**。

```text
┌──────────────┐   拷贝 package\（或 update\ / www） ┌──────────────────┐
│  开发 / 构建机 │  ────────────────────────────────► │  实施 / 现场机    │
│ 完整打包.bat   │  首次 jars+www；日常增量 JAR         │ 双击 启动运维.bat │
│ 更新打包.bat   │                                    │ → :5098 启停服务  │
└──────────────┘                                    └──────────────────┘
```

### 〇.1 流程总览

| 步骤 | 在哪做 | 做什么 |
|------|--------|--------|
| 1 | 开发机 | 配置 `package\env.txt`（JDK / Maven；前端需 Node/npm） |
| 2 | 开发机 | **首次**：双击 `完整打包.bat` → JAR → `jars\`，前端 → `www\` |
| 2b | 开发机 | **日常**：双击 `更新打包.bat` → 只打有代码变更的 JAR，并生成 `update\` |
| 3 | — | **首次**整份 `package` 拷到实施机；**增量**只拷 `update\` 覆盖现场 `jars\`（前端有改另拷 `www\`） |
| 4 | 实施机 | 安装 JDK 17、PostgreSQL、Redis（Memurai）、MinIO（见 〇.3） |
| 5 | 实施机 | 改实施机上的 `package\env.txt`（JAVA_HOME、数据库等） |
| 6 | 实施机 | 双击 `启动运维.bat` → 浏览器 `http://localhost:5098` |
| 7 | 实施机 | 齐套检查 →「启动核心」或「启动全部」→ 冒烟验证 |
| 8 | 实施机 | Nginx 托管前端：`root` 指向 `package\www`（或拷到 `D:\meis\www`）+ `/api` 反代；App 填 Nginx 端口 |

> **禁止**直接双击打开 `index.html`（无法启动 Java）。  
> **禁止**把运维口 `5098` 暴露到院内网 / 公网。

### 〇.2 开发机：打包

1. 进入仓库根目录下的 `package\`。
2. 若尚无 `env.txt`：复制 `env.example.txt` → `env.txt`，至少填写：

| 项 | 说明 |
|----|------|
| `JAVA_HOME` | JDK 17 根目录（内含 `bin\java.exe`） |
| `MAVEN_HOME` | Maven 根目录（内含 `bin\mvn.cmd`），或改用 `MAVEN_CMD=` 指向 `mvn.cmd` |
| Node / npm | 本机 PATH 有 `npm`，或 `NODE_HOME` / `NPM_CMD`；用于 `meis-web` 生产构建 |
| `SKIP_FRONTEND_BUILD` | 默认 `0`；设为 `1` 则只打 JAR、不跑前端（`pack.ps1 -SkipFrontend` 同效） |

3. 选择打包方式：

| 脚本 | 何时用 | 结果 |
|------|--------|------|
| **`完整打包.bat`** | 首次交付、大版本、共享库大改、指纹丢失、前端有改 | 全量 `clean package` → `jars\`；`npm run build` → `www\`；写入指纹基线；清空 `update\` |
| **`更新打包.bat`** | 日常只改了部分业务模块 | 对比指纹，**只编译有变更的模块**；覆盖 `jars\`；写出 `update\`（**不重打前端**） |

变更判定（更新打包）：

- 比对各模块 `src/**` + `pom.xml` 内容指纹（相对上次成功打包）
- `jars\` 中缺某个 JAR → 视为需打
- **`meis-common` / `meis-api` / 根 `pom.xml` 有变更** → 重打 **全部** 业务 JAR（依赖面大）
- 无指纹文件时：提示先跑完整打包（exit 2）

4. 交付：

| 场景 | 拷什么 |
|------|--------|
| 首次 / 完整 | **整个 `package` 目录**（含 `jars\` + `www\`） |
| 增量（仅后端） | 仅 `package\update\` 里的 `*.jar`，覆盖实施机 `package\jars\` |
| 增量（含前端） | 再覆盖 `www\`，或整包拷贝 |

建议整包拷贝前可删体积大的运行日志（可选）：

```text
package\logs\*.log
package\logs\jobs\
```

**不要删**：`jars\`、`www\`、`*.bat`、`*.ps1`、`index.html`、`services.json`、`env.txt` / `env.example.txt`。  
旧名 `打包.bat` 仍可用，会转调 `完整打包.bat`。

### 〇.3 实施机：中间件前置

实施机至少需要：

| 组件 | 要求 | 备注 |
|------|------|------|
| JDK 17 | 已安装，路径写入 `env.txt` 的 `JAVA_HOME` | 与开发机路径可不同 |
| PostgreSQL 15 | 库 `meis`、账号可用 | 密码写入 `POSTGRES_*` |
| Redis / Memurai | 默认 `127.0.0.1:6379` | |
| MinIO | API 建议 **9100**（避开高拍仪 Eloam 的 **9000**） | `MINIO_ENDPOINT=http://127.0.0.1:9100` |

库与账号可用仓库脚本（若实施机有完整源码）；仅现场包时，由实施人员按院内规范建库，保证 `env.txt` 中库名/用户/密码正确即可。

### 〇.4 实施机：改 `env.txt`

打开实施机上的 `package\env.txt`，按现场改：

```text
JAVA_HOME=C:\Program Files\Java\jdk-17
OPS_TOKEN=现场自定口令
OPS_PORT=5098
PROFILE=dev

POSTGRES_HOST=127.0.0.1
POSTGRES_PORT=5432
POSTGRES_DB=meis
POSTGRES_USER=med
POSTGRES_PASSWORD=<现场密码>
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
MINIO_ENDPOINT=http://127.0.0.1:9100
```

| 项 | 说明 |
|----|------|
| `JAVA_HOME` | **必改**为实施机真实路径 |
| `MAVEN_HOME` | 实施机只启停 JAR **可不填**（打包只在开发机） |
| `OPS_TOKEN` | 运维页启停操作口令；勿用默认值长期对外 |
| `POSTGRES_*` / `MINIO_*` | 必须与现场中间件一致 |

### 〇.5 实施机：启动运维

1. **双击 `启动运维.bat`**（或 `start-ops.bat`）。
2. 浏览器自动打开（或手动访问）`http://localhost:5098/`。
3. 页面上：
   - 先看 **JAR 齐套**：缺包则回到开发机重新 `完整打包.bat`（或补打后拷 `update\`）。
   - 填运维口令（与 `OPS_TOKEN` 一致）。
   - 建议顺序：**启动核心**（含 tenant → auth → … → gateway）→ 业务需要再 **启动全部**。
4. 关闭运维窗口 **不会**停掉已启动的业务 JAR；要停服务用页面上的停止，或结束对应 `java.exe`。

启动顺序约定（页面「核心」已按此编排）：

| 顺序 | 服务 | 端口 |
|------|------|------|
| 1 | meis-tenant（首次会 Flyway 迁库） | 8082 |
| 2 | meis-auth | 8081 |
| 3 | 其它业务 JAR | 8083–8094 |
| 最后 | meis-gateway | 8080 |

日志目录：`package\logs\`。tenant 失败时优先看 `meis-tenant.out.log`。

### 〇.6 冒烟验证

在实施机本机执行：

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/auth/health
Invoke-RestMethod -Method POST http://127.0.0.1:8080/api/auth/login `
  -ContentType 'application/json' `
  -Body '{"tenantCode":"demo","username":"admin","password":"admin123"}'
```

| 检查项 | 期望 |
|--------|------|
| health | 返回正常 |
| login | 能拿到 token |
| 运维页 | 核心端口为监听中 |

上线前务必修改或禁用演示账号 `admin/admin123`。

### 〇.7 前端 / Nginx / App（实施常用）

现场包当前以 **后端 JAR + 运维启停** 为主。浏览器访问若已有 Nginx：

| 项 | 建议 |
|----|------|
| 静态页 | Nginx `root` 指向现场包 **`package\www`**（开发机 `打包.bat` 生成；等同 `meis-web/dist`） |
| API | `location /api/` → `http://127.0.0.1:8080` |
| 临时对外端口 | 若暂用 **5174** 对外，须在 Nginx 配置 `listen 5174` 且带 `/api` 反代 |
| Flutter App | 服务器 IP + 端口 **5174**（走 Nginx）；直连 Gateway 才填 **8080** |
| 正式生产 | 改为 **443 + HTTPS**，勿长期对外暴露 5174/8080 |

网关与各微服务端口 **不要**对院内普通终端开放；只开放 Nginx 入口。

### 〇.8 实施检查清单

- [ ] 开发机 `完整打包.bat` 成功，`jars\` 齐全（或增量已覆盖）  
- [ ] 整份 `package` 已拷到实施机  
- [ ] 实施机 JDK / PG / Redis / MinIO 就绪  
- [ ] `env.txt` 的 `JAVA_HOME`、库密码、MinIO 地址正确  
- [ ] `启动运维.bat` → `:5098` 能开页  
- [ ] 核心服务启动；`/api/auth/health`、登录成功  
- [ ] （如有）Nginx `/api` 反代；App/浏览器可登录  
- [ ] 运维口 `5098` 仅本机；演示口令已改  

### 〇.9 升级现场包

1. 开发机：小改动用 **`更新打包.bat`**，大改/首次用 **`完整打包.bat`**。  
2. 实施机运维页 **停止**相关服务（或停止全部）。  
3. **完整**：用新 `jars\` 覆盖（建议先备份）；**增量**：用 `update\` 内 JAR 覆盖现场 `jars\` 同名文件。  
4. 再 `启动运维.bat` → 先启 **meis-tenant**（确认迁库）→ 再启其余。  
5. 冒烟：登录、台账、上传（若启用 MinIO）。

长期稳定运行请改为 NSSM/WinSW（见 [§七](#七注册为-windows-服务推荐)），避免注销桌面后进程退出。

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
  ├── package\             # 现场包（实施推荐）
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

### 4.1 实施交付（推荐）：`package\` 现场包

**日常实施请直接按 [§〇](#〇实施环境部署流程推荐) 执行。** 目录要点：

```text
package\
  完整打包.bat      ← 开发机：全量 JAR → jars\ + 前端 → www\ + 指纹基线
  更新打包.bat      ← 开发机：仅变更模块 → jars\ 覆盖 + update\ 增量（不含前端）
  启动运维.bat      ← 实施机：打开本机运维页 :5098
  env.txt           ← 各机各自配置（模板见 env.example.txt；含 SKIP_FRONTEND_BUILD）
  jars\             ← 全部后端 JAR（实施运行用）
  www\              ← meis-web 生产静态页（Nginx root）
  update\           ← 最近一次「更新打包」的变更 JAR（可只拷这一目录）
  index.html + ops-helper.ps1 / 运维助手.ps1
  services.json     ← 服务名 / 端口 / 启停顺序
```

开发机：首次 `完整打包.bat`，日常 `更新打包.bat`（前端有改再完整打包）。  
实施机：改 `env.txt` → 双击 `启动运维.bat` → 齐套检查并启停；Nginx 指向 `www\`。

> 纯双击 HTML **不能**启动 Java；必须先开「启动运维.bat」。

### 4.2 全量源码构建（备选）

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

**备选旧包：** `scripts\bs\pack-windows-field-kit.ps1`（或根转发 `scripts\pack-windows-field-kit.ps1`）仍可打 `release\windows-field-kit\`（产物内 scripts 展平）。

---

## 五、环境变量与安全配置

启动前为会话或「系统环境变量」设置（示例）。使用 `package\env.txt` 时，运维助手会把其中项注入启动进程。

| 变量 | 生产示例 |
|------|----------|
| `MEIS_JAVA_HOME` / `JAVA_HOME` | `D:\meis\jdk-17` |
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

### 6.2 现场包运维页（实施首选）

见 [§〇.5](#〇5-实施机启动运维)。地址仅 `http://localhost:5098`，口令见 `OPS_TOKEN`。

### 6.3 仓库脚本（完整源码机）

```powershell
cd D:\meis\app
powershell -File scripts\start.ps1 -Profile dev
powershell -File scripts\status.ps1
powershell -File scripts\stop.ps1
```

> 脚本面向「本机运维窗口」便捷启停。**正式生产**请改为 NSSM/WinSW 服务（见第七节），避免注销桌面会话后进程退出。

仓库内另有 `scripts\bs\ops-panel.ps1`（同为 localhost:5098），与 `package\启动运维.bat` 能力类似；**交付实施请优先给 `package\`**，勿把完整 `scripts` 目录当现场包。

### 6.4 手动启动单服务（排错）

```powershell
$java = Join-Path $env:MEIS_JAVA_HOME 'bin\java.exe'
& $java -jar D:\meis\package\jars\meis-tenant-1.0.0-SNAPSHOT.jar
```

确认 `meis-tenant` 日志出现 `Public schema migrated`、演示或正式租户 Schema 就绪后，再启其余服务。

### 6.5 验证 API

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
nssm set MeisTenant AppParameters -Xms256m -Xmx512m -jar D:\meis\package\jars\meis-tenant-1.0.0-SNAPSHOT.jar
nssm set MeisTenant AppDirectory D:\meis\package
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

`api` 反代 Gateway，`app` 托管静态页。实施临时入口可用 `5174`（须含 `/api`），正式请用 443：

```nginx
# 实施临时：单端口同时出静态 + API（示例 5174）
server {
    listen 5174;
    server_name _;

    client_max_body_size 50m;
    root D:/meis/www;
    index index.html;

    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}

# 正式：API
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

# 正式：Web
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

将 Nginx 亦注册为 Windows 服务；防火墙仅放行 **443**（及运维 RDP）。临时 5174 仅限实施窗口使用。

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
| **禁止** 入站 8080–8094、5432、6379、8848、9100/9101、**5098** | 仅本机或内网应用访问 |
| 出站 | 按院内策略；通知/集成模块若需外联另开白名单 |

移动端（Flutter App / 小程序）生产环境将 API Base 配为 `https://api.医院域名/api`；实施临时可为 `http://服务器IP:5174/api`。

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

1. 维护窗口通知；运维页停止或停止 Windows 服务（Gateway 可先停）。
2. 备份数据库与 MinIO。
3. 替换 `package\jars\` / `www` 静态资源。
4. **先启 `meis-tenant`**，确认迁移成功。
5. 再启其余服务与 Gateway；冒烟：登录、台账列表、文件上传。
6. 结构变更只走 Flyway 固定槽位（见需求文档附录 D），禁止手工改生产表结构。

---

## 十二、安全 checklist

- [ ] 修改所有默认密码（PG、MinIO、演示 `admin`、`OPS_TOKEN`）
- [ ] 统一修改 `meis.jwt.secret`
- [ ] MinIO 控制台不对公网
- [ ] 全站 HTTPS；关闭明文 80 或仅跳转（正式环境）
- [ ] 防火墙收敛端口（含 5098）
- [ ] Windows Update / 防病毒排除策略与日志目录协商
- [ ] 操作审计与备份策略落地

---

## 十三、常见问题

| 现象 | 排查 |
|------|------|
| 登录 401 | `meis-tenant` 是否迁库成功；JWT secret 是否各服务一致；Token 是否过期 |
| 网关 503 / 404 | 对应微服务是否在监听端口；Gateway 路由/直连配置 |
| 前端 / App 登录 405 | Nginx 是否配置了 `/api` → Gateway；是否误打到纯静态端口 |
| 文件上传失败 / Non-XML 404 | MinIO 是否在 **9100**；是否误连 Eloam **9000** |
| 服务「启动后消失」 | 用桌面会话跑 jar 会随注销退出 → 改为 NSSM/WinSW |
| `meis-tenant` 立即退出 | 查看 `package\logs\`；Flyway 失败会主动退出 |
| 运维页打不开 | 须先双击 `启动运维.bat`，勿只开 HTML；确认 `OPS_PORT` |
| `打包.bat` / `完整打包.bat` 失败 | 检查 `env.txt` 的 `JAVA_HOME` / `MAVEN_HOME`；看 Maven 编译日志 |
| `更新打包.bat` 提示无指纹 | 先跑一次 `完整打包.bat` 建立基线 |
| `更新打包` 未检出改动 | 确认改的是模块 `src/` 或 `pom.xml`；共享库改动会触发全量业务重打 |
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
| **实施 / 试运行** | [§〇 `package` 现场包](#〇实施环境部署流程推荐) |
| 已有 K8s / Docker 运维能力 | 优先 [production-deploy.md](production-deploy.md) |
| 院内标准为 Windows Server、无容器平台 | 本文原生部署 + NSSM |
| 仅开发调试 | [local-dev-deploy.md](local-dev-deploy.md) |

同一套业务 JAR 与 Flyway 脚本；差异主要在进程托管、反向代理与运维习惯。
