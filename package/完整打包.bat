@echo off
chcp 65001 >nul
cd /d "%~dp0"
title MEIS 完整打包
echo ========================================
echo   MEIS package - 完整打包
echo   编译并收集全部 JAR -^> jars\
echo   请先配置 env.txt（JAVA_HOME / MAVEN_HOME）
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
echo 完整打包成功。可将整个 package 文件夹拷到实施机。
pause
