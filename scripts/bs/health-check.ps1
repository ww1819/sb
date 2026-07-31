# MEIS production / local health probe: ports + /actuator/health + gateway auth health
# Exit code 0 = all checked targets OK; non-zero = at least one failure
param(
    [string]$GatewayUrl = '',
    [int]$TimeoutSec = 5
)

$ErrorActionPreference = 'Continue'
. "$PSScriptRoot\meis-services.ps1"

function Test-HttpHealth {
    param(
        [Parameter(Mandatory = $true)][string]$Url,
        [int]$TimeoutSec = 5
    )
    try {
        $resp = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec $TimeoutSec
        if ($resp.StatusCode -lt 200 -or $resp.StatusCode -ge 300) {
            return @{ Ok = $false; Detail = "HTTP $($resp.StatusCode)" }
        }
        $body = $resp.Content
        if ($body -match '"status"\s*:\s*"UP"' -or $body -match '"code"\s*:\s*0' -or $body -match 'UP|ok|OK') {
            return @{ Ok = $true; Detail = 'OK' }
        }
        # auth/health may return Result wrapper without actuator status field
        if ($Url -match '/api/auth/health' -and $resp.StatusCode -eq 200) {
            return @{ Ok = $true; Detail = 'OK' }
        }
        return @{ Ok = $false; Detail = 'unexpected body' }
    } catch {
        return @{ Ok = $false; Detail = $_.Exception.Message }
    }
}

Write-Host 'MEIS health check' -ForegroundColor Cyan
Write-Host ''

$fail = 0
$pass = 0

foreach ($s in $script:MeisServices) {
    $listening = Test-MeisPortListening -Port $s.port
    $portLabel = if ($listening) { 'LISTEN' } else { 'NO_PORT' }
    $url = "http://127.0.0.1:$($s.port)/actuator/health"
    $http = if ($listening) { Test-HttpHealth -Url $url -TimeoutSec $TimeoutSec } else { @{ Ok = $false; Detail = 'skip' } }

    if ($listening -and $http.Ok) {
        Write-Host ("  OK   {0,-28} :{1}  {2}" -f $s.name, $s.port, $http.Detail) -ForegroundColor Green
        $pass++
    } else {
        $why = if (-not $listening) { $portLabel } else { $http.Detail }
        Write-Host ("  FAIL {0,-28} :{1}  {2}" -f $s.name, $s.port, $why) -ForegroundColor Red
        $fail++
    }
}

Write-Host ''
$gwCandidates = @()
if ($GatewayUrl) { $gwCandidates += ($GatewayUrl.TrimEnd('/') + '/api/auth/health') }
$gwCandidates += 'http://127.0.0.1:8080/api/auth/health'

$gwOk = $false
$gwDetail = ''
foreach ($u in $gwCandidates) {
    $r = Test-HttpHealth -Url $u -TimeoutSec $TimeoutSec
    if ($r.Ok) {
        $gwOk = $true
        $gwDetail = $u
        break
    }
    $gwDetail = "$u -> $($r.Detail)"
}

if ($gwOk) {
    Write-Host ("  OK   gateway auth/health     {0}" -f $gwDetail) -ForegroundColor Green
    $pass++
} else {
    Write-Host ("  FAIL gateway auth/health     {0}" -f $gwDetail) -ForegroundColor Red
    $fail++
}

Write-Host ''
if ($fail -eq 0) {
    Write-Host "All checks passed ($pass)." -ForegroundColor Green
    exit 0
}

Write-Host "$pass passed, $fail failed. Logs: powershell -File scripts\logs.ps1 -List" -ForegroundColor Yellow
exit 1
