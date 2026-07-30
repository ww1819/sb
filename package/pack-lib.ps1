# Shared helpers for package\pack.ps1 and pack-update.ps1
$ErrorActionPreference = 'Stop'

function Get-MeisPackagePaths {
    $pkgDir = $script:MeisPkgDir
    if (-not $pkgDir) { $pkgDir = $PSScriptRoot }
    if (-not $pkgDir) { throw 'MeisPkgDir not set' }
    [pscustomobject]@{
        PkgDir          = $pkgDir
        Root            = Split-Path $pkgDir -Parent
        JarsDir         = Join-Path $pkgDir 'jars'
        UpdateDir       = Join-Path $pkgDir 'update'
        WwwDir          = Join-Path $pkgDir 'www'
        LogsDir         = Join-Path $pkgDir 'logs'
        ServicesFile    = Join-Path $pkgDir 'services.json'
        FingerprintFile = Join-Path (Join-Path $pkgDir 'jars') '.pack-fingerprint.json'
    }
}

function Get-MeisServices([string]$ServicesFile) {
    if (-not (Test-Path $ServicesFile)) {
        throw "Missing services.json: $ServicesFile"
    }
    @(Get-Content $ServicesFile -Raw -Encoding UTF8 | ConvertFrom-Json)
}

function Get-MeisSharedModules {
    @('meis-common', 'meis-api')
}

function Get-FileContentSha256([string]$Path) {
    (Get-FileHash -Path $Path -Algorithm SHA256).Hash
}

function Get-MeisModuleFingerprint([string]$ModuleDir) {
    if (-not (Test-Path $ModuleDir)) {
        return 'MISSING'
    }
    $files = New-Object System.Collections.Generic.List[string]
    $pom = Join-Path $ModuleDir 'pom.xml'
    if (Test-Path $pom) { $files.Add((Resolve-Path $pom).Path) }
    $src = Join-Path $ModuleDir 'src'
    if (Test-Path $src) {
        Get-ChildItem -Path $src -Recurse -File -ErrorAction SilentlyContinue | ForEach-Object {
            $files.Add($_.FullName)
        }
    }
    $sorted = $files | Sort-Object
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        foreach ($full in $sorted) {
            $rel = $full.Substring($ModuleDir.Length).TrimStart('\', '/').Replace('\', '/')
            $bytes = [System.Text.Encoding]::UTF8.GetBytes($rel + '|')
            [void]$sha.TransformBlock($bytes, 0, $bytes.Length, $null, 0)
            $fileBytes = [System.IO.File]::ReadAllBytes($full)
            [void]$sha.TransformBlock($fileBytes, 0, $fileBytes.Length, $null, 0)
            $sep = [System.Text.Encoding]::UTF8.GetBytes(';')
            [void]$sha.TransformBlock($sep, 0, $sep.Length, $null, 0)
        }
        [void]$sha.TransformFinalBlock(@(), 0, 0)
        return ([System.BitConverter]::ToString($sha.Hash) -replace '-', '')
    } finally {
        $sha.Dispose()
    }
}

function Get-MeisRootPomFingerprint([string]$Root) {
    $pom = Join-Path $Root 'pom.xml'
    if (-not (Test-Path $pom)) { return 'MISSING' }
    return Get-FileContentSha256 $pom
}

function Read-MeisPackFingerprint([string]$Path) {
    if (-not (Test-Path $Path)) {
        return $null
    }
    try {
        return (Get-Content $Path -Raw -Encoding UTF8 | ConvertFrom-Json)
    } catch {
        Write-Host "WARN: cannot read fingerprint, treat as empty: $Path" -ForegroundColor Yellow
        return $null
    }
}

function Save-MeisPackFingerprint {
    param(
        [string]$Path,
        [hashtable]$ModuleHashes,
        [string]$RootPomHash,
        [string]$Mode
    )
    $dir = Split-Path $Path -Parent
    New-Item -ItemType Directory -Path $dir -Force | Out-Null
    $obj = [ordered]@{
        version     = 1
        mode        = $Mode
        updatedAt   = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
        rootPom     = $RootPomHash
        modules     = [ordered]@{}
    }
    foreach ($k in ($ModuleHashes.Keys | Sort-Object)) {
        $obj.modules[$k] = $ModuleHashes[$k]
    }
    ($obj | ConvertTo-Json -Depth 5) | Set-Content -Path $Path -Encoding UTF8
}

function Get-MeisJarName([string]$ServiceName) {
    "$ServiceName-1.0.0-SNAPSHOT.jar"
}

function Copy-MeisServiceJar {
    param(
        [string]$Root,
        [string]$JarsDir,
        [string]$ServiceName,
        [string]$UpdateDir = ''
    )
    $jarName = Get-MeisJarName $ServiceName
    $src = Join-Path $Root "$ServiceName\target\$jarName"
    $dest = Join-Path $JarsDir $jarName
    if (-not (Test-Path $src)) {
        return $false
    }
    New-Item -ItemType Directory -Path $JarsDir -Force | Out-Null
    Copy-Item $src $dest -Force
    if ($UpdateDir) {
        New-Item -ItemType Directory -Path $UpdateDir -Force | Out-Null
        Copy-Item $src (Join-Path $UpdateDir $jarName) -Force
    }
    $kb = [math]::Round((Get-Item $dest).Length / 1KB)
    Write-Host "  OK $jarName ($kb KB)" -ForegroundColor Green
    return $true
}
