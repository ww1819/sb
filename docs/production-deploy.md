# MEIS 生产环境部署与启动

本文档面向 **Linux 生产服务器**（Docker / Kubernetes）。本地 Windows 开发请阅读：[local-dev-deploy.md](local-dev-deploy.md)

---

## 一、生产架构

```
用户浏览器
    │
    ├── app.example.com  ──► Nginx（静态 Web：meis-web 构建产物）
    │
    └── api.example.com  ──► Nginx（TLS）──► Spring Cloud Gateway
                                        │
                    ┌───────────────────┼───────────────────┐
                    ▼                   ▼                   ▼
              meis-auth          meis-system         meis-* 业务服务
                    │                   │                   │
                    └───────────────────┴───────────────────┘
                                        │
              ┌─────────────────────────┼─────────────────────────┐
              ▼                         ▼                         ▼
        PostgreSQL 15            Redis / Memurai              MinIO
              │
        Nacos（注册发现 + 配置，集群或单机）
```

| 组件 | 说明 |
|------|------|
| Nginx | TLS 终结；`api.*` → Gateway；`app.*` → 静态前端 |
| Gateway | 统一入口、JWT 校验、路由 |
| 微服务 | 各业务域独立部署，支持水平扩展 |
| PostgreSQL | Schema 多租户；`public` + `tenant_*` |
| Redis | 缓存、会话（按需） |
| MinIO | 设备附件与文件存储 |
| Nacos | 服务注册与发现（生产建议启用） |

---

## 二、前置条件

| 项 | 要求 |
|----|------|
| 操作系统 | Linux（推荐 Ubuntu 22.04 / CentOS 7+） |
| Docker | 24+ 与 Docker Compose v2 |
| Kubernetes | 1.26+（若走 K8s） |
| 域名与证书 | `api.example.com`、`app.example.com` |
| 资源建议 | 最低 8C16G；生产建议各微服务 2 副本 + 独立 PG/Redis |

---

## 三、构建制品

### 3.1 后端 JAR

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
mvn clean package -DskipTests
```

各模块产物：`meis-*/target/meis-*-1.0.0-SNAPSHOT.jar`

### 3.2 前端静态资源

```bash
cd meis-web
npm ci
npm run build
```

产物目录：`meis-web/dist/`，部署至 Nginx 或对象存储。

### 3.3 Docker 镜像（示例）

为每个微服务构建镜像（示例 gateway）：

```bash
docker build -t meis/gateway:1.0.0 -f deploy/docker/gateway.Dockerfile .
docker build -t meis/auth:1.0.0 -f deploy/docker/auth.Dockerfile .
# ... 其余服务同理
docker push registry.example.com/meis/gateway:1.0.0
```

---

## 四、Docker Compose 部署

清单路径：`deploy/docker-compose/docker-compose.yml`

### 4.1 启动基础设施

```bash
cd deploy/docker-compose
docker compose up -d postgres redis nacos minio
```

默认暴露：

| 服务 | 端口 |
|------|------|
| PostgreSQL | 5432 |
| Redis | 6379 |
| Nacos | 8848 |
| MinIO API | 9000 |
| MinIO 控制台 | 9001 |

**生产务必修改**数据库密码、MinIO 密钥，并通过 `.env` 或 Compose `environment` 注入。

### 4.2 初始化数据库

首次部署：启动 `meis-tenant` 完成 Flyway 迁移与演示租户 Schema。

```bash
docker compose up -d meis-tenant
docker compose logs -f meis-tenant
```

确认日志中出现 `Public schema migrated` 与 `tenant_demo` 就绪后，启动其余服务。

### 4.3 启动应用服务

```bash
docker compose up -d meis-auth meis-system meis-gateway
# 按需扩展其余微服务镜像后一并启动
docker compose up -d
```

### 4.4 验证

```bash
curl -s http://localhost:8080/api/auth/health
curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"tenantCode":"demo","username":"admin","password":"admin123"}'
```

---

## 五、Kubernetes 部署

清单目录：`deploy/k8s/`

### 5.1 创建命名空间

```bash
kubectl apply -f deploy/k8s/namespace.yaml
```

### 5.2 配置 Secret / ConfigMap

生产需单独创建（示例）：

```bash
kubectl -n meis-saas create secret generic meis-db \
  --from-literal=POSTGRES_PASSWORD='<强密码>'
kubectl -n meis-saas create secret generic meis-jwt \
  --from-literal=MEIS_JWT_SECRET='<256位随机串>'
