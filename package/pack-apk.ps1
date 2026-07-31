# Build meis-mobile Android release APK -> package\apk\
# Configure FLUTTER_ROOT in package\env.txt if flutter is not on PATH.
param(
    [ValidateSet('release', 'debug', 'profile')]
    [string]$Mode = 'release',
    [switch]$SplitPerAbi
)

$ErrorActionPreference = 'Stop'
$pkgDir = $PSScriptRoot
$root = Split-Path $pkgDir -Parent
$mobileDir = Join-Path $root 'meis-mobile'
$apkOutDir = Join-Path $pkgDir 'apk'

. (Join-Path $pkgDir 'load-env.ps1')
Import-MeisPackageEnv -EnvFile (Join-Path $pkgDir 'env.txt')

function Find-MeisFlutterBat {
    $candidates = New-Object System.Collections.Generic.List[string]
    foreach ($hint in @($env:FLUTTER_ROOT, $env:MEIS_FLUTTER_ROOT)) {
        if (-not $hint) { continue }
        [void]$candidates.Add((Join-Path $hint 'bin\flutter.bat'))
        [void]$candidates.Add((Join-Path $hint 'flutter\bin\flutter.bat'))
    }
    $cmd = Get-Command flutter.bat -ErrorAction SilentlyContinue
    if ($cmd) { [void]$candidates.Add($cmd.Source) }
    $cmd2 = Get-Command flutter -ErrorAction SilentlyContinue
    if ($cmd2 -and $cmd2.Source) { [void]$candidates.Add($cmd2.Source) }
    foreach ($p in @(
            (Join-Path $env:LOCALAPPDATA 'flutter\bin\flutter.bat'),
            'E:\flutter\bin\flutter.bat',
            'D:\flutter\bin\flutter.bat',
            'C:\flutter\bin\flutter.bat',
            (Join-Path $env:USERPROFILE 'flutter\bin\flutter.bat')
        )) {
        [void]$candidates.Add($p)
    }
    foreach ($bat in ($candidates | Select-Object -Unique)) {
        if ($bat -and (Test-Path -LiteralPath $bat)) {
            return (Resolve-Path -LiteralPath $bat).Path
        }
    }
    return $null
}

# China mirrors — always normalize (User/Machine env often stores "https://..." with quotes)
$pub = Set-MeisFlutterMirrorEnv 'PUB_HOSTED_URL' 'https://pub.flutter-io.cn'
$stor = Set-MeisFlutterMirrorEnv 'FLUTTER_STORAGE_BASE_URL' 'https://storage.flutter-io.cn'
Write-Host "PUB_HOSTED_URL=$pub" -ForegroundColor DarkGray
Write-Host "FLUTTER_STORAGE_BASE_URL=$stor" -ForegroundColor DarkGray

if (-not (Test-Path -LiteralPath $mobileDir)) {
    throw "meis-mobile not found: $mobileDir"
}
if (-not (Test-Path -LiteralPath (Join-Path $mobileDir 'pubspec.yaml'))) {
    throw "pubspec.yaml missing under $mobileDir"
}
if (-not (Test-Path -LiteralPath (Join-Path $mobileDir 'android'))) {
    throw "android/ missing. Run scripts\setup-mobile.bat once, then retry."
}

$flutterBat = Find-MeisFlutterBat
if (-not $flutterBat) {
    throw 'Flutter not found. Set FLUTTER_ROOT in package\env.txt (folder that contains bin\flutter.bat).'
}
$flutterRoot = Split-Path (Split-Path $flutterBat -Parent) -Parent
$env:FLUTTER_ROOT = $flutterRoot
$env:Path = "$(Split-Path $flutterBat -Parent);$env:Path"
Write-Host "FLUTTER=$flutterBat" -ForegroundColor Cyan
Write-Host "Mode=$Mode SplitPerAbi=$SplitPerAbi" -ForegroundColor Cyan

