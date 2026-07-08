# Stop MEIS dev panel (frees http://localhost:5099)
param(
    [int]$Port = 5099,
    [switch]$Quiet
)

$ErrorActionPreference = 'Continue'
$killed = @{}

function Stop-PidSafe([int]$ProcId) {
    if ($ProcId -le 4 -or $killed.ContainsKey($ProcId)) { return }
    if ($ProcId -eq $PID) { return }
    try {
        Stop-Process -Id $ProcId -Force -ErrorAction Stop
        $killed[$ProcId] = $true
        if (-not $Quiet) { Write-Host "Stopped PID $ProcId" }
    } catch {
        if (-not $Quiet) { Write-Host "Skip PID $ProcId : $($_.Exception.Message)" -ForegroundColor DarkGray }
    }
}

# 1) soft shutdown via API (when panel is healthy)
try {
    $null = Invoke-WebRequest -UseBasicParsing "http://localhost:$Port/api/panel/shutdown" `
        -Method POST -TimeoutSec 3 -ErrorAction Stop
    Start-Sleep -Seconds 1
    if (-not $Quiet) { Write-Host "Sent soft shutdown to panel API." }
} catch {
    # panel may already be dead / hung
}

# 2) kill PowerShell hosts whose command line runs dev-panel.ps1
Get-CimInstance Win32_Process -Filter "Name='powershell.exe' OR Name='pwsh.exe'" -ErrorAction SilentlyContinue | ForEach-Object {
    if ($_.CommandLine -and $_.CommandLine -match 'dev-panel\.ps1') {
        Stop-PidSafe $_.ProcessId
    }
}

Start-Sleep -Milliseconds 600

# 3) kill listeners on panel port (skip System/pid 4)
$lines = netstat -ano | Select-String ":\s*$Port\s+.*LISTENING"
foreach ($line in $lines) {
    if ($line -match '\s+(\d+)\s*$') {
        Stop-PidSafe ([int]$Matches[1])
    }
}

Start-Sleep -Seconds 1
$still = @(netstat -ano | Select-String ":\s*$Port\s+.*LISTENING")
if ($still.Count -gt 0) {
    if (-not $Quiet) {
        Write-Host "WARN: port $Port may still be busy:" -ForegroundColor Yellow
        $still | ForEach-Object { Write-Host "  $_" }
        Write-Host "Tip: close the terminal that started the panel, then retry." -ForegroundColor DarkGray
    }
    exit 1
}

if (-not $Quiet) {
    if ($killed.Count -eq 0) {
        Write-Host "Dev panel was not running (port $Port free)."
    } else {
        Write-Host "Dev panel stopped ($($killed.Count) process/es). Port $Port is free." -ForegroundColor Green
    }
}
exit 0
