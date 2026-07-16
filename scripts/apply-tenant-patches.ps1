# Apply tenant business column patches to a schema (idempotent).
# Prefer restarting meis-tenant (Flyway R__). This applies R__columns_biz offline only.
param(
    [Parameter(Mandatory = $true)]
    [string]$Schema,
    [string]$DbHost = "localhost",
    [int]$Port = 5432,
    [string]$DbName = "meis",
    [string]$AppUser = "med",
    [string]$AppPassword = "med123456"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path $PSScriptRoot -Parent
$patchFile = Join-Path $repoRoot "meis-tenant\src\main\resources\db\migrations\tenant\R__columns_biz.sql"

if (-not (Test-Path $patchFile)) { throw "Patch file not found: $patchFile" }

function Invoke-WithJdbc {
    param([string]$Sql)
    $jar = Join-Path $env:USERPROFILE ".m2\repository\org\postgresql\postgresql\42.7.3\postgresql-42.7.3.jar"
    $runner = Join-Path $PSScriptRoot "ApplySql.java"
    if (-not (Test-Path $jar)) { throw "PostgreSQL JDBC jar not found: $jar" }
    if (-not (Test-Path $runner)) { throw "ApplySql.java not found" }
    $tmp = [System.IO.Path]::GetTempFileName() + ".sql"
    Set-Content -Path $tmp -Value $Sql -Encoding UTF8
    Push-Location $PSScriptRoot
    try {
        javac -cp $jar ApplySql.java
        java -cp ".;$jar" ApplySql $DbHost $Port $DbName $AppUser $AppPassword $Schema $tmp
    } finally {
        Pop-Location
        Remove-Item $tmp -ErrorAction SilentlyContinue
    }
}

$sql = Get-Content $patchFile -Raw -Encoding UTF8
Write-Host "Applying R__columns_biz to schema [$Schema] ..."
Invoke-WithJdbc -Sql $sql
Write-Host "Done. For R__columns_audit / R__data_fix, restart meis-tenant."
