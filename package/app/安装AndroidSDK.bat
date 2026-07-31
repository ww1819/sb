@echo off
chcp 65001 >nul
cd /d "%~dp0.."
set "MEIS_PKG_ROOT=%CD%"
title MEIS 安装 Android SDK
echo ========================================
echo   安装 Android SDK (命令行工具)
echo   默认目录: env.txt 的 ANDROID_HOME
echo            或 %%LOCALAPPDATA%%\Android\Sdk
echo   需要: 网络 + JDK17 (env.txt JAVA_HOME)
echo   说明: 优先走腾讯云等国内镜像；Google 超时可换镜像
echo   离线: 先下 commandlinetools-win-*_latest.zip，再执行
echo     powershell -File setup-android-sdk.ps1 -ZipPath 路径\xxx.zip
echo   或: 用已安装的 Android Studio -^> SDK Manager 装到同一目录
echo   装完后可双击 打包apk.bat
echo ========================================
echo.

if not exist "%MEIS_PKG_ROOT%\env.txt" (
  echo ERROR: 找不到 env.txt
  pause
  exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup-android-sdk.ps1"
set ERR=%ERRORLEVEL%
echo.
if %ERR% neq 0 (
  echo 安装失败, exit %ERR%
  echo 也可打开 Android Studio -^> SDK Manager，安装到 env.txt 的 ANDROID_HOME。
  echo 或手动下载 zip 后: powershell -File setup-android-sdk.ps1 -ZipPath 本地zip路径
  pause
  exit /b %ERR%
)
echo 安装成功。请再运行 打包apk.bat
pause