# JDK 17+ required by current Android Gradle Plugin / Gradle 9
try {
    $jdk = Resolve-MeisPackageJavaHome
    $javaExe = Join-Path $jdk 'bin\java.exe'
    if (-not (Test-Path -LiteralPath $javaExe)) {
        throw "java.exe missing under $jdk"
    }
    # java -version writes to stderr; with ErrorAction=Stop PS treats it as terminating — temporarily Continue
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $verOut = & $javaExe -version 2>&1 | ForEach-Object { "$_" }
    } finally {
        $ErrorActionPreference = $prevEap
    }
    $verText = ($verOut -join "`n")
    $major = $null
    if ($verText -match 'version\s+"(\d+)') {
        $major = [int]$Matches[1]
    } elseif ($verText -match 'version\s+(\d+)') {
        $major = [int]$Matches[1]
    }
    if ($null -eq $major -or $major -lt 17) {
        throw "JAVA_HOME must be JDK 17+. Got: $($verText.Trim()) [$jdk]"
    }
    $env:JAVA_HOME = $jdk
    # Put JDK17 first so flutter/gradle never pick system Java 8
    $env:Path = "$jdk\bin;$env:Path"
    Write-Host "JAVA_HOME=$jdk (Java $major)" -ForegroundColor Cyan
    Set-MeisAndroidGradleJavaHome -MobileDir $mobileDir -JdkHome $jdk
    Write-Host "org.gradle.java.home set in android\gradle.properties" -ForegroundColor DarkGray
    # flutter config may fail on older CLI; non-fatal
    try { & $flutterBat config "--jdk-dir=$jdk" 2>&1 | Out-Host } catch { }
} catch {
    throw "Need JDK 17+. Set JAVA_HOME in package\env.txt. $($_.Exception.Message)"
}

# Android SDK required for flutter build apk
try {
    $sdk = Resolve-MeisAndroidSdkHome
    $env:ANDROID_HOME = $sdk
    $env:ANDROID_SDK_ROOT = $sdk
    $env:Path = "$(Join-Path $sdk 'platform-tools');$(Join-Path $sdk 'cmdline-tools\latest\bin');$env:Path"
    Write-Host "ANDROID_HOME=$sdk" -ForegroundColor Cyan
    Write-MeisAndroidLocalProperties -MobileDir $mobileDir -SdkRoot $sdk -FlutterRoot $flutterRoot
    # Persist for Android Studio / dart_build (they often miss process ANDROID_HOME)
    try {
        & $flutterBat config "--android-sdk=$sdk" 2>&1 | Out-Host
    } catch { }
    try {
        [Environment]::SetEnvironmentVariable('ANDROID_HOME', $sdk, 'User')
        [Environment]::SetEnvironmentVariable('ANDROID_SDK_ROOT', $sdk, 'User')
    } catch { }
    # Flutter AGP requires side-by-side NDK; incomplete installs cause InstallFailedException
    $ndkVer = '28.2.13676358'
    $ndkDir = Join-Path $sdk "ndk\$ndkVer"
    $ndkOk = (Test-Path (Join-Path $ndkDir 'source.properties')) -and (Test-Path (Join-Path $ndkDir 'ndk-build.cmd'))
    if (-not $ndkOk) {
        throw "Android NDK $ndkVer missing/incomplete under $ndkDir. Run package\安装AndroidSDK.bat (downloads NDK via Tencent mirror), then retry."
    }
    Write-Host "NDK=$ndkDir" -ForegroundColor DarkGray
} catch {
    throw "$($_.Exception.Message)"
}

# Ensure Gradle wrapper uses a mirror JDK can trust (services.gradle.org often PKIX-fails here)
$wrapperProps = Join-Path $mobileDir 'android\gradle\wrapper\gradle-wrapper.properties'
if (Test-Path -LiteralPath $wrapperProps) {
    $txt = Get-Content $wrapperProps -Raw -Encoding UTF8
    if ($txt -match 'services\.gradle\.org') {
        $txt2 = $txt -replace 'https\\://services\.gradle\.org/distributions/', 'https\://mirrors.cloud.tencent.com/gradle/'
        Set-Content -Path $wrapperProps -Value $txt2.TrimEnd() -Encoding UTF8
        Write-Host 'Gradle distributionUrl -> Tencent mirror (PKIX workaround)' -ForegroundColor Yellow
    }
}

# Force plugin/deps Maven lookups through China mirrors (blocks TLS failures to google/maven central)
# Note: --init-script cannot go in GRADLE_OPTS (JVM opts). Use ~/.gradle/init.d instead.
$mirrorInit = Join-Path $mobileDir 'android\init.mirror.gradle'
if (Test-Path -LiteralPath $mirrorInit) {
    $initDir = Join-Path $env:USERPROFILE '.gradle\init.d'
    New-Item -ItemType Directory -Path $initDir -Force | Out-Null
    $destInit = Join-Path $initDir 'meis-android-mirror.gradle'
    Copy-Item -LiteralPath $mirrorInit -Destination $destInit -Force
    Write-Host "Gradle init.d mirror: $destInit" -ForegroundColor DarkGray
}

