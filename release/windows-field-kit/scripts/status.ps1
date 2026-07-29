# Quick MEIS backend port / process status
$ErrorActionPreference = "SilentlyContinue"
. "$PSScriptRoot\meis-services.ps1"

Write-Host "MEIS backend status" -ForegroundColor Cyan
Write-Host ""

$up = 0
$down = 0
foreach ($s in $script:MeisServices) {
    $listening = Test-MeisPortListening -Port $s.port
    if ($listening) {
        Write-Host ("  OK   {0,-28} :{1}" -f $s.name, $s.port) -ForegroundColor Green
        $up++
    } else {
        Write-Host ("  DOWN {0,-28} :{1}" -f $s.name, $s.port) -ForegroundColor DarkGray
        $down++
    }
}

Write-Host ""
if ($up -eq 0) {
    Write-Host "No services listening. Start: powershell -File scripts\start.ps1" -ForegroundColor Yellow
} elseif ($down -gt 0) {
    Write-Host "$up up, $down down. Check logs: powershell -File scripts\logs.ps1 -List" -ForegroundColor Yellow
} else {
    Write-Host "All $($up) services up. Gateway: http://localhost:8080" -ForegroundColor Green
}

Write-Host ""
Write-Host "View logs:  powershell -File scripts\logs.ps1 -Service gateway -Follow"
