@echo off
chcp 65001 >nul
call "%~dp0bs\start.bat" %*
exit /b %ERRORLEVEL%