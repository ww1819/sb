@echo off
chcp 65001 >nul
cd /d "%~dp0"

call "%~dp0db-config.bat"

echo [MEIS] Database backup
echo   PG_BIN_DIR=%PG_BIN_DIR%
echo   DB=%DB_NAME% @ %DB_HOST%:%DB_PORT%
echo   BACKUP_DIR=%BACKUP_DIR%
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0backup-db.ps1" ^
  -PgBinDir "%PG_BIN_DIR%" ^
  -DbHost "%DB_HOST%" ^
  -Port %DB_PORT% ^
  -DbName "%DB_NAME%" ^
  -AppUser "%APP_USER%" ^
  -AppPassword "%APP_PASSWORD%" ^
  -BackupDir "%BACKUP_DIR%" ^
  -StopServices

if errorlevel 1 (
    echo.
    echo Backup failed. Check db-config.bat and PostgreSQL service.
    pause
    exit /b 1
)

echo.
pause
