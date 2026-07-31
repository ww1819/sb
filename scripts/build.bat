@echo off
chcp 65001 >nul
call "%~dp0bs\build.bat" %*
exit /b %ERRORLEVEL%