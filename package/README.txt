MEIS package 现场包
==================

【开发机 — 打包】

1. 复制 env.example.txt → env.txt，填写 JAVA_HOME、MAVEN_HOME
   （前端生产构建还需本机已装 Node.js，npm 在 PATH；或设 NODE_HOME）
2. 首次 / 全量：双击 完整打包.bat
   → 编译全部模块，写入 jars\，建立指纹基线
   → 同时构建 meis-web → www\（生产静态页）
   仅打 JAR：env.txt 设 SKIP_FRONTEND_BUILD=1，或 pack.ps1 -SkipFrontend
3. 日常有改动：双击 更新打包.bat
   → 只编译「相对上次打包」源码有变更的模块
   → 覆盖 jars\ 中对应 JAR
   → 另输出 package\update\（仅变更 JAR，给实施增量覆盖）
   → 不重打前端；前端有改请再跑完整打包（或去掉 SKIP_FRONTEND）
4. App APK：双击 打包apk.bat
   → 构建 meis-mobile release APK，拷到 package\apk\
   → 需本机 Flutter；env.txt 可设 FLUTTER_ROOT（如 E:\flutter）
   → 需 Android SDK；没有则先双击 安装AndroidSDK.bat（或装 Android Studio 后设 ANDROID_HOME）
   → 国内网络：安装脚本优先腾讯镜像 + curl；Google 超时可用 -ZipPath 离线 zip

说明：
- meis-common / meis-api / 根 pom 有变更时，更新打包会重打全部业务 JAR
- 无指纹时必须先完整打包（或 pack-update.ps1 -ForceAll）
- 旧名 打包.bat 会转调 完整打包.bat
- 首次打 APK 前需已有 meis-mobile\android（可先跑 scripts\setup-mobile.bat）
- 打 APK 前需 Android SDK（安装AndroidSDK.bat 或 Android Studio）

【交付实施】

- 首次：拷贝整个 package 文件夹（含 jars\ + www\）
- 增量（仅后端）：只拷贝 package\update\ 内 *.jar，覆盖实施机 package\jars\
- 增量（含前端）：再拷 www\，或整包覆盖

【实施机】

1. 先装好：JDK 17、PostgreSQL、Redis/Memurai、MinIO（API 建议 9100）
2. 改 env.txt：JAVA_HOME、POSTGRES_*、MINIO_*、OPS_TOKEN
3. 双击 启动运维.bat（或 start-ops.bat）
4. 浏览器打开 http://localhost:5098/
5. 齐套检查 → 填口令 → 启动核心 / 启动全部
6. 前端：Nginx root 指向 package\www（或拷到 D:\meis\www），/api 反代网关 :8080

【注意】
- 不要直接双击 index.html（无法启动 Java）
- 不要把 5098 暴露到院内网 / 公网
- 完整步骤见：docs/windows-production-deploy.md  「〇、实施环境部署流程」
