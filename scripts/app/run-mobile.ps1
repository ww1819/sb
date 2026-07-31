# Run MEIS Flutter app on Windows desktop
param(
    [string]$FlutterRoot = ""
)

$ErrorActionPreference = "Stop"
. (Join-Path (Split-Path $PSScriptRoot -Parent) 'common\meis-root.ps1')
. (Join-Path $script:MeisScriptsCommon 'mobile-env.ps1') -FlutterRoot $FlutterRoot

$MobileDir = Join-Path $script:MeisRoot 'meis-mobile'
if (-not (Test-Path (Join-Path $MobileDir "windows"))) {
    Write-Host "Platform project missing. Running setup..."
    & "$PSScriptRoot\setup-mobile.ps1" -FlutterRoot $FlutterRoot
}

Write-Host "Ensure MEIS backend is running: scripts\bs\start.bat (or scripts\start.bat)"
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
