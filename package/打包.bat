@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo [提示] 「打包.bat」已更名为「完整打包.bat」，正在转调...
echo.
call "%~dp0完整打包.bat"
