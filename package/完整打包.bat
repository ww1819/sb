@echo off
chcp 65001 >nul
call "%~dp0bs\完整打包.bat" %*
exit /b %ERRORLEVEL%