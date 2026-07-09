# Install MEIS modules to local Maven repo (fixes IDE Missing artifact)
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\meis-services.ps1"
$env:JAVA_HOME = Resolve-MeisJavaHome
$mvn = Resolve-MeisMaven
Write-Host "Using JAVA_HOME: $env:JAVA_HOME"
Write-Host "Using Maven: $mvn"
Set-Location $PSScriptRoot\..

Write-Host "Installing MEIS backend to local Maven repository..."
& $mvn install -DskipTests
if ($LASTEXITCODE -ne 0) { throw "Maven install failed" }

Write-Host "Maven install complete."
