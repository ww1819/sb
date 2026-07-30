@echo off
chcp 65001 >nul
cd /d "%~dp0"
title MEIS 完整打包
echo ========================================
echo   MEIS package - 完整打包
echo   JAR -^> jars\  +  前端 -^> www\
echo   请先配置 env.txt（JAVA_HOME / MAVEN_HOME）
echo   前端需 Node/npm；仅打 JAR 可设 SKIP_FRONTEND_BUILD=1
echo ========================================
echo.

if not exist "%~dp0env.txt" (
  echo ERROR: 找不到 env.txt
  echo 请复制 env.example.txt 为 env.txt，并设置 JAVA_HOME / MAVEN_HOME。
  pause
  exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0pack.ps1"
set ERR=%ERRORLEVEL%
echo.
if %ERR% neq 0 (
  echo 完整打包失败, exit %ERR%
  pause
  exit /b %ERR%
)
echo 完整打包成功。jars\ + www\ 已就绪，可将整个 package 拷到实施机。
pause
