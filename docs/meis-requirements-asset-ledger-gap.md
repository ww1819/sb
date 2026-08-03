# 资产台账字段缺口分析（三甲 / 国际高标准）

> 对应需求草稿：对照三甲建设与国际高标准，列出 `medical_device` 及宜用从表承载的能力缺口。  
> **状态**：分析定稿（2026-08-01）；**本期不批量加列**，实施项进 `BACKLOG-AST-GAP-*`。  
> **三甲再审（推荐阅读）**：[`meis-requirements-tertiary-gap-review.md`](meis-requirements-tertiary-gap-review.md)（标量 / 从表对象 / 流程，已对照 OWN/LOC/PART 等已实现项）。  
> 主文档索引：`docs/meis-requirements.md` → AST-GAP-01 / AST-GAP-REVIEW-01。

## 1. 现状基线（已具备）

基本标识（编码/名称/品牌/规格/型号/SN/注册证）、分类三体系、科室/管理科室、位置（院区楼宇库房楼层房间/存放）、财务折旧体系、厂家供应商、合同与购置启用验收节点、风险等级、生命支持/应急/计量/公用/保养巡检 PM 标志、电流监测上下限、维保截止（含维保包 AST-WRN-02）、使用年限与到期、特种设备独立表 `special_device`。

## 2. 建议补强的主表字段（仍挂 `medical_device`）

| 优先级 | 建议字段 | 依据 / 说明 |
|--------|----------|-------------|
| P0 | `udi_di` / `udi_pi`（或统一 `udi_code`） | 药监/器械唯一标识追溯；现仅采购明细有 UDI |
| P0 | `asset_manager_user_id` + `asset_manager_name` | 设备科资产管理员（UUID+姓名快照）；与自由文本 `use_dept_head` 区分 |
| P0 | `clinical_owner_user_id` + `clinical_owner_name` | 使用科室临床责任人（可对应用户） |
| P1 | `nmpa_license_no` / `nmpa_license_expiry` | 注册证外补充证照号与有效期（若与 registration_no 拆分） |
| P1 | `eq_class`（Ⅰ/Ⅱ/Ⅲ类） | 医疗器械管理类别；可与分类主数据冗余快照 |
| P1 | `criticality` 或字典「关键等级」 | 区别于报修「紧急程度」与布尔 `is_emergency` |
| P1 | `depreciation_method` | 折旧方法（平均年限等），财务审计常用 |
| P1 | `acquisition_mode` | 购置方式：购入/捐赠/划拨/融资租赁 |
| P1 | `in_service_flag` / `idle_reason` | 在用/闲置原因（评级资产利用率） |
| P2 | `gs1_gtin` / `lot_no` | 国际供应链追溯补充 |
| P2 | `energy_class` / `radiation_flag` 细化 | 能效；放射相关可与特种表交叉 |
| P2 | `networked` 已有时可补 `ip_address` / `mac_address` | 联网设备运维 |

## 3. 建议以从表 / 独立对象存在的能力

| 对象 | 建议形态 | 说明 |
|------|----------|------|
| **UDI / 标识历史** | 从表或变更流水 | DI 不变、PI/标签更换可追溯 |
| **证照与合格证明** | 附件从表（证照类型+有效期+文件） | 注册证扫描件、强检证书、计量证书副本 |
| **保养/巡检/计量计划执行** | **已有**执行/计划表 | 台账 Tab 已挂 |
| **维保合同与覆盖** | **已有** `device_warranty*` | AST-WRN-02 |
| **配件更换流水** | 维修复用 `repair_workorder_segment_part` + 非维修表 `device_part_replacement` | AST-UI-21 已落地维修侧；**AST-PART-01** 方案定稿（确认生效 + 生成方式） |
| **附属低值品** | **已有** `device_accessory` | 非更换语义；可后续挂「附属配件」Tab |
| **特种设备扩展** | **已有** `special_device` | 压力容器/放射等参数勿堆主表 |
| **效益/收入挂钩** | 已有/半成品效益模块 | 收费项目映射宜从表 |
| **不良事件** | **已有**不良事件表 | 台账 Tab 已挂 |
| **培训与授权使用人** | 新从表 | 三甲「持证上岗」常见：人员+证书+有效期 |
| **移机/位置变更史** | `device_location_period` + 按钮真变更/纠错 | **AST-LOC-01** 方案已定（实现 P2 / `BACKLOG-AST-LOC-01`）；与科室交互同构 |
| **归属/处置统一视图** | 归属区间表 + 处置聚合 | AST-OWN-01/02（「归属历史」+「补录归属历史」）、AST-DISP-01（方案已定稿，待开工） |
| **校准/强检证书历史** | 计量执行已有时可扩证书附件 | 避免主表只留「上次/下次日期」 |

## 4. 本期落地边界

| 项 | 处理 |
|----|------|
| 缺口清单 | 本文件 + 主文档 AST-GAP-01 |
| 批量加列 | **不实施**；按优先级排入 BACKLOG |
| 配件更换 Sheet | 复用维修配件明细，见 AST-UI-21 |
| 演示数据 | 列表约 3 页（60 台）`SD2408****`，脚本 `data/seed/demo_asset_ledger.sql`（先删后插） |

## 5. 衍生提醒（未排期）

- 责任人：公共列表已映射 `use_dept_head`；若上 UUID 责任人需双写姓名快照（约定包 §6.3）。
- UDI 与注册证关系、是否强制扫码入库，需产品确认后再迁库。
- 非维修配件更换：**已定稿** AST-PART-01（独立流水、确认生效、默认不扣备件库存；与 REP-F-02 库存扣减解耦）。
