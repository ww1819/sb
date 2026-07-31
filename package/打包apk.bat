@echo off
chcp 65001 >nul
call "%~dp0app\打包apk.bat" %*
exit /b %ERRORLEVEL%