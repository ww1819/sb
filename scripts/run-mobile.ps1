# Run MEIS Flutter app on Windows desktop
param(
    [string]$FlutterRoot = ""
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\mobile-env.ps1" -FlutterRoot $FlutterRoot

$MobileDir = Join-Path $PSScriptRoot "..\meis-mobile"
if (-not (Test-Path (Join-Path $MobileDir "windows"))) {
    Write-Host "Platform project missing. Running setup..."
    & "$PSScriptRoot\setup-mobile.ps1" -FlutterRoot $FlutterRoot
}

Write-Host "Ensure MEIS backend is running: scripts\start.bat"
Write-Host "App API: http://127.0.0.1:8080/api"
Write-Host "Hot reload: r | Quit: q"
Write-Host ""

Push-Location $MobileDir
try {
    & $script:FlutterBat run -d windows
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
finally {
    Pop-Location
}
