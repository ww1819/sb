# MEIS 设备管理系统 — 需求与问题跟踪

> **用途**：梳理、编写、评审 MEIS 全系统业务需求，并跟踪已知问题与技术债。  
> 请在本文件中直接增删改；模块细节可拆分子文档后在此链接。

**文档状态**：草稿  
**最后更新**：2026-07-11  
**负责人**：待填写  
**系统定位**：医院医疗设备与固定资产全生命周期管理 SaaS（Schema 多租户）

**关联文档**：

- [架构说明](architecture.md)
- [设备报修 / 维修工单](repair-workorder.md)
- [用户手册（骨架）](user-manual.md)
- [本地开发部署](local-dev-deploy.md)

---

## 如何使用本文档

| 场景 | 做法 |
|------|------|
| 写新需求 | 在对应模块章节新增条目，或复制 [附录 A 需求模板](#附录-a-需求条目模板) |
| 记录缺陷 | 写入 [第 4 章 已知问题](#4-已知问题与技术债) |
| 待产品决策 | 写入 [第 5 章 待确认问题](#5-待确认问题) |
| 模块已评审 | 更新模块表中的「状态」列 |
| 版本留痕 | 更新 [第 6 章 版本记录](#6-版本记录) |

**需求编号规则**（建议）：

```
{模块缩写}-{类型}-{序号}

模块缩写：SYS 系统 | PUR 采购 | AST 资产 | WH 库房 | REP 维修 | MT 保养
         | INS 巡检 | MET 计量 | QC 质控 | MC 维保合同 | SPC 特种
         | SHR 公用设备 | PM 预防性维护 | ANA 效益 | PWR 电流监测 | PLT 平台

类型：F 功能 | B 业务规则 | U 界面 | I 接口 | D 数据 | N 非功能 | X 问题
```

示例：`REP-F-01`（维修功能需求）、`PLT-X-03`（平台已知问题）

---

## 1. 系统概述

### 1.1 建设目标

为医院提供「采购 → 入库 → 台账 → 使用维护 → 质控合规 → 报废处置 → 效益分析」的一体化设备管理平台，支撑多院区、多科室、多租户 SaaS 部署。

### 1.2 用户角色（待细化权限矩阵）

| 角色 | 典型范围 |
|------|----------|
| 平台管理员 | 租户开户、全局配置 |
| 医院系统管理员 | 用户/角色/字典/审批流 |
| 设备科管理员 | 采购、台账、维修调度、保养巡检 |
| 临床科室用户 | 报修、验收、借调申请 |
| 维修工程师 | 接单、维修、完工 |
| 质控人员 | 不良事件、计量、性能检测 |
| 院领导 / 分析人员 | 看板、效益、大屏 |

### 1.3 技术架构摘要

| 层级 | 说明 |
|------|------|
| 租户模型 | `public` 存租户元数据；`tenant_{code}` 存业务表 |
| 前端 | `meis-web`（Vue3 + Element Plus） |
| 后端 | 微服务（网关 8080，各业务服务 8081–8094） |
| 数据库迁移 | `meis-tenant` Flyway + `R__tenant_schema_sync.sql` 租户补表 |
| 移动端 | `meis-mobile`（Flutter 骨架） |

### 1.4 模块地图

| 域 | 菜单/路径前缀 | 后端服务 | 文档状态 |
|----|---------------|----------|----------|
| 平台与系统 | `/system/*` | meis-system | 待编写 |
| 基础字典 | `/dict/*` | meis-system | 待编写 |
| 采购管理 | `/purchase/*` | meis-purchase | 待编写 |
| 资产管理 | `/asset/*` | meis-asset | 待编写 |
| 库房管理 | `/warehouse/*` | meis-asset | 待编写 |
| 维修管理 | `/repair/*` | meis-repair | 部分（见 repair-workorder.md） |
| 保养管理 | `/maintain/*` | meis-maintain | 待编写 |
| 巡检管理 | `/inspect/*` | meis-inspect | 待编写 |
| 计量管理 | `/metrology/*` | meis-metrology | 待编写 |
| 质控管理 | `/qc/*` | meis-qc | 待编写 |
| 维保合同 | `/maintenance-contract/*` | meis-maintenance-contract | 待编写 |
| 特殊设备 | `/special/*` | meis-special | 待编写 |
| 公用设备 | `/shared/*` | meis-shared | 待编写 |
| 预防性维护 | `/pm/*` | meis-pm | 待编写 |
| 效益分析 | `/analytics/*` | meis-analytics | 待编写 |
| 电流监测 | `/power/*` | meis-analytics | 待编写 |
| 运营大屏 | `/screen/*` | meis-analytics | 待编写 |
| 外部集成 | — | meis-integration | 待编写 |

---

## 2. 跨模块通用需求

### 2.1 多租户与数据隔离

| 编号 | 需求 | 验收标准 | 状态 |
|------|------|----------|------|
| PLT-D-01 | 所有业务数据按租户 Schema 隔离 | 登录后仅访问本租户数据 | 已实现 |
| PLT-D-02 | 新开户租户自动建表、补列、补注释 | Flyway 迁移成功即可用；**V1 建表字段须最全**（见附录 D） | 进行中 |
| PLT-D-03 | 跨环境数据库备份可还原 | plain SQL + 版本兼容策略 | 已修复 |

### 2.2 列表与 CRUD 规范

| 编号 | 需求 | 验收标准 | 状态 |
|------|------|----------|------|
| PLT-U-01 | 列表页统一：关键词 + 筛选 + 分页 | 与 `CrudPage` 行为一致 | 已实现 |
| PLT-U-02 | 外键选择优先弹窗，避免全量下拉 | 设备/基站等大数据量字段用 Picker | 进行中 |
| PLT-I-01 | 列表与保存 API 路径一致 | `listPageUrl` 与 `saveUrl` 配对配置 | 已修复 |
| PLT-I-02 | 主从单据保存须含明细 | 主从页不可只存主表 | **待处理** |

### 2.3 审批与工作流

| 编号 | 需求 | 验收标准 | 状态 |
|------|------|----------|------|
| PLT-F-01 | 可配置审批流 | `sys_approval_flow` | 骨架 |
| PLT-F-02 | 业务单据支持提交审批 | `WorkflowCrudPage` + `ApprovalPanel` | 部分模块 |

### 2.4 导入导出

| 编号 | 需求 | 验收标准 | 状态 |
|------|------|----------|------|
| PLT-F-03 | 支持 Excel 导入的表提供模板下载 | `import/template` | 部分表 |
| PLT-F-04 | 列表支持 CSV/Excel 导出 | 通用 export | 部分表 |

### 2.5 设备台账联动

| 编号 | 需求 | 验收标准 | 状态 |
|------|------|----------|------|
| PLT-B-01 | `medical_device` 为各业务模块核心主数据 | 维修/保养/巡检/计量等均关联 device_id | 已实现 |
| PLT-B-02 | 设备状态与工单状态联动 | 报修中→maintenance；待验收→pending_verify | 已实现（维修） |
| PLT-B-03 | 设备选择交互统一 | 复用 `AssetDevicePicker` / `RepairDevicePicker` | 进行中 |

---

## 3. 分模块需求

> 每节包含：业务目标、功能清单、关键业务规则、界面要点、接口/数据、验收用例（待填写）。  
> **状态**：未开始 | 草稿 | 评审中 | 已确认 | 开发中 | 已实现

---

### 3.1 平台与系统管理（SYS）

**服务**：meis-system、meis-auth、meis-tenant  
**状态**：草稿

| 子模块 | 路径 | 核心表/实体 | 状态 |
|--------|------|-------------|------|
| 院区管理 | `/system/campus` | `campus` | 待编写 |
| 科室管理 | `/system/dept` | `department` | 待编写 |
| 用户管理 | `/system/user` | `sys_user` | 待编写 |
| 角色管理 | `/system/role` | `sys_role` | 待编写 |
| 数据字典 | `/system/dict` | `sys_dict` | 待编写 |
| 操作日志 | `/system/log` | `sys_operation_log` | 待编写 |
| 审批配置 | `/system/approval` | `sys_approval_flow` | 待编写 |
| 租户管理 | 平台 | `sys_tenant` | 待编写 |

**需求摘要（待补充）**：

- [ ] SYS-F-01 登录：医院编码 + 账号 + 密码，JWT 含 schemaName
- [ ] SYS-F-02 角色权限控制菜单与按钮（`v-permission`）
- [ ] SYS-F-03 租户开户自动执行 Schema 迁移
- [ ] SYS-B-01 科室与院区层级关系规则
- [ ] SYS-B-02 用户与科室归属、数据权限范围

---

### 3.2 基础字典（DICT）

**服务**：meis-system  
**状态**：未开始

| 子模块 | 路径 | 说明 |
|--------|------|------|
| 供应商 | `/dict/supplier` | 支持导入、拼音简码 |
| 生产厂家 | `/dict/manufacturer` | 支持导入、拼音简码 |
| 设备 68 分类 | `/dict/category` | 医疗器械分类 |
| 资产分类 | `/dict/asset-category` | |
| 财务分类 | `/dict/finance-category` | |
| 科室维护 | `/dict/dept` | |
| 仓库维护 | `/dict/warehouse` | |
| 单位维护 | `/dict/unit` | |

**需求摘要（待补充）**：

- [ ] DICT-F-01 字典数据全院共享，变更需审计
- [ ] DICT-F-02 拼音简码批量生成
- [ ] DICT-B-01 已被业务引用的字典项不可物理删除

---

### 3.3 采购管理（PUR）

**服务**：meis-purchase  
**状态**：未开始

| 子模块 | 路径 | 主从 | 说明 |
|--------|------|------|------|
| 采购计划 | `/purchase/plan` | 是 | 含明细 |
| 采购申请 | `/purchase/apply` | 是 | |
| 采购审批 | `/purchase/approval` | — | 审批实例 |
| 采购项目 | `/purchase/project` | — | |
| 设备合同 | `/purchase/contract` | — | 含付款计划 |
| 安装验收 | `/purchase/acceptance` | — | 由合同驱动，禁止手工新增 |
| 采购看板 | `/purchase/dashboard` | — | |
| 业务追溯 | `/purchase/trace` | — | |

**需求摘要（待补充）**：

- [ ] PUR-F-01 计划 → 申请 → 审批 → 项目 → 合同 → 验收 全链路
- [ ] PUR-B-01 验收单由合同自动生成，不可页面直接新增
- [ ] PUR-B-02 审批状态机与驳回重提规则
- [ ] PUR-F-02 合同验收完成后联动设备入库（待确认流程）
- [ ] PUR-I-01 专用分页/保存 API 与通用 CRUD 分工

---

### 3.4 资产管理（AST）

**服务**：meis-asset  
**状态**：未开始

| 子模块 | 路径 | 说明 |
|--------|------|------|
| 资产综合查询 | `/asset/query` | 多条件查询 |
| 资产管理（台账） | `/asset/device` | 设备主档案 |
| 资产导入 | `/asset/import` | Excel 导入 |
| 设备入库 | `/asset/entry` | 主从单据 |
| 设备出库 | `/asset/outbound` | 主从单据 |
| 资产流转 | `/asset/transfer` | |
| 资产盘点 | `/asset/inventory` | 主从 |
| 设备报废 | `/asset/scrap` | |

**需求摘要（待补充）**：

- [ ] AST-F-01 设备台账字段分组：基本信息 / 财务 / 位置 / 合规
- [ ] AST-B-01 设备编码、财务编码生成规则
- [ ] AST-B-02 设备状态枚举及与各业务模块联动
- [ ] AST-D-01 台账新增待机电流上下限（见 [3.16.1](#3161-待机电流与运行状态判定)）
- [ ] AST-F-02 入库/出库/盘点主从保存完整性
- [ ] AST-F-03 盘点扫码（移动端，待规划）
- [ ] AST-U-01 台账详情卡片式展示

---

### 3.5 库房管理（WH）

**服务**：meis-asset（与资产共用）  
**状态**：未开始

| 子模块 | 路径 | 说明 |
|--------|------|------|
| 库房维护 | `/warehouse/setting` | |
| 设备入库 | `/warehouse/entry` | 同资产入库 |
| 设备出库 | `/warehouse/outbound` | |
| 设备退货 | `/warehouse/return` | 主从 |
| 库房调拨 | `/warehouse/transfer` | |
| 库存盘点 | `/warehouse/inventory` | |
| 设备报废 | `/warehouse/scrap` | |

**需求摘要（待补充）**：

- [ ] WH-B-01 库房与科室/院区关系
- [ ] WH-B-02 入库来源：采购验收 / 调拨 / 其他
- [ ] WH-F-01 退货流程与台账状态回滚
- [ ] WH-D-01 租户 Schema 须含 `device_return*` 等表

---

### 3.6 维修管理（REP）

**服务**：meis-repair  
**状态**：部分已文档化 → [repair-workorder.md](repair-workorder.md)

| 子模块 | 路径 | 说明 |
|--------|------|------|
| 报修申请 | `/repair/apply` | listMode=apply |
| 维修处理 | `/repair/handle` | listMode=handle |
| 维修验收 | `/repair/verify` | listMode=verify |
| 维修工单 | `/repair/workorder` | 全量 |
| 工程师 | `/repair/engineer` | |
| 配件档案 | `/repair/spare-archive` | |
| 故障库 | `/repair/fault` | |

**需求摘要**：

- [x] REP-F-01 设备弹窗选择（`RepairDevicePicker`）
- [x] REP-B-01 双层状态：主状态 + 维修子状态
- [x] REP-B-02 设备台账 `pending_verify` 状态
- [ ] REP-F-02 备件领用与库存扣减
- [ ] REP-F-03 外协维修独立单据
- [ ] REP-F-04 移动端扫码报修

---

### 3.7 保养管理（MT）

**服务**：meis-maintain  
**状态**：未开始

| 子模块 | 路径 |
|--------|------|
| 保养参数 | `/maintain/param` |
| 保养模板 | `/maintain/template` |
| 保养计划 | `/maintain/plan` |
| 保养执行 | `/maintain/execution` |
| 保养记录查询 | `/maintain/query` |
| 保养设备管理 | `/maintain/device` |
| 保养记录 | `/maintain/record` |

**需求摘要（待补充）**：

- [ ] MT-B-01 模板 → 计划 → 执行 → 记录 闭环
- [ ] MT-B-02 保养等级与周期规则
- [ ] MT-F-01 到期提醒与任务分派
- [ ] MT-F-02 执行项勾选/拍照/签名（移动端）

---

### 3.8 巡检管理（INS）

**服务**：meis-inspect  
**状态**：未开始

| 子模块 | 路径 |
|--------|------|
| 巡检参数 | `/inspect/param` |
| 巡检计划 | `/inspect/plan` |
| 巡检执行 | `/inspect/execution` |
| 巡检记录查询 | `/inspect/query` |
| 巡检设备管理 | `/inspect/device` |

**需求摘要（待补充）**：

- [ ] INS-B-01 与保养模块的差异界定（日常巡检 vs 定期保养）
- [ ] INS-F-01 巡检模板检查项配置
- [ ] INS-F-02 异常项自动生成维修工单（待确认）

---

### 3.9 计量管理（MET）

**服务**：meis-metrology  
**状态**：未开始

| 子模块 | 路径 |
|--------|------|
| 计量参数 | `/metrology/param` |
| 计量计划 | `/metrology/plan` |
| 计量执行 | `/metrology/execution` |
| 计量记录查询 | `/metrology/query` |
| 计量设备管理 | `/metrology/device` |

**需求摘要（待补充）**：

- [ ] MET-B-01 强检/非强检分类
- [ ] MET-B-02 证书有效期与到期预警
- [ ] MET-F-01 检定机构管理
- [ ] MET-F-02 计量结果与台账 `is_metrology` 联动

---

### 3.10 质控管理（QC）

**服务**：meis-qc  
**状态**：未开始

| 子模块 | 路径 |
|--------|------|
| 风险评估 | `/qc/risk` |
| 不良事件 | `/qc/adverse` |
| 不良事件上报 | `/qc/adverse/report` |
| 不良事件查询 | `/qc/adverse/query` |
| 计量管理 | `/qc/metrology` |
| 性能检测 | `/qc/performance` |

**需求摘要（待补充）**：

- [ ] QC-B-01 不良事件上报与调查闭环
- [ ] QC-F-01 与维修工单、设备台账关联
- [ ] QC-B-02 性能检测周期与模板

---

### 3.11 维保合同（MC）

**服务**：meis-maintenance-contract  
**状态**：未开始

| 子模块 | 路径 |
|--------|------|
| 维保合同 | `/maintenance-contract/list` |
| 履约记录 | `/maintenance-contract/fulfillment` |

**需求摘要（待补充）**：

- [ ] MC-B-01 合同覆盖设备范围
- [ ] MC-F-01 履约记录与费用统计
- [ ] MC-F-02 到期提醒

---

### 3.12 特殊设备（SPC）

**服务**：meis-special  
**状态**：未开始

| 子模块 | 路径 |
|--------|------|
| 生命支持设备 | `/special/life` |
| 特种设备登记 | `/special/radiation` |
| 应急设备库 | `/special/emergency` |
| 租赁设备 | `/special/leased` |
| 证照到期提醒 | `/special/alerts` |

**需求摘要（待补充）**：

- [ ] SPC-B-01 生命支持设备重点监控规则
- [ ] SPC-F-01 特种设备证照管理
- [ ] SPC-F-02 应急设备调配记录

---

### 3.13 公用设备（SHR）

**服务**：meis-shared  
**状态**：未开始

| 子模块 | 路径 |
|--------|------|
| 公用设备管理 | `/shared/device` |
| 借调申请 | `/shared/loan` |
| 借调审批 | `/shared/loan-approve` |
| 归还申请 | `/shared/return` |
| 归还审批 | `/shared/return-approve` |
| 借调收费 | `/shared/fee` |
| 借调记录查询 | `/shared/record` |

**需求摘要（待补充）**：

- [ ] SHR-B-01 借调审批流程
- [ ] SHR-B-02 归还验收与设备状态恢复
- [ ] SHR-F-01 借调计费规则
- [ ] SHR-D-01 租户 Schema 须含 `shared_device*` 表

---

### 3.14 预防性维护（PM）

**服务**：meis-pm  
**状态**：未开始

| 子模块 | 路径 |
|--------|------|
| PM 参数 | `/pm/param` |
| PM 计划 | `/pm/plan` |
| PM 执行 | `/pm/execution` |
| PM 记录 | `/pm/query` |
| PM 设备 | `/pm/device` |

**需求摘要（待补充）**：

- [ ] PM-B-01 与保养模块边界（厂家 PM vs 院内保养）
- [ ] PM-F-01 模板检查项与执行记录

---

### 3.15 效益分析（ANA）

**服务**：meis-analytics  
**状态**：未开始

| 子模块 | 路径 |
|--------|------|
| 对照管理 | `/analytics/mapping` |
| 数据抓取 | `/analytics/sync` |
| 效益分析汇总 | `/analytics/summary` |
| 成本上报 | `/analytics/cost` |
| 单机效益分析 | `/analytics/device` |

**需求摘要（待补充）**：

- [ ] ANA-B-01 效益指标定义（使用率、利润率、机时等）
- [ ] ANA-F-01 HIS/PACS 数据对照与同步
- [ ] ANA-F-02 成本项归集规则

---

### 3.16 电流监测（PWR）

**服务**：meis-analytics  
**状态**：评审中

| 子模块 | 路径 |
|--------|------|
| 基站维护 | `/power/station` |
| 标签维护 | `/power/tag` |
| 设备运行状态 | `/power/status` |
| 设备运行统计 | `/power/stats` |
| 监测记录 | `/power/record` |

**需求摘要**：

- [x] PWR-U-01 关联设备、所属基站使用弹窗选择（非下拉）
- [ ] PWR-D-01 租户 Schema 须含 `power_*` 表及新增扩展表（见下文）
- [ ] PWR-F-01 待机电流上下限与运行状态自动判定
- [ ] PWR-F-02 基站维护：关联标签列表、基站级监测记录
- [ ] PWR-F-03 标签维护：扩展列表字段、监测记录弹窗、电流上下限维护、绑定追溯
- [ ] PWR-F-04 实时采集数据接入（MQTT/协议，替换当前 Mock 采集）

---

#### 3.16.1 待机电流与运行状态判定

**关联台账**：`medical_device`（资产台账）

| 编号 | 需求 | 说明 |
|------|------|------|
| PWR-D-02 | 台账新增字段 `standby_current_max_ma` | 待机电流**上限**，单位：毫安（mA） |
| PWR-D-03 | 台账新增字段 `standby_current_min_ma` | 待机电流**下限**，单位：毫安（mA） |
| PWR-B-01 | 运行状态判定规则 | 依据实时电流读数（mA）与设备台账上下限比较；支持上下限部分缺失 |
| PWR-B-02 | 上下限维护归属 | 标签页维护的值**回写设备台账** `medical_device`，不在 `power_tag` 存副本 |

**已确认决策（2026-07-11）**

1. **标签页维护待机电流上下限 → 回写设备台账**（Q-09 ✓）
2. **上下限缺失时的判定规则**（Q-10 ✓），见下表  
3. **读数等于下限时归入待机**（边界规则 ✓）

**判定逻辑**（`work_state`，读数单位 mA，上限 = `standby_current_max_ma`，下限 = `standby_current_min_ma`）：

**情形 A — 上下限均已配置**

| 条件 | 状态 | 编码 |
|------|------|------|
| 读数 **>** 上限 | 工作中 | `running` |
| 下限 ≤ 读数 ≤ 上限 | 待机 | `idle` |
| 读数 **<** 下限，或读数 **=** 0 | 关机 | `offline` |

**情形 B — 上下限均未配置**

| 条件 | 状态 | 编码 |
|------|------|------|
| 读数 **>** 0 | 待机 | `idle` |
| 读数 **=** 0 | 关机 | `offline` |

**情形 C — 仅配置上限**

| 条件 | 状态 | 编码 |
|------|------|------|
| 读数 **>** 上限 | 工作中 | `running` |
| 0 **<** 读数 ≤ 上限 | 待机 | `idle` |
| 读数 **=** 0 | 关机 | `offline` |

**情形 D — 仅配置下限**

| 条件 | 状态 | 编码 |
|------|------|------|
| 读数 **<** 下限 | 关机 | `offline` |
| 读数 **≥** 下限（**含等于下限**） | 待机 | `idle` |

> 例：下限 50mA 时，读数 50mA → 待机；读数 30mA 或 0 → 关机。

> 实现参考（伪代码）：
>
> ```
> if (hasMax && hasMin) {
>   if (v > max) return running;
>   if (v == 0 || v < min) return offline;
>   return idle;
> }
> if (!hasMax && !hasMin) {
>   return v > 0 ? idle : offline;
> }
> if (hasMax && !hasMin) {
>   if (v == 0) return offline;
>   if (v > max) return running;
>   return idle;
> }
> if (!hasMax && hasMin) {
>   if (v < min) return offline;
>   return idle;  // v >= min，含等于下限
> }
> ```

> 注：当前实现为 Mock 随机状态（`PowerMonitorService`），须按上表改写。

**验收标准**：

- [ ] 资产管理台账表单可维护两个电流上下限字段
- [ ] 标签页修改上下限后，`medical_device` 对应记录同步更新
- [ ] 采集服务在 A/B/C/D 四种情形下输出正确 `work_state`
- [ ] 设备运行状态页展示与规则一致

---

#### 3.16.2 基站维护

| 编号 | 需求 | 说明 | 优先级 |
|------|------|------|--------|
| PWR-F-10 | **标签列表** | 在基站详情/编辑中查看该基站关联的全部标签 | P0 |
| PWR-F-11 | **监测记录** | 查看该基站接收到的电流读数原始记录 | P0 |

**PWR-F-10 标签列表**

- 入口：基站维护 → 编辑/详情 →「关联标签」Tab 或按钮
- 数据范围：`power_tag.station_id = 当前基站`
- 展示字段（至少）：标签编码、标签名称、关联设备编码、关联设备名称、是否启用、安装日期

**PWR-F-11 基站监测记录**

- 入口：基站维护 →「监测记录」
- 数据范围：该基站下所有标签上报的读数
- 列表字段（至少）：

| 字段 | 说明 |
|------|------|
| `tag_code` | 标签编码 |
| `station_code` | 基站编码 |
| `device_id` | 设备 ID |
| `device_code` | 设备编码（冗余展示） |
| `current_ma` | 电流读数（mA） |
| `read_at` | 读取时间（设备/采集端时间） |
| `created_at` | 插入时间（系统入库时间） |

- 支持：分页、按读取时间范围筛选

---

#### 3.16.3 标签维护

| 编号 | 需求 | 说明 | 优先级 |
|------|------|------|--------|
| PWR-F-20 | **列表扩展字段** | 列表 JOIN 设备台账展示更多设备信息 | P0 |
| PWR-F-21 | **监测记录弹窗** | 单标签电流读数报表 | P0 |
| PWR-F-22 | **维护待机电流上下限** | 标签已绑设备时可直接维护 | P0 |
| PWR-F-23 | **绑定记录** | 标签历史绑定设备追溯 | P1 |

**PWR-F-20 标签列表扩展列**

在现有标签字段基础上，列表须展示（来自 `medical_device` 及关联表）：

| 列 | 来源字段 |
|----|----------|
| 设备编码 | `device_code` |
| 设备名称 | `device_name` |
| 规格 | `specification` |
| 型号 | `model` |
| 序列号 | `serial_number` |
| 生产厂家 | `manufacturer_name`（JOIN） |
| 所属科室 | `dept_name`（JOIN） |

**PWR-F-21 标签监测记录弹窗**

- 入口：标签列表行操作 →「监测记录」
- 形式：弹窗内表格报表
- 数据范围：当前标签的全部原始读数
- 功能：
  - [ ] 按**读取时间**正序 / 逆序排列（可切换）
  - [ ] 查询条件：**开始时间**、**结束时间**
  - [ ] 支持**导出**（Excel/CSV，格式待确认）
- 列字段：同 [PWR-F-11](#pwr-f-11-基站监测记录)（可不展示基站编码，或固定为当前标签所属基站）

**PWR-F-22 标签页维护待机电流上下限**

- 条件：标签已绑定 `device_id`
- 行为（**已确认：回写设备台账**）：
  - 标签编辑表单展示并可编辑 `standby_current_max_ma`、`standby_current_min_ma`
  - 保存标签时同步 `UPDATE medical_device`，**不在 `power_tag` 存副本**
  - 表单初始值从关联设备台账加载
- 未绑定设备时：字段只读或隐藏，提示先选择设备

**PWR-F-23 标签绑定记录**

- 背景：标签存在换绑、解绑，需追溯历史
- 入口：标签编辑/详情 →「绑定记录」Tab 或弹窗
- 展示字段（至少）：

| 字段 | 说明 |
|------|------|
| 设备编码 | 曾绑定的设备 |
| 设备名称 | |
| 绑定开始时间 | `bound_at` |
| 绑定结束时间 | `unbound_at`（当前绑定则为空） |
| 操作人 | 可选 |
| 备注 | 换绑原因等 |

- 规则：每次变更 `device_id` 时关闭上一条记录、插入新记录

---

#### 3.16.4 数据模型补充（草案）

> 现有 `power_monitor_record` 为**按日汇总**（运行/待机/关机时长、能耗），不满足原始读数查询。须新增或扩展表结构。

**A. 资产台账字段**（`medical_device`）

```sql
standby_current_max_ma DECIMAL(10,2)  -- 待机电流上限(mA)
standby_current_min_ma DECIMAL(10,2)  -- 待机电流下限(mA)
```

**B. 原始读数表**（建议新建 `power_current_reading`）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID | 主键 |
| `tag_id` | UUID | 标签 |
| `tag_code` | VARCHAR | 冗余 |
| `station_id` | UUID | 基站 |
| `station_code` | VARCHAR | 冗余 |
| `device_id` | UUID | 可空（未绑定时） |
| `current_ma` | DECIMAL | 电流读数 mA |
| `read_at` | TIMESTAMPTZ | 读取时间 |
| `created_at` | TIMESTAMPTZ | 系统插入时间 |

**C. 标签绑定历史表**（建议新建 `power_tag_bind_log`）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID | 主键 |
| `tag_id` | UUID | 标签 |
| `device_id` | UUID | 设备 |
| `device_code` | VARCHAR | 冗余 |
| `device_name` | VARCHAR | 冗余 |
| `bound_at` | TIMESTAMPTZ | 绑定开始 |
| `unbound_at` | TIMESTAMPTZ | 绑定结束（NULL=当前有效） |
| `operator_id` | UUID | 操作人（可选） |
| `remark` | TEXT | 备注 |

**D. 与现有表关系**

| 现有表 | 用途 | 与本需求关系 |
|--------|------|--------------|
| `power_monitor_record` | 日汇总统计 | 保留；由原始读数聚合生成 |
| `power_device_status` | 当前态快照 | 保留；由最新读数 + 上下限判定更新 |

---

#### 3.16.5 接口草案（待开发）

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/power/station/{id}/tags` | GET | 基站关联标签列表 |
| `/api/power/station/{id}/readings/page` | GET | 基站监测记录分页 |
| `/api/power/tag/page` | GET | 扩展 JOIN 设备字段（已有，需加列） |
| `/api/power/tag/{id}/readings/page` | GET | 标签监测记录，支持时间范围、排序 |
| `/api/power/tag/{id}/readings/export` | GET | 标签监测记录导出 |
| `/api/power/tag/{id}/bind-log` | GET | 标签绑定历史 |
| `/api/power/tag` | POST | 保存时写绑定日志、**回写**设备台账电流上下限 |

---

#### 3.16.6 验收用例（电流监测专项）

**待机电流判定**

| 步骤 | 操作 | 期望 |
|------|------|------|
| 1 | 设备设上限 500mA、下限 50mA，读数 600mA | `running` |
| 2 | 同上，读数 100mA | `idle` |
| 3 | 同上，读数 30mA 或 0 | `offline` |
| 4 | 上下限均未配置，读数 10mA | `idle` |
| 5 | 上下限均未配置，读数 0 | `offline` |
| 6 | 仅上限 500mA，读数 600mA | `running` |
| 7 | 仅上限 500mA，读数 100mA | `idle` |
| 8 | 仅上限 500mA，读数 0 | `offline` |
| 9 | 仅下限 50mA，读数 100mA | `idle` |
| 10 | 仅下限 50mA，读数 50mA（等于下限） | `idle` |
| 11 | 仅下限 50mA，读数 30mA 或 0 | `offline` |
| 12 | 标签页修改上下限并保存 | `medical_device` 字段已更新 |

**基站标签列表与监测记录**

| 步骤 | 操作 | 期望 |
|------|------|------|
| 1 | 打开某基站详情 | 可见关联标签列表 |
| 2 | 打开基站监测记录 | 仅含该基站读数，含标签编码、读数、读取/插入时间 |

**标签监测记录与绑定追溯**

| 步骤 | 操作 | 期望 |
|------|------|------|
| 1 | 标签列表 | 可见设备编码、名称、规格、型号、序列号、厂家、科室 |
| 2 | 点击「监测记录」 | 弹窗报表，支持时间筛选、排序、导出 |
| 3 | 换绑设备后查看绑定记录 | 历史设备与时间段完整 |

---

### 3.17 运营大屏（SCR）

**路径**：`/screen/equipment`  
**状态**：未开始

**需求摘要（待补充）**：

- [ ] SCR-F-01 设备总量、维修中、保养到期等指标卡
- [ ] SCR-F-02 科室/院区维度钻取
- [ ] SCR-N-01 大屏刷新频率与性能

---

### 3.18 外部集成（INT）

**服务**：meis-integration  
**状态**：占位

**需求摘要（待补充）**：

- [ ] INT-F-01 HIS 患者/科室数据同步
- [ ] INT-F-02 PACS 检查量抓取（效益分析）
- [ ] INT-F-03 HRP 财务数据对接
- [ ] INT-F-04 LIS 对接（如需要）

---

### 3.19 移动端（MOB）

**工程**：meis-mobile（Flutter）  
**状态**：骨架

**需求摘要（待补充）**：

- [ ] MOB-F-01 扫码报修
- [ ] MOB-F-02 盘点扫码
- [ ] MOB-F-03 保养/巡检任务执行
- [ ] MOB-F-04 维修验收确认

---

## 4. 已知问题与技术债

> 开发、测试、实施中已发现的问题。修复后请将「状态」改为已解决，并注明版本/提交。

| 编号 | 模块 | 问题描述 | 影响 | 处理建议 | 状态 |
|------|------|----------|------|----------|------|
| PLT-X-01 | 平台 | 租户 Schema 缺表（如 `power_*`、`unit_dict`、`shared_device*`），导致业务 API 报错 | 新租户/迁移后功能不可用 | `SchemaTableEnsuring` 执行 V1/V2；`R__` 仅补列 | 已修复 |
| PLT-X-02 | 平台 | Flyway 可重复脚本在表未建时执行 INSERT 导致 `meis-tenant` 启动失败 | 租户服务无法启动 | 调整迁移顺序：Ensuring → migrate → CommentFiller | 已修复 |
| PLT-X-03 | 前端 | 列表用专用 `page` API，新增却走通用 `POST /{table}`，保存后列表看不到 | 基站等多模块 | `pageRegistry.saveUrl` + `WorkflowCrudPage` 合并配置 | 已修复 |
| PLT-X-04 | 前端 | `refSelectConfig` 未注册 `linkTable` 时外键下拉为空 | 标签所属基站等 | 补注册或改弹窗选择 | 已修复（基站） |
| PLT-X-05 | 前端 | 主从单据页简单「新增」可能只保存主表，明细丢失 | 入库/出库/计划等 | 需专用保存流程或禁止简单新增 | **待处理** |
| PLT-X-06 | 数据库 | PG 自定义格式备份跨版本 `pg_restore` 不兼容 | 环境迁移失败 | 默认 plain SQL 备份 + 还原预检 | 已修复 |
| PLT-X-07 | 数据库 | 还原后对象所有权不一致 | 应用连接失败 | `fix-db-ownership.sql` | 已修复 |
| PLT-X-08 | 电流监测 | 标签关联设备曾用全量下拉，体验差且性能差 | 标签维护 | 改用 `AssetDevicePicker` 弹窗 | 已修复 |
| PLT-X-09 | 采购 | 安装验收单不应手工新增 | 数据一致性 | `hide-add` | 已修复 |
| PLT-X-10 | 全局 | 各模块大量页面仍使用 RefSelect 全量下拉 | 性能、体验 | 逐步改为分页弹窗选择器 | 待规划 |
| PLT-X-11 | 审批 | 审批流仅部分模块接入 | 流程不完整 | 按模块优先级接入 `WorkflowCrudPage` | 待规划 |
| PLT-X-13 | 电流监测 | `power_monitor_record` 仅为日汇总，无原始读数表 | 无法满足基站/标签监测记录需求 | 新增 `power_current_reading` | **待处理** |
| PLT-X-14 | 电流监测 | 运行状态为 Mock 随机，未按待机电流上下限判定 | 状态不准确 | 接入上下限规则 | **待处理** |

**新增问题模板**（复制到上表）：

```markdown
| XXX-X-NN | 模块 | 问题描述 | 影响 | 处理建议 | 待处理 |
```

---

## 5. 待确认问题

| 编号 | 模块 | 问题 | 选项/建议 | 决策 |
|------|------|------|-----------|------|
| Q-01 | 全局 | 设备主数据以何为准？采购入库 vs 手工建档 | 建议：入库/验收驱动建档 | 待确认 |
| Q-02 | 维修 | 外协维修是否独立工单类型 | 建议：二期独立 | 待确认 |
| Q-03 | 电流监测 | 一设备多标签是否允许 | 视硬件而定 | 待确认 |
| Q-09 | 电流监测 | 待机电流上下限维护在标签页是回写台账还是标签独立覆盖 | 回写 `medical_device` | **已确认** |
| Q-10 | 电流监测 | 上下限未配置或部分配置时运行状态如何判定 | 见 §3.16.1 情形 A–D | **已确认** |
| Q-11 | 电流监测 | 原始读数保留多久 | 影响存储与归档策略 | 待确认 |
| Q-04 | 库房 | 资产模块与库房模块菜单重复，如何分工 | 按角色/场景拆分或合并 | 待确认 |
| Q-05 | 保养/巡检/PM | 三模块边界与是否合并展示 | 需业务方定义 | 待确认 |
| Q-06 | 权限 | 科室用户数据权限：本科室 vs 本院 | 需院方确认 | 待确认 |
| Q-07 | 移动端 | 一期移动端范围 | 报修 + 盘点？ | 待确认 |
| Q-08 | 部署 | 单院私有部署 vs 多租户 SaaS 优先级 | 影响认证与运维 | 待确认 |

---

## 6. 版本记录

| 版本 | 日期 | 作者 | 变更说明 |
|------|------|------|----------|
| 0.1 | 2026-07-11 | — | 初稿：仅电流监测 |
| 1.0 | 2026-07-11 | — | 扩展为全系统需求与问题跟踪 |
| 1.1 | 2026-07-11 | — | 补充电流监测：待机电流上下限、基站/标签监测记录、绑定追溯 |
| 1.2 | 2026-07-11 | — | 确认 Q-09/Q-10：上下限回写台账；四种缺失情形判定规则 |
| 1.3 | 2026-07-11 | — | 明确仅下限时读数等于下限归入待机 |
| 1.4 | 2026-07-11 | — | 附录 D：数据库迁移双轨规范；V1 补全电流监测相关字段与表 |
| 1.5 | 2026-07-11 | — | 附录 D.4：明确老租户启动先建表后补列；`SchemaTableEnsuring` 执行 V1/V2 |
| 1.6 | 2026-07-11 | — | 清理 `R__tenant_schema_sync.sql` 建表语句；R__ 仅保留补列与种子数据 |
| 1.7 | 2026-07-11 | — | 附录 E：开发完成验收清单；开发面板热加载改为同步执行并分步反馈 |

---

## 附录 A：需求条目模板

```markdown
#### {编号} {标题}

**用户故事**  
作为 ___角色___，我希望 ___做什么___，以便 ___达成什么目标___。

**业务规则**
1. 
2. 

**界面要求**
- 

**接口 / 数据**
- 表：
- API：

**验收标准**
- [ ] 
- [ ] 

**优先级**：P0 / P1 / P2  
**状态**：草稿 / 评审中 / 已确认 / 开发中 / 已实现  
**负责人**：
```

---

## 附录 B：配置与代码索引

| 用途 | 路径 |
|------|------|
| 页面路由与 API 配置 | `meis-web/src/config/pageRegistry.ts` |
| 字段/表单 Schema | `meis-web/src/config/businessSchemas.ts` |
| 外键下拉配置 | `meis-web/src/config/refSelectConfig.ts` |
| 通用 CRUD 页 | `meis-web/src/components/CrudPage.vue` |
| 审批 CRUD 页 | `meis-web/src/components/WorkflowCrudPage.vue` |
| 主从单据页 | `meis-web/src/components/MasterDetailPage.vue` |
| 设备弹窗（通用） | `meis-web/src/components/form/AssetDevicePicker.vue` |
| 设备弹窗（报修） | `meis-web/src/components/repair/RepairDevicePicker.vue` |
| 基站弹窗 | `meis-web/src/components/form/PowerStationPicker.vue` |
| 租户迁库 | `meis-tenant/src/main/resources/db/migrations/` |
| 租户补表脚本 | `meis-tenant/.../R__tenant_schema_sync.sql` |
| 数据库备份还原 | `scripts/backup-db.ps1`、`scripts/restore-db.ps1` |

---

## 附录 C：非功能需求（全局）

| 编号 | 类别 | 要求 |
|------|------|------|
| NF-01 | 性能 | 列表首屏 ≤ 3s（常规数据量） |
| NF-02 | 性能 | 弹窗选择器首屏 ≤ 100 条 |
| NF-03 | 安全 | 租户隔离、JWT 鉴权、操作审计 |
| NF-04 | 可用性 | 核心服务支持 Docker 部署 |
| NF-05 | 可维护性 | 新业务表优先复用 GenericTableController + Schema 配置 |
| NF-06 | 兼容性 | 数据库备份跨 PG 小版本可还原 |
| NF-07 | 文档 | 复杂流程须独立 md（如 repair-workorder.md） |

---

## 附录 D：数据库迁移规范（必读）

> 详细说明见 [`meis-tenant/src/main/resources/db/README.md`](../meis-tenant/src/main/resources/db/README.md)

### D.1 双轨同步原则

新增或修改租户表字段时，**必须同时维护两处**，保证新开户与老租户升级结果一致：

| 场景 | `V1__tables.sql`（全量建表） | `R__tenant_schema_sync.sql`（老租户补列） |
|------|------------------------------|------------------------------------------|
| **新建表** | 写入完整 `CREATE TABLE` + 字段 + 默认 `COMMENT` | **不写**（建表由 `SchemaTableEnsuring` 执行 V1） |
| **已有表加列** | 在对应 `CREATE TABLE` 中补上该列 | **单独一行** `ALTER TABLE ... ADD COLUMN IF NOT EXISTS ...`（禁止一条 ALTER 多列） |
| **索引** | `V2__extensions.sql` | **不写**（索引由 `SchemaTableEnsuring` 执行 V2） |
| **手工镜像** | `db/source/create/tenant_tables.sql` | `db/source/patches/tenant_column_patches.sql`（与 R__ 补列段同步） |

### D.2 开发检查清单（提交前）

- [ ] `V1__tables.sql` 中 `CREATE TABLE` 已包含全部字段（新租户开箱即用）
- [ ] `R__tenant_schema_sync.sql` 中每条新增列有对应 `ADD COLUMN IF NOT EXISTS`
- [ ] R__ 中**无** `CREATE TABLE` / `CREATE INDEX`（建表与索引仅维护 V1/V2）
- [ ] `V2__extensions.sql` 已补充必要索引
- [ ] `tenant_column_patches.sql` 已镜像 R__ 补列语句
- [ ] 未在 R__ 中写 `COMMENT ON`（空注释由 `SchemaCommentFiller` 补全）

### D.3 本次审计补全记录（2026-07-11）

对照 R__ 补列脚本与 V1 建表，已补入 V1 的缺失项：

| 表 | 补入 V1 的字段/对象 |
|----|---------------------|
| `medical_device` | `is_shared_device`、`is_pm_device`、`standby_current_max_ma`、`standby_current_min_ma` |
| `power_tag` | `device_code`、`device_name` |
| （新表） | `power_current_reading`、`power_tag_bind_log` + 索引 |

其余 R__ 中的 `ALTER` 列（如 `repair_workorder`、`inspection_plan`、`device_outbound` 等）经核对 **已在 V1 中存在**，无需重复添加。

---

### D.4 老租户启动时的建表与补列（已落地）

**问：老租户执行建表语句有无问题？**  
**答：没有问题。** 使用 `CREATE TABLE IF NOT EXISTS` 时，已存在的表会被跳过，不会删表、不会改已有列，不影响存量数据。

**`meis-tenant` 启动时**（`TenantSchemaMigrator`）对每个活跃租户 schema 自动执行：

| 步骤 | 组件 | 作用 |
|------|------|------|
| 1 | `SchemaTableEnsuring` | 幂等建表与索引：`V1__tables.sql` + `V2__extensions.sql` |
| 2 | Flyway `migrate` | 补列与修正：`R__tenant_schema_sync.sql` 中的 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` 及字典/数据 UPDATE |
| 3 | `SchemaCommentFiller` | 仅对空注释列/表补 `COMMENT ON` |

代码位置：`meis-tenant/.../TenantSchemaMigrator.java`、`SchemaTableEnsuring.java`。

---

## 附录 E：开发完成验收清单（必读）

> 多次出现「前端已改但页面无数据/字段为空」，通常是 **热错了微服务**、**后端未重启** 或 **前后端字段名不一致**。每次功能开发完成后，请按本清单自检。

### E.1 模块与微服务对照

| 前端模块/路由 | API 前缀 | 负责微服务 | 面板端口 |
|--------------|----------|------------|----------|
| 电流监测 `/power/*` | `/api/power/**` | **meis-analytics** | :8091 |
| 资产台账 `/asset/*` | `/api/asset/**` | meis-asset | :8085 |
| 租户/迁库 | `/api/tenant/**` | meis-tenant | :8082 |
| 系统设置 `/system/*` | `/api/system/**` | meis-system | :8083 |
| 前端页面 | — | meis-web（Vite） | :5173 |

**规则**：改哪个服务的 Java 代码，就对哪个服务做「热加载」或「重启」；`meis-tenant` 热加载 **不会** 更新电流监测 API。

### E.2 后端变更后

- [ ] 在开发面板对 **正确微服务** 点「热加载」（调试模式）或「重启」
- [ ] 热加载结果见按钮旁 **✓/✗ + 时间**；Toast 显示 `✓编译 → ✓写JAR → ✓重启 → ✓健康检查`
- [ ] 失败时自动打开该服务调试日志，排查 Flyway/端口占用等
- [ ] 用浏览器或 `curl` 经网关 `:8080` 调用列表 API，确认返回字段与前端 Schema `prop` 一致

### E.3 前端变更后

- [ ] 确认 `meis-web` Vite 开发服运行中（:5173）
- [ ] 浏览器 **硬刷新**（`Ctrl+Shift+R`）或重新进入页面
- [ ] 打开开发者工具 Network，确认列表请求 200 且 `data.records` 非空
- [ ] 核对 `businessSchemas.ts` 列表列 `prop` 与 API 返回字段名一致（勿用旧别名如 `linked_device_name`）

### E.4 数据库变更后

- [ ] 改 `V1` + `R__` 双轨（见附录 D）
- [ ] 重启 **meis-tenant** 触发迁库
- [ ] 再重启相关业务服务（如 meis-analytics）

### E.5 开发面板热加载说明

| 阶段 | 含义 | 失败表现 |
|------|------|----------|
| 编译 | `mvn compile` | ✗编译，多为语法错误 |
| 写JAR | class 写入运行中 JAR | ✗写JAR，JAR 损坏需「完整打包」 |
| 重启 | 停止并重新拉起服务 | ✗重启，查看 stderr 日志 |
| 健康检查 | HTTP/JDWP 端口就绪 | ✗健康检查，服务未监听端口 |

热加载为 **同步执行**（约 1–2 分钟），完成后立即显示成功或失败，不再仅提示「已提交后台」。

---

*本文档随项目演进持续更新。模块评审通过后，可将对应章节拆为独立文档并在本章保留链接。*