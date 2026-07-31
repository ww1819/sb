@echo off
chcp 65001 >nul
call "%~dp0bs\启动运维.bat" %*
exit /b %ERRORLEVEL%