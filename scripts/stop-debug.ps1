# Stop MEIS debug session: all backend JAR/JDWP processes + Vite dev server (5173)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\meis-services.ps1"

Write-Host 'Stopping MEIS debug session ...' -ForegroundColor Cyan

Stop-MeisServices

$killed = @{}
foreach ($port in @(5173)) {
    $lines = netstat -ano | Select-String ":\s*$port\s+.*LISTENING"
    foreach ($line in $lines) {
        if ($line -match '\s+(\d+)\s*$') {
            $procId = [int]$matches[1]
            if ($procId -gt 0 -and -not $killed.ContainsKey($procId)) {
                Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
                $killed[$procId] = $true
                Write-Host "Stopped frontend PID $procId (port $port)" -ForegroundColor Green
            }
        }
    }
}

if ($killed.Count -eq 0) {
    Write-Host 'No frontend dev server on :5173.' -ForegroundColor Yellow
}

Write-Host 'Debug session stopped.' -ForegroundColor Green
Write-Host 'Tip: also press Shift+F5 in Run and Debug to disconnect attach sessions.' -ForegroundColor Cyan
