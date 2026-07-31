@echo off
chcp 65001 >nul
call "%~dp0bs\restore-db.bat" %*
exit /b %ERRORLEVEL%