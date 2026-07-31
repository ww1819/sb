@echo off
chcp 65001 >nul
call "%~dp0bs\更新打包.bat" %*
exit /b %ERRORLEVEL%