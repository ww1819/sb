# MEIS 设备管理系统 — 需求与问题跟踪

> **用途**：梳理、编写、评审 MEIS 全系统业务需求，并跟踪已知问题与技术债。  
> 请在本文件中直接增删改；模块细节可拆分子文档后在此链接。

**文档状态**：草稿  
**最后更新**：2026-07-12  
**负责人**：待填写  
**系统定位**：医院医疗设备与固定资产全生命周期管理 SaaS（Schema 多租户）

**关联文档**：

- [架构说明](architecture.md)
- [设备报修 / 维修工单](repair-workorder.md)
- [用户手册（骨架）](user-manual.md)
- [本地开发部署](local-dev-deploy.md)
- [**可复用工程约定包**](reusable-engineering-conventions.md)（跨项目沉淀，其他系统可直接复用）

---

## 如何使用本文档

| 场景 | 做法 |
|------|------|
| 写新需求 | 在对应模块章节新增条目，或复制 [附录 A 需求模板](#附录-a-需求条目模板) |
| 记录缺陷 | 写入 [第 4 章 已知问题](#4-已知问题与技术债) |
| 待产品决策 | 写入 [第 5 章 待确认问题](#5-待确认问题) |
| 合理但暂缓开发 | 写入 [第 7 章 待开发功能池](#7-待开发功能池)（编号 `BACKLOG-{模块}-{序号}`） |
| 模块已评审 | 更新模块表中的「状态」列 |
| 版本留痕 | 更新 [第 6 章 版本记录](#6-版本记录) |
| 协作约定 | 见 [附录 Q](#附录-q需求协作与交付约定)；可沉淀的约定确认后写入 Q，**可复用部分须双写** [约定包](reusable-engineering-conventions.md) |
| 跨项目复用约定 | 见 [可复用工程约定包](reusable-engineering-conventions.md) |

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
| 数据库迁移 | `meis-tenant` Flyway + `R__columns_biz.sql / R__data_fix.sql` 租户补表 |
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
| ~~院区管理~~ | `/system/campus` | `campus` | **菜单已迁至基础字典** `/dict/campus` |
| ~~科室管理~~ | `/system/dept` | `department` | **菜单已停用**；入口在基础字典 `/dict/dept` |
| 用户管理 | `/system/user` | `sys_user` | 已增强（见 SYS-F-04） |
| 角色管理 | `/system/role` | `sys_role` | 待编写 |
| 数据字典 | `/system/dict` | `sys_dict` | 待编写 |
| 操作日志 | `/system/log` | `sys_operation_log` | 待编写 |
| 审批配置 | `/system/approval` | `sys_approval_flow` | 待编写 |
| 系统配置 | `/system/config` | `sys_config` | |
| 租户管理 | 平台 | `sys_tenant` | 待编写 |

**需求摘要（待补充）**：

- [ ] SYS-F-01 登录：医院编码 + 账号 + 密码，JWT 含 schemaName
- [ ] SYS-F-02 角色权限控制菜单与按钮（`v-permission`）
- [ ] SYS-F-03 租户开户自动执行 Schema 迁移
- [x] **SYS-F-04 用户管理增强（2026-07-14）**
  - 编辑/新建表单增加 **是否维修工程师**（`is_repair_engineer`），与维修工程师管理页同源字段
  - 列表筛选：关键词 + 启用状态 + **科室** + **是否维修工程师** + **角色** + **权限模式**
  - **批量修改**：勾选用户后可批量设置科室 / 启用状态 / 是否维修工程师（只更新勾选的字段；不含密码与权限）
  - 列表展示「维修工程师」列
- [ ] SYS-B-01 科室与院区层级关系规则
- [ ] SYS-B-02 用户与科室归属、数据权限范围

---

### 3.2 基础字典（DICT）

**服务**：meis-system  
**状态**：未开始

| 子模块 | 路径 | 说明 |
|--------|------|------|
| 院区管理 | `/dict/campus` | 由系统管理迁入（`system_campus`） |
| 仓库维护 | `/dict/warehouse` | **唯一菜单入口**（`dict_warehouse`）；库房管理/系统管理侧重复菜单已停用 |
| 科室维护 | `/dict/dept` | |
| 供应商管理 | `/dict/supplier` | 支持导入、拼音简码 |
| 生产厂商 | `/dict/manufacturer` | 支持导入、拼音简码 |
| 设备分类 | `/dict/category` | 左侧多级树（按 `parent_code`）；点节点筛本级+直接下级；新增默认上级为当前选中 |
| 资产分类 | `/dict/asset-category` | |
| 财务分类 | `/dict/finance-category` | 左侧多级树（按 `parent_id`）；点节点筛下级；新增默认上级为当前选中 |
| 单位维护 | `/dict/unit` | |

**需求摘要（待补充）**：

- [x] DICT-M-01 院区/供应商/设备分类/生产厂商菜单归入基础字典（自系统管理迁出）
- [x] DICT-UI-01 科室维护：操作按钮下行排列；新增右侧抽屉；隐藏 ListSelectionBar；列表底部分页
- [x] DICT-UI-02 供应商：查询贴搜索框；导入/生成简码在导出后；新增右侧抽屉；隐藏 ListSelectionBar（**仅 supplier 路径启用 `toolbarLayout`/`formPlacement`，不波及通用 CrudPage 默认页**）
- [x] DICT-UI-03 财务分类：左侧多级树状分类 + 右侧列表按树节点联动（本级+直接下级，`tree_node_id`）；新增默认挂当前选中节点；列表含序号；列名为分类编码/分类名称，会计科目前展示上级分类；上级分类可改/可清空（清空=一级），不可选自身及子孙
- [x] DICT-UI-04 通用保存：校验 `Result.code===0`（后端失败常 HTTP 200）；HTTP 拦截器拒绝业务失败，避免“提示成功但未入库”
- [x] DICT-UI-05 设备分类：左侧多级树（`parent_code`）+ 右侧本级/直接下级联动；新增默认挂当前节点；上级可清空为一级；`level`/`full_path` 后端自动补齐
- [x] DICT-UI-06 设备分类 Excel 导入：两列编码/名称即可；自动推导上级与层级（68 码 4/6/8 位）；支持无表头、编码补前导零；可重导更新；已种子入库国标 68 分类全文
- [x] DICT-UI-07 设备分类树：默认仅展开「全部」（子级收起）；同级手风琴展开；列表分类编码/名称/上级编码可排序；上级编码后展示上级分类名称
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
| 库存查询 | `/asset/stock` | 台账侧库存视角 |
| ~~设备入库~~ | `/asset/entry` | **菜单已停用**（`asset_entry`）；功能入口在库房管理 `/warehouse/entry`，API/页面仍可直达 |
| ~~设备出库~~ | `/asset/outbound` | **菜单已停用**；入口在库房管理 |
| ~~资产流转~~ | `/asset/transfer` | **菜单已停用**；入口在库房调拨等 |
| ~~资产盘点~~ | `/asset/inventory` | **菜单已停用**；入口在库房管理 |
| ~~设备报废~~ | `/asset/scrap` | **菜单已停用**；入口在库房管理 |

**需求摘要（待补充）**：

- [x] AST-M-01 资产台账侧停用与库房重复菜单：`asset_entry` / `asset_outbound` / `asset_transfer` / `asset_inventory` / `asset_scrap`（入口统一库房管理，见 Q-04）
- [ ] AST-F-01 设备台账字段分组：基本信息 / 财务 / 位置 / 合规
- [x] AST-01 计量检定类型字段（`metrology_type_code`，见附录 N / M）
- [x] AST-02~04 公用设备标志、借调计费配置、借调记录 Tab（见附录 N）
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
| ~~库房维护~~ | `/warehouse/setting` | **菜单已停用**（`warehouse_setting`）；仓库主数据入口在基础字典 `/dict/warehouse` |
| 设备入库 | `/warehouse/entry` | 同资产入库 |
| 设备出库 | `/warehouse/outbound` | |
| 设备退货 | `/warehouse/return` | 主从 |
| 库房调拨 | `/warehouse/transfer` | |
| 库存盘点 | `/warehouse/inventory` | |
| 设备报废 | `/warehouse/scrap` | |

**需求摘要（待补充）**：

- [x] WH-M-01 停用重复仓库维护菜单：`warehouse_setting`、`system_warehouse`（入口统一 `dict_warehouse`）
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
| 工程师 | `/repair/engineer` | 将改为「维修工程师管理」，见附录 U |
| 配件档案 | `/repair/spare-archive` | 见 **U.15.2**（去进销存 UI；拼音简码；复制/删除） |
| 故障库 | `/repair/fault` | |

**需求摘要**：

- [x] REP-F-01 设备弹窗选择（`RepairDevicePicker`）
- [x] REP-B-01 双层状态：主状态 + 维修子状态
- [x] REP-B-02 设备台账 `pending_verify` 状态
- [ ] REP-F-02 备件领用与库存扣减（**长期搁置**，待真实用户后再做；与 U.15.2 隐藏进销存 UI 一致）
- [ ] REP-F-03 外协维修独立单据（**长期搁置**，待真实用户后再做）
- [ ] REP-B-03 维修调度（派单/抢单/接单）、维修进程段、配件明细（附录 U / **U.15**）
- [ ] REP-F-04 移动端扫码报修
- [x] REP-F-05 配件档案：拼音简码、复制、有业务引用禁止删（U.15.2）
- [x] REP-B-04 业务子表冗余 `device_id` 等（附录 **W**；维修 P0）
- [x] REP-B-05 进程段编辑/删除、确认补 ended_at、待验自动确认（U.15.1–U.15.2）

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

- [x] MET-B-01 强检/非强检分类（`metrology_type` 维护，见附录 M）
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

**服务**：meis-special（`meis-shared` 路由别名）  
**状态**：部分骨架已实现，业务待按附录 N 重构

| 子模块 | 路径 |
|--------|------|
| 公用设备管理 | `/shared/device` |
| 借调申请 | `/shared/loan` |
| 借调审批 | `/shared/loan-approve` |
| 归还申请 | `/shared/return` |
| 归还审批 | `/shared/return-approve` |
| 借调收费 | `/shared/fee` |
| 借调记录查询 | `/shared/record` |

**需求摘要**（详见 **附录 N**）：

- [x] SHR-01 公用设备列表改查资产台账（`is_shared_device`）；**废弃 `shared_device` 表**
- [x] SHR-02 从非公用设备中新增为公用设备
- [x] SHR-03 取消公用设备 / 查看借用记录
- [x] SHR-05 列表展示借调状态（在库/申请中/已借用/归还申请中）
- [x] SHR-F-01~06 计费方式（按次/计时月天小时）、借调快照、归还自动计费
- [x] AST-01~04 台账计量类型、公用标志、计费配置、借调记录 Tab
- [x] SHR-D-01 租户 Schema 含 `shared_device*` 表（V1 已有）
- [~] SHR-B-01 借调审批流程（骨架已有，待与状态/计费联动）
- [~] SHR-B-02 归还验收与设备状态恢复（已有，缺自动计费）

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

**PWR-F-22 维护待机电流上下限（独立操作）**

- 条件：标签已绑定 `device_id`
- 入口：标签列表行操作 **「待机电流」**（非标签编辑表单内）
- 行为（**已确认：回写设备台账**）：
  - 弹窗展示当前关联设备及台账中的 `standby_current_max_ma`、`standby_current_min_ma`
  - 保存调用 `PUT /api/power/tag/{id}/standby-limits`，仅 `UPDATE medical_device`
  - **不在**标签编辑保存中维护上下限，避免用户误以为是标签属性
- 列表列：展示电流上限/下限（只读，来自关联设备台账），便于核对
- 未绑定设备时：「待机电流」按钮禁用

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
| PLT-X-15 | 前端 | 会话断开（401）弹窗点确定后未跳转登录页 | 用户误以为仍在线 | 401 时同步清除 Pinia 登录态，`router.replace('/login')` | 已修复 |
| PLT-X-16 | 报修 | 列表删除走通用 CRUD 被拦截；操作按钮位置不当 | 草稿无法删、操作不便 | 列表操作列 + `delete-url=/repair/workorder`；报修页仅基本信息、无取消 | 已修复 |

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
| Q-04 | 库房 | 资产模块与库房模块菜单重复，如何分工 | 入库/出库/调拨/盘点/报废入口统一「库房管理」；仓库主数据统一「基础字典→仓库维护」；停用 `asset_entry` 等及 `warehouse_setting`/`system_warehouse` | **已确认**（2026-07-15） |
| Q-05 | 保养/巡检/PM | 三模块边界与是否合并展示 | 需业务方定义 | 待确认 |
| Q-06 | 权限 | 科室用户数据权限：本科室 vs 本院 | 需院方确认 | 待确认 |
| Q-07 | 移动端 | 一期移动端范围 | 报修 + 盘点？ | 待确认 |
| Q-08 | 部署 | 单院私有部署 vs 多租户 SaaS 优先级 | 影响认证与运维 | 待确认 |

---

## 6. 版本记录

| 版本 | 日期 | 作者 | 变更说明 |
|------|------|------|----------|
| 1.52 | 2026-07-15 20:42:27 | — | 附录 U.15 进程编辑/删除与确认补全；配件档案去进销存+简码；附录 W 业务冗余字段 |
| 1.51 | 2026-07-15 | — | 设备分类导入国标 68 码全文（1388 条）；category_code 扩至 VARCHAR(16) |
| 1.50 | 2026-07-15 | — | 设备分类支持 Excel 导入（自动 parent_code/level）；页面开放导入按钮 |
| 1.49 | 2026-07-15 | — | 设备分类左侧多级树（parent_code）；tree_node_id 支持编码层级表 |
| 1.48 | 2026-07-15 | — | 财务分类左侧多级树；通用分页支持扁平筛选参数（parent_id 等） |
| 1.47 | 2026-07-15 | — | 供应商列表：序号列；编码/名称表头升序降序（通用分页支持 sortBy） |
| 1.47 | 2026-07-15 17:36:00 | — | 附录 Q.9 文档时间颗粒度；附录 U.14 维修列表功能分列/进程展示与段确认固化 |
| 1.46 | 2026-07-15 | — | 科室维护 UI：操作按钮第二行、右侧抽屉、隐藏勾选条、底部分页 |
| 1.45 | 2026-07-15 | — | 基础字典菜单顺序：院区→仓库→科室→供应商→生产厂商→设备/资产/财务分类→单位 |
| 1.45 | 2026-07-15 | — | 附录 L.5：后端停止后清空开发面板「热加载」时间列 |
| 1.44 | 2026-07-15 | — | 院区/供应商/设备分类/生产厂商菜单迁入基础字典；取消前端强制归并到系统管理 |
| 1.43 | 2026-07-15 | — | Q-04 补充：停用库房管理「库房维护」、系统管理「仓库维护」；仓库入口统一基础字典 |
| 1.42 | 2026-07-15 | — | Q-04：资产台账隐藏与库房重复的设备入库等菜单；修正 R__ 误把 `asset_entry` 重新启用 |
| 0.1 | 2026-07-11 | — | 初稿：仅电流监测 |
| 1.0 | 2026-07-11 | — | 扩展为全系统需求与问题跟踪 |
| 1.1 | 2026-07-11 | — | 补充电流监测：待机电流上下限、基站/标签监测记录、绑定追溯 |
| 1.2 | 2026-07-11 | — | 确认 Q-09/Q-10：上下限回写台账；四种缺失情形判定规则 |
| 1.3 | 2026-07-11 | — | 明确仅下限时读数等于下限归入待机 |
| 1.4 | 2026-07-11 | — | 附录 D：数据库迁移双轨规范；V1 补全电流监测相关字段与表 |
| 1.5 | 2026-07-11 | — | 附录 D.4：明确老租户启动先建表后补列；`SchemaTableEnsuring` 执行 V1/V2 |
| 1.6 | 2026-07-11 | — | 清理 `R__columns_biz.sql / R__data_fix.sql` 建表语句；R__ 仅保留补列与种子数据 |
| 1.7 | 2026-07-11 | — | 附录 E：开发完成验收清单；开发面板热加载改为同步执行并分步反馈 |
| 1.8 | 2026-07-11 | — | 标签：修复换绑设备；待机电流独立行操作；列表展示上下限 |
| 1.9 | 2026-07-11 | — | 标签：修复换绑与绑定记录；标签名称不得与编码相同 |
| 1.10 | 2026-07-12 | — | 附录 Q 扩展：约定沉淀/衍生提示/待开发池；新增第 7 章；技术约定索引 |
| 1.11 | 2026-07-12 | — | 抽出跨项目 [可复用工程约定包](reusable-engineering-conventions.md) |
| 1.12 | 2026-07-12 | — | Q.3：约定双写可复用包（与需求文档同步维护） |
| 1.13 | 2026-07-13 | — | PLT-X-15：401 会话过期后正确跳转登录页 |
| 1.14 | 2026-07-13 | — | PLT-X-16：报修列表操作列（提交/撤回/删除）与专用删除 API |
| 1.15 | 2026-07-13 | — | 附录 S.4：报修仅基本信息；取消移出报修页；S.5 流程表拆分入待开发池 |
| 1.16 | 2026-07-13 | — | BACKLOG-REP-01：`repair_workorder_process` 流程表 + 主单仅同步状态 |
| 1.17 | 2026-07-13 | — | 附录 L.5：开发面板展示后端源码变更时间并倒序排列 |
| 1.18 | 2026-07-13 | — | 开发面板：源码变更列移至 JDWP 后、修复倒序排序、前端 HMR 提示 |
| 1.19 | 2026-07-13 | — | 开发面板：修复热加载后仍显示待编译；表头可排序 |
| 1.20 | 2026-07-13 | — | 附录 L.6：服务筛选 + 保存即热加载（FileSystemWatcher） |
| 1.21 | 2026-07-13 | — | 修复状态 API 排序脚本致列表空白；默认开启保存即热加载 |
| 1.22 | 2026-07-13 | — | 开发面板新增「热加载」时间列，自动热加载结果可追踪 |
| 1.23 | 2026-07-13 | — | 后端列表：双横向滚动条、筛选/表头浮动、列显示开关 |
| 1.24 | 2026-07-13 | — | 附录 U：维修调度/工程师/进程/验收定稿（含拒绝验收状态） |
| 1.25 | 2026-07-13 | — | 附录 U 补充：废弃 engineer 表、`verify_rejected`、列表范围与完整性评估 |
| 1.42 | 2026-07-15 | — | U.13.4：进程工程师默认锁定+勾选多选；成员表 segment_user；修多处 JDBC ?/参数不匹配 |
| 1.41 | 2026-07-15 | — | 附录 U.13：列表抢单；添加进程工程师/起止时间（补录勾选结束）；进程类型空与抢单缺列修复 |
| 1.40 | 2026-07-15 | — | 附录 V / Q.8：列表勾选跨页缓存、全选当页、取消全选；导出/批量作用域（选中 vs 全部查询结果）；双写约定包 v1.3 §5.4 |
| 1.39 | 2026-07-14 | — | Q.7：从文档沉淀约定增量双写约定包 v1.2（槽位/串库/软删读过滤/主从保存/新表通检等） |
| 1.38 | 2026-07-14 | — | 附录 D.6：public/tenant 固定脚本槽位整合（建表/索引/补列/数据）；废止零散 V20+/patch |
| 1.37 | 2026-07-14 | — | 附录 G.10：物理删除盘点；库结构 SQL 为主+代码扫描为辅；SoftDelete 无列时禁止静默物理删 |
| 1.36 | 2026-07-14 | — | 附录 I.5：动态补齐缺 `is_deleted` 的租户表（标签打印日志/实体变更记录等） |
| 1.35 | 2026-07-14 | — | 附录 I.4：自定义读 SQL 统一补 SoftDelete 过滤（system/auth/repair/asset/purchase/maintain/qc） |
| 1.34 | 2026-07-14 | — | 租户库补齐 `sys_user.is_repair_engineer`；全表未删行 `is_deleted=0` + DEFAULT 0（见附录 I.3） |
| 1.33 | 2026-07-14 | — | 维修处理：派工/取消进操作列；工程师下拉 `/engineer/options`；添加进程可见性说明 |
| 1.32 | 2026-07-14 | — | 用户管理：维修工程师开关、列表筛选、批量改科室/启用/工程师 |
| 1.31 | 2026-07-13 | — | 附录 U.11：四列表查询/状态多选；REP-F-02/03 长期搁置 |
| 1.30 | 2026-07-13 | — | 落地 REP-05：维修进程类型、工单进程段、段上配件明细 |
| 1.29 | 2026-07-13 | — | 落地 REP-04：抢单 API、负责人互斥、抢单并发锁 |
| 1.28 | 2026-07-13 | — | 落地 REP-03：维修工程师管理改 sys_user.is_repair_engineer、assigned_user_id |
| 1.27 | 2026-07-13 | — | 落地 REP-02/06/07：列表范围、`verify_rejected` 拒绝验收与返修闭环 |
| 1.26 | 2026-07-13 | — | U.8 已取消单只读；U.6 拒绝验收后可加进程并可直接待验收 |

---


## 7. 待开发功能池

> 已认可、值得做，但因现场节奏/人力暂未开发或未测完的功能。  
> **区别**：第 4 章 = 缺陷/技术债；第 5 章 = 产品决策未定；**本章 = 已确认方向、待排期**。  
> 编号规则：`BACKLOG-{模块缩写}-{序号}`，模块缩写同文档开头（如 `REP` 维修、`AST` 资产、`SYS` 系统）。

| 编号 | 模块 | 摘要 | 来源/背景 | 优先级 | 阻塞原因 | 状态 |
|------|------|------|-----------|--------|----------|------|
| BACKLOG-REP-01 | 维修 | 派工/接单/转派/维修/验收等业务数据独立表，主单仅更新状态 | 附录 S.5；避免直接写 `repair_workorder` 流程字段 | P1 | — | 已完成 |
| BACKLOG-REP-02 | 维修 | 维修处理列表纳入待派单（`reported` 等）；与附录 U.4 列表范围一致 | 附录 U；当前 handle 不含 `reported` 致列表空 | P0 | — | 已完成 |
| BACKLOG-REP-03 | 维修 | 维修工程师管理：废弃 `engineer` 表，`sys_user.is_repair_engineer` + 派工改 `assigned_user_id` | 附录 U.3（已确认无正式业务数据） | P1 | — | 已完成 |
| BACKLOG-REP-04 | 维修 | 派单/抢单/接单与工程师操作互斥 | 附录 U.2 | P1 | — | 已完成 |
| BACKLOG-REP-05 | 维修 | 维修进程类型 + 工单进程段 + 配件明细 | 附录 U.4–U.5 | P1 | — | 已完成 |
| BACKLOG-REP-06 | 维修 | 维修验收：满意度、拒绝验收；主状态 `verify_rejected` | 附录 U.6 | P1 | — | 已完成 |
| BACKLOG-REP-07 | 维修 | 报修申请列表展示全部未删工单；维修处理列表范围与进程可编规则 | 附录 U.8 | P1 | — | 已完成 |
| BACKLOG-REP-F-02 | 维修 | 配件库存扣减与费用汇总 | 附录 U.7 / REP-F-02 | P2 | 待真实用户、降低交付难度 | **长期搁置** |
| BACKLOG-REP-F-03 | 维修 | 外协维修独立单据 | 附录 U.7 / REP-F-03 | P2 | 待真实用户、降低交付难度 | **长期搁置** |
| BACKLOG-AST-W01 | 跨模块 | 保养/计量/巡检/PM 执行表与公用设备费用表等补齐 device_id/code/name 冗余 | 附录 W.3.2 | P1 | 维修 P0 先落地；其余分批 | 可排期 |

**状态取值**：`暂缓` / `可排期` / `开发中` / `已完成` / **`长期搁置`**。完成后可移入版本记录说明，或将状态改为已完成并保留一行备查。

**新增条目模板**（复制到上表）：

```markdown
| BACKLOG-XXX-01 | 模块 | 一句话摘要 | 谁提出/哪个现场 | P0/P1/P2 | 暂无时间测试 / 等客户验收窗口 等 | 暂缓 |
```

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
| 租户补表脚本 | `meis-tenant/.../R__columns_biz.sql / R__data_fix.sql` |
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

| 场景 | `V1__tables.sql`（全量建表） | `R__columns_biz.sql`（老租户业务补列） / `R__data_fix.sql`（数据） |
|------|------------------------------|------------------------------------------------------|
| **新建表** | 写入完整 `CREATE TABLE` + 字段 + 默认 `COMMENT` | **不写**（建表由 `SchemaTableEnsuring` 执行 V1） |
| **已有表加列** | 在对应 `CREATE TABLE` 中补上该列 | **单独一行** `ALTER TABLE ... ADD COLUMN IF NOT EXISTS ...`（禁止一条 ALTER 多列；标准七列走 `R__columns_audit.sql`） |
| **索引** | `V2__indexes.sql` | **不写**（索引由 `SchemaTableEnsuring` 执行 V2） |
| **手工镜像** | `db/source/create/tenant_tables.sql` | `db/source/patches/` 已废弃新补丁；只改 Flyway 槽位 |

### D.2 开发检查清单（提交前）

- [ ] `V1__tables.sql` 中 `CREATE TABLE` 已包含全部字段（新租户开箱即用）
- [ ] **标准七列**已写入新建表：`created_at` / `updated_at` / `created_by` / `updated_by` / `is_deleted` / `deleted_at` / `deleted_by`（见附录 G.0）
- [ ] `R__columns_biz.sql` 中每条**业务**新增列有对应 `ADD COLUMN IF NOT EXISTS`（标准七列走 `R__columns_audit.sql`；字典/回填写 `R__data_fix.sql`）
- [ ] R__ 中**无** `CREATE TABLE` / `CREATE INDEX`（建表与索引仅维护 V1/V2）
- [ ] `V2__indexes.sql` 已补充必要索引
- [ ] 未在 `db/source/patches` 新增功能补丁（该目录已废弃）
- [ ] 未在 R__ 中写 `COMMENT ON`（空注释由 `SchemaCommentFiller` 补全）
- [ ] 前后端 CRUD 与交互走通（清单见附录 M.7）

### D.3 本次审计补全记录（2026-07-11）

对照 R__ 补列脚本与 V1 建表，已补入 V1 的缺失项：

| 表 | 补入 V1 的字段/对象 |
|----|---------------------|
| `medical_device` | `is_shared_device`、`is_pm_device`、`standby_current_max_ma`、`standby_current_min_ma` |
| `power_tag` | `device_code`、`device_name` |
| （新表） | `power_current_reading`、`power_tag_bind_log` + 索引 |
| （新表） | `metrology_type`（计量检定类型）+ 索引 `idx_metrology_type_parent` / `idx_metrology_type_group` |

其余 R__ 中的 `ALTER` 列（如 `repair_workorder`、`inspection_plan`、`device_outbound` 等）经核对 **已在 V1 中存在**，无需重复添加。

---

### D.4 老租户启动时的建表与补列（已落地）

**问：老租户执行建表语句有无问题？**  
**答：没有问题。** 使用 `CREATE TABLE IF NOT EXISTS` 时，已存在的表会被跳过，不会删表、不会改已有列，不影响存量数据。

**`meis-tenant` 启动时**（`TenantSchemaMigrator`）对每个活跃租户 schema 自动执行：

| 步骤 | 组件 | 作用 |
|------|------|------|
| 1 | `SchemaTableEnsuring` | 幂等建表与索引：`V1__tables.sql` + `V2__indexes.sql`（**先** `CREATE EXTENSION`，再 `SET search_path TO tenant, public`） |
| 2 | Flyway `migrate` | 补列与修正：`R__columns_audit.sql` → `R__columns_biz.sql` → `R__data_fix.sql` |
| 3 | `SchemaCommentFiller` | 仅对空注释列/表补 `COMMENT ON` |
| 4 | `TenantSchemaShadowGuard` | 校验 V1 表是否齐全；缺表则重跑建表；仍缺则**启动失败**（避免串写 public） |

代码位置：`meis-tenant/.../TenantSchemaMigrator.java`、`SchemaTableEnsuring.java`、`TenantSchemaShadowGuard.java`。

### D.5 租户缺表串写 public（Schema Shadow，2026-07-11）

**现象**：保存成功或部分成功，但外键报错、绑定记录丢失、数据写到错误库。

**根因链**：

1. 连接 `search_path = tenant_xxx, public`（`TenantAwareDataSource`）
2. 租户 schema **缺表**（如 `power_tag_bind_log`），未限定表名的 SQL 落到 **public 同名表**
3. public 表外键指向 **public 父表**（空或旧数据），与租户业务数据不一致 → FK 失败或静默写错库

**为何缺表**：`SchemaTableEnsuring` 曾未执行 `CREATE EXTENSION`，`uuid_generate_v4()` 解析失败，大量 `CREATE TABLE IF NOT EXISTS` 被 **skip**，启动日志仅有 warn。

**已落地防护**：

| 措施 | 说明 |
|------|------|
| `ensureDatabaseExtensions()` | 建表前确保 `uuid-ossp` / `pgcrypto` |
| `TenantSchemaShadowGuard` | 启动后对照 V1 表清单；缺表重跑 ensure；仍缺则抛错阻断启动 |
| `scripts/ensure-tenant-tables.ps1` | 离线对全部活跃租户补 V1/V2 表（不重启服务） |
| `scripts/V1TenantGapScan.java` | 对比 V1 与租户实际表，标 `[PUBLIC SHADOW]` |

**提交前 / 环境修复检查**：

```powershell
# 1) 扫描缺表（应为 0）
cd scripts && javac -encoding UTF-8 -cp ../meis-common/target/deps/postgresql-42.7.3.jar V1TenantGapScan.java
java -cp ".;../meis-common/target/deps/postgresql-42.7.3.jar" V1TenantGapScan

# 2) 一键补表（可选，等同启动时 SchemaTableEnsuring）
powershell -File scripts/ensure-tenant-tables.ps1
```

**注意**：`public` 中误建的租户业务表（如 `power_tag_bind_log`）不会自动删除；修复后以租户 schema 内表为准。新环境勿向 public 写入租户业务 DDL。

### D.6 固定脚本槽位（2026-07-14 整合）

**目标**：public / 租户各一套固定文件；**禁止**「每加一张表/一个字段就新建一个迁移文件」。

| 职责 | public | tenant |
|------|--------|--------|
| **建表** | `V1__tables.sql` | `V1__tables.sql` |
| **索引** | `V2__indexes.sql` | `V2__indexes.sql` |
| **一次性种子** | `V3__seed_data.sql`（冻结） | `V3__seed_data.sql` |
| **历史注释** | `V4__comments.sql`（冻结） | `V4__comments.sql` |
| **补全字段** | 平台列：写入 `R__data_fix.sql` 的 ALTER 段 | `R__columns_audit.sql`（七列）+ `R__columns_biz.sql`（业务列） |
| **更正数据** | `R__data_fix.sql`（菜单等） | `R__data_fix.sql`（字典/回填） |

本次整合：合并 `R__audit`+`R__is_deleted` → `R__columns_audit`；拆分原 `R__tenant_schema_sync` → `R__columns_biz` + `R__data_fix`；`V2__extensions` 更名为 `V2__indexes`；删除 `V20`/`V21`；`db/source/patches` 标记废弃。

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
- [ ] 有勾选列：跨页缓存、全选当页、取消全选；导出/批量变更先选作用域（附录 V）

### E.4 数据库变更后

- [ ] 改 `V1` + `R__` 双轨（见附录 D）
- [ ] 重启 **meis-tenant** 触发迁库（含 ShadowGuard 缺表校验）
- [ ] 或执行 `scripts/ensure-tenant-tables.ps1` 离线补表
- [ ] 运行 `V1TenantGapScan` 确认各租户 **missing = 0**
- [ ] 再重启相关业务服务（如 meis-analytics）

### E.5 开发面板热加载说明

| 阶段 | 含义 | 失败表现 |
|------|------|----------|
| 编译 | `mvn compile` | ✗编译，多为语法错误 |
| 停止 | 写入 JAR 前短暂停服务（解除文件锁） | ✗停止，端口未释放 |
| 写JAR | `jar uf` 仅更新 `BOOT-INF/classes` | ✗写JAR，需「完整打包」修复 JAR |
| 重启 | 拉起服务（保留 JDWP） | ✗重启，查看 stderr 日志 |
| 健康检查 | HTTP/JDWP 端口就绪 | ✗健康检查，服务未监听端口 |

热加载为 **同步执行**（约 1–2 分钟），完成后立即显示成功或失败，不再仅提示「已提交后台」。

---

## 附录 F：public schema 迁移规范（2026-07-11）

> 与租户 schema 对齐，**禁止**再新增 `V5+` 分散脚本。

### F.1 文件职责

| 文件 | 职责 |
|------|------|
| `public/V1__tables.sql` | 平台表全量 `CREATE TABLE` + `COMMENT ON`（含全部字段） |
| `public/V2__indexes.sql` | 索引 |
| `public/V3__seed_data.sql` | 一次性：演示租户、套餐、平台管理员 |
| `public/V4__comments.sql` | 历史注释回填 |
| `public/R__data_fix.sql` | **菜单目录**幂等同步；**已有表**逐列 `ADD COLUMN` |

### F.2 操作规则

1. **新平台表** → 写入 `V1__tables.sql`
2. **平台表加列** → V1 补 `CREATE TABLE` 字段 + R__ 补一行 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`
3. **新菜单 / 改菜单** → 只改 `R__data_fix.sql`（`ON CONFLICT DO UPDATE`）
4. **禁止**新建 `V5__xxx.sql` / `V20+` 等版本化脚本（原 V5–V21 已删除并并入 R__）
5. `spring.flyway.ignore-migration-patterns: "*:missing"` + dev 环境 `repair`，兼容已执行过旧版本的库

### F.3 本次整合记录

| 变更 | 说明 |
|------|------|
| 删除 | `V5`–`V19`（15 个模块菜单脚本） |
| 新增 | `R__data_fix.sql`（基础菜单 + 各模块调整 + 套餐/租户授权） |
| 精简 | `V3__seed_data.sql` 仅保留平台级一次性种子 |
| 配置 | `application.yml` 增加 `ignore-migration-patterns` |

---

*本文档随项目演进持续更新。模块评审通过后，可将对应章节拆为独立文档并在本章保留链接。*

---

## 附录 G：软删除与审计字段规范（2026-07-12）

### G.0 标准七列（强制约定）

每张**租户业务表**必须具备以下字段（创建者 / 创建时间 / 更新者 / 更新时间 / 删除标志 / 删除者 / 删除时间）：

| 字段 | 类型 | 默认 | 含义 |
|------|------|------|------|
| `created_at` | `TIMESTAMPTZ` | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMPTZ` | `CURRENT_TIMESTAMP` | 更新时间 |
| `created_by` | `UUID` | NULL | 创建者（用户 id） |
| `updated_by` | `UUID` | NULL | 更新者（用户 id） |
| `is_deleted` | `SMALLINT NOT NULL` | `0` | 删除标志：`0` 未删除，`1` 已删除 |
| `deleted_at` | `TIMESTAMPTZ` | NULL | 删除时间 |
| `deleted_by` | `UUID` | NULL | 删除者（用户 id） |

**落地规则**：

| 场景 | 做法 |
|------|------|
| **以后新建表** | 在 `tenant/V1__tables.sql` 的 `CREATE TABLE` 中**直接写出上述七列**（文件头已有模板注释） |
| **以前已有表** | 由 `R__columns_audit.sql` 幂等 `ADD COLUMN IF NOT EXISTS` 补全（含标准七列与动态 `is_deleted`）；重启 **meis-tenant** 生效 |
| **禁止** | 在 `R__columns_biz.sql` 业务补列段零散追加标准七列；勿再拆 `V5+` 脚本 |

应用层读写约定见 G.1；工具类为 `SoftDeleteSupport`。

### G.1 原则

| 项 | 约定 |
|----|------|
| 删除 | 禁止物理 `DELETE`（明细行「先删后插」除外）；写 `is_deleted=1`、`deleted_at`、`deleted_by`；有 `is_active` 时同步置 `false` |
| 查询 | 默认 `is_deleted = 0`（兼容旧库仅有 `deleted_at` 时用 `deleted_at IS NULL`） |
| 唯一键冲突 | 新建命中已软删行的唯一键 → 清空删除标记并 **UPDATE**，不 INSERT |
| 审计 | 插入填 `created_by`；更新填 `updated_at`、`updated_by`（**仅**由 `appendUpdateAuditSets` 写入，禁止再进业务 SET） |
| UPDATE 禁写列 | `id` / `created_at` / `created_by` / `updated_at` / `updated_by`；普通 PUT 另剔除 `is_deleted` / `deleted_*` |

### G.2 实现位置

| 层级 | 类 / 脚本 |
|------|-----------|
| 工具 | `meis-common` → `SoftDeleteSupport`、`TableColumnCache`、`UniqueConstraintCache` |
| 通用 CRUD | `GenericTableController`（删/建/改/查） |
| 分页 | `PageableJdbc`、`ExcelExportHelper` |
| 导入 | `SimpleTableImporter` |
| 库表补列 | `tenant/R__columns_audit.sql`（标准七列 + `is_deleted` 兼容补全） |

### G.3 明细表例外

模板项、单据明细等「保存时先清空再写入」的子表，可保留物理删除子行；主表（含唯一业务编码）必须软删除。

### G.4 验证

1. 重启 **meis-tenant**（R__ 补列生效）
2. 热加载/重启业务服务（`meis-common` 变更）
3. 删除供应商/标签 → 再以相同编码新建 → 应恢复成功、无唯一键报错
4. 列表不展示已删记录
5. 新建命中已软删编码 → 恢复成功，且无 `updated_by` 重复导致的 bad SQL grammar

### G.9 UPDATE SET 列重复（2026-07-12 修复）

**现象**：供应商等通用 CRUD 恢复/更新时报  
`bad SQL grammar [... updated_by = ?, ..., updated_by = ?::uuid]`

**原因**：
1. `applyInsertAudit` / 前端整行回传把 `updated_by` 写入 body
2. `executeUpdate` 把 body 全部拼进 SET
3. `appendUpdateAuditSets` 再追加一次 `updated_by`

**修复约定**（`SoftDeleteSupport`）：

| API | 作用 |
|-----|------|
| `UPDATE_SKIP_COLUMNS` / `isUpdateSkipColumn` | 通用 UPDATE 跳过主键与审计列 |
| `stripClientUpdateFields` | 普通 PUT 剔除审计 + 软删字段，防伪造 |
| `prepareRestore` | 恢复前写 `is_deleted=0` 等，并剔除 `updated_*` / `created_*` / `id` |
| `applyInsertAudit` | **不再**预填 `updated_by`（避免恢复 UPDATE 重复） |

涉及：`GenericTableController`、`SimpleTableImporter`；手工恢复 SQL（院区/科室/字典/电流标签）补 `updated_by`。

`meis-common` / `meis-api` 为 **Maven 公共库**，无 HTTP 端口、不可独立启动，故不在「后端微服务」列表中。

开发面板在「前端」与「后端微服务」之间增加 **公共库模块** 区块：

| 操作 | 说明 |
|------|------|
| 快速编译 | 仅 `mvn compile -pl <模块>`（约数秒）；面板「编译中」在 classes 更新后自动消失 |
| 热加载依赖 | 编译本库后，对**调试运行中**的微服务**逐个**热加载（服务多时较慢，见 G.6） |

### G.6 编译为何感觉慢

| 现象 | 原因 |
|------|------|
| 公共库一直显示「编译中」 | 已修复：此前后台任务结束后状态未刷新，最长误显示 5 分钟 |
| `meis-common` 快速编译 | 实际约 **2–5 秒**；仅编译本模块，不编译 15 个微服务 |
| 热加载依赖（15 个服务） | 每个依赖服务约 1–2 分钟（compile + 写 JAR + 重启），**串行**执行 |
| 微服务「快速更新」 | `mvn compile -pl 服务 -am` 会连带编译 `meis-common` 等上游模块 |
| Maven Install / 打包后端 | 全量 reactor 构建，首次或 clean 后可达数分钟 |
| **打包后端**（工具栏，紧挨整体 Clean） | `mvn package -DskipTests`，编译并生成全部 JAR，clean 后常用 |

### G.8 整体 Clean 与打包（2026-07-12）

工具栏构建区顺序：**整体 Clean** → **打包后端** → Maven Install。

| 按钮 | Maven 命令 | 适用场景 |
|------|-----------|----------|
| 整体 Clean | `clean` | 清除全部 `target`；之后须再点「打包后端」 |
| 打包后端 | `package -DskipTests` | 编译并生成全部可运行 JAR（推荐日常全量构建） |
| Maven Install | `install -DskipTests` | 安装到本地 `.m2`，供其他工程依赖 |

> 已移除工具栏「整体编译」：`compile` 只生成 classes 不生成 JAR，易误解；全量构建请用「打包后端」。单模块仍可用公共库/微服务行的「快速编译」。

**JAR 列显示约定**：

| 显示 | 含义 |
|------|------|
| `classes 就绪` + `JAR 缺失` | 曾仅 compile 或 clean 后未打包；点「打包后端」 |
| `缺失` | classes 与 JAR 均不存在，需「打包后端」或单服务「完整打包」 |
| `N KB` | JAR 正常，可启动/热加载 |

状态栏「后台任务」显示 `整体 Clean` / `打包后端` 等进行中状态。执行 **整体 Clean** 或 **打包后端** 会清除面板「编译中」误显示。

### G.7 迁库锁表（2026-07-12 修复）

`R__columns_biz.sql / R__data_fix.sql` 中 **DO 块批量 ALTER 114 表**会在单事务内长时间持锁，导致所有业务查询（含资产管理列表）超时失败。

已拆分为独立脚本 `tenant/R__columns_audit.sql`，并设置 `flyway:executeInTransaction=false`，逐条 `ALTER` 提交释放锁。若再次遇到全站查询挂起，检查 `pg_stat_activity` 是否有未结束的迁库会话。

修改 `meis-common`（如软删除工具类）后，在面板对 `meis-common` 点 **热加载依赖**，或手动对具体微服务点「热加载」。

## 附录 H：外键字段显示名称（2026-07-12）

### H.1 问题

资产台账详情、列表及部分表单中，外键字段（如 `manufacturer_id`、`supplier_id`、`dept_id`）直接显示 UUID，未解析为对象名称或编码。

### H.2 实现约定

| 层级 | 说明 |
|------|------|
| `refSelectConfig.ts` | 定义外键表 → API、`labelKey`（名称）、`codeKey`（编码兜底） |
| `useRefLabelMap.ts` | `preloadRefLabelMaps` 预加载；`resolveRefLabel` 解析显示文本 |
| `pageSchemas.ts` | `collectLinkTables` 收集 schema 中全部 `linkTable` |
| `CrudPage` | 挂载时预加载主表 + 明细表全部外键，列表 `TableCellValue` 显示名称 |
| `DeviceDetailTabs` | 设备详情 Tab 预加载 `medical_device` 外键，复用 `TableCellValue` |
| `RefDisplay` | 只读外键字段展示组件；`FieldRenderer` 在 `readonly + linkTable` 时使用 |
| `RefSelect` | 编辑态下拉；名称优先，无名称时回退编码 |

### H.3 验证

1. 资产管理 → 双击设备进入详情 →「基本信息」中生产厂商、供应商应显示名称而非 UUID。
2. 各 CRUD 列表中外键列（科室、库房、设备等）应显示可读文本。
3. 只读表单字段（如审批人）应显示姓名而非 ID。

## 附录 I：删除状态字段 is_deleted（2026-07-12）

### I.1 字段约定

| 值 | 含义 |
|----|------|
| `0` | 未删除（默认值） |
| `1` | 已删除 |

列定义：`is_deleted SMALLINT NOT NULL DEFAULT 0`，与 `deleted_at` / `deleted_by` **同步维护**。

### I.2 行为

| 操作 | 写入 |
|------|------|
| 软删除 | `is_deleted=1`，`deleted_at=NOW()`，`deleted_by=当前用户` |
| 恢复（唯一键冲突重建） | `is_deleted=0`，`deleted_at=NULL`，`deleted_by=NULL` |
| 新建 | `is_deleted=0`（`applyInsertAudit` 默认填充） |
| 查询过滤 | 优先 `is_deleted = 0`（`SoftDeleteSupport.notDeletedClause`） |

### I.3 迁库

脚本 `tenant/R__columns_audit.sql`（`flyway:executeInTransaction=false`）：

1. 全租户业务表补齐标准七列（含 `is_deleted`）
2. 将已有 `deleted_at IS NOT NULL` 的行回填为 `is_deleted=1`
3. **存量修补（2026-07-14）**：凡含 `is_deleted` 的基表，对未删除行统一 `is_deleted=0`（含原 `NULL`），并 `ALTER … SET DEFAULT 0` / 尽量 `NOT NULL`（`R__columns_audit.sql` 末尾 DO 块；手工补丁见 `db/source/patches/sys_user_repair_engineer_and_is_deleted_defaults.sql`）

部署后重启 **meis-tenant**，并对业务服务热加载 **meis-common**。

新建表请直接在 `V1__tables.sql` 写入七列，见 **附录 G.0**。

> **运维提示**：若租户库长期未跑 R__，`sys_user.is_repair_engineer` 缺失会导致派工工程师下拉无数据、`/repair/engineer/me` 失败、「添加进程」不显示。缺列时用户管理中开关可能看似已保存但无法落库；补列后需在用户管理 / 维修工程师管理中 **重新勾选**。

## 附录 J：开发面板整体 Clean 与打包（2026-07-12）

工具栏构建区：
- **整体 Clean**：`mvn -q clean`（需确认）
- **打包后端**：`mvn -q package -DskipTests`（紧挨 Clean，全量生成 JAR）

已移除 **整体编译** 按钮。详见附录 G.8。

### J.1 排查结论（2026-07-12）

**JAR 路径检测正确**（`{name}/target/{name}-1.0.0-SNAPSHOT.jar`）。此前「JAR 缺失」多因只执行了 `compile` 而未 `package`。

推荐流程：**整体 Clean**（可选）→ **打包后端** → 启动服务。
### J.2 标准七列约定（2026-07-12）

已落定：业务表统一具备创建/更新/删除审计字段；老表由 R__ 补全，新表写 V1。详见 **附录 G.0**。

## 附录 K：审计字段与软删唯一键修补（2026-07-12）

### K.1 问题

手写 Controller / 导入路径未统一走 `SoftDeleteSupport`，导致：

1. INSERT/UPDATE 缺少 `created_by` / `updated_by` / `is_deleted`
2. 软删后再新建同唯一键（如 `warehouse_code`、`role_code`、`device_code`、`device_id`）直接撞 UNIQUE

### K.2 约定

| 操作 | 行为 |
|------|------|
| 新建 | `prepareCreate` / `applyInsertAudit` + `findSoftDeletedId`；命中软删则 UPDATE 恢复 |
| 更新 | 写 `updated_at` + `updated_by` |
| 删除 | `softDelete`（`is_deleted=1` + `deleted_*`） |
| 列表 | `notDeletedClause` |

通用 CRUD / `SimpleTableImporter` 已覆盖；本轮补齐缺口路径。

### K.3 本轮修补范围

- **system**：Warehouse、Role、Campus、Department（含导入）、Dict、ApprovalFlowConfig
- **analytics**：PowerStation（`station_code`）
- **special**：SharedDevice / SpecialLife / SpecialRadiation（`device_id` UNIQUE）
- **qc / maintain**：InspectionType、PmType、MaintenanceLevel、MetrologyOrg、MetrologyCategory
- **common**：`MedicalDeviceImporter`、`ImportMasterDataResolver`；`SoftDeleteSupport.prepareCreate`

部署：热加载 **meis-common** 及依赖模块（system / analytics / special / qc / maintain）。

## 附录 L：开发面板状态显示滞后（2026-07-12）

### L.1 现象

终端显示 15 个服务均已绑定端口，但面板仅显示 5 个核心服务「运行中」；终端偶发 `Request error: …指定的网络名不再可用`。

### L.2 根因

1. `/api/status` 对每个服务各执行一次 `netstat`（15 服务 × HTTP+JDWP ≈ 30 次），单次轮询可达 **5s+**
2. 面板 **单线程** 处理 HTTP；日志 `/api/logs/stream` 与状态轮询（原 4s）叠加排队
3. 浏览器请求超时/取消后，服务端写响应失败 → 终端报「网络名不再可用」
4. 前端刷新失败时 **保留旧快照**（例如仅启动过 core 5 个时的状态）

### L.3 修补

| 项 | 改动 |
|----|------|
| `meis-services.ps1` | `Get-MeisListeningPortSet`：一次 netstat 解析全部 LISTENING 端口并缓存 800ms |
| `Get-MeisServiceStatusList` / 日志 | 复用端口快照，不再逐服务 netstat |
| `dev-panel.ps1` | 客户端断开不再红色报错；`Invoke-PanelRequestSafe` 统一捕获 |
| `index.html` | 状态轮询 5s；日志轮询避让 `refreshInFlight`；失败时提示「刷新失败，显示可能过期」 |

### L.4 使用说明

修改脚本后需 **重启开发面板**（关闭 :5099 再运行 `scripts\dev-panel.ps1`），否则仍跑旧逻辑。可用 `scripts\status.ps1` 核对真实端口状态。

### L.5 源码变更时间与构建判断（2026-07-13）

| 项 | 说明 |
|----|------|
| **源码变更** | 各后端微服务 `src/` 下最近修改文件时间；列在 JDWP 右侧，列表 **按此倒序** |
| **待编译** | 源码时间晚于 `classes` 内最新文件与 JAR 中较新者 → 需编译或热加载；热加载后 JAR 更新则不再误报 |
| **JAR 落后** | `classes` 最新文件时间晚于 JAR → 热加载前需写 JAR，或「完整打包」 |
| **状态刷新** | 编译/JAR/源码比对结果随 `/api/status` **每 5 秒** 自动刷新（与运行状态同一轮询） |
| **列表排序** | 默认按源码变更倒序；可点击 **服务 / 源码变更 / JAR** 表头切换正序/逆序 |
| **列表筛选** | 服务类型（全部/核心/非核心）+ 名称或功能模糊搜索（附录 L.6） |
| **热加载时间** | 独立列展示上次热加载完成时间；若源码变更晚于热加载则标「待同步」；**失败但服务/JAR 已恢复**时不再标红（显示灰色「已恢复」，或 JAR 重建后清除）；**服务停止后清空**该时间（单服务停止 / 停止全部 / 状态检测到 HTTP+JDWP 均未监听） |
| **自动热加载** | 仅 **HTTP + JDWP 均就绪** 的调试服务会触发；非调试模式显示「待调试热加载」；热加载进行中显示「同步中…」并抑制「JAR 落后」误报 |
| **mtime 缓存** | 热加载成功后清除该模块 mtime 缓存，避免 5s 内误报待编译/JAR 落后 |
| **后端热加载** | 默认需手动「热加载」；可勾选 **保存即热加载**（附录 L.6，仅调试中服务） |
| **前端日常开发** | Vite 开发服（`:5173`）自带 HMR，**改 `.vue`/`.ts` 无需点构建** |
| **前端构建按钮** | 仅用于 `npm install`、类型检查、`vite build` 生产包验证；与日常热更新无关 |

**状态**：已实施（筛选与自动热加载见 L.6）。

### L.6 自动热加载与列表筛选（2026-07-13）

#### 实现方式对比

| 方式 | 说明 | 本项目 |
|------|------|--------|
| **面板 FileSystemWatcher** | 监视各服务 `src/`，保存后防抖触发热加载 | **已提供**（勾选「保存即热加载」） |
| **IDE 热替换** | IntelliJ / VS Code Java 调试时方法体热替换 | 可用，但难覆盖 Spring Bean 结构变更 |
| **Spring Boot DevTools** | `spring-boot-devtools` 重启 | 当前以 JAR + JDWP 调试为主，未集成 |
| **公共库变更** | `meis-common` 等改动影响多服务 | 仍须在「公共库」区手动「热加载依赖」 |

**自动热加载约定**（默认开启，可取消勾选）：

- 监视 `.java/.xml/.yml/.properties/.sql` 等；事件含 **Changed / Created / Renamed**（覆盖本机保存与 `git pull` 新增/覆盖）；保存后约 **2s 防抖**，同一服务 **20s 内不重复**触发
- 在 `/api/status` 轮询时排队执行，与手动「热加载」相同流程
- 热加载成功后会 **清除该模块 mtime 缓存**（`classes`/`jar`/`src`），避免 5s 内误报「JAR 落后」
- **不依赖文件 mtime 与同事提交时间比较**；`git pull` 时面板须已运行才会收到事件（见附录末「拉取同事代码」）

**机制修补（2026-07-13）**：

| 现象 | 原因 | 修复 |
|------|------|------|
| 源码已改但长期「待同步」 | 非调试/构建中/冷却时 **提前清除 dirty**，变更丢失 | dirty 仅在确认排队热加载后清除；否则显示「排队热加载 / 待调试热加载」 |
| 热加载完成后仍 JAR 落后 | mtime **5s 缓存** 未失效 | 热加载成功 → `Clear-MeisModuleMtimeCache` |
| 热加载过程中 JAR 落后 | compile 先于 sync 更新 classes | 进行中显示「同步中…」，抑制误报 |

#### 列表筛选

| 筛选项 | 说明 |
|--------|------|
| **服务类型** | 全部 / 核心（tenant、auth、system、file、gateway）/ 非核心 |
| **名称或功能** | 对服务名、`labelZh`、`descZh` 模糊匹配 |

筛选偏好保存在浏览器本地。

#### 列表滚动与列显示（2026-07-13）

| 项 | 说明 |
|----|------|
| **横向滚动** | 表格上下各一条横向滚动条，联动同步 |
| **浮动区** | 筛选条件、列名表头、列显示设置在纵向滚动页面时保持可见（表头置于浮动区内，避免被横向滚动容器裁剪） |
| **列显示** | 「列显示 ▾」可开关各列；**构建**列默认隐藏，其余默认显示；偏好本地保存 |

**状态**：已实施。

---

## 附录 M：医学装备计量检定类型维护（2026-07-12）

### M.1 业务分类体系（用户规范摘要）

| 维度 | 说明 | 字典 `metrology_classification_group` |
|------|------|----------------------------------------|
| 法规监管 | 强制检定（法定）/ 非强制（校准溯源） | `regulatory` |
| 实施时机 | 首次 / 周期 / 修理后 / 仲裁 / 期间核查 | `timing` |
| 执行地点 | 送检 / 现场上门 | `location` |
| 医院分级 | A 强检 / B 重要非强检 / C 一般辅助 | `grade` |

**与 `metrology_category` 区分**：`metrology_category` 为力学/电学等**参量类别**；`metrology_type` 为**检定管理分类**（法规属性、时机、地点、ABC 分级及设备范围说明）。

### M.2 数据模型 `metrology_type`

| 字段 | 说明 |
|------|------|
| `type_code` / `type_name` | 类型编码与名称（租户内唯一，软删后可重建） |
| `classification_group` | 分类维度 |
| `parent_id` | 上级类型（树形，如强制检定 → 周期强检） |
| `regulatory_attr` | 法规属性（强制 / 非强制） |
| `traceability_mode` | 溯源方式（检定 / 校准） |
| `timing_kind` | 实施时机 |
| `location_kind` | 执行地点（送检 / 现场） |
| `management_grade` | 医院 A/B/C 分级 |
| `cycle_rule` | 周期规则说明 |
| `certificate_kind` | 证书类型（检定证 / 校准证） |
| `legal_basis` / `executor_scope` | 法规依据、执行机构范围 |
| `sort_order` / `is_active` | 排序与启用 |

### M.3 种子数据

`R__columns_biz.sql / R__data_fix.sql` 预置：法规（`MANDATORY`、`MANDATORY_ONCE`、`MANDATORY_PERIODIC`、`VOLUNTARY` 及设备范围）、时机、地点、A/B/C 分级等条目。

### M.4 接口与前端

| 能力 | 路径 |
|------|------|
| 分页 + 维度筛选 | `GET /api/metrology/type/page?classification_group=` |
| 全量列表 | `GET /api/metrology/type/list` |
| 保存 | `POST /api/metrology/type` |
| 删除 | `DELETE /api/metrology/type/{id}` |
| 页面 | **计量管理 → 计量参数设置** →「计量检定类型」Tab |

### M.5 部署

重启 **meis-tenant**（`V1__tables.sql` 已含 `metrology_type` 建表 + `R__` 种子）与 **meis-qc**（`MetrologyTypeController`）后生效。新建表须写入 V1，禁止再增 `V5+` 版本脚本（见附录 G.0）。

### M.6 CRUD 完整性检查（`metrology_type` 审计结果，2026-07-12）

| 项 | 后端 | 前端 | 状态 |
|----|------|------|------|
| **C 新增** | `POST /api/metrology/type`；`applyInsertAudit` + 软删唯一键恢复 | `CrudPage` 新增 → `saveUrl`；必填校验 | 通过 |
| **R 查询** | `GET /page`（分页+关键词+维度筛选）、`GET /list`（下拉）、`GET /{id}` | 列表 `listPageUrl`；`listFilters` 维度筛选；`parent_id` 外键显示 | 通过（已修 `refSelectConfig` 用 `id` 作 FK 值） |
| **U 更新** | 同 POST，按 `id` 分支 UPDATE + `updated_by` | 行内编辑 → 同一 `saveUrl` | 通过 |
| **D 删除** | `DELETE /{id}` → `SoftDeleteSupport.softDelete` | `delete-url="/metrology/type"` | 通过 |
| **审计/软删** | 七列 + `findSoftDeletedId(type_code)` | — | 通过 |
| **字典/外键** | 种子字典 6 类 | `businessSchemas` 字段 + `dictType` / `linkTable` | 通过 |

**本次修补**：`parent_id` 外键 `valueKey` 改回 `id`；分页关键词搜 `type_code`/`type_name`；空 `parent_id` UUID 规范化；`CrudPage` 保存成功/失败提示与必填校验。

### M.7 新增表/字段通用检查清单（每次必做）

**数据库**

- [ ] `V1__tables.sql` 全量建表（含标准七列）；索引写 `V2__indexes.sql`
- [ ] 业务补列写 `R__columns_biz.sql / R__data_fix.sql`；种子数据（字典/主数据）同步
- [ ] `MetrologyDomainController`（或对应域 `TABLES`）注册表名

**后端**

- [ ] 分页 `GET .../page`（返回 `PageResult`：`records` + `total`）
- [ ] 保存 `POST`（新增/更新合一；软删恢复；审计字段）
- [ ] 删除 `DELETE`（软删，非物理删）
- [ ] 可选：`GET /list`（下拉）、`GET /{id}`（详情）
- [ ] 关键词/筛选参数与前端 `listFilters` 对齐

**前端**

- [ ] `businessSchemas.ts` 字段定义（`list`/`required`/`dictType`/`linkTable`）
- [ ] `refSelectConfig.ts` 外键下拉（`valueKey`：`*_id` 用 `id`，编码列另建 `*_code` 键）
- [ ] 页面 `PageConfig`：`saveUrl`、`listPageUrl`、`deleteUrl`（自定义 Controller 时必配）
- [ ] 路由/菜单可访问；列表筛选、新增、编辑、删除走通

**验证**

- [ ] `mvn compile` 通过；重启相关服务后手工点一遍 CRUD

---

## 附录 N：资产台账 × 公用设备需求梳理（2026-07-12）

> 来源：语音输入原始需求。**N.8 已确认（2026-07-12），可按 N.7 实施。**

### N.1 需求背景与目标

在现有资产台账（`medical_device`）与公用设备模块基础上，打通「计量类型维护」「公用设备标识」「借调全生命周期」「计费快照与自动结算」，并在台账侧提供与保养/巡检一致的子页签体验。

### N.2 功能域拆分

#### N.2.1 资产台账扩展（AST）

| 编号 | 需求 | 说明 |
|------|------|------|
| AST-01 | 计量类型字段 | 台账维护设备所属**计量检定类型**（关联 `metrology_type.type_code`），用于计量计划/到期规则，与 `is_metrology` 联动展示 |
| AST-02 | 公用设备标志 | 台账增加「是否公用设备」`is_shared_device`（库表已有，前端 schema 未暴露） |
| AST-03 | 借调计费配置 | 公用设备在台账维护**计费方式**与**单价**（见 N.2.3） |
| AST-04 | 借调记录子页 | 台账新增 **「借调记录」Sheet**，与维修/保养/巡检/计量记录同级，按 `device_id` 查 `shared_device_loan` |

#### N.2.2 公用设备管理重构（SHR）

| 编号 | 需求 | 说明 |
|------|------|------|
| SHR-01 | 列表数据源 | **直接展示资产台账**，筛选 `is_shared_device = true`；不以 `shared_device` 快照表作为主列表 |
| SHR-02 | 新增公用设备 | 从**非公用设备**（`is_shared_device = false`）中检索选择，确认后标记为公用并写入计费配置 |
| SHR-03 | 取消公用设备 | 操作项：取消公用资格（`is_shared_device = false`）；**借调中/归还审批中禁止取消** |
| SHR-04 | 查看借用记录 | 操作项：跳转或弹窗展示该设备全部借调单 |
| SHR-05 | 借调状态列 | 列表展示设备**当前借调态**（见 N.3 状态机），非仅 `availability_status` |

#### N.2.3 计费规则（SHR-F）

| 编号 | 需求 | 说明 |
|------|------|------|
| SHR-F-01 | 计费方式维护 | 字典 `shared_fee_mode`：`per_use` 按次 / `time` 计时 |
| SHR-F-02 | 计时单位 | 计时收费子字典 `shared_fee_time_unit`：`month` / `day` / `hour` |
| SHR-F-03 | 单价维护 | 台账或公用设备配置页维护 `fee_unit_price`（单价，元） |
| SHR-F-04 | 借调单快照 | 创建/提交借调申请时，将当时 `fee_mode`、`fee_time_unit`、`fee_unit_price` **写入借调单**，与设备后续改价脱钩 |
| SHR-F-05 | 归还自动计费 | 归还单**审批通过**后，按快照规则自动结算并写入 `shared_device_fee`（算法见 **N.8.2**） |
| SHR-F-06 | 费用查看 | 台账可查看该设备历史借调费用汇总/明细 |

### N.3 借调状态（列表展示用）

设备在公用设备列表上的「当前状态」由借调/归还单据**派生**（不再使用 `shared_device` 表）：

| 展示状态 | 判定条件（建议） |
|----------|------------------|
| 在库 | 无进行中借调；或最近借调已归还 |
| 借调申请中 | 存在 `shared_device_loan.status IN ('draft','pending')` |
| 已借用 | 存在 `shared_device_loan.status = 'on_loan'` |
| 归还申请中 | 借调单 `on_loan` 且存在 `shared_device_return.status IN ('pending',…)` 待审 |

### N.4 与现状差距（代码审计）

| 项 | 现状 | 差距 |
|----|------|------|
| `medical_device.is_shared_device` | V1 已有字段；登记公用设备时会置 `true` | 台账表单未展示；无「取消公用」 |
| `medical_device` 计量类型 | 仅有 `is_metrology`、校准日期；**无** `metrology_type_code` | 需加字段 + 关联 `metrology_type` 下拉 |
| 公用设备列表 | `SharedDevicePage` 查 `shared_device` 表 | 改查 `medical_device`；**废弃 `shared_device` 表** |
| `shared_device` 表 | 冗余快照 + `fee_standard`（元/天） | **本期直接废弃**（见 N.8.1） |
| `shared_device_loan` | 含 `shared_device_id`、`fee_standard` | 去掉 `shared_device_id`；改 `fee_mode` / `fee_time_unit` / `fee_unit_price` 快照 |
| 归还审批 | `SharedReturnController.approve` 恢复科室与可用状态 | **未**自动生成 `shared_device_fee` |
| 台账借调记录 Tab | `DeviceLedgerForm` 有维修/保养/巡检/计量等 Tab | **无**借调记录 Tab |
| §3.13 需求项 | SHR-B/F 均为待开发 | 与本次梳理一致 |

### N.5 数据模型建议（待开发时落地）

**`medical_device` 新增/暴露字段（写 V1 + R__）**

| 字段 | 类型 | 说明 |
|------|------|------|
| `metrology_type_code` | VARCHAR(50) | 关联 `metrology_type.type_code` |
| `is_shared_device` | BOOLEAN | 已有，前端暴露 |
| `shared_fee_mode` | VARCHAR(20) | `per_use` / `time` |
| `shared_fee_time_unit` | VARCHAR(10) | `month` / `day` / `hour`（`time` 时有效） |
| `shared_fee_unit_price` | DECIMAL(12,2) | 单价 |

**`shared_device_loan` 调整**

| 字段 | 说明 |
|------|------|
| `device_id` | 唯一关联台账（**移除 `shared_device_id`**） |
| `fee_mode` | 申请时计费方式快照 |
| `fee_time_unit` | 申请时计时单位快照（`time` 时有效） |
| `fee_unit_price` | 申请时单价快照 |
| `billing_start_at` | 计时起点：借调**审批通过**时刻（写入 `approve`） |
| `billing_end_at` | 计时终点：归还**审批通过**时刻（写入归还 `approve`） |

**`shared_device` 表：废弃（N.8.1）**

- 新功能不再读写 `shared_device`；公用属性与计费配置全部落在 `medical_device`。
- 借调/归还/收费单据仅关联 `device_id`。
- 存量租户：迁移脚本将 `shared_device` 中有效计费数据合并至 `medical_device` 后，表可保留空壳或后续版本删除（Flyway 不新增 V 脚本删表，仅停写）。

**取消公用设备**：仅置 `medical_device.is_shared_device = false`（并清空计费配置可选）；无 `shared_device` 行需维护。

### N.6 接口与页面改动清单（开发阶段）

| 层级 | 改动 |
|------|------|
| 后端 | 废弃 `SharedDeviceController` 对 `shared_device` 的 CRUD；改查/改 `medical_device`；借调单去 `shared_device_id`；归还审批计费（N.8.2） |
| 前端 | 移除 `shared_device` schema/引用；`SharedDevicePage` 台账列表；借调申请选 `medical_device`（`is_shared_device=true`） |
| 字典 | `shared_fee_mode`、`shared_fee_time_unit`；扩展借调状态展示文案 |
| 文档 | §3.13 勾选 SHR 子项；MET 台账联动 |

1. **AST-01/02**：台账字段（计量类型、公用标志）+ schema/表单  
2. **SHR-F-01~03**：计费字典与台账计费配置字段  
3. **SHR-01~05**：公用设备页改台账列表 + 状态派生 + 取消公用；**停用 `shared_device` 读写**  
4. **SHR-F-04**：借调申请写入计费快照（来自 `medical_device`）  
5. **SHR-F-05~06 + AST-04**：归还自动计费（N.8.2）+ 台账借调/费用 Tab  
6. **数据迁移**：`shared_device` → `medical_device` 一次性合并；代码清除 `shared_device_id` 引用

### N.8 已确认规则（2026-07-12）

#### N.8.1 表与公用设备登记

| 决策 | 结论 |
|------|------|
| `shared_device` 表 | **已删除**（V1 去除建表；R__ 迁移后 DROP） |
| 取消公用设备 | 仅 `is_shared_device = false`（借调中/归还审批中禁止） |
| 新增公用设备 | 从非公用台账勾选 → 置 `is_shared_device = true` 并配置计费字段 |

#### N.8.2 计费结算算法

| 计费方式 | 规则 |
|----------|------|
| **按次** `per_use` | **一张借调单计 1 次**；`fee_amount = fee_unit_price`（快照单价，不乘天数） |
| **计时** `time` | **起算**：借调单审批通过时刻（`shared_device_loan.approved_at`，写入 `billing_start_at`） |
| | **止算**：归还单审批通过时刻（`shared_device_return.approved_at`，写入 `billing_end_at`） |
| | **金额**：按快照 `fee_time_unit`（月/天/小时）折算时长 × `fee_unit_price`（不足 1 单位按 1 单位计，或向上取整——实现时固定一种并写入单元测试） |

借调申请保存/提交时：从台账复制 `shared_fee_mode`、`shared_fee_time_unit`、`shared_fee_unit_price` 至借调单快照字段；审批通过后写入 `billing_start_at`；归还审批通过后写入 `billing_end_at` 并生成 `shared_device_fee`。

#### N.8.3 原待确认问题 — 答复归档

1. ~~`shared_device` 去留~~ → **直接废弃**  
2. ~~按次如何计数~~ → **一张借调单据算一次**  
3. ~~计时起止点~~ → **借调审核通过后开始，归还审核通过后结束**（不用 `lend` 操作时刻，不用申请单上的计划日期 `loan_start`/`loan_end` 计费）

---

**状态**：**已按 N.7 实施（2026-07-12）**。重启 meis-tenant + meis-special 后生效；`shared_device` 表已删除。

---

## 附录 O：运维设备主数据架构（巡检 / 保养 / 计量 / 预防性维护）

> 来源：需求语音整理（2026-07-12）

### O.1 核心原则

| 原则 | 说明 |
|------|------|
| **主对象** | 巡检、保养、计量、预防性维护（PM）的设备主对象均为 **资产台账** `medical_device` |
| **计量属性** | 计量检定类型（`metrology_type`）及 `is_metrology`、`metrology_type_code`、计量周期等属性维护在台账内 |
| **多计划** | 同一设备可同时参与多个保养/巡检/PM 计划；业务以 **计划**（`*_plan`）承载，不以独立设备表重复建档 |
| **标志位** | 台账布尔字段：`is_inspection_device`、`is_maintain_device`、`is_metrology`、`is_pm_device`（可选筛选，非第二套主数据） |

### O.2 废弃项（与附录 N `shared_device` 同类）

| 类别 | 处理 |
|------|------|
| `shared_device` 表 | **删除**（V1 去除建表；`R__columns_biz / R__data_fix` 迁移后 `DROP`） |
| `*DeviceController`（保养/巡检/计量/PM） | **删除**；执行单生成迁入 `*ExecutionGenerator` + `*PlanController` |
| 前端 `*DevicePage.vue` 及菜单 `*_device` | **停用并移除**；设备标记与属性在台账表单维护 |
| 独立设备表（保养/巡检/计量/PM） | **不存在**；无需删表 |

### O.3 保留的业务表

- **计划**：`inspection_plan`、`maintenance_plan`、`metrology_plan`、`pm_plan`
- **执行**：对应 `*_execution` / `*_execution_item` / `*_execution_result`
- **参数**：模板、类型、分级等参数表（各模块 `*_template`、`*_type` 等）

### O.4 实施清单

- [x] 删除 `shared_device` 建表及审计脚本引用
- [x] `R__columns_biz / R__data_fix`：存量迁移 + `DROP TABLE shared_device`
- [x] 删除 4 个 `*DeviceController`，执行生成逻辑迁入 Generator
- [x] 删除 4 个 `*DevicePage.vue`，移除 `ModulePage` / `pageRegistry` 路由
- [x] 停用菜单：`maintain_device`、`inspect_device`、`metrology_device`、`pm_device`
- [x] 台账 schema 补全 `is_pm_device`

**状态**：**已按 O.4 实施（2026-07-12）**。重启 meis-tenant 后菜单与 `shared_device` 删表生效；重启 meis-qc、meis-maintain 后后端生效。

---

## 附录 P：资产台账查看/编辑拆分与标签打印

> 来源：需求确认（2026-07-12）

### P.1 操作列

| 操作 | 说明 |
|------|------|
| **查看** | 位于编辑与删除之间；只读；为后续权限拆分预留（低权限可仅开放查看） |
| **编辑** | 可改主数据 |
| **删除** | 无业务数据时显示；有业务数据时**隐藏**；后端二次校验 |

### P.2 编辑 vs 查看 Tab

| 模式 | 可见 Tab |
|------|----------|
| **编辑** | 基本信息 + 设备图片 + 设备档案 |
| **查看** | 基本信息（只读）+ 设备图片 + 设备档案 + 全部业务 Sheet（资产卡片、维修/保养/巡检/计量、借调、盘点、不良事件等） |

### P.3 设备编码与二维码

| 规则 | 定稿 |
|------|------|
| 二维码载荷 | **`device_code`（设备编码）** |
| 设备编码 | **创建后禁止修改**（前端只读 + 后端 update 忽略改码） |
| 禁止 | 不得用设备名称、科室、财务编码、出厂序列号等可改字段作二维码 |

### P.4 标签打印

- 标签预览 / 打印（载荷 = `device_code`）
- 打印记录表 `device_label_print_log`（设备、编码快照、打印人、时间）
- `label_printed` 在至少打印一次后置 true

### P.5 删除业务校验

存在任一关联业务数据则禁止删除（维修、保养/巡检/计量/PM、流转、出入库、盘点、报废、借调、不良事件、特种设备关联等）。

### P.6 实施清单

- [x] 列表操作：查看 / 编辑 / 删除（按可删性隐藏）
- [x] DeviceLedgerForm 按 mode 切换 Tab 与只读
- [x] `device_code` 创建后只读；通用 update 剥离改码
- [x] 删除前后端业务存在性校验
- [x] 标签打印 + `device_label_print_log`

**状态**：已按 P.6 实施（2026-07-12）。重启 meis-tenant / meis-asset 并刷新前端后生效。

---

## 附录 Q：需求协作与交付约定

> 来源：用户约定（2026-07-12）。后续需求默认按本附录执行，无需每次重复强调。

### Q.1 流程（强制顺序）

1. **合理性审查（先于改代码）**
   - 收到需求后，先判断是否合理、可落地。
   - 若存在**风险、歧义、漏洞、与现有架构冲突**，先提醒并与用户澄清；**不得直接开发**。
   - 与用户明确并完善后，将定稿需求写入 `docs/meis-requirements.md`（附录或对应章节），再进入开发。

2. **按文档开发**
   - 以文档定稿为准实现/修改功能，避免口头需求与实现漂移。

3. **交付前自检与修补**
   - 修改完成后，检查并补全/修正：
     - **后端 CRUD**：列表/详情、创建、更新、删除、校验、权限、软删、关联约束、错误返回等；
     - **前端交互**：入口操作、表单/抽屉、只读与可编辑边界、按钮显隐、成功失败提示、与后端契约一致等。
   - 发现缺口则当场修补，再视为完成。

### Q.2 执行说明

- 用户后续发送需求时，**自动**执行 Q.1，无需再次强调「先合理性检查 / 前后端逻辑检查」。
- 若需求已足够明确且无风险，可在简要确认要点后写入文档并开发；有疑问仍须先对齐。

### Q.3 约定沉淀与跨模块推广

- 需求合理且适合固化为**开发约定**时：与用户确认后写入本附录 Q（或对应技术附录），并同步 `.cursor/rules`（如有）。
- **双写可复用包（强制）**：凡写入本需求文档的**可跨项目复用**约定（协作流程、库表双轨、软删审计、变更记录、字典/外键展示、草稿提交撤回、验收清单等），须**同步补充**到 [`docs/reusable-engineering-conventions.md`](reusable-engineering-conventions.md)；仅 MEIS 业务定稿（如具体表字段清单、报修状态机细项）可只留本需求文档，在可复用包「落地映射」中链回即可。
- 同一思路可提升**其他模块**健壮性时：先提示影响范围与收益；用户确认后再批量修补，避免只改当前点、遗漏同类入口。

### Q.4 衍生需求提示

- 主需求本身无问题，但经验上常连带相邻能力（如草稿/提交、查看/变更记录、字典中文等）时：**主动提醒**衍生项及建议优先级。
- 用户确认纳入后：**先写入文档**（模块章节、附录，或 [第 7 章 待开发功能池](#7-待开发功能池)），再排期开发，减少返工。

### Q.5 待开发功能池

- 合理、能提高完成度，但暂无时间开发或测试的需求：记入 [第 7 章](#7-待开发功能池)，编号 `BACKLOG-{模块}-{序号}`。
- 不得与第 4 章缺陷、第 5 章待确认问题混放。

### Q.6 技术约定索引

| 主题 | 位置 |
|------|------|
| **跨项目可复用约定全集** | [reusable-engineering-conventions.md](reusable-engineering-conventions.md)（**v1.4**） |
| 数据库迁移双轨 / **固定槽位** / 串库防护 | [附录 D](#附录-d数据库迁移规范必读)（含 D.5 / D.6） |
| 开发完成验收清单 | [附录 E](#附录-e开发完成验收清单必读) |
| public schema 迁移 | [附录 F](#附录-fpublic-schema-迁移规范2026-07-11) |
| 标准七列 / 软删与审计 / 读过滤 / 物理删盘点 | [附录 G](#附录-g软删除与审计字段规范2026-07-12)、[附录 I](#附录-i删除状态字段-is_deleted2026-07-12)、[附录 K](#附录-k审计字段与软删唯一键修补2026-07-12)、G.10、I.4–I.5 |
| 外键显示名称 | [附录 H](#附录-h外键字段显示名称2026-07-12) |
| 新增表/字段 CRUD 通检 | [附录 M.7](#m7-新增表字段通用检查清单每次必做) |
| 开发面板 Clean/打包与状态 | [附录 J](#附录-j开发面板整体-clean-与打包2026-07-12)、[附录 L](#附录-l开发面板状态显示滞后2026-07-12) |
| 主数据变更记录与精简快照 | [附录 T](#附录-t主数据查看与变更记录)（含 T.5 / T.6） |
| 列表字典中文 | [附录 R](#附录-r列表状态与字典值中文显示) |
| 报修草稿/提交/撤回 | [附录 S](#附录-s设备报修草稿--提交--撤回) |
| 维修调度/进程/工程师/验收 | [附录 U](#附录-u维修调度工程师进程与验收2026-07-13)（含 U.13 / U.14 / **U.15**） |
| 列表勾选 / 批量作用域 | [附录 V](#附录-v列表勾选跨页缓存与批量作用域2026-07-15) |
| 业务冗余字段（device 等） | [附录 W](#附录-w业务冗余字段约定2026-07-15) |
| 需求文档时间颗粒度 | [附录 Q.9](#q9-需求文档时间颗粒度2026-07-15) |

### Q.7 2026-07-14 从需求文档沉淀的约定增量

已写入 [约定包 v1.2](reusable-engineering-conventions.md)，摘要：

| 增量 | 来源附录 | 约定包章节 |
|------|----------|------------|
| 迁库固定槽位、禁止碎片脚本 | D.6 / F | §2.1 |
| search_path 缺表串库防护 | D.5 | §2.2 |
| 读接口全覆盖软删过滤；softDelete 禁静默物理删 | I.4 / G.10 | §3.1 / §3.4 |
| 主从单据禁止只存主表 | PLT-X-05 | §6.1 |
| UUID 字符串化、options 路径勿与 `/{id}` 冲突 | 工程实践 / H | §5.3 |
| 新表 CRUD + 变更记录通检 | M.7 / T | §7.4 |
| 业务编码创建后只读；有关联禁止删主数据 | O / 资产标签等 | §8 NF-06/07 |

### Q.8 2026-07-15 列表勾选与批量作用域

已写入 [约定包 v1.3 §5.4](reusable-engineering-conventions.md) 与 [附录 V](#附录-v列表勾选跨页缓存与批量作用域2026-07-15)。

### Q.9 需求文档时间颗粒度（2026-07-15）

> 指同事在本需求文档中登记需求/问题/版本的时间，**不是**业务单据字段。

| 项 | 定稿 |
|----|------|
| **适用范围** | [第 4 章 已知问题](#4-已知问题与技术债)、[第 5 章 待确认问题](#5-待确认问题)、[第 6 章 版本记录](#6-版本记录)；附录内「已确认（日期）」类标注同理 |
| **新记录** | 使用 **`YYYY-MM-DD HH:mm:ss`**（本地时区，到秒） |
| **历史记录** | **保持原日期**（仅 `YYYY-MM-DD`），不强制回填时分秒 |
| **决策列示例** | `**已确认**（2026-07-15 17:36:00）` |

### Q.10 2026-07-15 业务冗余字段约定

已写入 [约定包 v1.4 §6.2](reusable-engineering-conventions.md) 与 [附录 W](#附录-w业务冗余字段约定2026-07-15)。

---

## 附录 R：列表状态与字典值中文显示

> 来源：用户需求（2026-07-12）。降低列表英文码理解成本。

### R.1 合理性结论

- **合理**：业务字典（如 `device_status=normal`）在库中已有中文标签，列表却直接渲染英文码，属于前端展示缺口，非缺数据。
- **范围**：凡列表/详情只读单元格绑定了 `dictType` 的字段，应按字典显示 **中文标签**；布尔类 `is_*` 无字典时显示「是/否」（`is_active`/`is_clinical` 保持启用停用、临床/非临床）。
- **风险**：若某 `dict_code` 未入 `sys_dict`，回退显示原值并依赖种子补齐；不改变存库英文码。

### R.2 实现要点

1. `TableCellValue` / `StatusTag`：有 `dictType` 时用字典缓存解析 `dict_label`。
2. 列表页（`CrudPage` 及自定义列表）进入时预加载本页字段涉及的字典类型。
3. 不改后端存值；筛选下拉已走字典，保持一致。

**状态**：按 R.2 实施。


---

## 附录 S：设备报修草稿 / 提交 / 撤回

> 来源：用户需求（2026-07-12）；定稿：撤回后可再提交；与「取消」并存；用语「撤回」。

### S.1 状态与规则

| 状态/操作 | 说明 |
|---|---|
| `draft` | 未提交草稿；可修改、删除 |
| **提交** | `draft` → `reported`；占用设备（`maintenance`）；进入派工流程 |
| **撤回** | 仅 `reported` 且尚未派单/接单/开始维修；回到 `draft`；释放设备；可再改/删/提交 |
| **取消** | 流程作废 → `cancelled`；**仅维修处理等后续流程**，报修申请页不提供；草稿请用删除 |
| 已提交后 | `reported` 及之后禁止修改、删除（走流程动作） |

### S.2 审计

- 修改 / 提交 / 撤回 / 删除：写入 `repair_workorder_event`（`update`/`submit`/`withdraw`/`delete`）+ 通用 `sys_entity_change_log`。
- 取消等原有流程事件保持不变。

### S.3 实现要点

- 创建默认 `draft`，**不**占用设备；提交时再占用。
- 专用 API：`PUT` 更新、`POST .../submit`、`POST .../withdraw`、`DELETE`；通用 CRUD 对 `repair_workorder` 禁止绕过。
- 字典 `wo_status` 增加 `draft`=未提交。

### S.4 报修界面字段与操作边界（2026-07-13）

| 页面 | 表单展示 | 列表/详情操作 |
|------|----------|----------------|
| **报修申请** `/repair/apply` | 仅 **基本信息** + **备注**（设备、故障描述、紧急程度等）；**不展示**财务信息、派工/费用/验收操作区；**可只读展示**维修进程段与配件更换（见 U.14） | **提交**、**撤回**、**删除**（草稿）、**变更记录**；**禁止「取消」**（取消属后续维修流程） |
| **维修处理** `/repair/handle` | 展示流程/财务等处理字段 | 派工、接单、转派、完工、挂起、**取消** 等 |
| **维修验收** `/repair/verify` | 展示验收相关字段 | 验收通过/不通过 |

- 报修侧「作废未派单单据」统一用 **撤回** → 回到 `draft`，不用「取消」。
- 「取消」仅用于已进入派单/维修流程后的作废（`cancelled`），入口在 **维修处理** 等后续页面。

### S.5 流程业务数据与主单职责

| 层级 | 职责 |
|------|------|
| **`repair_workorder`（主单）** | 报修核心信息 + **主状态** `status`、当前 `repair_sub_status`、`assigned_engineer_id` 的及时准确更新 |
| **`repair_workorder_process`（流程业务表）** | 派单、接单、转派、维修处理、验收等 **操作业务数据** 独立落表；主单不直接写入费用/方案/验收明细 |
| **事件流水** | `repair_workorder_event` 继续作时间轴审计；与流程业务表双写，展示字段由流程表 `enrich` 回填 |

**状态**：S.1–S.5 已实施（流程表见 `V1__tables.sql`、`RepairWorkorderProcessService`）。

#### 列表空白排查（2026-07-13）

| 现象 | 原因 | 处理 |
|------|------|------|
| 报修申请列表「暂无数据」，但已成功保存草稿 | 列表接口 `/repair/workorder/page` 在 `enrichWorkorders` 查询 `repair_workorder_process` 时 500；老租户 schema 缺该表（及 `repair_workorder_event`） | 重启 **meis-tenant** 触发 `SchemaTableEnsuring` 幂等建表；或执行 `db/source/patches/repair_workorder_flow_tables.sql` |
| 同上 | 代码已加防御：流程表不存在时列表跳过 enrich，不再整页失败 | 需重新打包 **meis-repair** |

---

## 附录 T：主数据查看与变更记录

> 来源：用户需求（2026-07-12）；定稿：粒度 A+C（字段级 diff + 删/提交类附精简快照）；范围 P0+P1+P2 本轮。

### T.1 目标

- 主数据支持 **查看**（只读）与 **变更记录**，便于 CRUD 分权与追溯。
- 与 `sys_operation_log`（接口审计）分离：新建 `sys_entity_change_log`（实体级）。

### T.2 变更记录模型（A+C）

| 动作 | 记录内容 |
|---|---|
| create / update | 字段级 `changed_fields`：`[{field, label, oldValue, newValue}]` |
| delete / submit / withdraw | 字段 diff（如有）+ `snapshot_json` 精简快照 |
| 敏感字段 | 不落库（如 `password`/`password_hash`/`token` 等） |

### T.3 纳入实体（本轮）

| 批次 | 实体 |
|---|---|
| P0 | `medical_device`、`manufacturer`、`supplier`、`department`、`sys_user`、`repair_workorder` |
| P1 | `campus`、`building`、`warehouse`、`asset_category`、`medical_device_category` |
| P2 | `engineer`、`fault_type_dict`、`finance_category`、`unit_dict`、`sys_role` |

### T.4 前端

- 上述实体列表启用「查看」；详情提供「变更记录」入口。
- 报修详情同时保留时间轴事件与变更记录。

**状态**：按 T 实施。

### T.5 精简快照字段清单（`snapshot_json`）

> 用于 delete / submit / withdraw 等动作。**一项主数据一张表**；列为字段名、类型、中文注释；字段顺序即落库顺序。
> 扩展时**先改本节再改代码**（`EntityChangeLogService.SNAPSHOT_FIELDS`）。
> 通用排除：`id` 不进快照（已有 `entity_id`）；审计列 `created_*/updated_*/deleted_*/is_deleted` 不进；敏感列永不落库。
> 说明：`sys_role.permissions`、`sys_user.permissions` / `role_ids` / 密码类字段不进精简快照。

#### `medical_device`（资产台账）

| 字段名 | 类型 | 中文注释 |
|---|---|---|
| `device_code` | `VARCHAR(20)` | 设备编码 |
| `device_name` | `VARCHAR(200)` | 设备名称 |
| `brand` | `VARCHAR(100)` | 品牌 |
| `model` | `VARCHAR(100)` | 型号 |
| `serial_number` | `VARCHAR(100)` | 出厂序列号 |
| `device_status` | `VARCHAR(20)` | 设备运行状态 |
| `risk_level` | `VARCHAR(20)` | 风险等级 |
| `dept_id` | `UUID` | 领用科室 |
| `campus_id` | `UUID` | 所属院区 |
| `original_value` | `DECIMAL(15,2)` | 原值 |
| `enable_date` | `DATE` | 启用日期 |
| `is_active` | `BOOLEAN` | 是否启用 |

#### `manufacturer`（生产厂家）

| 字段名 | 类型 | 中文注释 |
|---|---|---|
| `manufacturer_code` | `VARCHAR(20)` | 厂商编码 |
| `manufacturer_name` | `VARCHAR(200)` | 厂商名称 |
| `pinyin_code` | `VARCHAR(50)` | 拼音简码 |
| `country` | `VARCHAR(50)` | 国家/地区 |
| `is_domestic` | `BOOLEAN` | 是否国产厂商 |
| `contact_phone` | `VARCHAR(20)` | 联系电话 |
| `is_active` | `BOOLEAN` | 是否启用 |

#### `supplier`（供应商）

| 字段名 | 类型 | 中文注释 |
|---|---|---|
| `supplier_code` | `VARCHAR(20)` | 供应商编码 |
| `supplier_name` | `VARCHAR(200)` | 供应商名称 |
| `pinyin_code` | `VARCHAR(50)` | 拼音简码 |
| `contact_person` | `VARCHAR(50)` | 联系人 |
| `contact_phone` | `VARCHAR(20)` | 联系电话 |
| `unified_social_credit_code` | `VARCHAR(18)` | 统一社会信用代码 |
| `is_authorized` | `BOOLEAN` | 是否授权经销商 |
| `is_active` | `BOOLEAN` | 是否启用 |

#### `department`（科室）

| 字段名 | 类型 | 中文注释 |
|---|---|---|
| `dept_code` | `VARCHAR(3)` | 科室编码 |
| `dept_name` | `VARCHAR(100)` | 科室名称 |
| `pinyin_code` | `VARCHAR(50)` | 拼音简码 |
| `campus_id` | `UUID` | 所属院区 |
| `parent_id` | `UUID` | 上级科室 |
| `is_clinical` | `BOOLEAN` | 是否临床科室 |
| `sort_order` | `INTEGER` | 排序号 |
| `is_active` | `BOOLEAN` | 是否启用 |

#### `sys_user`（人员/用户）

| 字段名 | 类型 | 中文注释 |
|---|---|---|
| `username` | `VARCHAR(50)` | 用户名 |
| `real_name` | `VARCHAR(50)` | 姓名 |
| `employee_no` | `VARCHAR(20)` | 工号 |
| `phone` | `VARCHAR(20)` | 手机号 |
| `email` | `VARCHAR(100)` | 邮箱 |
| `dept_id` | `UUID` | 所属科室 |
| `is_active` | `BOOLEAN` | 是否启用 |
| `is_repair_engineer` | `BOOLEAN` | 是否维修工程师 |
| `permission_mode` | `VARCHAR(20)` | 权限模式（synced/custom） |

#### `repair_workorder`（报修工单）

| 字段名 | 类型 | 中文注释 |
|---|---|---|
| `wo_no` | `VARCHAR(30)` | 工单号 |
| `device_id` | `UUID` | 设备ID |
| `device_code` | `VARCHAR(20)` | 设备编码 |
| `device_name` | `VARCHAR(200)` | 设备名称 |
| `status` | `VARCHAR(20)` | 工单状态 |
| `urgency_level` | `VARCHAR(20)` | 紧急程度 |
| `fault_description` | `TEXT` | 故障描述 |
| `reporter_id` | `UUID` | 报修人 |
| `report_dept_id` | `UUID` | 报修科室 |
| `report_time` | `TIMESTAMP WITH TIME ZONE` | 报修时间 |
| `assigned_user_id` | `UUID` | 指派维修负责人（sys_user） |
| `repair_sub_status` | `VARCHAR(30)` | 维修子状态 |

#### `repair_process_type`（维修进程类型）

| 字段名 | 类型 | 中文注释 |
|---|---|---|
| `type_code` | `VARCHAR(30)` | 类型编码 |
| `type_name` | `VARCHAR(100)` | 类型名称 |
| `sort_order` | `INTEGER` | 排序 |
| `is_active` | `BOOLEAN` | 是否启用 |
| `can_add_parts` | `BOOLEAN` | 是否允许添加配件 |
| `can_engineer_add` | `BOOLEAN` | 工程师是否可主动新增段 |
| `engineer_add_rule` | `VARCHAR(30)` | 工程师新增规则（如 verify_rejected_only） |

#### `campus`（院区）

| 字段名 | 类型 | 中文注释 |
|---|---|---|
| `campus_code` | `VARCHAR(1)` | 院区编码 |
| `campus_name` | `VARCHAR(100)` | 院区名称 |
| `address` | `VARCHAR(500)` | 地址 |
| `contact_phone` | `VARCHAR(20)` | 联系电话 |
| `is_active` | `BOOLEAN` | 是否启用 |

#### `building`（楼栋）

| 字段名 | 类型 | 中文注释 |
|---|---|---|
| `building_code` | `VARCHAR(1)` | 建筑物编码 |
| `building_name` | `VARCHAR(100)` | 建筑物名称 |
| `campus_id` | `UUID` | 所属院区 |
| `floor_count` | `INTEGER` | 楼层数 |
| `is_active` | `BOOLEAN` | 是否启用 |

#### `warehouse`（仓库）

| 字段名 | 类型 | 中文注释 |
|---|---|---|
| `warehouse_code` | `VARCHAR(50)` | 库房编码 |
| `warehouse_name` | `VARCHAR(100)` | 库房名称 |
| `warehouse_type` | `VARCHAR(30)` | 库房类型 |
| `campus_id` | `UUID` | 所属院区 |
| `dept_id` | `UUID` | 归属科室 |
| `manager_id` | `UUID` | 管理员 |
| `address` | `VARCHAR(500)` | 地址 |
| `is_active` | `BOOLEAN` | 是否启用 |

#### `asset_category`（资产分类）

| 字段名 | 类型 | 中文注释 |
|---|---|---|
| `category_code` | `VARCHAR(50)` | 分类编码 |
| `category_name` | `VARCHAR(200)` | 分类名称 |
| `parent_id` | `UUID` | 上级分类 |
| `depreciation_years` | `INTEGER` | 折旧年限 |
| `residual_rate` | `DECIMAL(5,2)` | 残值率 |
| `sort_order` | `INTEGER` | 排序号 |
| `is_active` | `BOOLEAN` | 是否启用 |

#### `medical_device_category`（设备68码分类）

| 字段名 | 类型 | 中文注释 |
|---|---|---|
| `category_code` | `VARCHAR(16)` | 分类编码（68码，支持 4/6/8 位） |
| `category_name` | `VARCHAR(200)` | 分类名称 |
| `parent_code` | `VARCHAR(16)` | 上级分类编码 |
| `level` | `INTEGER` | 层级 |
| `sort_order` | `INTEGER` | 排序号 |
| `is_active` | `BOOLEAN` | 是否启用 |

#### `engineer`（工程师）

| 字段名 | 类型 | 中文注释 |
|---|---|---|
| `engineer_no` | `VARCHAR(20)` | 工号 |
| `real_name` | `VARCHAR(50)` | 姓名 |
| `user_id` | `UUID` | 关联用户 |
| `specialty` | `VARCHAR(100)` | 专业方向 |
| `phone` | `VARCHAR(20)` | 电话 |
| `is_on_duty` | `BOOLEAN` | 是否在岗 |

#### `fault_type_dict`（故障类型）

| 字段名 | 类型 | 中文注释 |
|---|---|---|
| `fault_code` | `VARCHAR(20)` | 故障编码 |
| `fault_name` | `VARCHAR(100)` | 故障名称 |
| `level` | `INTEGER` | 层级 |
| `is_active` | `BOOLEAN` | 是否启用 |

#### `finance_category`（财务分类）

| 字段名 | 类型 | 中文注释 |
|---|---|---|
| `finance_code` | `VARCHAR(50)` | 分类编码（列表展示名） |
| `finance_name` | `VARCHAR(200)` | 分类名称（列表展示名） |
| `parent_id` | `UUID` | 上级分类（列表展示于会计科目前） |
| `account_subject` | `VARCHAR(50)` | 会计科目 |
| `fund_source` | `VARCHAR(50)` | 资金来源 |
| `sort_order` | `INTEGER` | 排序号 |
| `is_active` | `BOOLEAN` | 是否启用 |

#### `unit_dict`（计量单位）

| 字段名 | 类型 | 中文注释 |
|---|---|---|
| `unit_code` | `VARCHAR(20)` | 单位编码 |
| `unit_name` | `VARCHAR(50)` | 单位名称 |
| `unit_type` | `VARCHAR(20)` | 单位类型 |
| `sort_order` | `INTEGER` | 排序号 |
| `is_active` | `BOOLEAN` | 是否启用 |

#### `sys_role`（角色）

| 字段名 | 类型 | 中文注释 |
|---|---|---|
| `role_code` | `VARCHAR(50)` | 角色编码 |
| `role_name` | `VARCHAR(100)` | 角色名称 |
| `description` | `TEXT` | 描述 |
| `sort_order` | `INTEGER` | 排序号 |
| `is_active` | `BOOLEAN` | 是否启用 |

### T.6 后续主数据约定（强制）

1. **新增主数据表/类型**时，必须：
   - 纳入 `EntityChangeLogService.TRACKED_TABLES`（及通用 CRUD / 专用 Controller 的写路径打点）；
   - 在本附录 **T.5** 增加一张「字段名 / 类型 / 中文注释」表；
   - 代码 `SNAPSHOT_FIELDS` 与 T.5 **保持一致**。
2. **扩展已有实体快照字段**时：先改 T.5 对应表格，再改 `SNAPSHOT_FIELDS`，便于比对与评审。
3. 交付自检：创建 / 更新 / 删除（及有业务动作的 submit/withdraw 等）均能查到变更记录。

**状态**：按 T 实施（含 T.5 / T.6）。

---

## 附录 U：维修调度、工程师、进程与验收（2026-07-13）

> 来源：用户需求（2208–2232 行草稿）；与 [repair-workorder.md](repair-workorder.md)、附录 S 衔接。

### U.1 合理性结论

| 项 | 结论 |
|----|------|
| 派单 / 抢单 / 接单 | **合理**；与现有 `dispatch` / `accept` API 方向一致，需补「抢单」与操作互斥 |
| 工程师取自工作人员 | **合理**；`sys_user` 即人员主数据（附录 T.5） |
| 取消独立 `engineer` 表 | **已确认**：无正式业务数据，**本轮废弃** `engineer` 表；派工字段改为 `assigned_user_id` → `sys_user`（U.3） |
| 维修进程可维护 + 分段起止 | **合理**；与现有 `repair_sub_status` 字典、`repair_workorder_process`（动作审计）并存，需新增 **进程类型主数据 + 工单进程段** |
| 进程上挂配件明细 | **合理**；与 BACKLOG 备件领用（REP-F-02）衔接 |
| 维修处理显示待派单 | **合理且紧迫**；当前 `listMode=handle` **不含** `reported`，已提交待派工单不会出现在维修处理列表（与截图「暂无数据」一致） |

### U.2 维修工单调度

| 场景 | 规则 |
|------|------|
| **管理派单** | 状态为 `reported` / `dispatching` 且未指派工程师时，管理人员指派工程师 → `pending_accept`（或 `dispatching`→`pending_accept`）；写入 `repair_workorder_process`（`dispatch`） |
| **工程师接单** | 仅 **被指派** 工程师可接单；接单后 → `repairing`，并 **开启进程段**「院内维修中」；其他工程师不可操作该单 |
| **工程师抢单** | 无指派工程师的待派单（`reported`，可选含 `dispatching`）允许抢单；抢单者成为负责人 → 直接 `repairing` + 进程段「院内维修中」；写入 `grab` 类流程记录 |
| **操作互斥** | 非当前负责人不可派工后续动作（转派、加进程、配件等）；转派规则仍按附录 S / repair-workorder 文档 |

### U.3 维修工程师管理

| 项 | 定稿 |
|----|------|
| 菜单名 | **维修工程师管理**（原「工程师」） |
| 人员来源 | `sys_user`；新增 **`is_repair_engineer`**（布尔，默认 false） |
| 列表 | 仅 `is_repair_engineer = true` 的人员；展示工号、姓名、科室、电话等；操作：**查看维修记录**、**设为非工程师** |
| 新增 | 弹窗列出 **非** 维修工程师的 `sys_user`，支持多选 + **跨页勾选缓存**；确认后批量置 `is_repair_engineer = true` |
| 与 `engineer` 表 | **本轮废弃**：删除 `engineer` 表及关联 FK；`repair_workorder.assigned_engineer_id` 重命名为 **`assigned_user_id`**（→ `sys_user`）；`RefSelect` / 流程记录工程师字段统一为 `user_id` |
| 工作量 | **不存静态分**；按 **报修单 + 维修进程段** 动态统计（见 U.9） |
| 变更记录 | `sys_user` 纳入 `is_repair_engineer` 变更的字段级记录（附录 T） |

### U.4 维修处理列表与进程

**列表范围（`/repair/handle`）**应包含：

| 业务含义 | 主状态 `status` | 可否添加维修进程 |
|----------|-----------------|------------------|
| 待派单 | `reported`（可选 `dispatching` 且无负责人） | **可** |
| 派单/接单中 | `dispatching`、`pending_accept`、`accepted` | **可**（规则待与现场确认） |
| 维修中 / 挂起 | `repairing`、`suspended` | **可** |
| **拒绝验收** | **`verify_rejected`（新增）** | **可**（返修；见 U.6） |
| 已维修待验收 | `pending_verify` | **不可**（只读查看） |
| 已验收 | `verified`（可选含 `closed`） | **不可**（只读查看） |

> `cancelled` **不纳入**维修处理列表（作废单在报修申请/全量页只读查看）。

**维修进程（新能力）**

1. **主数据** `repair_process_type`（可维护）：名称、排序、是否启用、**是否允许添加配件**（`can_add_parts`）、**是否允许作为工程师主动新增的下一段进程**（`can_engineer_add`，可选）；初始种子：

   | 进程类型 | 允许配件 | 工程师可新增段 |
   |----------|----------|----------------|
   | 院内维修中 | 是 | 是 |
   | 院外维修中 | 是 | 是 |
   | 等待配件中 | 是 | 是 |
   | **拒绝验收** | 否 | 否（仅验收驳回时系统自动写入） |
   | 已维修待验收 | 否 | **仅 `verify_rejected` 时可由工程师主动添加**（见 U.6）；其余状态不可 |
   | 已验收 | 否 | 否 |

2. **工单进程段** `repair_workorder_segment`：关联工单、进程类型、负责人（`user_id`）、**开始/结束时间**；新增可编辑段时 **自动结束上一段**。
3. **待派单** 可直接添加首段进程（不必先派工），同步主状态（通常 → `repairing`）。
4. **配件记录**：仅在 `can_add_parts = true` 的进程段上维护明细。

### U.5 维修进程维护

- 维护入口：系统字典级或独立「维修进程类型」页（与 U.4 主数据一致）。
- **`can_add_parts = false`** 的进程（如已维修待验收、已验收）**禁止**新增配件明细。

### U.6 维修验收与拒绝验收

**列表（`/repair/verify`）**：`pending_verify` + `verified`（及可选 `closed`）。

| 操作 | 规则 |
|------|------|
| 验收通过 | `pending_verify` → `verified`；满意度写入流程记录；设备 → `normal`（或按现有结案规则 → `closed`） |
| **拒绝验收** | 见下表 **定稿** |

#### 拒绝验收后工单状态（定稿，2026-07-13 修订）

| 维度 | 取值 | 说明 |
|------|------|------|
| **主状态** | **`verify_rejected`（拒绝验收）** | 新增 `wo_status` 字典项；与「维修中」区分，便于列表筛选与审计 |
| **设备台账** | **`maintenance`（维修中）** | 验收驳回后设备仍不可用，**不**保持 `pending_verify` |
| **负责人** | **保持原指派工程师**（`assigned_user_id`） | 返修由原工程师继续，除非转派 |
| **进程段** | 结束「已维修待验收」段；**系统自动写入「拒绝验收」段**（含拒绝原因、验收人、时间） | 见下方 **工程师后续可添加的进程** |
| **维修处理** | `verify_rejected` 工单在维修处理列表，**允许**继续添加维修进程 | — |

- **不建议**回到 `reported` / `draft`（已发生过实质维修）。
- **不建议**直接 `cancelled`（取消用于整单作废）。

**拒绝验收后，工程师可添加的进程（2026-07-13 补充）**

| 新增进程类型 | 效果 |
|--------------|------|
| 院内维修中、院外维修中、等待配件中等 | 主状态保持 **`verify_rejected`** 或同步为 **`repairing`**（实现时二选一，**推荐保持 `verify_rejected` 直至再次待验收**）；设备保持 **`maintenance`**；继续返修 |
| **已维修待验收** | 主状态 → **`pending_verify`**；设备 → **`pending_verify`**；开启新一段「已维修待验收」进程；**进入待验收阶段**（等价于返修后再次提交验收，但以 **添加进程** 为操作入口） |

> 除上述外，仍可提供「提交验收」快捷动作，与添加「已维修待验收」进程段 **业务等价**，后端统一校验。

拒绝时弹窗必填 **拒绝验收原因**；写入流程记录（`verify_fail`）+ 进程段 + 事件流水。

**返修闭环**：`verify_rejected` →（返修进程段）→ 工程师 **添加「已维修待验收」进程段** → `pending_verify`，可再次走验收通过/拒绝。

### U.7 衍生提醒（未纳入本轮）

- 抢单并发（多人同时抢同一单）需后端 **乐观锁 / 状态条件更新**。
- 配件明细与库存扣减、费用汇总（REP-F-02）。
- 外协维修独立单据（REP-F-03）。

### U.8 报修申请列表范围（2026-07-13 确认）

| 项 | 定稿 |
|----|------|
| **列表** | `/repair/apply` 展示 **除已删除（软删）外全部报修单**，含 **`cancelled`（已取消）** 等所有状态 |
| **`cancelled` 已取消** | **仍在报修申请列表显示**；**不允许任何操作**（仅查看、变更记录；无提交/撤回/删除/取消等按钮） |
| **其他状态操作** | 仍遵守附录 S.4：`draft` 可改删提交；`reported` 可撤回；已进入维修流程的仅查看 + 变更记录（禁止「取消」） |
| **实现** | `listMode=apply` 改为「未删全量」：仅 `is_deleted = 0`，不按 `status` 收窄 |

### U.9 工程师工作量（2026-07-13 确认）

不维护 `engineer.workload_score` 静态字段。工作量 **由运行时统计**：

| 指标（建议） | 数据来源 |
|--------------|----------|
| 负责工单数 | `repair_workorder` 按 `assigned_user_id` + 状态分组 |
| 进程段数 / 时长 | `repair_workorder_segment` 按负责人、`started_at`/`ended_at` 汇总 |
| 配件成本 | 进程段配件明细汇总（REP-05 落地后） |

展示入口：维修工程师管理 →「查看维修记录」/ 统计面板（具体 UI 排期）。

### U.10 业务完整性评估（2026-07-13）

#### 已覆盖（闭环较完整）

| 环节 | 覆盖情况 |
|------|----------|
| 报修 → 提交 → 待派单 | ✓ 附录 S + U.8 全量可见 |
| 派单 / 抢单 / 接单 | ✓ U.2 |
| 维修进程分段 + 配件 | ✓ U.4–U.5 |
| 提交验收 → 通过 / 拒绝 | ✓ U.6；拒绝用 `verify_rejected` + 进程「拒绝验收」 |
| 拒绝后返修 → 再验收 | ✓ U.6：可加返修进程段，并可 **添加「已维修待验收」** 进入 `pending_verify` |
| 工程师主数据 | ✓ U.3 废弃 engineer，用 `sys_user` |
| 工作量 | ✓ U.9 动态统计 |

#### 建议本轮一并定稿的缺口（否则现场易卡点）

| 缺口 | 风险 | 建议 |
|------|------|------|
| **挂起 `suspended`** 与 `verify_rejected` 互转规则 | 挂起后能否直接提交验收不明确 | 挂起仅允许「恢复维修」→ `repairing`；禁止从挂起直跳验收 |
| **转派** 后 `verify_rejected` 工单 | 返修中途换人 | 允许转派；新负责人继承可添加进程权限 |
| **抢单并发** | 双抢一单 | UPDATE … WHERE status=`reported` AND assigned_user_id IS NULL |
| **维修处理中的只读单**（待验收/已验收） | 误操作 | 前端禁用「添加进程/配件」；后端接口校验状态 |
| **`cancelled` 在报修列表** | 临床看到作废单 | **已确认**：列表显示、**禁止一切操作**（U.8） |
| **权限** | 调度/工程师/科室角色混用 | 派单=调度角色；抢单/接单=维修工程师；验收=报修科室或指定角色（沿用现有权限体系，落地时列按钮级权限） |

#### 可后续迭代（不阻塞本轮）

- 配件库存扣减与财务汇总（REP-F-02）
- 外协院外维修独立单据（REP-F-03）
- 工程师工作台 KPI 图表、科室满意度报表

**完整性结论**：在补齐 **返修再验收**、**列表/进程可编规则**、**engineer 表迁移** 三项实现后，主流程 **可形成闭环**，满足医院维修调度与验收的主场景；**权限与并发**需在开发自检中显式覆盖。

**状态**：U.1–U.11 已定稿；REP-02/03/04/05/06/07 已落地；REP-F-02/03 **长期搁置**。

### U.11 列表查询与状态筛选（2026-07-13）

四页统一支持 **关键词**（工单号/设备编码/设备名称/故障描述；工程师页为姓名/工号/账号/电话）+ **组合筛选**。**状态支持多选**，与各页默认列表范围取交集。

| 页面 | 默认列表范围 | 筛选项 |
|------|--------------|--------|
| **报修申请** `/repair/apply` | 未删全量 | 状态（多选）、紧急程度、报修科室、负责人、指派（未指派/已指派）、报修日期 |
| **维修处理** `/repair/handle` | 待派单～已验收（不含 draft/cancelled/closed） | 状态（多选，子集）、紧急程度、报修科室、负责人、指派、报修日期 |
| **维修验收** `/repair/verify` | 待验收 + 已验收 + 已关闭 | 状态（多选：pending_verify/verified/closed）、紧急程度、报修科室、负责人、报修日期 |
| **维修工程师管理** | `is_repair_engineer=true` | 科室、在办工单（有/无；统计不含已关闭/已取消） |

API：`GET /repair/workorder/page` 增加 `statuses`（逗号分隔）、`urgencyLevel`、`reportDeptId`、`assignedUserId`、`assignment`、`reportTimeFrom/To`；`GET /repair/engineer/page` 增加 `deptId`、`workload`。

### U.12 尚未实现 / 不阻塞上线（2026-07-13 盘点）

| 项 | 说明 | 处置 |
|----|------|------|
| REP-F-02 库存扣减/费用汇总 | 进程段已可录配件，不扣库存 | **长期搁置** |
| REP-F-03 外协独立单据 | 院外暂用子状态/进程段 | **长期搁置** |
| REP-F-04 移动端扫码报修 | 第 3 章待办 | 待排期 |
| U.9 工程师 KPI 图表 | 已有「查看维修记录」、在办工单数 | 待真实用户后迭代 |
| 按钮级角色权限 | 派单/抢单/验收分角色显隐 | 沿用现有权限体系，细粒度待排期 |
| 消息通知（派单/验收提醒） | 未纳入附录 U | 待排期 |
| `engineer` 表物理删除 | 保养模块仍引用 | 保留空壳，维修已改 sys_user |

**主流程结论**：报修 → 派单/抢单 → 维修进程 → 验收/拒绝/返修 **已闭环**；上述缺口不影响首期上线。

### U.13 列表抢单与进程段补录字段（2026-07-15）

> 来源：用户需求 + 现场问题（抢单 bad SQL、进程类型下拉空）。

#### U.13.1 合理性

| 项 | 结论 |
|----|------|
| 列表操作「派工」后增加「抢单」 | **合理**；与详情 footer 抢单同规则 |
| 添加进程可选工程师 | **合理**；默认工单负责人（无则当前登录工程师），可改选 |
| 开始/结束时间 | **合理**；开始默认此刻可改；结束默认空，勾选后才可填（补录） |
| 进程类型为空 | **缺陷**：须修种子/过滤/`addable` 静默空列表 |

#### U.13.2 行为定稿

| 能力 | 规则 |
|------|------|
| **列表抢单** | `#row-actions` 在派工之后；规则同详情 `can('grab')` |
| **进程工程师** | 默认接单人（`assigned_user_id`）**只读**；勾选「修改工程师」后可改且 **支持多选**；第一个为主责写入段 `user_id`，全部写入 `repair_workorder_segment_user` |
| **开始时间** | 必填，默认当前时刻 |
| **结束时间** | 默认不启用；勾选「填写结束时间（补录）」后可填；有值则段入库即已结束；须 ≥ 开始 |
| **上一段** | 新段前自动结束开放段；上一段 `ended_at` 取本段 `started_at` |
| **进程类型** | `/addable` 不因 segment 表短暂缺失静默空返回；布尔稳健解析；种子按 `type_code` 幂等补齐 |

#### U.13.3 抢单 bad grammar

多因租户缺 `assigned_user_id`。处置：`R__columns_biz` + 重启 meis-tenant；缺列时接口返回明确提示。

#### U.13.4 进程工程师多选与 JDBC 占位符排查（2026-07-15）

| 项 | 定稿 |
|----|------|
| 默认 | 工程师=工单接单人，禁止编辑 |
| 修改 | 勾选后可多选维修工程师；一段可多人同时参与 |
| 存储 | 段表 `user_id`=主责（列表第一人）；成员表 `repair_workorder_segment_user` |
| JDBC 扫描 | 已修 `?`/参数不匹配：进程段 INSERT、入库生成设备、报废、保养记录/执行单、巡检/计量执行、公用借调 |

**状态**：按 U.13（含 U.13.4）实施。

### U.14 列表功能分列、进程展示与段确认固化（2026-07-15 17:36:00）

> 来源：用户需求（文末草稿已并入本节后删除）。

#### U.14.1 合理性

| 项 | 结论 |
|----|------|
| 维修列表横向滚动（仅维修相关页） | **合理**；字段与功能只会增多 |
| 取消单一「操作列」，每功能独立列 | **合理**；便于后续调列序；操作列仅保留查看/编辑/删除/变更记录等通用项（或按页精简） |
| 申请+处理可见进程与配件 | **合理**；修订附录 S.4：申请页 **只读**展示进程/配件，仍不展示财务与派工操作 |
| 工程师行列表 + 工作内容选填 | **合理**；`segment_user.work_content` |
| 段确认固化（非完整审批流） | **合理**；与科室验收区分，避免双重审批 |

#### U.14.2 维修列表交互（方案 A）

| 项 | 定稿 |
|----|------|
| **范围** | 仅维修：`/repair/apply`、`/repair/handle`、`/repair/verify`、`/repair/workorder`（不做全局 CrudPage 约定） |
| **横向滚动** | 表格区域可左右滑动；页面骨架不动 |
| **功能列** | **维修处理**：取消把派工/接单/转派等塞进同一操作列；**每个功能单独一列**（派工、抢单、接单、转派、添加进程、开始维修、完工、挂起、恢复、取消…）；后续新功能继续追加列，列序可后调 |
| **通用操作** | 「查看 / 编辑 / 删除 / 变更记录」可保留窄操作列或并入功能列；权限规则与详情 footer 一致，**不必进详情才能操作** |
| **详情 footer** | 保留同等能力（双入口） |

#### U.14.3 进程与配件展示

| 页面 | 规则 |
|------|------|
| **报修申请** | 详情内 **只读**展示「维修进程段」及每段 **配件更换记录**；不可添加进程/配件/确认 |
| **维修处理** | 详情内展示进程段 + 配件；未确认段可维护；已确认段只读 |
| **列表** | 本轮不强制加「进程摘要」列（可后续追加） |

#### U.14.4 进程段工程师与工作内容

| 项 | 定稿 |
|----|------|
| UI | 添加/编辑进程时为 **工程师行列表**（选人 + 工作内容 + 主责标记），非仅多选下拉 |
| `work_content` | **选填**；存 `repair_workorder_segment_user.work_content` |
| 主责 | 仍同步段表 `user_id`；成员表 `is_primary` |
| 展示 | 进程段下列出各工程师姓名与工作内容 |

#### U.14.5 段确认固化

| 项 | 定稿 |
|----|------|
| **模型** | 轻量「确认固化」，**不做**通过/驳回审批流 |
| **必记字段** | 段确认操作须写入并展示：**确认状态**、**确认时间**（`confirmed_at`）、**确认人**（`confirmed_by` → 姓名） |
| **确认状态** | `未确认` / `已确认`；系统自动段展示为 `已确认（系统）`（`auto_created`，可不写 `confirmed_by`） |
| **落库** | `POST .../segments/{id}/confirm` 时 `confirmed_at = NOW()`、`confirmed_by = 当前登录用户`；事件流水 `confirm_segment` |
| **未确认** | 可改：进程备注/起止（若已开放编辑接口）、工程师列表与工作内容、配件明细；可删除未确认段（若实现删除） |
| **已确认** | **禁止**删改进程、工程师、配件；只读展示 |
| **系统段** | `auto_created = true` → **视为已固化**（接单开段、拒绝验收、待验收等） |
| **谁确认** | 具备 **维修处理** 操作权限的用户（调度/设备科；与派工同级）；工程师维护未确认段，确认由调度执行 |
| **提交验收** | 除当前仍开放且即将关闭的段外，**历史段须均已确认**；否则拦截并提示 |
| **API** | `POST /repair/workorder/{id}/segments/{segmentId}/confirm` |

#### U.14.6 库表变更（固定槽位）

| 表 | 变更 |
|----|------|
| `repair_workorder_segment` | `confirmed_at TIMESTAMPTZ`、`confirmed_by UUID`（→ sys_user） |
| `repair_workorder_segment_user` | `work_content TEXT` |
| 脚本 | `V1__tables.sql` 同步建表；`R__columns_biz.sql` 补列 |

**状态**：按 U.14 实施。

### U.15 进程段编辑删除、确认补全与配件档案（2026-07-15 20:42:27）

> 来源：用户需求（文末草稿已并入本节与附录 W 后删除）。  
> 待确认已定稿：删段不重开上一段；工程师至少保留 1 人；进行中可改 `ended_at`；确认时空 `ended_at` 回填；开待验段时自动确认未确认段；冗余选方案 B 并推广（附录 W）。

#### U.15.1 进程段编辑 / 删除 / 配件行

| 项 | 定稿 |
|----|------|
| **可编辑** | 仅 **未确认** 段（含进行中开放段）：备注、`started_at` / `ended_at`、工程师行（含工作内容/主责）、配件行 |
| **配件行可改** | 单价、数量、**供应商**（`repair_workorder_segment_part.supplier_id`，缺列则双轨补列）；可删行 |
| **工程师** | 可删人；**至少保留 1 人**；删主责则按剩余顺序第一人升主责并同步段 `user_id` |
| **删除段** | 仅未确认；**软删**；**不**清空/重开上一段 `ended_at` |
| **已确认 / 系统段** | 仍只读（U.14.5） |
| **API** | `PUT .../segments/{segmentId}`；`DELETE .../segments/{segmentId}`；`PUT .../segments/{segmentId}/parts/{partId}`；`DELETE .../parts/{partId}`；工程师随段 PUT 全量覆盖或提供删人接口 |
| **页面** | 维修处理详情可操作；报修申请仍只读（U.14.3） |

#### U.15.2 段确认与待验自动确认

| 项 | 定稿 |
|----|------|
| **确认时空 `ended_at`** | `POST .../confirm` 时若 `ended_at IS NULL`，则 **`ended_at = NOW()`**（与 `confirmed_at` 同次写入） |
| **开「已维修待验」段** | 创建 `pending_verify` 系统段时：**自动确认**本工单全部仍未确认的段（含刚结束的开放段）；对每段执行与手工确认同等落库（`confirmed_at`/`confirmed_by`；空则补 `ended_at`）；系统段自身仍按 U.14 视为已固化 |
| **提交验收拦截** | 保留 U.14.5：历史段须已确认；因待验开段已自动确认，正常完工路径应已满足 |

#### U.15.3 配件档案（去进销存 UI）

| 项 | 定稿 |
|----|------|
| **隐藏字段** | 表单/列表不展示：库房、库位、库存数量、最低库存；**经验默认一并隐藏**最高库存（`max_stock`） |
| **隐藏 Tab** | 「库存预警」「流水记录」整页隐藏（库表可保留，接口可不挂菜单） |
| **进销存** | 与 REP-F-02 一致，**本期不做**；禁止从档案页维护库存数量 |
| **拼音简码** | `spare_part.pinyin_code`（双轨补列）；列表支持「生成简码」（对齐供应商/科室） |
| **复制** | 支持；打开新增并带出名称/规格等；**编码须重填**（不自动生成，避免撞码） |
| **删除** | 软删；若存在进程配件明细 / 使用记录 / 流水等业务引用则 **禁止删除** 并提示 |

#### U.15.4 库表变更（固定槽位）

| 表 | 变更 |
|----|------|
| `repair_workorder_segment_part` | `supplier_id UUID`（→ supplier） |
| `spare_part` | `pinyin_code VARCHAR(50)` |
| 冗余列 | 见 [附录 W](#附录-w业务冗余字段约定2026-07-15)（维修 P0） |
| 脚本 | `V1__tables.sql` + `R__columns_biz.sql` |

**状态**：U.15 / W.3.1（维修 P0）已实施；跨模块见 W.3.2 / `BACKLOG-AST-W01`。

**开发面板热加载自检（2026-07-13）**：截图中 `meis-tenant`「JAR 落后 + 待同步」而 `meis-repair` 已热加载，属 **机制 + 状态刷新** 叠加（见附录 L.6 机制修补），非业务缺陷。请 **重启开发面板** 使脚本生效；若 tenant 仅 HTTP 无 JDWP，须手动热加载或「调试启动」。

#### 拉取同事代码与 mtime 早晚（2026-07-13 确认）

| 问题 | 结论 |
|------|------|
| 同事提交时间较早、工作区文件 mtime 可能晚于本地 `classes`/`JAR`，自动热加载能否识别？ | **自动热加载不靠比较 mtime 早晚**，而靠 `FileSystemWatcher` 收到 **写入事件**（Changed/Created/Renamed）后打 dirty；dirty 时间用 **当前时刻**，与同事提交时间无关。 |
| `git pull` / `checkout` 时面板已开 | 覆盖/新增源码通常会触发事件 → 调试中服务可排队自动热加载（仍须 HTTP+JDWP、2s 防抖、20s 冷却）。 |
| 拉取时面板未开 / 拉取后才启动面板 | **不会**补扫历史变更；须手动点「热加载」或「热加载依赖」。 |
| 仅改公共库 `meis-common` | 仍须在公共库区手动「热加载依赖」（监视器只挂各微服务 `src/`）。 |
| 面板「待编译」红字 | **才**用源码树最新 mtime vs classes/JAR；若拉取文件 mtime 偏旧，**可能不显示待编译**，但内容已变——以自动/手动热加载为准，勿只信 mtime 提示。 |

**建议**：`git pull` 后若涉及 Java，对改过的服务点一次「热加载」最稳妥；依赖 `meis-common` 时用「热加载依赖」。

### I.4 查询默认排除已软删（2026-07-14）

| 项 | 约定 |
|----|------|
| 适用范围 | 所有读接口：列表、详情、下拉/options、统计 COUNT/SUM、登录与权限解析、业务校验用的存在性查询 |
| 实现 | 拼接 `SoftDeleteSupport.notDeletedClause(jdbc, table, alias)`（无 `is_deleted`/`deleted_at` 列时返回空串，安全兼容） |
| 例外 | 变更记录快照加载、按唯一键**查找已软删行以便恢复**、明确「含已删」的管理排查接口（须标注） |
| 联表 | **每张有软删列的业务表别名**各自加过滤；勿只滤主表而忽略子表/候选设备等 |

> 手工 SQL / 业务重写的 page/get（尤其 `meis-purchase`、`meis-maintain`、`meis-qc`、`meis-asset` 出入库类）历史上易漏；`GenericTableController` 路径已默认过滤。

### I.5 缺 `is_deleted` 表的动态补齐（2026-07-14）

| 项 | 约定 |
|----|------|
| 范围 | 各租户 schema 内全部 `BASE TABLE`（排除 `flyway_%`） |
| 动作 | 缺列则 `ADD is_deleted SMALLINT NOT NULL DEFAULT 0`；同步补 `deleted_at`/`deleted_by`；存量因 DEFAULT 为 `0`；有 `deleted_at` 时已删行回填 `is_deleted=1` |
| 脚本 | `R__columns_audit.sql` 动态 DO（权威）；勿再新增零散 patch |
| 扫描结果 | `tenant_*` 仅缺：`device_label_print_log`、`sys_entity_change_log`（日志类亦补标志；读过滤仍按 I.4 例外） |
| public | 平台/遗留副本不在本轮租户补齐范围 |


### G.10 物理删除盘点与结构补齐策略（2026-07-14）

#### G.10.1 代码中 `DELETE FROM` 盘点

| 类别 | 表 / 位置 | 结论 |
|------|-----------|------|
| **明细先清空再写入（保留物理删）** | `*_item` / `*_member` / `contract_payment`（随主单保存重写）：采购计划/合同付款/验收、出入库退货盘点明细、保养/巡检/计量/PM 模板项 | **符合 G.3**，不改为软删（否则每次保存堆积软删行） |
| **主表删除 API** | 走 `SoftDeleteSupport.softDelete` / `GenericTableController` | **已是软删** |
| **工具兜底** | `SoftDeleteSupport`：表无软删列时曾物理 `DELETE` | **改为报错**，禁止静默物理删主数据 |
| **平台租户注销** | `TenantService`：`sys_tenant` + `sys_tenant_menu` | **例外**：平台配置/关联重绑；租户行现状物理删 |
| **租户菜单重绑** | `TenantMenuService`：先删后插绑定 | **等同明细例外**，保留物理删 |

> 结论：租户**业务主表**侧未发现「删除接口仍物理删」的缺口；现存物理删几乎全是 G.3 明细重写或平台绑定。

#### G.10.2 新增表必须含标准七列（重申并加强）

| 要求 | 说明 |
|------|------|
| **强制** | 新建租户业务表 `CREATE TABLE` **必须**含附录 G.0 七列 |
| `is_deleted` | `SMALLINT NOT NULL DEFAULT 0`（未删默认 0） |
| **禁止** | 建表后再靠业务代码「碰巧」补列；禁止无七列的新表进入 `V1__tables.sql` |
| **存量** | 继续靠 `R__columns_audit.sql`（含动态补 `is_deleted`） |

#### G.10.3 库结构补齐：SQL 脚本为主，代码扫描为辅（定稿）

| 方式 | 擅长 | 不擅长 |
|------|------|--------|
| **SQL / Flyway R__（结构真理来源）** | 幂等加列、默认值、存量回填、多租户一致、可审计 | 发现代码误写物理 `DELETE` |
| **代码扫描（行为门禁）** | 找 `DELETE FROM`、漏 `notDeletedClause`、主表未走 `softDelete` | 不能代替迁库给各租户加列 |

**定稿组合**：

1. **库结构** → **只认 SQL**：`V1`（新表）+ `R__`（老表幂等）；必要时手工 patch；启动 `meis-tenant` 落到各租户。
2. **运行行为** → **代码约定 + 扫描**：主表禁止裸 `DELETE`；明细 G.3 例外须可识别；可用巡检扫 `DELETE FROM`。
3. **不要**用应用启动时 Java 扫库再 `ALTER` 作主方案；动态 DO 放在 **R__ SQL** 内（已采用）。

---

## 附录 V：列表勾选跨页缓存与批量作用域（2026-07-15）

> 来源：用户需求；通用原则双写 [约定包 §5.4](reusable-engineering-conventions.md)。

### V.1 合理性结论

- **合理**：分页列表仅「当页勾选」易导致误操作；导出/批量变更若不区分「勾选行 / 当前查询全部」容易伤数或漏数。
- **无批量/导出**的列表不应挂空勾选列。

### V.2 行为约定

| 能力 | 说明 |
|------|------|
| 跨页缓存 | `row-key` + `reserve-selection` + `useCrossPageSelection` |
| 全选当页 / 取消全选 | `ListSelectionBar`：全选叠加跨页；取消清空全部 |
| 筛选/重置 | 清空勾选 |
| 导出 / 批量写 | `promptListActionScope`：仅勾选行 vs 全部查询结果；后端收 `ids` 或筛选条件（`keyword`/`filters`/`all`） |
| 候选池确认添加 | 只需已选 id（如维修工程师批量添加） |

### V.3 MEIS 落地

| 组件/页面 | 说明 |
|-----------|------|
| `CrudPage` | 有勾选列时展示 `ListSelectionBar`；导出/生成简码走作用域 |
| 用户 / 科室 | 同上；用户批量修改支持 `all` + 筛选；**科室维护**按业务要求隐藏 `ListSelectionBar`（仍保留勾选列供导出/生成简码作用域） |
| 工程师候选弹窗 | 跨页勾选 + 全选当页；确认添加仅已选 |
| 盘点设备选择 | 勾选条 + 全选/取消 |
| 系统配置 / 电流读数 | 去掉无用勾选列 |
| 采购计划/合同 | 去掉重复的 `window.open` 导出，统一走 `CrudPage` 导出 |

**状态**：按 V.2 / V.3 实施；新列表默认遵守约定包 §5.4。

---

## 附录 W：业务冗余字段约定（2026-07-15）

> 来源：用户确认方案 B，并要求不限于 `device_id`、按经验推广关键字段冗余，便于台账追溯与直查。  
> 可复用原则双写 [约定包 v1.4 §6.2](reusable-engineering-conventions.md)。

### W.1 原则

| 项 | 定稿 |
|----|------|
| **目的** | 子表/流水可按设备（及关键展示字段）直查，少跨多层 join；保留写入时点快照 |
| **写入** | 插入子行时从 **主单 / 台账** 拷贝冗余列；同事务 |
| **变更** | 子行 **不随** 台账事后改名自动级联（快照语义）；主单若允许改 `device_id`（罕见），须同事务刷新未关闭子行或禁止改设备 |
| **勿冗余** | 纯字典/模板主数据、无设备上下文的配置表 |
| **索引** | 对高频按设备查询的表建 `device_id` 索引（`V2__indexes.sql`） |
| **回填** | 老数据一次性 `UPDATE … FROM` 主单回填；可空列允许历史 NULL，新写入必填（主单有设备时） |

### W.2 推荐冗余列（设备业务子表）

| 列 | 说明 |
|----|------|
| `device_id` | **必冗余**（主单有设备时） |
| `device_code` | 编码快照，列表/导出免 join |
| `device_name` | 名称快照 |
| 单据号快照（可选） | 如 `wo_no`，便于流水独立展示 |
| 组织快照（可选） | 如报修科室 `report_dept_id` 仅主单需要时不必向下铺 |

配件明细行另可冗余：`supplier_id`（业务可改，以行为准）+ 展示用供应商名称可由 join 或快照二选一（本期 **只存 supplier_id**，名称走字典/外键显示）。

### W.3 落地范围

#### W.3.1 维修（P0，与 U.15 同轮）

| 表 | 补列 |
|----|------|
| `repair_workorder_event` | `device_id`、`device_code`、`device_name` |
| `repair_workorder_process` | 同上 |
| `repair_workorder_segment` | 同上 |
| `repair_workorder_segment_part` | 同上 + `supplier_id` |
| `spare_part_usage` | `device_id`、`device_code`、`device_name`（有工单时从工单带入） |
| `spare_part_transaction` | 同上（若本期仍保留表结构） |

`repair_workorder_segment_user`：**不强制**铺设备冗余（人维度，台账少直查）；需要时经 segment → device。

主单 `repair_workorder` 已有 `device_id/code/name`，保持写入校验（创单/改单设备必填）。

#### W.3.2 其他模块（P1，同约定分批补）

| 模块 | 现状缺口（经验） | 补齐 |
|------|------------------|------|
| 保养 | `maintenance_execution` / `maintenance_execution_result` 缺 `device_id` | 从计划/记录带入 + code/name |
| 计量 | `metrology_execution` 缺 | 同上 |
| 巡检 | `inspection_execution` 缺 | 同上 |
| 预防性维护 PM | `pm_execution` 缺 | 同上 |
| 公用设备 | `shared_device_fee` 缺 `device_id` | 从借调/归还单带入 + code/name |

已有 `device_id` 的主单/明细（如 `maintenance_record`、`inspection_record`、借调单等）**补齐 code/name 快照**（若尚无），写入时从 `medical_device` 拷贝。

P1 记入第 7 章：`BACKLOG-AST-W01`（跨模块执行表/费用表冗余补齐），实施时仍走固定迁库槽位。

### W.4 台账查询

设备台账子页（维修/保养/巡检/计量/借调等）优先：

1. 有冗余 `device_id` 的子表/流水 → `WHERE device_id = ?`  
2. 否则 → 经主单 `device_id` 关联  

**状态**：W.3.1 与 U.15 同轮实施；W.3.2 进待开发池后分批。


请问工作人员姓名是否有冗余到各个表内，请将工作人员姓名也做冗余。为操作记录背书，具体操作责任落实到人，避免姓名变更导致追溯链路断开