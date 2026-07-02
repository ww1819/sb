# MEIS 架构说明

## 多租户（Schema 隔离）

- `public`：平台租户元数据
- `tenant_{code}`：业务表（约 50 张）

登录流程：查 `sys_tenant` → 校验租户 Schema 内 `sys_user` → 签发 JWT（含 schemaName）

## 微服务映射

| 模块 | 服务 | 端口 |
|------|------|------|
| 网关 | meis-gateway | 8080 |
| 认证 | meis-auth | 8081 |
| 租户 | meis-tenant | 8082 |
| 系统 | meis-system | 8083 |
| 采购 | meis-purchase | 8084 |
| 资产 | meis-asset | 8085 |
| 维修 | meis-repair | 8086 |
| 保养 | meis-maintain | 8087 |
| 质控 | meis-qc | 8088 |
| 维保合同 | meis-maintenance-contract | 8089 |
| 特殊设备 | meis-special | 8090 |
| 分析 | meis-analytics | 8091 |
| 文件 | meis-file | 8092 |
| 消息 | meis-notification | 8093 |
| 集成 | meis-integration | 8094 |

## 集成（V1 占位）

HIS / PACS / LIS / HRP 适配器接口 + Mock，见 `meis-integration`。
