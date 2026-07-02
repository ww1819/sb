# 创建 PostgreSQL 空库 + 应用账号
param(
    [string]$PostgresPassword = "aspt",
    [string]$PgBin = "E:\PGSQL\bin\psql.exe",
    [string]$SuperUser = "postgres",
    [string]$DbHost = "localhost",
    [int]$Port = 5432,
    [string]$DbName = "sb",
    [string]$AppUser = "med",
    [string]$AppPassword = "med123456"
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path $PgBin)) { throw "psql not found: $PgBin" }

if (-not $PostgresPassword) {
    $sec = Read-Host "PostgreSQL superuser ($SuperUser) password" -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec)
    $PostgresPassword = [Runtime.InteropServices.Marshal]::PtrToStringAuto($ptr)
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
}

$env:PGPASSWORD = $PostgresPassword

function Invoke-Psql {
    param([string]$Sql, [string]$Database = "postgres", [bool]$AllowFail = $false)
    & $PgBin -U $SuperUser -h $DbHost -p $Port -d $Database -v ON_ERROR_STOP=1 -c $Sql
    if (-not $AllowFail -and $LASTEXITCODE -ne 0) { throw "psql failed" }
}

Invoke-Psql "CREATE ROLE $AppUser LOGIN PASSWORD '$AppPassword';" -AllowFail $true
Invoke-Psql "ALTER ROLE $AppUser WITH PASSWORD '$AppPassword';" -AllowFail $true

$dbExists = & $PgBin -U $SuperUser -h $DbHost -p $Port -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$DbName'" 2>$null
if ($dbExists -match "1") {
    Write-Host "Database '$DbName' already exists"
} else {
    Invoke-Psql "CREATE DATABASE $DbName OWNER $AppUser ENCODING 'UTF8';"
}

Invoke-Psql "GRANT ALL PRIVILEGES ON DATABASE $DbName TO $AppUser;"
Invoke-Psql "GRANT ALL ON SCHEMA public TO $AppUser; GRANT CREATE ON SCHEMA public TO $AppUser;" -Database $DbName

Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
Write-Host "OK: database=$DbName user=$AppUser password=$AppPassword"
