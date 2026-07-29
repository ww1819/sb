@echo off
chcp 65001 >nul
cd /d "%~dp0"
title MEIS package build
echo ========================================
echo   MEIS package - build JARs
echo   Edit env.txt for JAVA_HOME / MAVEN_HOME
echo ========================================
echo.

if not exist "%~dp0env.txt" (
  echo ERROR: env.txt not found.
  echo Copy env.example.txt to env.txt and set JAVA_HOME / MAVEN_HOME.
  pause
  exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0pack.ps1"
set ERR=%ERRORLEVEL%
echo.
if %ERR% neq 0 (
  echo Build failed, exit %ERR%
  pause
  exit /b %ERR%
)
echo Build OK. Copy the whole package folder to the field PC.
pause
