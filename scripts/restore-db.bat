@echo off
chcp 65001 >nul
cd /d "%~dp0"

call "%~dp0db-config.bat"

REM 指定要还原的备份文件；留空则自动使用 BACKUP_DIR 下最新的 .dump / .sql
set "BACKUP_FILE="

echo [MEIS] Database restore
echo   PG_BIN_DIR=%PG_BIN_DIR%
echo   DB=%DB_NAME% @ %DB_HOST%:%DB_PORT%
if defined BACKUP_FILE (
    echo   BACKUP_FILE=%BACKUP_FILE%
) else (
    echo   BACKUP_FILE=(latest in %BACKUP_DIR%)
)
echo.

if defined BACKUP_FILE (
    "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "%~dp0restore-db.ps1" ^
      -PgBinDir "%PG_BIN_DIR%" ^
      -DbHost "%DB_HOST%" ^
      -Port %DB_PORT% ^
      -DbName "%DB_NAME%" ^
      -AppUser "%APP_USER%" ^
      -AppPassword "%APP_PASSWORD%" ^
      -SuperUser "%SUPER_USER%" ^
      -SuperPassword "%SUPER_PASSWORD%" ^
      -BackupDir "%BACKUP_DIR%" ^
      -BackupFile "%BACKUP_FILE%"
) else (
    "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "%~dp0restore-db.ps1" ^
      -PgBinDir "%PG_BIN_DIR%" ^
      -DbHost "%DB_HOST%" ^
      -Port %DB_PORT% ^
      -DbName "%DB_NAME%" ^
      -AppUser "%APP_USER%" ^
      -AppPassword "%APP_PASSWORD%" ^
      -SuperUser "%SUPER_USER%" ^
      -SuperPassword "%SUPER_PASSWORD%" ^
      -BackupDir "%BACKUP_DIR%"
)

if errorlevel 1 (
    echo.
    echo Restore failed. Check db-config.bat and backup file.
    pause
    exit /b 1
)

echo.
pause
