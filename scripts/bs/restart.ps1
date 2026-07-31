# Restart MEIS backend: stop all -> optional build -> start
param(
    [string]$Profile = "dev",
    [switch]$Build,
    [switch]$FollowLogs
)

$ErrorActionPreference = "Stop"

Write-Host "=== Stopping MEIS backend ==="
& "$PSScriptRoot\stop.ps1"

Start-Sleep -Seconds 2

if ($Build) {
    Write-Host "=== Building MEIS backend ==="
    & "$PSScriptRoot\build.ps1"
}

Write-Host "=== Starting MEIS backend ==="
& "$PSScriptRoot\start.ps1" -Profile $Profile -FollowLogs:$FollowLogs

Write-Host "=== Restart complete ==="
