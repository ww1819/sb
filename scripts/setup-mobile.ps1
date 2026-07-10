# MEIS Flutter 移动端 - 首次初始化（生成 android/windows 等平台工程）
param(
    [string]$FlutterRoot = ""
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\mobile-env.ps1" -FlutterRoot $FlutterRoot
. "$PSScriptRoot\ensure-developer-mode.ps1"
$FlutterBin = $script:FlutterBat
$MobileDir = Join-Path $PSScriptRoot "..\meis-mobile"

Write-Host "=== Flutter path: $FlutterBin ==="
& $FlutterBin --version
if ($LASTEXITCODE -ne 0) { throw "flutter --version failed" }

Write-Host ""
Write-Host "=== Enable Windows desktop ==="
& $FlutterBin config --enable-windows-desktop
& $FlutterBin config --enable-web

Write-Host ""
Write-Host "=== Generate platform projects (if missing) ==="
Push-Location $MobileDir
try {
    if (-not (Test-Path "windows")) {
        & $FlutterBin create . --project-name meis_mobile --platforms=windows,android,web
        if ($LASTEXITCODE -ne 0) { throw "flutter create failed" }
    } else {
        Write-Host "Platform folders already exist, skip flutter create"
    }

    Write-Host ""
    Write-Host "=== Check symlink / Developer Mode ==="
    Ensure-DeveloperMode

    Write-Host ""
    Write-Host "=== pub get ==="
    & $FlutterBin pub get
    if ($LASTEXITCODE -ne 0) { throw "flutter pub get failed" }

    Write-Host ""
    Write-Host "=== Patch Android cleartext HTTP (LAN) ==="
    $manifest = Join-Path $MobileDir "android\app\src\main\AndroidManifest.xml"
    if (Test-Path $manifest) {
        $xml = Get-Content $manifest -Raw
        if ($xml -notmatch 'usesCleartextTraffic') {
            $xml = $xml -replace '<application', '<application android:usesCleartextTraffic="true"'
            Set-Content $manifest $xml -NoNewline
            Write-Host "Added usesCleartextTraffic to AndroidManifest.xml"
        }
    }
}
finally {
    Pop-Location
}

Write-Host ""
Write-Host "=== flutter doctor (summary) ==="
& $FlutterBin doctor

Write-Host ""
Write-Host "OK. Next: run scripts\run-mobile.bat"
