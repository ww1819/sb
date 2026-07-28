# MEIS 生产环境服务运行状态监测

本文说明如何判断各微服务与中间件是否正常运行，适用于 **Windows Server 原生部署** 与 **Linux Docker/K8s**。部署步骤见：

| 场景 | 文档 |
|------|------|
| Windows 生产 | [windows-production-deploy.md](windows-production-deploy.md) |
| Linux 生产 | [production-deploy.md](production-deploy.md) |
| 本地开发 | [local-dev-deploy.md](local-dev-deploy.md) |

---

## 一、监测分层（建议按此顺序）

| 层 | 看什么 | 工具 / 入口 |
|----|--------|-------------|
| 1. 进程 / 端口 | JAR 是否在听端口 | `scripts\status.ps1`；Windows 服务；`docker ps` / `kubectl get pods` |
| 2. HTTP 存活 | Actuator / 业务 health | `scripts\health-check.ps1`；`/actuator/health` |
| 3. 网关连通 | 对外 API 是否通 | `GET /api/auth/health`（经 Gateway） |
| 4. 中间件 | PG / Redis / MinIO | 本机连接探测 |
| 5. 业务冒烟 | 登录 + 一条列表 | 运维验收清单 |

---

## 二、微服务端口一览

| 服务 | 端口 | 本机健康检查（示例） |
|------|------|----------------------|
| meis-gateway | 8080 | `http://127.0.0.1:8080/actuator/health` |
| meis-auth | 8081 | `http://127.0.0.1:8081/actuator/health` |
| meis-tenant | 8082 | `http://127.0.0.1:8082/actuator/health` |
| meis-system | 8083 | `http://127.0.0.1:8083/actuator/health` |
| meis-purchase | 8084 | 同上模式 |
| meis-asset | 8085 | |
| meis-repair | 8086 | |
| meis-maintain | 8087 | |
| meis-qc | 8088 | |
| meis-maintenance-contract | 8089 | |
| meis-special | 8090 | |
| meis-analytics | 8091 | |
| meis-file | 8092 | |
| meis-notification | 8093 | |
| meis-integration | 8094 | |

各服务已暴露 Spring Boot Actuator：`management.endpoints.web.exposure.include: health,info`。

**经网关的业务探测（免登录）**：

```text
GET https://api.医院域名/api/auth/health
```

Gateway JWT 白名单含 `/api/auth/health`、`/actuator/**`（直连各服务时用本机端口，勿把 Actuator 对公网裸奔）。

---

## 三、一键检查（推荐）

在仓库根目录：

### 3.1 仅看端口是否监听

```powershell
powershell -File scripts\status.ps1
```

输出 `OK` / `DOWN` 对应各服务端口。

### 3.2 端口 + HTTP 健康

```powershell
powershell -File scripts\health-check.ps1
# 经公网域名再测网关（可选）：
powershell -File scripts\health-check.ps1 -GatewayUrl 'https://api.example.com'
```

脚本会：

1. 检查各服务端口是否监听  
2. 请求 `http://127.0.0.1:{port}/actuator/health`，期望 JSON 含 `"status":"UP"`  
3. 请求 Gateway 的 `/api/auth/health`  
4. 以非零退出码表示存在 DOWN（便于计划任务 / CI）

### 3.3 查看日志

```powershell
powershell -File scripts\logs.ps1 -List
powershell -File scripts\logs.ps1 -Service gateway -Follow
```

---

## 四、Windows Server 生产

### 4.1 Windows 服务状态（NSSM / WinSW）

若已按生产文档注册服务：

```powershell
Get-Service Meis* | Format-Table Name, Status, StartType
# 或
sc.exe query MeisGateway
sc.exe query MeisTenant
```

期望：`RUNNING` / `SERVICE_RUNNING`。某服务反复重启 → 看 NSSM 的 stdout/stderr 日志目录。

### 4.2 计划任务（可选）

每 5 分钟执行：

```powershell
powershell -NoProfile -File D:\meis\app\scripts\health-check.ps1
```

失败时写事件日志或发邮件（院内监控平台对接即可）。**勿**把 Actuator 映射到公网 443 以外的裸端口。

### 4.3 中间件

