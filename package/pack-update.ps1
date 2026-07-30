# Incremental build: only modules with source changes since last pack fingerprint.
# Outputs:
#   - overwrite changed JARs in package\jars\
#   - also copy them to package\update\ for slim field delivery
param(
    [switch]$ForceAll
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
Write-Host 'Mode: UPDATE (更新打包 — 仅代码有变更的模块)' -ForegroundColor Cyan

$prev = Read-MeisPackFingerprint $paths.FingerprintFile
if (-not $prev -and -not $ForceAll) {
    Write-Host ''
    Write-Host '没有打包指纹（jars\.pack-fingerprint.json）。' -ForegroundColor Yellow
    Write-Host '请先双击「完整打包.bat」建立基线；或对本脚本使用 -ForceAll。' -ForegroundColor Yellow
    exit 2
}

# Current fingerprints
$current = @{}
foreach ($m in ($shared + $serviceNames | Select-Object -Unique)) {
    $current[$m] = Get-MeisModuleFingerprint (Join-Path $paths.Root $m)
}
$rootPomNow = Get-MeisRootPomFingerprint $paths.Root
$rootPomPrev = if ($prev -and $prev.rootPom) { [string]$prev.rootPom } else { '' }

$prevModules = @{}
if ($prev -and $prev.modules) {
    $prev.modules.PSObject.Properties | ForEach-Object { $prevModules[$_.Name] = [string]$_.Value }
}

$sharedChanged = $false
$changedShared = @()
foreach ($m in $shared) {
    $old = if ($prevModules.ContainsKey($m)) { $prevModules[$m] } else { '' }
    if ($ForceAll -or $old -ne $current[$m]) {
        $sharedChanged = $true
        $changedShared += $m
    }
}
if ($ForceAll -or ($rootPomPrev -ne $rootPomNow)) {
    $sharedChanged = $true
    if ($rootPomPrev -ne $rootPomNow) {
        Write-Host '  root pom.xml changed' -ForegroundColor Yellow
    }
}

$changedServices = New-Object System.Collections.Generic.List[string]
if ($sharedChanged -or $ForceAll) {
    Write-Host 'Shared lib / root POM changed -> rebuild ALL service JARs' -ForegroundColor Yellow
    foreach ($s in $serviceNames) { [void]$changedServices.Add($s) }
    if ($changedShared.Count -gt 0) {
        Write-Host ("  shared: " + ($changedShared -join ', ')) -ForegroundColor Yellow
    }
} else {
    foreach ($s in $serviceNames) {
        $old = if ($prevModules.ContainsKey($s)) { $prevModules[$s] } else { '' }
        $jarPath = Join-Path $paths.JarsDir (Get-MeisJarName $s)
        $missingJar = -not (Test-Path $jarPath)
        if ($old -ne $current[$s] -or $missingJar) {
            [void]$changedServices.Add($s)
            if ($missingJar) {
                Write-Host "  $s : JAR missing in package\jars" -ForegroundColor Yellow
            } else {
                Write-Host "  $s : source changed" -ForegroundColor Yellow
            }
        } else {
            Write-Host "  $s : skip (no change)" -ForegroundColor DarkGray
        }
    }
}

if ($changedServices.Count -eq 0) {
    Write-Host ''
    Write-Host '没有需要更新的 JAR，跳过编译。' -ForegroundColor Green
    Write-Host '若确认有改动仍未检出：先完整打包，或检查是否改了未纳入指纹的文件。' -ForegroundColor DarkGray
    exit 0
}

Write-Host ''
Write-Host ("Will build $($changedServices.Count) module(s): " + ($changedServices -join ', ')) -ForegroundColor Cyan

$mvn = Resolve-MeisPackageMaven
Write-Host "MAVEN=$mvn" -ForegroundColor Cyan
$pl = ($changedServices -join ',')
# no clean: faster incremental; -am builds meis-common/meis-api when needed
Write-Host "Building: mvn -pl $pl -am package -DskipTests" -ForegroundColor Cyan
Push-Location $paths.Root
try {
    & $mvn -pl $pl -am package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        throw "Maven failed, exit=$LASTEXITCODE"
    }
} finally {
    Pop-Location
}

# Fresh update folder
if (Test-Path $paths.UpdateDir) {
    Remove-Item $paths.UpdateDir -Recurse -Force
}
New-Item -ItemType Directory -Path $paths.UpdateDir -Force | Out-Null

$ok = 0
$fail = @()
foreach ($name in $changedServices) {
    if (Copy-MeisServiceJar -Root $paths.Root -JarsDir $paths.JarsDir -ServiceName $name -UpdateDir $paths.UpdateDir) {
        $ok++
        # refresh fingerprint for this service from current disk (post-build sources unchanged)
        $current[$name] = Get-MeisModuleFingerprint (Join-Path $paths.Root $name)
    } else {
        $fail += (Get-MeisJarName $name)
        Write-Host "  MISSING target JAR for $name" -ForegroundColor Red
    }
}

# Merge fingerprints: keep old for untouched services; refresh changed + shared
$merged = @{}
foreach ($m in ($shared + $serviceNames | Select-Object -Unique)) {
    $mustRefresh = ($shared -contains $m) -or ($changedServices -contains $m) -or $sharedChanged -or $ForceAll
    if ($mustRefresh) {
        $merged[$m] = Get-MeisModuleFingerprint (Join-Path $paths.Root $m)
    } elseif ($prevModules.ContainsKey($m)) {
        $merged[$m] = $prevModules[$m]
    } else {
        $merged[$m] = $current[$m]
    }
}

Save-MeisPackFingerprint -Path $paths.FingerprintFile -ModuleHashes $merged -RootPomHash $rootPomNow -Mode 'update'

$changedList = @($changedServices | ForEach-Object { Get-MeisJarName $_ })
$readme = @"
MEIS 增量更新包
==============
生成时间: $((Get-Date).ToString('yyyy-MM-dd HH:mm:ss'))
变更 JAR 数: $($changedList.Count)

实施机操作:
1. 停掉对应服务（或运维页「停止」）
2. 将本目录下 *.jar 覆盖到实施机 package\jars\
3. 按顺序先启 meis-tenant（若在列表中），再启其余 / 网关
4. 冒烟: 登录、相关功能

变更列表:
$($changedList | ForEach-Object { "  - $_" } | Out-String)
"@
Set-Content -Path (Join-Path $paths.UpdateDir 'README.txt') -Value $readme.TrimEnd() -Encoding UTF8
Set-Content -Path (Join-Path $paths.UpdateDir 'CHANGED.txt') -Value ($changedList -join "`r`n") -Encoding UTF8

Write-Host ''
Write-Host "Done: $ok / $($changedServices.Count) updated -> package\jars\ + package\update\" -ForegroundColor Cyan
Write-Host "交付实施: 只需拷贝 package\update\ 内 JAR 覆盖现场 jars\" -ForegroundColor Green
if ($fail.Count -gt 0) {
    Write-Host ("Missing: " + ($fail -join ', ')) -ForegroundColor Red
    exit 1
}
