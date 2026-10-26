# 资产台账字段缺口分析（三甲 / 国际高标准）

> 对应需求草稿：对照三甲建设与国际高标准，列出 `medical_device` 及宜用从表承载的能力缺口。  
> **状态**：分析定稿（2026-08-01）；**本期不批量加列**，实施项进 `BACKLOG-AST-GAP-*`。  
> **三甲再审（推荐阅读）**：[`meis-requirements-tertiary-gap-review.md`](meis-requirements-tertiary-gap-review.md)（标量 / 从表对象 / 流程，已对照 OWN/LOC/PART 等已实现项）。  
> **主文档（SSOT）**：[`meis-requirements.md`](meis-requirements.md) → AST-GAP-01 / AST-GAP-REVIEW-01 / **AST-ARCH-01·02** / **PLT-CAM-03·04** / **AST-SNJ-01** / 第 7 章 BACKLOG。  
> **约定**：本文件仅作分析附记；**定稿、排期、后续需求与修改一律写入主文档**，勿只改本稿。

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

## 6. 设备档案 / 高拍仪增强（已收口 → AST-ARCH-01 / PLT-CAM-03）

对照神农架台账档案与高拍仪，用户确认：

1. 复制：物理复制（MinIO 新 object）
2. 旋转：只存角度，CSS 显示
3. 自定义排序：按档案类型分组内排
4. 字典对齐：扩展 `device_archive_type` 为合格证/说明书/技术文档/图片/其他资料，去掉 remark 中文 hack
5. 高拍仪横纵向：成片 canvas 旋转
6. P0+P1 同批落地

正式定稿见主文档 **AST-ARCH-01**、**PLT-CAM-03**。

> 成片横纵向（canvas JPEG）：会有轻微重编码损失；若极敏感可改为只存角度+CSS、不重压成片。

## 7. 神农架对照：仍值得 MEIS 补强的项（供自研排期）

> 对照：`cestar_shenNongJia` 台账设备档案 Tab（`sb.js` / `sb_detail.jsp`）、`SbWdAction` / `SbWdMngImp`、`eloam/gaopaiyi.js`。  
> **已排除** AST-ARCH-01 / PLT-CAM-01～03 已落地项（物理复制、CSS 旋转角、类型内排序、改名改类、字典对齐、成片横纵向、纠偏、厂家表等）。  
> 建议后续可开 **AST-ARCH-02** / **PLT-CAM-04** 或记入第 7 章 `BACKLOG-AST-ARCH-*`。

### 7.1 建议补强清单

| 优先级 | 能力项 | 神农架做法 | MEIS 现状 | 建议 |
|--------|--------|------------|-----------|------|
| **P0** | PDF / 非图片在线预览 | 双击 PDF iframe 全屏；`viewFile.do` inline | 图片侧栏预览；PDF 仅提示下载 | 侧栏/弹层 PDF iframe；Office 先新窗/下载 |
| **P0** | 合同附件只读聚合 | 列表并入 `ht_` 前缀合同图，禁改名/旋转/复制/删 | 仅 `device_archive_file`；合同有 `contract_file_url` 等但未挂档案 Tab | 有关联合同数据时只读展示 +「合同来源」；操作禁用 |
| **P1** | 批量修改档案类型 | `updateCategory.do` 多 ID | 仅单行改类 | 多选 + batch-reclassify；改类后排序归入新类型 |
| **P1** | 复制到多台目标设备 | 设备表多选 checkbox 一次 `copyTo` | 后端已支持 `target_device_ids[]`；UI 单选 | 复制弹窗改多选 |
| **P1** | 排序管理 UX | 「排序调整」层：拖拽/序号/可改显示名 | 仅单选上移下移 | 排序抽屉：拖拽或序号；可选同步改名 |
| **P1** | 预览增强 | 左/右旋、还原、保存角、缩放、滚轮、大图层 | 每次点旋转即写库；无缩放/还原 | 预览临时角 +「保存」；补 zoom/全屏 |
| **P1** | 新窗预览带旋转角 | 预览链路带角 | `window.open` 原图，无角 | 预览页传角或 blob 渲染 |
| **P1** | 列表缩略图 | `viewThumb.do` 最长边 400 | 纯表格无缩略图 | 文件/档案 thumb API + 列表/卡片 |
| **P1** | 卡片/网格视图 | dataView 四列；按扩展名封面 | 仅 el-table | 表格/卡片切换 |
| **P1** | 规范下载 | `download.do` 带展示名 attachment | 多次 `window.open` | 走 Content-Disposition；可选 zip 批量 |
| **P1** | 高拍仪：闪光灯 / 空格连拍 / 成片命名 / 左右旋 | `SetLed`、空格、缩略图可改名、↺↻ | 有纠偏与横纵向；无闪光/空格/命名 | `useEloamCamera` 增 setLed；对话框空格；缩略图命名与 L/R |
| **P1** | 高拍仪：进窗自动开预览 + 断线重连 | connect 后自动开相机；2s 重连 | 需手动「打开预览」 | 档案场景可自动开；调试页保持手动 |
| **P2** | 档案列表分页 | list 默认 limit=8 | `by-device` 全量 | 极大附件量时接已有 `/page` |
| **P2** | 排序偏好记忆 | localStorage | 每次默认 custom | 记住 sort 模式 |
| **P2** | 改名主名/扩展名分离 | 只改 baseName | prompt 整段名 | 名称 + 只读扩展名 |
| **P2** | 上传自动命名规则 | 条码-类型-序号 | 原文件名/URL 截断 | 设备编码+类型+序号（按院方规范） |
| **P2** | 批量删除单次 API | `batchDelete.do` | 循环 DELETE | 可选 batch-delete |
| **P2** | 高拍仪分辨率/设备索引 UI | 内部有 GetResolution | composable 有字段 UI 未暴露 | 调试页下拉 |
| **P2** | Word 转 PDF 预览 | 后端转换 + iframe | 无 | BACKLOG，依赖转换服务 |
| **P2** | 路径异常标记 | 非本设备磁盘路径提示 | MinIO 场景少 | 复制失败明确错误即可 |

