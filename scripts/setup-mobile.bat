@echo off
chcp 65001 >nul
cd /d "%~dp0"

REM Override mirror vars for this session (no quotes)
set "PUB_HOSTED_URL=https://pub.flutter-io.cn"
set "FLUTTER_STORAGE_BASE_URL=https://storage.flutter-io.cn"

echo [MEIS] Flutter mobile - first-time setup
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup-mobile.ps1"
if errorlevel 1 (
    echo Setup failed.
    pause
    exit /b 1
)
echo.
pause
