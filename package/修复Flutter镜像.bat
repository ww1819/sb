@echo off
chcp 65001 >nul
call "%~dp0app\修复Flutter镜像.bat" %*
exit /b %ERRORLEVEL%