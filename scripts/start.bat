@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo [MEIS] Starting backend...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1" -Profile dev
if errorlevel 1 (
    echo.
    echo Failed. See errors above.
    pause
    exit /b 1
)
echo.
pause
