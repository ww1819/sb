@echo off
chcp 65001 >nul
call "%~dp0app\fix-flutter-mirror.bat" %*
exit /b %ERRORLEVEL%