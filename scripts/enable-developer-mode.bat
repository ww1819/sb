@echo off
chcp 65001 >nul
call "%~dp0app\enable-developer-mode.bat" %*
exit /b %ERRORLEVEL%