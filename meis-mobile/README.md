# MEIS Mobile（Flutter）

医院设备管理移动端，方案 C 工程化结构：Riverpod + Dio + go_router。

## 功能流程

1. **连接方式选择** — 局域网 / 以太网
2. **局域网设置** — IP + 端口，测试连接，成功后自动完成设置
3. **以太网设置** — 医院全称（占位，提示开发中）
4. **登录** — 医院编码 + 用户名 + 密码
5. **首页** — 报修 / 盘点 / 保养 / 巡检 / 消息

## 首次运行

```bash
cd meis-mobile

# 若目录下没有 android/ ios/，先生成平台工程
flutter create . --project-name meis_mobile

flutter pub get
flutter run
```

## 局域网调试

| 场景 | IP 填写 |
|------|---------|
| Android 模拟器访问本机 | `10.0.2.2` |
| 真机访问电脑 | 电脑局域网 IP，如 `192.168.1.100` |
| 端口 | 默认 `8080`（Gateway） |

测试连接请求：`GET http://{ip}:{port}/api/auth/health`

## Android 允许 HTTP（局域网）

在 `android/app/src/main/AndroidManifest.xml` 的 `<application>` 标签添加：

```xml
android:usesCleartextTraffic="true"
```

## 演示账号

- 医院编码：`demo`
- 用户名：`admin`
- 密码：`admin123`

## 目录结构

```
lib/
  main.dart                 # 入口
  app.dart                  # MaterialApp
  router/app_router.dart    # 路由与启动重定向
  core/                     # 主题、常量、模型、本地存储
  features/
    setup/                  # 连接方式 + 局域网/以太网设置
    auth/                   # 登录
    home/                   # 首页与业务列表页
  shared/                   # API 服务、通用组件
```

## 依赖

- `flutter_riverpod` — 状态管理
- `dio` — 网络请求
- `go_router` — 声明式路由
- `shared_preferences` — 本地配置与登录态
