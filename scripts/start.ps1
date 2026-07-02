# MEIS SaaS local startup (Windows native, PG/Redis required)
param(
    [string]$Profile = "dev"
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\meis-services.ps1"
Start-MeisServices -Profile $Profile
Write-Host "Web dev: cd meis-web && npm run dev"
