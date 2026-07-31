# MEIS SaaS local startup (Windows native, PG/Redis required)
param(
    [string]$Profile = "dev",
    [switch]$FollowLogs
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\meis-services.ps1"
Start-MeisServices -Profile $Profile -FollowLogs:$FollowLogs
Write-Host "Web dev: cd meis-web && npm run dev"
