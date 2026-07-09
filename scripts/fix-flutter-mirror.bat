@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo [MEIS] Fix Flutter mirror environment variables
echo.
echo Current values may contain extra quotes, e.g. "https://storage.flutter-io.cn"
echo Flutter requires values WITHOUT quotes.
echo.

REM Fix current terminal session immediately
set "PUB_HOSTED_URL=https://pub.flutter-io.cn"
set "FLUTTER_STORAGE_BASE_URL=https://storage.flutter-io.cn"

REM Fix user-level env permanently (no quotes in value)
setx PUB_HOSTED_URL https://pub.flutter-io.cn >nul
setx FLUTTER_STORAGE_BASE_URL https://storage.flutter-io.cn >nul

echo OK - session and user env updated:
echo   PUB_HOSTED_URL=%PUB_HOSTED_URL%
echo   FLUTTER_STORAGE_BASE_URL=%FLUTTER_STORAGE_BASE_URL%
echo.
echo Please CLOSE and REOPEN Cursor/terminal, then run:
echo   scripts\setup-mobile.bat
echo   scripts\run-mobile.bat
echo.
pause
