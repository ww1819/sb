# MEIS PostgreSQL backup (full database: public + all tenant_* schemas)
param(
    [string]$PgBinDir = "E:\PGSQL\bin",
    [string]$DbHost = "localhost",
    [int]$Port = 5432,
    [string]$DbName = "meis",
    [string]$AppUser = "med",
    [string]$AppPassword = "med123456",
    [string]$BackupDir = "E:\backup\meis",
    [ValidateSet("c", "p")]
    [string]$Format = "c",
    [switch]$StopServices
)

$ErrorActionPreference = "Stop"

$pgDump = Join-Path $PgBinDir "pg_dump.exe"
if (-not (Test-Path $pgDump)) {
    throw "pg_dump not found: $pgDump`nPlease set PG_BIN_DIR in scripts\db-config.bat"
}

New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null

$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$ext = if ($Format -eq "c") { "dump" } else { "sql" }
$outFile = Join-Path $BackupDir "${DbName}_${stamp}.${ext}"

if ($StopServices) {
    Write-Host "=== Stopping MEIS backend ==="
    & "$PSScriptRoot\stop.ps1"
    Start-Sleep -Seconds 2
}

Write-Host "=== Backing up database '$DbName' ==="
Write-Host "Host: ${DbHost}:${Port}"
Write-Host "Output: $outFile"

$env:PGPASSWORD = $AppPassword
try {
    $args = @(
        "-U", $AppUser,
        "-h", $DbHost,
        "-p", $Port,
        "-d", $DbName,
        "-F", $Format,
        "-f", $outFile,
        "-v"
    )
    & $pgDump @args
    if ($LASTEXITCODE -ne 0) { throw "pg_dump failed with exit code $LASTEXITCODE" }
}
finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}

$sizeMb = [math]::Round((Get-Item $outFile).Length / 1MB, 2)
Write-Host "=== Backup complete ==="
Write-Host "File: $outFile"
Write-Host "Size: ${sizeMb} MB"
