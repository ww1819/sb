# 对所有活跃租户幂等执行 V1/V2 建表（等同 meis-tenant 启动时 SchemaTableEnsuring）
param(
    [string[]]$Schema = @(),
    [string]$DbHost = "localhost",
    [int]$Port = 5432,
    [string]$DbName = "meis",
    [string]$AppUser = "med",
    [string]$AppPassword = "med123456"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path $PSScriptRoot -Parent
$jar = Join-Path $repoRoot "meis-common\target\deps\postgresql-42.7.3.jar"
if (-not (Test-Path $jar)) {
    Push-Location (Join-Path $repoRoot "meis-common")
    mvn -q dependency:copy-dependencies -DincludeArtifactIds=postgresql -DoutputDirectory=target/deps | Out-Null
    Pop-Location
}
Push-Location (Join-Path $repoRoot "scripts")
try {
    javac -encoding UTF-8 -cp $jar EnsureTenantV1Tables.java
    if ($Schema.Count -gt 0) {
        java -cp ".;$jar" EnsureTenantV1Tables @Schema
    } else {
        java -cp ".;$jar" EnsureTenantV1Tables
    }
} finally {
    Pop-Location
}
