MEIS package 现场包
==================

【开发机】
1. 复制 env.example.txt → env.txt，填写 JAVA_HOME、MAVEN_HOME
2. 双击 打包.bat  → 生成 jars\
3. 把整个 package 文件夹拷到实施机（U 盘 / 共享盘）

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
