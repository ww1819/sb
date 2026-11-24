# 兼容入口：结构补全已并入 ensure-tenant-tables.ps1（V1/V2 + R__columns_audit/biz）。
# 推荐：start.ps1 会自动调用；也可手动：
#   powershell -File scripts\bs\ensure-tenant-tables.ps1 [-Schema tenant_demo]
# 字典/回填仍依赖重启 meis-tenant 跑 R__data_fix。
param(
    [string]$Schema = "",
    [string]$DbHost = "",
    [int]$Port = 0,
    [string]$DbName = "",
    [string]$AppUser = "",
    [string]$AppPassword = ""
)

$ErrorActionPreference = "Stop"
. (Join-Path (Split-Path $PSScriptRoot -Parent) 'common\meis-root.ps1')

if ($DbHost) { $env:POSTGRES_HOST = $DbHost }
if ($Port -gt 0) { $env:POSTGRES_PORT = "$Port" }
if ($DbName) { $env:POSTGRES_DB = $DbName }
if ($AppUser) { $env:POSTGRES_USER = $AppUser }
if ($AppPassword) { $env:POSTGRES_PASSWORD = $AppPassword }

Write-Host "Delegating to ensure-tenant-tables.ps1 (includes R__columns_audit + R__columns_biz) ..." -ForegroundColor Cyan
$argsList = @()
if ($Schema) { $argsList += @('-Schema', $Schema) }
& (Join-Path $PSScriptRoot 'ensure-tenant-tables.ps1') @argsList
exit $LASTEXITCODE
