@echo off
chcp 65001 >nul
cd /d "%~dp0.."
set "MEIS_PKG_ROOT=%CD%"
title MEIS package ops
echo Starting ops helper on http://localhost:5098 ...
echo Edit env.txt for JAVA_HOME / OPS_TOKEN / DB settings.
echo.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0ops-helper.ps1"
pause
