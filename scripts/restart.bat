@echo off
chcp 65001 >nul
call "%~dp0bs\restart.bat" %*
exit /b %ERRORLEVEL%