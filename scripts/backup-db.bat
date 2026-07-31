@echo off
chcp 65001 >nul
call "%~dp0bs\backup-db.bat" %*
exit /b %ERRORLEVEL%