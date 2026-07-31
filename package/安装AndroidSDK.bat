@echo off
chcp 65001 >nul
call "%~dp0app\安装AndroidSDK.bat" %*
exit /b %ERRORLEVEL%