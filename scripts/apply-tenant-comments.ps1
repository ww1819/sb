# Apply tenant comment backfill to a schema
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
$sqlFile = Join-Path $repoRoot "db\source\patches\tenant_comment_backfill.sql"
if (-not (Test-Path $sqlFile)) {
    throw "Run scripts/ExtractComments.java first, or: javac scripts/ExtractComments.java && java scripts/ExtractComments"
}

$jar = Join-Path $env:USERPROFILE ".m2\repository\org\postgresql\postgresql\42.7.3\postgresql-42.7.3.jar"
Push-Location (Join-Path $PSScriptRoot ".")
try {
    javac -cp $jar ApplySql.java ExtractComments.java 2>$null
    if (-not (Test-Path $sqlFile)) { java -cp ".;$jar" ExtractComments }
    Write-Host "Applying comment backfill to [$Schema] ..."
    java -cp ".;$jar" ApplySql $DbHost $Port $DbName $AppUser $AppPassword $Schema $sqlFile
} finally {
    Pop-Location
}