| 组件 | 快速探测 |
|------|----------|
| PostgreSQL | `psql -h 127.0.0.1 -U med -d meis -c "SELECT 1"` |
| Memurai/Redis | `redis-cli -h 127.0.0.1 ping` → `PONG` |
| MinIO | 浏览器/ curl `http://127.0.0.1:9100/minio/health/live`（或看服务是否运行） |
| Nacos（若启用） | `http://127.0.0.1:8848/nacos` |

---

## 五、Linux Docker / Kubernetes

### 5.1 Docker Compose

```bash
cd deploy/docker-compose
docker compose ps
curl -sf http://127.0.0.1:8080/actuator/health
curl -sf http://127.0.0.1:8080/api/auth/health
```

### 5.2 Kubernetes

```bash
kubectl -n meis-saas get pods -o wide
kubectl -n meis-saas get svc
kubectl -n meis-saas logs deploy/meis-gateway --tail=100
```

Deployment 建议配置：

- `livenessProbe` / `readinessProbe`：`GET /actuator/health`（或各服务等价路径）  
- 探针失败自动重启；就绪失败不进负载

---

## 六、对外与对内探测怎么选

| 探测目标 | 推荐 URL | 说明 |
|----------|----------|------|
| 用户侧「系统是否可用」 | `https://api.../api/auth/health` | 经 Nginx + Gateway，最接近真实访问 |
| 单服务是否活着 | `http://127.0.0.1:{port}/actuator/health` | **仅本机/内网** |
| 网关进程本身 | `http://127.0.0.1:8080/actuator/health` | 不含后端业务是否全绿 |

某业务端口 UP 但经 Gateway 访问 503：优先查 Gateway 路由与该服务是否注册/直连地址是否正确。

---

## 七、业务冒烟（状态「绿」之后）

| 步骤 | 动作 | 期望 |
|------|------|------|
| 1 | `POST /api/auth/login`（正式租户账号） | 返回 token |
| 2 | Web 打开台账/系统管理任一列表 | 有数据或空表，无 5xx |
| 3 | 上传一张小图（若启用 MinIO） | `meis-file` 成功 |

登录 401 但 health UP：多半是 JWT/租户数据问题，不是「进程挂了」——见各部署文档排错表。

---

## 八、安全注意

- [ ] Actuator 仅本机或运维网段；公网只暴露 Nginx 443  
- [ ] 健康检查脚本使用的账号/密码勿写入可公开仓库  
- [ ] 监控告警阈值避免把服务打满（间隔 ≥ 1～5 分钟即可）  
- [ ] 生产关闭无关 endpoint 暴露（当前默认仅 `health,info`）

---

## 九、常见现象

| 现象 | 含义 | 处理 |
|------|------|------|
| `status.ps1` 全 DOWN | 未启动或启动失败 | `start.ps1` / 查 Windows 服务与日志 |
| 端口 OK，actuator 非 UP | 进程在但应用未就绪 / 依赖库连不上 | 看该服务日志（常见 PG/Redis） |
| 仅 gateway DOWN | 入口挂 | 先起业务再起 gateway，或查 8080 占用 |
| auth health 失败，其它失败 | 鉴权链路断 | 查 meis-auth:8081 与 Gateway 路由 |
| 本机全绿，公网不通 | 防火墙 / Nginx / 证书 | 查 443 与反代配置 |

---

## 十、平台 Web 监控（PLT-OPS-02）

平台管理员登录 Web 后，菜单 **平台管理 → 服务状态** 可查看各微服务实时探测结果。

| 项 | 说明 |
|----|------|
| 入口 | `/platform/service-health`（仅 `userType=platform`） |
| API | `GET /api/system/platform/service-health` |
| 探测方式 | `meis-system` 在内网请求各服务 `/actuator/health`（并行，默认超时 2.5s） |
| 配置 | `meis.ops.health-targets` / `meis.ops.timeout-ms`（见 `meis-system` 的 `application.yml`） |
| 权限 | 非平台 JWT 返回 403；租户账号菜单与路由均不可见 |

运维脚本（`status.ps1` / `health-check.ps1`）与平台页互补：脚本适合计划任务与本机排障，平台页适合登录后一眼总览。

---

## 十一、需求索引

| 编号 | 说明 |
|------|------|
| **PLT-OPS-01** | 脚本与运维侧监测（本文前几节） |
| **PLT-OPS-02** | 平台 Web 服务状态页（本节） |

详见 `meis-requirements.md`。
