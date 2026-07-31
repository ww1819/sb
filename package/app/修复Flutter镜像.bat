@echo off
chcp 65001 >nul
cd /d "%~dp0.."
set "MEIS_PKG_ROOT=%CD%"
title 修复 Flutter 镜像环境变量
echo 去掉 PUB_HOSTED_URL / FLUTTER_STORAGE_BASE_URL 里多余的引号
echo.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0fix-flutter-mirrors.ps1"
echo.
pause
