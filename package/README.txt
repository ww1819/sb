MEIS package 现场包
==================

【开发机 — 打包】

1. 复制 env.example.txt → env.txt，填写 JAVA_HOME、MAVEN_HOME
2. 首次 / 全量：双击 完整打包.bat
   → 编译全部模块，写入 jars\，建立指纹基线
3. 日常有改动：双击 更新打包.bat
   → 只编译「相对上次打包」源码有变更的模块
   → 覆盖 jars\ 中对应 JAR
   → 另输出 package\update\（仅变更 JAR，给实施增量覆盖）

说明：
- meis-common / meis-api / 根 pom 有变更时，更新打包会重打全部业务 JAR
- 无指纹时必须先完整打包（或 pack-update.ps1 -ForceAll）
- 旧名 打包.bat 会转调 完整打包.bat

【交付实施】

- 首次：拷贝整个 package 文件夹
- 增量：只拷贝 package\update\ 内 *.jar，覆盖实施机 package\jars\

【实施机】

1. 先装好：JDK 17、PostgreSQL、Redis/Memurai、MinIO（API 建议 9100）
2. 改 env.txt：JAVA_HOME、POSTGRES_*、MINIO_*、OPS_TOKEN
3. 双击 启动运维.bat（或 start-ops.bat）
4. 浏览器打开 http://localhost:5098/
5. 齐套检查 → 填口令 → 启动核心 / 启动全部

【注意】
- 不要直接双击 index.html（无法启动 Java）
- 不要把 5098 暴露到院内网 / 公网
- 完整步骤见：docs/windows-production-deploy.md  「〇、实施环境部署流程」
