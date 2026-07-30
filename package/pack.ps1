# Build backend JARs + meis-web production dist into package\
# Configure paths in package\env.txt (see env.example.txt)
param(
    [switch]$SkipBuild,
    [switch]$SkipFrontend
)

$ErrorActionPreference = 'Stop'
$pkgDir = $PSScriptRoot
$root = Split-Path $pkgDir -Parent
$jarsDir = Join-Path $pkgDir 'jars'
$wwwDir = Join-Path $pkgDir 'www'
$logsDir = Join-Path $pkgDir 'logs'
$servicesFile = Join-Path $pkgDir 'services.json'

. (Join-Path $pkgDir 'load-env.ps1')
Import-MeisPackageEnv -EnvFile (Join-Path $pkgDir 'env.txt')

if (-not (Test-Path $servicesFile)) {
    throw "Missing services.json: $servicesFile"
}
$services = Get-Content $servicesFile -Raw -Encoding UTF8 | ConvertFrom-Json

New-Item -ItemType Directory -Path $jarsDir -Force | Out-Null
New-Item -ItemType Directory -Path $logsDir -Force | Out-Null

$jdk = Resolve-MeisPackageJavaHome
$env:JAVA_HOME = $jdk
$env:MEIS_JAVA_HOME = $jdk
$env:Path = "$jdk\bin;" + $env:Path
Write-Host "JAVA_HOME=$jdk" -ForegroundColor Cyan

if (-not $SkipBuild) {
    $mvn = Resolve-MeisPackageMaven
    Write-Host "MAVEN=$mvn" -ForegroundColor Cyan
    $mods = ($services | ForEach-Object { $_.name }) -join ','
    Write-Host "Building: mvn -pl $mods -am clean package -DskipTests" -ForegroundColor Cyan
    Push-Location $root
    try {
        & $mvn -pl $mods -am clean package -DskipTests
        if ($LASTEXITCODE -ne 0) {
            throw "Maven failed, exit=$LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
} else {
    Write-Host 'SkipBuild: collect existing JARs only' -ForegroundColor Yellow
}

$ok = 0
$fail = @()
foreach ($s in $services) {
    $jarName = "$($s.name)-1.0.0-SNAPSHOT.jar"
    $src = Join-Path $root "$($s.name)\target\$jarName"
    $dest = Join-Path $jarsDir $jarName
    if (-not (Test-Path $src)) {
        $fail += $jarName
        Write-Host "  MISSING $src" -ForegroundColor Red
        continue
    }
    Copy-Item $src $dest -Force
    $kb = [math]::Round((Get-Item $dest).Length / 1KB)
    Write-Host "  OK $jarName ($kb KB)" -ForegroundColor Green
    $ok++
}

Write-Host ''
Write-Host "JARs: $ok / $($services.Count) -> package\jars\" -ForegroundColor Cyan
if ($fail.Count -gt 0) {
    Write-Host ("Missing: " + ($fail -join ', ')) -ForegroundColor Red
    exit 1
}

# --- Frontend production build (meis-web -> package\www) ---
$skipFe = $SkipFrontend -or ($env:SKIP_FRONTEND_BUILD -eq '1') -or ($env:SKIP_FRONTEND_BUILD -eq 'true')
$webDir = Join-Path $root 'meis-web'
$distDir = Join-Path $webDir 'dist'

if ($skipFe) {
    Write-Host 'SkipFrontend: leave package\www unchanged (or copy existing dist if present)' -ForegroundColor Yellow
    if ((Test-Path $distDir) -and (Test-Path (Join-Path $distDir 'index.html'))) {
        if (Test-Path $wwwDir) { Remove-Item $wwwDir -Recurse -Force }
        New-Item -ItemType Directory -Path $wwwDir -Force | Out-Null
        Copy-Item (Join-Path $distDir '*') $wwwDir -Recurse -Force
        Write-Host "  Copied existing meis-web\dist -> package\www" -ForegroundColor Green
    } elseif (-not (Test-Path (Join-Path $wwwDir 'index.html'))) {
        Write-Host '  WARN: no package\www and no meis-web\dist; field Nginx will need frontend separately' -ForegroundColor Yellow
    }
} else {
    if (-not (Test-Path (Join-Path $webDir 'package.json'))) {
        throw "meis-web not found: $webDir"
    }
    $npm = Resolve-MeisPackageNpm
    Write-Host "NPM=$npm" -ForegroundColor Cyan
    Write-Host 'Building meis-web production (npm run build)...' -ForegroundColor Cyan
    if (-not (Test-Path (Join-Path $webDir 'node_modules'))) {
        Write-Host '  npm install (node_modules missing)...' -ForegroundColor DarkGray
        Invoke-MeisPackageNpm -WorkingDirectory $webDir -Arguments @('install')
    }
    Invoke-MeisPackageNpm -WorkingDirectory $webDir -Arguments @('run', 'build')
    if (-not (Test-Path (Join-Path $distDir 'index.html'))) {
        throw "meis-web build OK but dist\index.html missing: $distDir"
    }
    if (Test-Path $wwwDir) { Remove-Item $wwwDir -Recurse -Force }
    New-Item -ItemType Directory -Path $wwwDir -Force | Out-Null
    Copy-Item (Join-Path $distDir '*') $wwwDir -Recurse -Force
    $files = @(Get-ChildItem $wwwDir -Recurse -File).Count
    Write-Host "  OK frontend -> package\www ($files files)" -ForegroundColor Green
}

Write-Host ''
Write-Host 'Done. Next: run package\start-ops.bat (or 启动运维.bat), then open the browser page.' -ForegroundColor DarkGray
Write-Host 'Frontend static: package\www (Nginx root / copy to D:\meis\www)' -ForegroundColor DarkGray
