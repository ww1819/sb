# MEIS SaaS build script (session JAVA_HOME, does not change system env)
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\meis-services.ps1"
$env:JAVA_HOME = Resolve-MeisJavaHome
$mvn = Resolve-MeisMaven
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
Write-Host "Using JAVA_HOME: $env:JAVA_HOME"
Write-Host "Using Maven: $mvn"
Set-Location $PSScriptRoot\..

Write-Host "Building MEIS backend..."
& $mvn -q package -DskipTests
if ($LASTEXITCODE -ne 0) { throw "Maven build failed" }

if (Test-Path "meis-web\package.json") {
    Write-Host "Building MEIS web (optional)..."
    Push-Location meis-web
    if (-not (Test-Path node_modules)) { npm install }
    npm run build
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Frontend build failed - backend jars are still usable for scripts\start.ps1" -ForegroundColor Yellow
    }
    Pop-Location
}

Write-Host "Build complete."
