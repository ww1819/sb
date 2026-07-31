# MEIS PostgreSQL restore (drops and recreates target database)
param(
    [string]$PgBinDir = "E:\PGSQL\bin",
    [string]$DbHost = "localhost",
    [int]$Port = 5432,
    [string]$DbName = "meis",
    [string]$AppUser = "med",
    [string]$AppPassword = "med123456",
    [string]$SuperUser = "postgres",
    [string]$SuperPassword = "aspt",
    [string]$BackupFile = "",
    [string]$BackupDir = "E:\backup\meis",
    [switch]$StopServices = $true,
    [switch]$Force
)

$ErrorActionPreference = "Stop"

$psql = Join-Path $PgBinDir "psql.exe"
$pgRestore = Join-Path $PgBinDir "pg_restore.exe"
if (-not (Test-Path $psql)) {
    throw "psql not found: $psql`nPlease set PG_BIN_DIR in scripts\db-config.bat"
}

function Invoke-Psql {
    param(
        [string]$Sql,
        [string]$Database = "postgres",
        [string]$User,
        [string]$Password
    )
    $env:PGPASSWORD = $Password
    try {
        & $psql -U $User -h $DbHost -p $Port -d $Database -v ON_ERROR_STOP=1 -c $Sql
        if ($LASTEXITCODE -ne 0) { throw "psql failed" }
    }
    finally {
        Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    }
}

function Resolve-BackupFile {
    if ($BackupFile -and (Test-Path $BackupFile)) {
        return (Resolve-Path $BackupFile).Path
    }
    if (-not (Test-Path $BackupDir)) {
        throw "Backup directory not found: $BackupDir"
    }
    $latest = Get-ChildItem $BackupDir -File |
        Where-Object { $_.Extension -in ".dump", ".sql", ".backup" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $latest) {
        throw "No backup file found in $BackupDir (expected .dump / .sql)"
    }
    return $latest.FullName
}

$backupPath = Resolve-BackupFile
$isSql = $backupPath.ToLower().EndsWith(".sql")

if (-not $isSql -and -not (Test-Path $pgRestore)) {
    throw "pg_restore not found: $pgRestore"
}

Write-Host "=== MEIS database restore ==="
Write-Host "Target database: $DbName @ ${DbHost}:${Port}"
Write-Host "Backup file: $backupPath"
Write-Host ""
Write-Host "WARNING: This will DROP and recreate database '$DbName'. All current data will be lost."

if (-not $Force) {
    $answer = Read-Host "Type YES to continue"
    if ($answer -ne "YES") {
        Write-Host "Restore cancelled."
        exit 0
    }
}

if ($StopServices) {
    Write-Host "=== Stopping MEIS backend ==="
    & "$PSScriptRoot\stop.ps1"
    Start-Sleep -Seconds 2
}

Write-Host "=== Terminating active connections ==="
Invoke-Psql -Sql @"
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = '$DbName' AND pid <> pg_backend_pid();
"@ -User $SuperUser -Password $SuperPassword

Write-Host "=== Recreating database ==="
Invoke-Psql -Sql "DROP DATABASE IF EXISTS $DbName;" -User $SuperUser -Password $SuperPassword
Invoke-Psql -Sql "CREATE DATABASE $DbName OWNER $AppUser ENCODING 'UTF8';" -User $SuperUser -Password $SuperPassword

Write-Host "=== Restoring data ==="
$env:PGPASSWORD = $AppPassword
try {
    if ($isSql) {
        & $psql -U $AppUser -h $DbHost -p $Port -d $DbName -v ON_ERROR_STOP=1 -f $backupPath
        if ($LASTEXITCODE -ne 0) { throw "psql restore failed with exit code $LASTEXITCODE" }
    }
    else {
        & $pgRestore -U $AppUser -h $DbHost -p $Port -d $DbName --no-owner --no-acl -v $backupPath
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "pg_restore exit code $LASTEXITCODE (some warnings may be harmless)"
        }
    }
}
finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}

Write-Host "=== Verifying restore ==="
$env:PGPASSWORD = $AppPassword
try {
    & $psql -U $AppUser -h $DbHost -p $Port -d $DbName -c "SELECT tenant_code, schema_name FROM public.sys_tenant LIMIT 5;"
    if ($LASTEXITCODE -ne 0) { throw "Verification query failed" }
}
finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}

Write-Host "=== Restore complete ==="
Write-Host "Run scripts\restart.ps1 to start backend services."
