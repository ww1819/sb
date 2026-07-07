# 对指定租户 schema 执行补充字段脚本（幂等）
param(
    [Parameter(Mandatory = $true)]
    [string]$Schema,
    [ValidateSet('all', 'org_master')]
    [string]$Patch = 'all',
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

$patchFiles = switch ($Patch) {
    'org_master' { @("db/source/patches/org_master_column_patches.sql") }
    default      { @("db/source/patches/tenant_column_patches.sql") }
}

Write-Host "=== Apply tenant column patches ==="
Write-Host "Database: $DbName @ ${DbHost}:${Port}"
Write-Host "Schema:   $Schema"
Write-Host "Patch:    $Patch"
Write-Host ""

$env:PGPASSWORD = $AppPassword
try {
    & $psql -U $AppUser -h $DbHost -p $Port -d $DbName -v ON_ERROR_STOP=1 -c "SET search_path TO $Schema, public;"
    if ($LASTEXITCODE -ne 0) { throw "Failed to set search_path" }

    foreach ($rel in $patchFiles) {
        $file = Join-Path $repoRoot $rel
        if (-not (Test-Path $file)) { throw "Patch file not found: $file" }
        Write-Host ">> $rel"
        & $psql -U $AppUser -h $DbHost -p $Port -d $DbName -v ON_ERROR_STOP=1 -c "SET search_path TO $Schema, public;" -f $file
        if ($LASTEXITCODE -ne 0) { throw "Failed applying $rel" }
    }

    Write-Host ""
    Write-Host "OK: patches applied to schema '$Schema'"
}
finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}
