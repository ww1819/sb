@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo [MEIS] Restarting backend...
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "%~dp0restart.ps1" -Profile dev
if errorlevel 1 (
    echo.
    echo Failed. See errors above.
    pause
    exit /b 1
)
echo.
pause
