# Install Android SDK command-line tools + packages needed by flutter build apk.
# Default location: %LOCALAPPDATA%\Android\Sdk (or ANDROID_HOME / -SdkRoot)
# Then writes ANDROID_HOME into package\env.txt if missing.
#
# China networks often time out on dl.google.com — script tries mirrors first,
# supports -ZipPath for offline install, and longer timeouts + retries.
param(
    [string]$SdkRoot = '',
    # Official zip name (keep in sync with Google "commandlinetools" release)
    [string]$CmdlineToolsZipName = 'commandlinetools-win-15859902_latest.zip',
    [string]$CmdlineToolsZipUrl = '',
    # Optional: local zip already downloaded (skip network)
    [string]$ZipPath = '',
    [int]$DownloadTimeoutSec = 600,
    [int]$DownloadRetries = 3
)

$ErrorActionPreference = 'Stop'
$pkgDir = $PSScriptRoot
. (Join-Path $pkgDir 'load-env.ps1')
Import-MeisPackageEnv -EnvFile (Join-Path $pkgDir 'env.txt')

if (-not $SdkRoot) {
    if ($env:ANDROID_HOME) { $SdkRoot = $env:ANDROID_HOME.Trim().Trim('"').Trim("'") }
    elseif ($env:ANDROID_SDK_ROOT) { $SdkRoot = $env:ANDROID_SDK_ROOT.Trim().Trim('"').Trim("'") }
    else { $SdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
}

Write-Host "SDK root: $SdkRoot" -ForegroundColor Cyan

try {
    $jdk = Resolve-MeisPackageJavaHome
    $env:JAVA_HOME = $jdk
    $env:Path = "$jdk\bin;$env:Path"
    Write-Host "JAVA_HOME=$jdk" -ForegroundColor Cyan
} catch {
    throw "JDK 17 required to run sdkmanager. Fix JAVA_HOME in package\env.txt. $($_.Exception.Message)"
}

function Get-MeisCmdlineToolsCandidateUrls {
    param([string]$ZipName, [string]$ExplicitUrl)
    $list = New-Object System.Collections.Generic.List[string]
    if ($ExplicitUrl) { [void]$list.Add($ExplicitUrl.Trim()) }
    # Prefer CN mirrors (Google often times out)
    foreach ($base in @(
            'https://mirrors.cloud.tencent.com/AndroidSDK/',
            'https://mirrors.tuna.tsinghua.edu.cn/android/repository/',
            'https://mirrors.aliyun.com/android/repository/',
            'https://dl.google.com/android/repository/'
        )) {
        [void]$list.Add(($base.TrimEnd('/') + '/' + $ZipName))
    }
    return @($list | Select-Object -Unique)
}

function Save-MeisWebFile {
    param(
        [string[]]$Urls,
        [string]$OutFile,
        [int]$TimeoutSec,
        [int]$Retries
    )
    $curl = Get-Command curl.exe -ErrorAction SilentlyContinue
    $lastErr = $null
    foreach ($url in $Urls) {
        for ($i = 1; $i -le $Retries; $i++) {
            Write-Host "Downloading ($i/$Retries) ..." -ForegroundColor Cyan
            Write-Host "  $url" -ForegroundColor DarkGray
            try {
                if ($curl) {
                    # curl is far more reliable than Invoke-WebRequest on flaky CN links
                    # -C - enables resume after connection reset (important for ~700MB NDK)
                    # Do NOT delete partial file between retries — resume from it.
                    if ($i -gt 1 -and (Test-Path -LiteralPath $OutFile)) {
                        $have = (Get-Item -LiteralPath $OutFile).Length
                        if ($have -gt 5MB) {
                            $trim = [Math]::Min(2MB, [int64]($have / 20))
                            $fs = [IO.File]::Open($OutFile, 'Open', 'ReadWrite')
                            try { $fs.SetLength($have - $trim) } finally { $fs.Close() }
                            Write-Host ("  resume after trim; have={0:N1} MB" -f (((Get-Item -LiteralPath $OutFile).Length) / 1MB)) -ForegroundColor DarkGray
                        }
                    }
                    $connect = [Math]::Min(60, [Math]::Max(15, [int]($TimeoutSec / 10)))
                    & curl.exe -L --http1.1 --connect-timeout $connect --retry 0 `
                        --max-time $TimeoutSec -C - -o $OutFile $url
                    if ($LASTEXITCODE -ne 0) {
                        throw "curl exit $LASTEXITCODE"
                    }
                } else {
                    Remove-Item -LiteralPath $OutFile -Force -ErrorAction SilentlyContinue
                    Invoke-WebRequest -Uri $url -OutFile $OutFile -UseBasicParsing -TimeoutSec $TimeoutSec
                }
                if ((Test-Path -LiteralPath $OutFile) -and ((Get-Item -LiteralPath $OutFile).Length -gt 1MB)) {
                    Write-Host ("  OK {0:N1} MB" -f (((Get-Item -LiteralPath $OutFile).Length) / 1MB)) -ForegroundColor Green
                    return $url
                }
                throw 'Downloaded file missing or too small'
            } catch {
                $lastErr = $_
                Write-Host ("  FAIL: " + $_.Exception.Message) -ForegroundColor Yellow
                # keep partial for curl resume; only wipe tiny/corrupt stubs
                if ((Test-Path -LiteralPath $OutFile) -and ((Get-Item -LiteralPath $OutFile).Length -lt 1MB)) {
                    Remove-Item -LiteralPath $OutFile -Force -ErrorAction SilentlyContinue
                }
                Start-Sleep -Seconds ([Math]::Min(5 * $i, 15))
            }
        }
        # next mirror: keep partial only if same content-length family; safer to wipe when switching host
        Remove-Item -LiteralPath $OutFile -Force -ErrorAction SilentlyContinue
    }
    $hint = @(
        'All download URLs failed.',
        'Options:',
        '  1) Open Android Studio -> SDK Manager, install SDK into this ANDROID_HOME, then re-run.',
        '  2) Manually download commandlinetools-win-*_latest.zip (Tencent mirror recommended), then:',
        '       powershell -File package\setup-android-sdk.ps1 -ZipPath D:\path\to\zip',
        '  3) Check proxy / VPN, or raise -DownloadTimeoutSec'
    ) -join [Environment]::NewLine
    throw "$hint`nLast error: $($lastErr.Exception.Message)"
}

function Ensure-MeisAndroidRepositoriesCfg {
    $androidDir = Join-Path $env:USERPROFILE '.android'
    New-Item -ItemType Directory -Path $androidDir -Force | Out-Null
    $cfg = Join-Path $androidDir 'repositories.cfg'
    if (-not (Test-Path -LiteralPath $cfg)) {
        Set-Content -Path $cfg -Value '' -Encoding ASCII
    }
}

New-Item -ItemType Directory -Path $SdkRoot -Force | Out-Null
$cmdlineLatest = Join-Path $SdkRoot 'cmdline-tools\latest'
$sdkmanager = Join-Path $cmdlineLatest 'bin\sdkmanager.bat'

if (-not (Test-Path -LiteralPath $sdkmanager)) {
    $tmp = Join-Path $env:TEMP ('meis-cmdline-tools-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $tmp -Force | Out-Null
    $zip = Join-Path $tmp 'cmdline-tools.zip'
    try {
        if ($ZipPath) {
            if (-not (Test-Path -LiteralPath $ZipPath)) {
                throw "ZipPath not found: $ZipPath"
            }
            Write-Host "Using local zip: $ZipPath" -ForegroundColor Cyan
            Copy-Item -LiteralPath $ZipPath -Destination $zip -Force
        } else {
            $urls = Get-MeisCmdlineToolsCandidateUrls -ZipName $CmdlineToolsZipName -ExplicitUrl $CmdlineToolsZipUrl
            [void](Save-MeisWebFile -Urls $urls -OutFile $zip -TimeoutSec $DownloadTimeoutSec -Retries $DownloadRetries)
        }
        Expand-Archive -Path $zip -DestinationPath $tmp -Force
        # Zip usually contains a top-level "cmdline-tools" folder
        $extracted = Join-Path $tmp 'cmdline-tools'
        if (-not (Test-Path -LiteralPath $extracted)) {
            $extracted = Get-ChildItem $tmp -Directory | Where-Object { $_.Name -ne $null } | Select-Object -First 1 -ExpandProperty FullName
        }
        if (-not $extracted -or -not (Test-Path -LiteralPath $extracted)) {
            throw "Unexpected zip layout under $tmp"
        }
        $destParent = Join-Path $SdkRoot 'cmdline-tools'
        if (Test-Path -LiteralPath (Join-Path $destParent 'latest')) {
            Remove-Item (Join-Path $destParent 'latest') -Recurse -Force
        }
        New-Item -ItemType Directory -Path $destParent -Force | Out-Null
        Move-Item $extracted (Join-Path $destParent 'latest')
        if (-not (Test-Path -LiteralPath $sdkmanager)) {
            throw "sdkmanager.bat missing after extract: $sdkmanager"
        }
        Write-Host "cmdline-tools installed: $cmdlineLatest" -ForegroundColor Green
    } finally {
        Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
    }
} else {
    Write-Host "cmdline-tools already present" -ForegroundColor DarkGray
}

$env:ANDROID_HOME = $SdkRoot
$env:ANDROID_SDK_ROOT = $SdkRoot
$env:Path = "$(Join-Path $cmdlineLatest 'bin');$(Join-Path $SdkRoot 'platform-tools');$env:Path"

Ensure-MeisAndroidRepositoriesCfg

Write-Host 'Accepting SDK licenses ...' -ForegroundColor Cyan
$yesFile = Join-Path $env:TEMP 'meis-sdk-licenses-yes.txt'
# sdkmanager prompts many times; feed enough y
@(1..100 | ForEach-Object { 'y' }) | Set-Content -Path $yesFile -Encoding ASCII
Get-Content $yesFile | & $sdkmanager --sdk_root="$SdkRoot" --licenses
Remove-Item $yesFile -Force -ErrorAction SilentlyContinue

$packages = @(
    'platform-tools',
    'platforms;android-35',
    'platforms;android-34',
    'build-tools;35.0.0',
    'build-tools;34.0.0'
)
Write-Host ('Installing: ' + ($packages -join ', ')) -ForegroundColor Cyan
Write-Host 'Note: package downloads still hit Google unless you use a system proxy; Studio SDK Manager is an alternative.' -ForegroundColor DarkGray
& $sdkmanager --sdk_root="$SdkRoot" $packages
if ($LASTEXITCODE -ne 0) {
    Write-Host 'WARN: bulk install returned non-zero; trying packages one by one...' -ForegroundColor Yellow
    foreach ($pkg in $packages) {
        Write-Host "  sdkmanager $pkg" -ForegroundColor DarkGray
        & $sdkmanager --sdk_root="$SdkRoot" $pkg
    }
}

# platform-tools often fails TLS handshake to Google; fall back to mirror zip via curl
$adb = Join-Path $SdkRoot 'platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $adb)) {
    Write-Host 'platform-tools missing after sdkmanager; trying Tencent/Google zip via curl ...' -ForegroundColor Yellow
    $ptTmp = Join-Path $env:TEMP ('meis-platform-tools-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $ptTmp -Force | Out-Null
    $ptZip = Join-Path $ptTmp 'platform-tools.zip'
    try {
        $ptUrls = @(
            'https://mirrors.cloud.tencent.com/AndroidSDK/platform-tools-latest-windows.zip',
            'https://dl.google.com/android/repository/platform-tools-latest-windows.zip'
        )
        [void](Save-MeisWebFile -Urls $ptUrls -OutFile $ptZip -TimeoutSec $DownloadTimeoutSec -Retries $DownloadRetries)
        Expand-Archive -Path $ptZip -DestinationPath $ptTmp -Force
        $ptSrc = Join-Path $ptTmp 'platform-tools'
        if (-not (Test-Path -LiteralPath $ptSrc)) {
            throw 'Unexpected platform-tools zip layout'
        }
        $ptDest = Join-Path $SdkRoot 'platform-tools'
        if (Test-Path -LiteralPath $ptDest) { Remove-Item $ptDest -Recurse -Force }
        Move-Item $ptSrc $ptDest
        Write-Host "platform-tools installed: $ptDest" -ForegroundColor Green
    } finally {
        Remove-Item $ptTmp -Recurse -Force -ErrorAction SilentlyContinue
    }
}

# Flutter current stable expects side-by-side NDK 28.2.13676358 (= android-ndk-r28c)
# Incomplete .installer leftovers from failed sdkmanager must be replaced via mirror zip.
$ndkVer = '28.2.13676358'
$ndkDir = Join-Path $SdkRoot "ndk\$ndkVer"
$ndkOk = (Test-Path -LiteralPath (Join-Path $ndkDir 'source.properties')) -and
    (Test-Path -LiteralPath (Join-Path $ndkDir 'ndk-build.cmd'))
if (-not $ndkOk) {
    Write-Host "NDK $ndkVer missing/incomplete; installing android-ndk-r28c via mirror (~700MB) ..." -ForegroundColor Yellow
    if (Test-Path -LiteralPath $ndkDir) { Remove-Item $ndkDir -Recurse -Force }
    $ndkTmp = Join-Path $env:TEMP ('meis-ndk-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $ndkTmp -Force | Out-Null
    $ndkZip = Join-Path $ndkTmp 'android-ndk-r28c-windows.zip'
    try {
        $ndkUrls = @(
            'https://mirrors.cloud.tencent.com/AndroidSDK/android-ndk-r28c-windows.zip',
            'https://dl.google.com/android/repository/android-ndk-r28c-windows.zip'
        )
        [void](Save-MeisWebFile -Urls $ndkUrls -OutFile $ndkZip -TimeoutSec ([Math]::Max($DownloadTimeoutSec, 1800)) -Retries $DownloadRetries)
        Expand-Archive -Path $ndkZip -DestinationPath $ndkTmp -Force
        $ndkSrc = Get-ChildItem $ndkTmp -Directory | Where-Object { $_.Name -like 'android-ndk*' } | Select-Object -First 1
        if (-not $ndkSrc) { throw 'Unexpected NDK zip layout' }
        New-Item -ItemType Directory -Path (Split-Path $ndkDir -Parent) -Force | Out-Null
        Move-Item $ndkSrc.FullName $ndkDir
        if (-not ((Test-Path (Join-Path $ndkDir 'source.properties')) -and (Test-Path (Join-Path $ndkDir 'ndk-build.cmd')))) {
            throw "NDK extract incomplete under $ndkDir"
        }
        Write-Host "NDK installed: $ndkDir" -ForegroundColor Green
    } finally {
        Remove-Item $ndkTmp -Recurse -Force -ErrorAction SilentlyContinue
    }
} else {
    Write-Host "NDK $ndkVer already present" -ForegroundColor DarkGray
}

# Persist ANDROID_HOME into env.txt
$envFile = Join-Path $pkgDir 'env.txt'
if (Test-Path -LiteralPath $envFile) {
    $raw = Get-Content $envFile -Encoding UTF8
    $found = $false
    $newLines = foreach ($line in $raw) {
        if ($line -match '^\s*ANDROID_HOME\s*=') {
            $found = $true
            "ANDROID_HOME=$SdkRoot"
        } else {
            $line
        }
    }
    if (-not $found) {
        $newLines = @($newLines) + @('', "ANDROID_HOME=$SdkRoot")
    }
    Set-Content -Path $envFile -Value $newLines -Encoding UTF8
    Write-Host "Updated package\env.txt ANDROID_HOME=$SdkRoot" -ForegroundColor Green
}

Write-Host ''
Write-Host "Android SDK ready: $SdkRoot" -ForegroundColor Cyan
Write-Host 'Next: double-click 打包apk.bat' -ForegroundColor DarkGray
