# Install Android SDK command-line tools + packages needed by flutter build apk.
# Default location: %LOCALAPPDATA%\Android\Sdk
# Then writes ANDROID_HOME into package\env.txt if missing.
param(
    [string]$SdkRoot = '',
    [string]$CmdlineToolsZipUrl = 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip'
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

New-Item -ItemType Directory -Path $SdkRoot -Force | Out-Null
$cmdlineLatest = Join-Path $SdkRoot 'cmdline-tools\latest'
$sdkmanager = Join-Path $cmdlineLatest 'bin\sdkmanager.bat'

if (-not (Test-Path -LiteralPath $sdkmanager)) {
    $tmp = Join-Path $env:TEMP ('meis-cmdline-tools-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $tmp -Force | Out-Null
    $zip = Join-Path $tmp 'cmdline-tools.zip'
    Write-Host "Downloading commandlinetools ..." -ForegroundColor Cyan
    Write-Host "  $CmdlineToolsZipUrl" -ForegroundColor DarkGray
    try {
        Invoke-WebRequest -Uri $CmdlineToolsZipUrl -OutFile $zip -UseBasicParsing
    } catch {
        throw "Download failed. Check network / proxy, or install Android Studio and set ANDROID_HOME. $($_.Exception.Message)"
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
    Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
    if (-not (Test-Path -LiteralPath $sdkmanager)) {
        throw "sdkmanager.bat missing after extract: $sdkmanager"
    }
    Write-Host "cmdline-tools installed: $cmdlineLatest" -ForegroundColor Green
} else {
    Write-Host "cmdline-tools already present" -ForegroundColor DarkGray
}

$env:ANDROID_HOME = $SdkRoot
$env:ANDROID_SDK_ROOT = $SdkRoot
$env:Path = "$(Join-Path $cmdlineLatest 'bin');$(Join-Path $SdkRoot 'platform-tools');$env:Path"

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
& $sdkmanager --sdk_root="$SdkRoot" $packages
if ($LASTEXITCODE -ne 0) {
    Write-Host 'WARN: bulk install returned non-zero; trying packages one by one...' -ForegroundColor Yellow
    foreach ($pkg in $packages) {
        Write-Host "  sdkmanager $pkg" -ForegroundColor DarkGray
        & $sdkmanager --sdk_root="$SdkRoot" $pkg
    }
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
