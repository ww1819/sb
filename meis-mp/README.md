# MEIS 微信小程序（UniApp）

精简端：登录、资产查询、扫码报修。仅走公网网关，无内外网切换。

## 功能

| 页面 | 说明 |
|------|------|
| 登录 | 医院编码 + 用户名 + 密码 → `POST /api/auth/login` |
| 首页 | 资产查询 / 扫码报修 两个入口 |
| 资产查询 | `GET /api/asset/device/page`，点进详情 |
| 扫码报修 | 扫码或手输编码 → `GET /api/asset/device/by-code/{code}` → 创建草稿并 `submit` |

## 配置公网地址

编辑 [`src/config/env.ts`](src/config/env.ts)：

```ts
export const API_BASE = 'https://your-domain.com/api'
```

必须与微信小程序后台配置的 **request 合法域名** 一致（仅 HTTPS，不含路径 `/api` 时以微信文档为准——域名填主机名，完整请求仍带 `/api` 前缀）。

开发阶段可在微信开发者工具勾选：**详情 → 本地设置 → 不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书**，临时使用：

```ts
export const API_BASE = 'http://127.0.0.1:8080/api'
```

真机调试请用内网穿透（ngrok / frp 等）或测试 HTTPS 域名。

## 运行方式（推荐 HBuilderX）

1. 安装 [HBuilderX](https://www.dcloud.io/hbuilderx.html)
2. 文件 → 导入 → 选择本目录 `meis-mp`
3. 打开 `src/manifest.json` → 微信小程序配置 → 填写测试/正式 **AppID**（可先用测试号）
4. 运行 → 运行到小程序模拟器 → 微信开发者工具
5. 确保本机已安装并登录 [微信开发者工具](https://developers.weixin.qq.com/miniprogram/dev/devtools/download.html)

## 可选：CLI（Vite）

需 Node 18+：

```bash
cd meis-mp
npm install
npm run dev:mp-weixin
```

用微信开发者工具打开输出目录 `dist/dev/mp-weixin`。

> `@dcloudio/*` 版本号会随官方发版变化；若 `npm install` 失败，优先用 HBuilderX 打开本工程编译。

## 后端依赖

- 网关公网可访问：`/api/auth/**`、`/api/asset/**`、`/api/repair/**`
- 已提供扫码专用接口：`GET /api/asset/device/by-code/{deviceCode}`（精确匹配设备编码）
- 重启 **meis-asset** 后生效

## 演示账号（与 Web 一致）

- 医院编码：`demo`
- 用户：`admin` / `admin123`

## 报修说明

- 二维码内容为 **设备编码**（与台账标签一致）
- `report_method` 固定为 `miniprogram`
- 设备处于维修中/报废或已有进行中工单时，接口返回 `can_report=false`，页面禁止提交
