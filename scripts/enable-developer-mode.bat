@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo [MEIS] Enable Windows Developer Mode for Flutter
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -Command ^
  ". '%~dp0ensure-developer-mode.ps1'; Ensure-DeveloperMode"
if errorlevel 1 (
    echo Failed.
    pause
    exit /b 1
)
echo.
echo OK.
pause
