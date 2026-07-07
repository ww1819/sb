# 对指定租户 schema 执行建表脚本
param(
    [Parameter(Mandatory = $true)]
    [string]$Schema,
    [ValidateSet('org_master', 'extensions')]
    [string]$Script = 'org_master',
    [string]$PgBinDir = "",
    [string]$DbHost = "localhost",
    [int]$Port = 5432,
    [string]$DbName = "meis",
    [string]$AppUser = "med",
    [string]$AppPassword = "med123456"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path $PSScriptRoot -Parent

if (-not $PgBinDir) {
    $cfg = Join-Path $PSScriptRoot "db-config.bat"
    if (Test-Path $cfg) {
        $content = Get-Content $cfg -Raw
        if ($content -match 'PG_BIN_DIR=(.+)') { $PgBinDir = $matches[1].Trim().Trim('"') }
        if ($content -match 'DB_HOST=(.+)') { $DbHost = $matches[1].Trim().Trim('"') }
        if ($content -match 'DB_PORT=(.+)') { $Port = [int]$matches[1].Trim().Trim('"') }
        if ($content -match 'DB_NAME=(.+)') { $DbName = $matches[1].Trim().Trim('"') }
        if ($content -match 'APP_USER=(.+)') { $AppUser = $matches[1].Trim().Trim('"') }
        if ($content -match 'APP_PASSWORD=(.+)') { $AppPassword = $matches[1].Trim().Trim('"') }
    }
}
if (-not $PgBinDir) { $PgBinDir = "D:\Program Files\PostgreSQL\18\bin" }

$psql = Join-Path $PgBinDir "psql.exe"
if (-not (Test-Path $psql)) { throw "psql not found: $psql" }

if ($Schema -notmatch '^[a-zA-Z_][a-zA-Z0-9_]*$') { throw "Invalid schema name: $Schema" }

$sqlFiles = switch ($Script) {
    'extensions' { @("db/source/create/00_extensions.sql") }
    'org_master'   { @("db/source/create/00_extensions.sql", "db/source/create/org_master_tables.sql") }
}

Write-Host "=== Apply tenant create scripts ==="
Write-Host "Database: $DbName @ ${DbHost}:${Port}"
Write-Host "Schema:   $Schema"
Write-Host "Script:   $Script"
Write-Host ""

$env:PGPASSWORD = $AppPassword
try {
    & $psql -U $AppUser -h $DbHost -p $Port -d $DbName -v ON_ERROR_STOP=1 -c "CREATE SCHEMA IF NOT EXISTS $Schema;"
    & $psql -U $AppUser -h $DbHost -p $Port -d $DbName -v ON_ERROR_STOP=1 -c "SET search_path TO $Schema, public;"
    if ($LASTEXITCODE -ne 0) { throw "Failed to set search_path" }

    foreach ($rel in $sqlFiles) {
        $file = Join-Path $repoRoot $rel
        if (-not (Test-Path $file)) { throw "SQL file not found: $file" }
        Write-Host ">> $rel"
        & $psql -U $AppUser -h $DbHost -p $Port -d $DbName -v ON_ERROR_STOP=1 -c "SET search_path TO $Schema, public;" -f $file
        if ($LASTEXITCODE -ne 0) { throw "Failed applying $rel" }
    }

    Write-Host ""
    Write-Host "OK: create scripts applied to schema '$Schema'"
}
finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}
