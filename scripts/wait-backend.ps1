# Wait until MEIS backend ports are listening (used by VS Code launch preLaunchTask)
param(
    [ValidateSet('full', 'custom')]
    [string]$Mode = 'custom',
    [int[]]$Ports = @(),
    [int]$TimeoutSec = 0,
    [int]$IntervalSec = 2
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\meis-services.ps1"

if ($Mode -eq 'full') {
    $Ports = @($script:MeisServicePorts)
    if ($TimeoutSec -le 0) { $TimeoutSec = 600 }
} elseif ($Ports.Count -eq 0) {
    $Ports = @(8082, 8081, 8080)
    if ($TimeoutSec -le 0) { $TimeoutSec = 300 }
} elseif ($TimeoutSec -le 0) {
    $TimeoutSec = 300
}

Write-Host "Waiting for backend ports ($Mode): $($Ports -join ', ')"
$deadline = (Get-Date).AddSeconds($TimeoutSec)

while ((Get-Date) -lt $deadline) {
    $pending = @($Ports | Where-Object { -not (Test-MeisPortListening -Port $_) })
    if ($pending.Count -eq 0) {
        if ($Ports -contains 8080) {
            try {
                $resp = Invoke-WebRequest -Uri 'http://localhost:8080/actuator/health' -TimeoutSec 3 -UseBasicParsing
                if ($resp.StatusCode -eq 200) {
                    Write-Host 'Backend ready (ports + gateway health).' -ForegroundColor Green
                    exit 0
                }
            } catch {
                Write-Host '  ports up, gateway health pending...'
                Start-Sleep -Seconds $IntervalSec
                continue
            }
        }
        Write-Host 'Backend ports ready.' -ForegroundColor Green
        exit 0
    }
    Write-Host "  pending: $($pending -join ', ')"
    Start-Sleep -Seconds $IntervalSec
}

Write-Host "Timeout (${TimeoutSec}s) waiting for backend ports: $($pending -join ', ')" -ForegroundColor Red
exit 1
