@echo off
chcp 65001 >nul
call "%~dp0bs\restart-build.bat" %*
exit /b %ERRORLEVEL%