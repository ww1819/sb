# Build all backend JARs and copy into package\jars\
# Configure paths in package\env.txt (see env.example.txt)
param(
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$pkgDir = $PSScriptRoot
$root = Split-Path $pkgDir -Parent
$jarsDir = Join-Path $pkgDir 'jars'
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
Write-Host "Done: $ok / $($services.Count) JARs -> package\jars\" -ForegroundColor Cyan
if ($fail.Count -gt 0) {
    Write-Host ("Missing: " + ($fail -join ', ')) -ForegroundColor Red
    exit 1
}
Write-Host 'Next: run package\start-ops.bat (or 启动运维.bat), then open the browser page.' -ForegroundColor DarkGray
