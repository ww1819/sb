@echo off
REM ========== MEIS 数据库连接配置（按需修改）==========
REM PostgreSQL 安装目录下的 bin 文件夹（不要带末尾反斜杠）
set "PG_BIN_DIR=D:\Program Files\PostgreSQL\18\bin"

set "DB_HOST=localhost"
set "DB_PORT=5432"
set "DB_NAME=meis"

REM 应用账号（日常备份用，需对 meis 库有读权限）
set "APP_USER=med"
set "APP_PASSWORD=med123456"

REM 超级用户（仅还原时删库/建库需要）
set "SUPER_USER=postgres"
set "SUPER_PASSWORD=aspt"

REM 备份文件存放目录
set "BACKUP_DIR=D:\DBBACKUP\meis"
REM =====================================================