# Clear stale Gradle locks from previously hung builds (Timeout waiting to lock buildLogic.lock)
$androidDir = Join-Path $mobileDir 'android'
$gradlew = Join-Path $androidDir 'gradlew.bat'
if (Test-Path -LiteralPath $gradlew) {
    Write-Host 'Stopping Gradle daemons ...' -ForegroundColor DarkGray
    Push-Location $androidDir
    try {
        & cmd.exe /c "`"$gradlew`" --stop" | Out-Host
    } catch { }
    finally { Pop-Location }
}
Get-ChildItem (Join-Path $androidDir '.gradle') -Recurse -Filter '*.lock' -ErrorAction SilentlyContinue |
    ForEach-Object {
        Write-Host ("  remove stale lock: " + $_.Name) -ForegroundColor Yellow
        Remove-Item $_.FullName -Force -ErrorAction SilentlyContinue
    }

# Cap Gradle heap if still using Flutter template defaults (prevents GC thrashing)
$gp = Join-Path $androidDir 'gradle.properties'
if (Test-Path -LiteralPath $gp) {
    $gpLines = Get-Content $gp -Encoding UTF8
    $gpOut = foreach ($line in $gpLines) {
        if ($line -match '^\s*org\.gradle\.jvmargs\s*=.*-Xmx([8-9]|[1-9]\d+)[Gg]') {
            'org.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m -XX:ReservedCodeCacheSize=256m -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8'
        } else {
            $line
        }
    }
    Set-Content -Path $gp -Value $gpOut -Encoding UTF8
}

# Pub cache on same drive as project (fixes Kotlin "different roots" C: vs E:)
$pubCache = $env:PUB_CACHE
if (-not $pubCache) {
    $pubCache = Join-Path $root '.pub-cache'
}
$env:PUB_CACHE = $pubCache
New-Item -ItemType Directory -Path $pubCache -Force | Out-Null
Write-Host "PUB_CACHE=$pubCache" -ForegroundColor Cyan

# Disable Kotlin incremental in gradle.properties if missing
$gp = Join-Path $androidDir 'gradle.properties'
if (Test-Path -LiteralPath $gp) {
    $gpText = Get-Content $gp -Raw -Encoding UTF8
    if ($gpText -notmatch '(?m)^\s*kotlin\.incremental\s*=') {
        Add-Content -Path $gp -Value "`r`nkotlin.incremental=false`r`nkotlin.incremental.java=false" -Encoding UTF8
        Write-Host 'Added kotlin.incremental=false (cross-drive fix)' -ForegroundColor Yellow
    }
}

New-Item -ItemType Directory -Path $apkOutDir -Force | Out-Null

Push-Location $mobileDir
try {
    Write-Host 'flutter clean (clear broken Kotlin incremental caches) ...' -ForegroundColor Cyan
    & $flutterBat clean
    Write-Host 'flutter pub get ...' -ForegroundColor Cyan
    & $flutterBat pub get
    if ($LASTEXITCODE -ne 0) { throw "flutter pub get failed, exit=$LASTEXITCODE" }

    $args = @('build', 'apk', "--$Mode")
    if ($SplitPerAbi) { $args += '--split-per-abi' }
    Write-Host ("Building: flutter " + ($args -join ' ')) -ForegroundColor Cyan
    & $flutterBat @args
    if ($LASTEXITCODE -ne 0) { throw "flutter build apk failed, exit=$LASTEXITCODE" }
} finally {
    Pop-Location
}

$flutterApkDir = Join-Path $mobileDir 'build\app\outputs\flutter-apk'
if (-not (Test-Path -LiteralPath $flutterApkDir)) {
    throw "APK output folder missing: $flutterApkDir"
}

# Clean previous copies in package\apk (keep .gitignore)
Get-ChildItem $apkOutDir -Filter '*.apk' -ErrorAction SilentlyContinue | Remove-Item -Force

$copied = @()
Get-ChildItem $flutterApkDir -Filter '*.apk' | ForEach-Object {
    $dest = Join-Path $apkOutDir $_.Name
    Copy-Item $_.FullName $dest -Force
    $mb = [math]::Round($_.Length / 1MB, 2)
    Write-Host ("  OK {0} ({1} MB)" -f $_.Name, $mb) -ForegroundColor Green
    $copied += $dest
}

if ($copied.Count -eq 0) {
    throw "No APK found under $flutterApkDir"
}

Write-Host ''
Write-Host "Done. APK copied to: $apkOutDir" -ForegroundColor Cyan
Write-Host 'Install the apk on phone, then set LAN IP + port (default 5174).' -ForegroundColor DarkGray
