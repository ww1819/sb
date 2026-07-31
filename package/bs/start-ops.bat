@echo off
chcp 65001 >nul
cd /d "%~dp0.."
set "MEIS_PKG_ROOT=%CD%"
title MEIS package ops
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0ops-helper.ps1"
pause
