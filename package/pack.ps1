# Full build: all backend JARs -> package\jars\ + meis-web dist -> package\www\
# Configure paths in package\env.txt (see env.example.txt)
param(
    [switch]$SkipBuild,
    [switch]$SkipFrontend
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
Write-Host 'Mode: FULL (完整打包 = JARs + 前端 www)' -ForegroundColor Cyan

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
Write-Host "JARs: $ok / $($serviceNames.Count) -> package\jars\" -ForegroundColor Cyan
Write-Host "Fingerprint saved: $($paths.FingerprintFile)" -ForegroundColor DarkGray
if ($fail.Count -gt 0) {
    Write-Host ("Missing: " + ($fail -join ', ')) -ForegroundColor Red
    exit 1
}

# --- Frontend production build (meis-web -> package\www) ---
$root = $paths.Root
$wwwDir = $paths.WwwDir
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
    # VS Code/Cursor 「调试启动」会给子进程注入 --inspect；生产构建勿挂调试器
    if ($env:NODE_OPTIONS -and ($env:NODE_OPTIONS -match '--inspect|--debug')) {
        $cleaned = (($env:NODE_OPTIONS -split '\s+') | Where-Object {
            $_ -and ($_ -notmatch '^--inspect') -and ($_ -notmatch '^--debug')
        }) -join ' '
        Write-Host '  Cleared NODE_OPTIONS inspect/debug flags for production build' -ForegroundColor DarkGray
        if ($cleaned) { $env:NODE_OPTIONS = $cleaned }
        else { Remove-Item Env:NODE_OPTIONS -ErrorAction SilentlyContinue }
    }
    Push-Location $webDir
    try {
        if (-not (Test-Path 'node_modules')) {
            Write-Host '  npm install (node_modules missing)...' -ForegroundColor DarkGray
            & $npm install
            if ($LASTEXITCODE -ne 0) { throw "npm install failed, exit=$LASTEXITCODE" }
        }
        & $npm run build
        if ($LASTEXITCODE -ne 0) { throw "npm run build failed, exit=$LASTEXITCODE" }
    } finally {
        Pop-Location
    }
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
Write-Host 'Done. Next: copy whole package\ to field PC, then 启动运维.bat' -ForegroundColor DarkGray
Write-Host 'Frontend static: package\www (Nginx root / copy to D:\meis\www)' -ForegroundColor DarkGray
