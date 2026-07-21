# MEIS 微信小程序（UniApp）

一期（附录 MP / BACKLOG-MP-01）：扫码查询台账、扫码报修、保养 / 巡检 / PM 执行。与 App/Web **同 API、同权限**；**不做**离线盘点。

## 功能

| 页面 | 说明 |
|------|------|
| 登录 | 医院编码 + 用户名 + 密码 → `POST /api/auth/login`（缓存 permissions） |
| 首页 | 扫码查询 / 报修 / 资产列表 / 三模块运维入口 |
| 扫码查询 | 扫码或手输 → `GET /api/asset/device/by-code/{code}` → 详情 |
| 资产查询 | `GET /api/asset/device/page` |
| 扫码报修 | by-code → 创建草稿并 submit；`report_method=miniprogram`；提交后申请订阅消息 |
| 运维执行 | 保养/巡检/PM：到期/扫码/直开/检查项/拍照/**手写签名**/提交（`client=mp`） |
| 我的报修 | 进度与验收确认 |
| 订阅消息 | 登录/报修/运维完成时 `requestSubscribeMessage`；模板 ID 见 `src/config/subscribe.ts` |

## 配置公网地址

编辑 [`src/config/env.ts`](src/config/env.ts)：

```ts
export const API_BASE = 'https://your-domain.com/api'
```

订阅消息模板 ID 编辑 [`src/config/subscribe.ts`](src/config/subscribe.ts)；后端微信凭证见 `meis-notification` 的 `meis.wechat-mp.*`。

必须与微信小程序后台配置的 **request / uploadFile 合法域名** 一致。

开发阶段可在微信开发者工具勾选不校验合法域名，临时使用：

```ts
export const API_BASE = 'http://127.0.0.1:8080/api'
```

## 运行方式（推荐 HBuilderX）

1. 安装 [HBuilderX](https://www.dcloud.io/hbuilderx.html)
2. 导入本目录 `meis-mp`
3. `src/manifest.json` 填写微信 AppID
4. 运行到微信开发者工具

## 可选：CLI

```bash
cd meis-mp
npm install
npm run dev:mp-weixin
```

用微信开发者工具打开 `dist/dev/mp-weixin`。

## 后端依赖

网关需开放：`/api/auth/**`、`/api/asset/**`、`/api/repair/**`、`/api/maintain/**`、`/api/inspect/**`、`/api/pm/**`、`/api/file/upload`

## 演示账号

- 医院编码：`demo`
- 用户：`admin` / `admin123`
