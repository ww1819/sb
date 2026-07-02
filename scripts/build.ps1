# MEIS SaaS build script (session JAVA_HOME, does not change system env)
$ErrorActionPreference = "Stop"
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
Set-Location $PSScriptRoot\..

Write-Host "Building MEIS backend..."
mvn -q package -DskipTests
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
