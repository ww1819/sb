# MEIS 用户手册（骨架）

## 1. 登录

使用医院编码 + 账号 + 密码登录 Web 管理端。

## 2. 模块导航

- 采购管理：计划、项目、合同
- 资产管理：台账、入库、流转、盘点、报废
- 维修管理：工单、工程师、备件 — 详见 [repair-workorder.md](repair-workorder.md)
- 全系统需求编写与问题跟踪 — 详见 [meis-requirements.md](meis-requirements.md)
- 保养管理：模板、计划、记录
- 质控管理：风险评估、不良事件、计量、性能检测
- 维保合同：合同与履约
- 特殊设备：生命支持、应急、租赁
- 数据分析：看板与报表
- 系统管理：院区、科室、用户、角色

## 3. 移动端（Flutter 骨架）

扫码报修、盘点、保养任务 — 见 `meis-mobile/README.md`

部署文档：[local-dev-deploy.md](local-dev-deploy.md)、[production-deploy.md](production-deploy.md)

## 4. 管理员手册

租户开户：平台「租户管理」或调用 `POST /api/tenant/create`
