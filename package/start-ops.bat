@echo off
chcp 65001 >nul
call "%~dp0bs\start-ops.bat" %*
exit /b %ERRORLEVEL%