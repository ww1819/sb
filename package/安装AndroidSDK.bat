@echo off
chcp 65001 >nul
cd /d "%~dp0"
title MEIS 安装 Android SDK
echo ========================================
echo   安装 Android SDK (命令行工具)
echo   默认目录: %%LOCALAPPDATA%%\Android\Sdk
echo   需要: 网络 + JDK17 (env.txt JAVA_HOME)
echo   装完后可双击 打包apk.bat
echo ========================================
echo.

if not exist "%~dp0env.txt" (
  echo ERROR: 找不到 env.txt
  pause
  exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup-android-sdk.ps1"
set ERR=%ERRORLEVEL%
echo.
if %ERR% neq 0 (
  echo 安装失败, exit %ERR%
  echo 也可安装 Android Studio，再在 env.txt 设置 ANDROID_HOME。
  pause
  exit /b %ERR%
)
echo 安装成功。请再运行 打包apk.bat
pause