```

各微服务通过环境变量引用：

| 变量 | 说明 |
|------|------|
| `POSTGRES_HOST` | PG 服务地址 |
| `POSTGRES_DB` | `meis` |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | 应用账号 |
| `NACOS_SERVER` | `nacos:8848` |
| `MINIO_ENDPOINT` | MinIO 内网地址 |
| `meis.jwt.secret` | JWT 密钥（必须修改） |

### 5.3 部署 Gateway 与应用

```bash
kubectl apply -f deploy/k8s/gateway-deployment.yaml
# 扩展其余 Deployment、Service、Ingress
kubectl apply -f deploy/k8s/
```

### 5.4 Ingress（Nginx）

典型规则：

| 域名 | 后端 |
|------|------|
| `api.example.com` | `meis-gateway:80` |
| `app.example.com` | 静态 Web ConfigMap 或独立 Nginx |

启用 TLS（cert-manager 或手动挂载证书）。

### 5.5 高可用

- Gateway 与各微服务：`replicas: 2` 及以上
- 配置 HPA 按 CPU/内存自动扩缩
- PostgreSQL、Redis 使用云托管或 Operator 集群方案

---

## 六、Nginx 配置要点

```nginx
# API
server {
    listen 443 ssl;
    server_name api.example.com;
    ssl_certificate     /etc/nginx/ssl/api.crt;
    ssl_certificate_key /etc/nginx/ssl/api.key;

    location / {
        proxy_pass http://meis-gateway:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}

# 静态 Web
server {
    listen 443 ssl;
    server_name app.example.com;
    ssl_certificate     /etc/nginx/ssl/app.crt;
    ssl_certificate_key /etc/nginx/ssl/app.key;

    root /var/www/meis-web;
    index index.html;
    location / { try_files $uri $uri/ /index.html; }
}
```

---

## 七、CI/CD（Jenkins）

流水线：`deploy/jenkins/Jenkinsfile`

典型阶段：

1. `mvn package -DskipTests`
2. `docker build` + `docker push`
3. `kubectl apply -f deploy/k8s/` 或 `docker compose pull && up -d`

在 Jenkins 中配置：JDK 17、Maven、Docker、kubeconfig 凭据。

---

## 八、生产启动顺序

| 顺序 | 组件 | 说明 |
|------|------|------|
| 1 | PostgreSQL | 库 `meis`、账号权限 |
| 2 | Redis | 缓存 |
| 3 | MinIO | 存储桶 `meis` |
| 4 | Nacos | `standalone` 或集群 |
| 5 | meis-tenant | **首次** Flyway + 租户 Schema |
| 6 | meis-auth、meis-system 等 | 业务微服务 |
| 7 | meis-gateway | API 入口 |
| 8 | Nginx | 对外 TLS + 静态 Web |

---

## 九、安全 checklist

- [ ] 修改 `meis.jwt.secret`（所有服务一致）
- [ ] 数据库使用独立强密码，限制网络访问
- [ ] MinIO 独立密钥，禁止公网暴露控制台
- [ ] 全站 HTTPS
- [ ] 关闭演示账号或修改 `admin` 密码
- [ ] 定期备份 PostgreSQL（含各 `tenant_*` Schema）
- [ ] 审计日志 `sys_operation_log` 保留策略

---

## 十、运维与排错

| 现象 | 排查 |
|------|------|
| 502/503 | Nacos 注册、Gateway 路由、Pod 健康检查 |
| 登录 401 | `meis-tenant` 是否完成迁移；JWT secret 是否一致 |
| 跨租户数据 | 检查 Gateway 是否透传 `X-Tenant-Schema` |
| 文件上传失败 | MinIO 连通性、`meis-file` 环境变量 |
| 迁移失败 | 查看 `meis-tenant` 日志；勿多实例同时跑 Flyway |

### 健康检查

```bash
curl https://api.example.com/api/auth/health
curl https://api.example.com/actuator/health
```

### OpenAPI

- 系统服务：`https://api.example.com`（经网关）+ 各服务 `/swagger-ui.html`
- 说明见 `docs/api/README.md`

---

## 十一、演示租户（验收用）

生产环境可保留或删除演示租户 `demo`：

| 项 | 值 |
|----|-----|
| 医院编码 | demo |
| Schema | tenant_demo |
| 默认账号 | admin / admin123（**上线前务必修改或禁用**） |

新医院开户：`POST /api/tenant/create`（需平台权限）。
