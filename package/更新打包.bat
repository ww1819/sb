@echo off
chcp 65001 >nul
cd /d "%~dp0"
title MEIS 更新打包
echo ========================================
echo   MEIS package - 更新打包
echo   仅编译「相对上次打包」有代码变更的模块
echo   产物: jars\（覆盖）+ update\（增量交付）
echo   首次请先运行「完整打包.bat」建立基线
echo ========================================
echo.

if not exist "%~dp0env.txt" (
  echo ERROR: 找不到 env.txt
  echo 请复制 env.example.txt 为 env.txt，并设置 JAVA_HOME / MAVEN_HOME。
  pause
  exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0pack-update.ps1"
set ERR=%ERRORLEVEL%
echo.
if %ERR% equ 0 (
  echo 更新打包结束。有变更时请把 package\update\ 拷给实施覆盖 jars\。
  pause
  exit /b 0
)
if %ERR% equ 2 (
  echo 请先运行「完整打包.bat」。
  pause
  exit /b 2
)
echo 更新打包失败, exit %ERR%
pause
exit /b %ERR%
