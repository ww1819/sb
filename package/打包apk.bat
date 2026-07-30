@echo off
chcp 65001 >nul
cd /d "%~dp0"
title MEIS 打包 APK
echo ========================================
echo   MEIS package - 打包 Android APK
echo   源码: ..\meis-mobile
echo   产物: package\apk\
echo   可选: env.txt 设置 FLUTTER_ROOT
echo ========================================
echo.

if not exist "%~dp0env.txt" (
  echo ERROR: 找不到 env.txt
  echo 请复制 env.example.txt 为 env.txt。
  pause
  exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0pack-apk.ps1"
set ERR=%ERRORLEVEL%
echo.
if %ERR% neq 0 (
  echo 打包 APK 失败, exit %ERR%
  pause
  exit /b %ERR%
)
echo 打包 APK 成功。请到 package\apk\ 取安装包。
pause