### 7.2 明确不做 / 低价值（对照神农架或已定稿排除）

| 项 | 说明 |
|----|------|
| 身份证 / 人脸 / 读卡 | 神农架生产 `gaopaiyi.js` 未做；PLT-CAM-01 不做 |
| 高拍仪 PDF 合成 / 打印 / 水印 | PLT-CAM-01 不做 |
| 设备端方向 API / 改预览流分辨率 | PLT-CAM-03 已明确不做 |
| 合同附件可写操作 | 神农架对 `ht_` 只读；MEIS 若聚合须同样只读 |

### 7.3 推荐下一迭代（自研）

1. **P0**：PDF 内嵌预览；合同附件只读挂接 — **已落地 AST-ARCH-02**（关联：`medical_device.contract_id` → 采购合同扫描件/验收报告）。  
2. **P1**：批量改类、多设备复制 UI、排序抽屉、预览缩放/保存角、规范下载、高拍仪闪光灯+空格+成片命名 — **已落地 AST-ARCH-02 / PLT-CAM-04**。  
3. **P2**：分页、命名规范、分辨率选择、Word→PDF、卡片/缩略图 — **`BACKLOG-AST-ARCH-02`**。

参考路径：神农架 `SbWdAction.java`、`SbWdMngImp.java`、`erps/zcgl/zczj/sb/sb.js`、`sb_detail.jsp`、`erps/common/eloam/gaopaiyi.js`；MEIS `DeviceArchivePanel.vue`、`DeviceArchiveFileController.java`、`EloamCaptureDialog.vue`。

## 8. 神农架设备管理其他模块对照（附记）

> **正式定稿与 BACKLOG 见主文档 [AST-SNJ-01](meis-requirements.md#ast-snj-01-神农架设备管理对照后续池定稿2026-10-15)**；下表为分析附记，修改时请同步主文档。  
> 对照 `erps/zcgl`、`jczl/zd/sbda`、盘点/借用/报废/标签/特种/效益等；**不含**已收口档案/高拍仪。  
> MEIS 已领先项（勿重复当 gap）：台账 Tab/Sheet、归属/位置、非维修配件、处置聚合、维保包、盘点扫码主路径、借调主流程、培训授权 Tab、电流标签运维。

| 优先级 | 模块/能力 | 神农架要点 | MEIS 现状 | 建议 / BACKLOG |
|--------|-----------|------------|-----------|----------------|
| **P0** | 台账字段与三甲对象 | 台账字段极全；大模板导入 | GAP/复核未全落地 | `BACKLOG-AST-GAP-01` 分批 |
| **P0** | 盘点盈亏闭环 | `sbykcl`/`sbykbb` 盈亏处理+审核打印 | 完成盘点仅统计 diff，写回另议 | `BACKLOG-AST-YK-01` |
| **P0** | 金蝶/外部资产同步 | 台账/报废多路 HTTP 同步 | `kingdee_asset_code` 只读；DISP-FIN 预留 | `BACKLOG-AST-KD-01` / DISP-FIN |
| **P0** | 效益分析闭环 | xyfx 上报/对照/审核/报表 | sync/菜单占位多 | `BACKLOG-AST-BEN-01`；与 ANA-* 合并排期 |
| **P0** | 资产动态统计 | `zcdttj` | 占位页 | `BACKLOG-AST-08` |
| **P1** | 报废子状态粒度 | 待报废/已报废使用中等 | 设备态较粗 | 评估子状态 vs 报废单口径 |
| **P1** | 科室调拨 vs 库房调拨 | `ksdbgl` | 库房调拨+改科室真变更 | 产品定是否独立科间调拨单 |
| **P1** | 借用费用 | `jyfyinit` | 借还有、费用无 | `BACKLOG-AST-JYFY-01` |
| **P1** | RFID/定位监测 | `dzbqdtjc`+大屏 | 电流标签有；无 EPC/定位大屏 | `BACKLOG-AST-RFID-01` |
| **P1** | 特种设备人员 | `tzsb` 人员管理 | 登记有；人员主从未齐 | `BACKLOG-AST-SPC-RY-01` |
| **P1** | 设备验收入口 | `sbys` 独立页 | 采购验收链为主 | 按院方是否绕开采购定 |
| **P1** | 不良事件监管同步 | `ws_blsj` | QC CRUD 有 | `BACKLOG-AST-AE-SYNC-01` |
| **P1** | 铭牌 OCR | — | PLT-OCR-01 | `BACKLOG-AST-11` |
| **P2** | 档案主数据 sbda | jczl 独立主数据页 | 字典+分类足够 | 一般不复刻 |
| **P2** | 资产增减明细查询 | `sbflow`/`zczjmx` | 出入库分散 | 一条查询报表即可 |
| **P2** | 折旧计提动作 | `sbzj` | 偏统计报表 | 与金蝶一并设计 |
| **P2** | 按钮级权限 | `sbbtn` | 菜单 RBAC | 强需求再开 |

**推荐排期**：GAP 字段 → 盈亏闭环 → 外部同步 → 效益最小闭环 + 动态统计；RFID/特种人员/不良同步按现场选型。
