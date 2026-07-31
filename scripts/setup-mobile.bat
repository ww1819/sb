@echo off
chcp 65001 >nul
call "%~dp0app\setup-mobile.bat" %*
exit /b %ERRORLEVEL%