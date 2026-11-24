# 对活跃租户幂等结构补全：V1/V2 建表索引 + R__columns_audit + R__columns_biz
# （等同 meis-tenant 启动时 SchemaTableEnsuring + Flyway 结构段；不跑 R__data_fix）
# 由 Start-MeisServices / start.ps1 自动调用，无需再单独执行 apply-tenant-patches.ps1
param(
    [string[]]$Schema = @(),
    [switch]$Quiet
)

$ErrorActionPreference = "Stop"
. (Join-Path (Split-Path $PSScriptRoot -Parent) 'common\meis-root.ps1')
$repoRoot = $script:MeisRoot
$env:MEIS_ROOT = $repoRoot

# 优先 package\env.txt（与启动脚本一致）
$envFile = Join-Path $repoRoot 'package\env.txt'
if (Test-Path -LiteralPath $envFile) {
    Get-Content -LiteralPath $envFile -Encoding UTF8 | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith('#')) { return }
        $eq = $line.IndexOf('=')
        if ($eq -lt 1) { return }
        $key = $line.Substring(0, $eq).Trim()
        $val = $line.Substring($eq + 1).Trim()
        if ($val.StartsWith('"') -and $val.EndsWith('"') -and $val.Length -ge 2) {
            $val = $val.Substring(1, $val.Length - 2)
        }
        if ($key) { Set-Item -Path "Env:$key" -Value $val }
    }
}

$jarCandidates = @(
    (Join-Path $repoRoot "meis-common\target\deps\postgresql-42.7.3.jar"),
    (Join-Path $env:USERPROFILE ".m2\repository\org\postgresql\postgresql\42.7.3\postgresql-42.7.3.jar")
)
$jar = $jarCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $jar) {
    Push-Location (Join-Path $repoRoot "meis-common")
    try {
        mvn -q dependency:copy-dependencies -DincludeArtifactIds=postgresql -DoutputDirectory=target/deps | Out-Null
    } finally {
        Pop-Location
    }
    $jar = Join-Path $repoRoot "meis-common\target\deps\postgresql-42.7.3.jar"
}
if (-not (Test-Path $jar)) { throw "PostgreSQL JDBC jar not found: $jar" }

if (-not $Quiet) {
    Write-Host "Ensuring tenant schema (V1/V2 + R__columns_audit/biz) ..." -ForegroundColor Cyan
}

Push-Location $script:MeisScriptsBs
try {
    javac -encoding UTF-8 -cp $jar EnsureTenantV1Tables.java
    if ($LASTEXITCODE -ne 0) { throw "javac EnsureTenantV1Tables failed" }
    if ($Schema.Count -gt 0) {
        java -cp ".;$jar" EnsureTenantV1Tables @Schema
    } else {
        java -cp ".;$jar" EnsureTenantV1Tables
    }
    if ($LASTEXITCODE -ne 0) { throw "EnsureTenantV1Tables failed (exit $LASTEXITCODE)" }
} finally {
    Pop-Location
}

if (-not $Quiet) {
    Write-Host "Tenant schema ensure finished. (R__data_fix still via meis-tenant Flyway)" -ForegroundColor Green
}
