# Full build: all backend JARs -> package\jars\
# Configure paths in package\env.txt (see env.example.txt)
param(
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$script:MeisPkgDir = $PSScriptRoot
. (Join-Path $PSScriptRoot 'pack-lib.ps1')
. (Join-Path $PSScriptRoot 'load-env.ps1')

$paths = Get-MeisPackagePaths
Import-MeisPackageEnv -EnvFile (Join-Path $paths.PkgDir 'env.txt')

$services = Get-MeisServices $paths.ServicesFile
$serviceNames = @($services | ForEach-Object { $_.name })
$shared = Get-MeisSharedModules

New-Item -ItemType Directory -Path $paths.JarsDir -Force | Out-Null
New-Item -ItemType Directory -Path $paths.LogsDir -Force | Out-Null

$jdk = Resolve-MeisPackageJavaHome
$env:JAVA_HOME = $jdk
$env:MEIS_JAVA_HOME = $jdk
$env:Path = "$jdk\bin;" + $env:Path
Write-Host "JAVA_HOME=$jdk" -ForegroundColor Cyan
Write-Host 'Mode: FULL (完整打包)' -ForegroundColor Cyan

if (-not $SkipBuild) {
    $mvn = Resolve-MeisPackageMaven
    Write-Host "MAVEN=$mvn" -ForegroundColor Cyan
    $mods = $serviceNames -join ','
    Write-Host "Building: mvn -pl $mods -am clean package -DskipTests" -ForegroundColor Cyan
    Push-Location $paths.Root
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
foreach ($name in $serviceNames) {
    if (Copy-MeisServiceJar -Root $paths.Root -JarsDir $paths.JarsDir -ServiceName $name) {
        $ok++
    } else {
        $jarName = Get-MeisJarName $name
        $fail += $jarName
        Write-Host "  MISSING $($paths.Root)\$name\target\$jarName" -ForegroundColor Red
    }
}

# Refresh fingerprints for shared + all services + root pom
$hashes = @{}
foreach ($m in ($shared + $serviceNames | Select-Object -Unique)) {
    $hashes[$m] = Get-MeisModuleFingerprint (Join-Path $paths.Root $m)
}
$rootPomHash = Get-MeisRootPomFingerprint $paths.Root
Save-MeisPackFingerprint -Path $paths.FingerprintFile -ModuleHashes $hashes -RootPomHash $rootPomHash -Mode 'full'

# Clear stale incremental update folder (full pack supersedes)
if (Test-Path $paths.UpdateDir) {
    Remove-Item $paths.UpdateDir -Recurse -Force
    Write-Host 'Cleared package\update\ (full pack)' -ForegroundColor DarkGray
}

Write-Host ''
Write-Host "Done: $ok / $($serviceNames.Count) JARs -> package\jars\" -ForegroundColor Cyan
Write-Host "Fingerprint saved: $($paths.FingerprintFile)" -ForegroundColor DarkGray
if ($fail.Count -gt 0) {
    Write-Host ("Missing: " + ($fail -join ', ')) -ForegroundColor Red
    exit 1
}
Write-Host 'Next: copy whole package\ to field PC, then 启动运维.bat' -ForegroundColor DarkGray
