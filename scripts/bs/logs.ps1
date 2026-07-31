# Tail MEIS service logs in the current terminal (Spring Boot output goes to files, not the start window)
param(
    [string]$Service = "gateway",
    [int]$Lines = 80,
    [switch]$Follow,
    [switch]$List
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\meis-services.ps1"

$logDir = Join-Path $script:MeisRoot "logs"
if (-not (Test-Path $logDir)) {
    Write-Host "Log directory not found: $logDir" -ForegroundColor Red
    Write-Host "Start backend first: powershell -File scripts\start.ps1"
    exit 1
}

if ($List) {
    Write-Host "Available logs in $logDir :" -ForegroundColor Cyan
    Get-ChildItem $logDir -Filter "*.out.log" | ForEach-Object {
        $name = $_.BaseName -replace '\.out$', ''
        $kb = [math]::Round($_.Length / 1KB, 1)
        Write-Host "  $name  ($kb KB)"
    }
    Write-Host ""
    Write-Host "Examples:"
    Write-Host "  powershell -File scripts\logs.ps1 -Service gateway -Follow"
    Write-Host "  powershell -File scripts\logs.ps1 -Service auth"
    exit 0
}

$aliases = @{
    gateway = "meis-gateway"
    auth    = "meis-auth"
    system  = "meis-system"
    tenant  = "meis-tenant"
    web     = "meis-gateway"
}

$serviceName = $aliases[$Service.ToLower()]
if (-not $serviceName) {
    if ($Service -match '^meis-') { $serviceName = $Service }
    else { $serviceName = "meis-$Service" }
}

$outLog = Join-Path $logDir "$serviceName.out.log"
$errLog = Join-Path $logDir "$serviceName.err.log"

if (-not (Test-Path $outLog)) {
    Write-Host "Log not found: $outLog" -ForegroundColor Red
    Write-Host "Run: powershell -File scripts\logs.ps1 -List"
    exit 1
}

Write-Host "=== $serviceName ===" -ForegroundColor Cyan
Write-Host "stdout: $outLog"
Write-Host "stderr: $errLog"
Write-Host ""

if ($Follow) {
    Write-Host "Following (Ctrl+C to stop) ..." -ForegroundColor Yellow
    Get-Content $outLog -Tail $Lines -Wait
} else {
    Get-Content $outLog -Tail $Lines
    if ((Test-Path $errLog) -and (Get-Item $errLog).Length -gt 0) {
        Write-Host ""
        Write-Host "=== stderr ===" -ForegroundColor Red
        Get-Content $errLog -Tail 30
    }
}
