@echo off
chcp 65001 >nul
call "%~dp0app\run-mobile.bat" %*
exit /b %ERRORLEVEL%