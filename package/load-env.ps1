# Shared loader for package\env.txt (ASCII only to avoid PS encoding issues)
function Import-MeisPackageEnv {
    param(
        [string]$EnvFile = (Join-Path $PSScriptRoot 'env.txt')
    )
    if (-not (Test-Path $EnvFile)) {
        Write-Host "WARN: env.txt not found: $EnvFile (using defaults / system env)" -ForegroundColor Yellow
        return
    }
    Get-Content $EnvFile -Encoding UTF8 | ForEach-Object {
        $line = $_.Trim()
        if (-not $line) { return }
        if ($line.StartsWith('#')) { return }
        $eq = $line.IndexOf('=')
        if ($eq -lt 1) { return }
        $key = $line.Substring(0, $eq).Trim()
        $val = $line.Substring($eq + 1).Trim()
        if (($val.StartsWith('"') -and $val.EndsWith('"')) -or ($val.StartsWith("'") -and $val.EndsWith("'"))) {
            $val = $val.Substring(1, $val.Length - 2)
        }
        if (-not $key) { return }
        Set-Item -Path "Env:$key" -Value $val
    }
    # aliases
    if ($env:JAVA_HOME) { $env:MEIS_JAVA_HOME = $env:JAVA_HOME }
    if ($env:MAVEN_HOME) { $env:MEIS_MAVEN_HOME = $env:MAVEN_HOME }
    if ($env:MAVEN_CMD) { $env:MEIS_MAVEN_CMD = $env:MAVEN_CMD }
    if ($env:OPS_TOKEN) { $env:MEIS_OPS_TOKEN = $env:OPS_TOKEN }
}

function Resolve-MeisPackageJavaHome {
    $tried = New-Object System.Collections.Generic.List[string]
    foreach ($c in @(
        $env:MEIS_JAVA_HOME,
        $env:JAVA_HOME
    )) {
        if (-not $c) { continue }
        [void]$tried.Add($c)
        $java = Join-Path $c 'bin\java.exe'
        if (Test-Path -LiteralPath $java) { return $c }
    }
    # Common installs (only if env.txt path missing/wrong)
    foreach ($c in @(
        'C:\Program Files\Java\jdk-17',
        'C:\Program Files\Eclipse Adoptium\jdk-17.0.14+7',
        'C:\Program Files\Microsoft\jdk-17.0.14-hotspot'
    )) {
        [void]$tried.Add($c)
        $java = Join-Path $c 'bin\java.exe'
        if (Test-Path -LiteralPath $java) {
            Write-Host "WARN: using fallback JDK: $c (fix JAVA_HOME in package\env.txt)" -ForegroundColor Yellow
            return $c
        }
    }
    $hint = if ($tried.Count -gt 0) { ' Tried: ' + ($tried -join ' | ') } else { ' JAVA_HOME is empty.' }
    throw ('JDK not found. Set JAVA_HOME in package\env.txt to a folder that contains bin\java.exe.' + $hint)
}

function Resolve-MeisPackageMaven {
    if ($env:MEIS_MAVEN_CMD -and (Test-Path $env:MEIS_MAVEN_CMD)) {
        return $env:MEIS_MAVEN_CMD
    }
    if ($env:MAVEN_CMD -and (Test-Path $env:MAVEN_CMD)) {
        return $env:MAVEN_CMD
    }
    foreach ($c in @($env:MEIS_MAVEN_HOME, $env:MAVEN_HOME)) {
        if (-not $c) { continue }
        $cmd = Join-Path $c 'bin\mvn.cmd'
        if (Test-Path $cmd) { return $cmd }
    }
    $inPath = Get-Command mvn -ErrorAction SilentlyContinue
    if ($inPath) { return $inPath.Source }
    throw 'Maven not found. Set MAVEN_HOME or MAVEN_CMD in package\env.txt'
}

function Resolve-MeisPackageJavaExe {
    $home = Resolve-MeisPackageJavaHome
    return (Join-Path $home 'bin\java.exe')
}

function Test-MeisAndroidSdkRoot([string]$SdkRoot) {
    if (-not $SdkRoot) { return $false }
    $sdkRoot = $SdkRoot.Trim().Trim('"').Trim("'")
    if (-not (Test-Path -LiteralPath $sdkRoot)) { return $false }
    # platform-tools or platforms indicates a usable SDK tree
    if (Test-Path -LiteralPath (Join-Path $sdkRoot 'platform-tools')) { return $true }
    if (Test-Path -LiteralPath (Join-Path $sdkRoot 'platforms')) { return $true }
    if (Test-Path -LiteralPath (Join-Path $sdkRoot 'cmdline-tools')) { return $true }
    return $false
}

function Resolve-MeisAndroidSdkHome {
    $tried = New-Object System.Collections.Generic.List[string]
    foreach ($c in @(
            $env:ANDROID_HOME,
            $env:ANDROID_SDK_ROOT,
            $env:MEIS_ANDROID_HOME
        )) {
        if (-not $c) { continue }
        $c = $c.Trim().Trim('"').Trim("'")
        [void]$tried.Add($c)
        if (Test-MeisAndroidSdkRoot $c) { return $c }
    }
    foreach ($c in @(
            (Join-Path $env:LOCALAPPDATA 'Android\Sdk'),
            (Join-Path $env:USERPROFILE 'AppData\Local\Android\Sdk'),
            'C:\Android\Sdk',
            'D:\Android\Sdk',
            'E:\Android\Sdk'
        )) {
        [void]$tried.Add($c)
        if (Test-MeisAndroidSdkRoot $c) { return $c }
    }
    $hint = if ($tried.Count -gt 0) { ' Tried: ' + ($tried -join ' | ') } else { '' }
    throw ('Android SDK not found. Run package\安装AndroidSDK.bat once, or set ANDROID_HOME in package\env.txt.' + $hint)
}

function Write-MeisAndroidLocalProperties {
    param(
        [Parameter(Mandatory = $true)][string]$MobileDir,
        [Parameter(Mandatory = $true)][string]$SdkRoot,
        [string]$FlutterRoot = ''
    )
    $lp = Join-Path $MobileDir 'android\local.properties'
    $dir = Split-Path $lp -Parent
    New-Item -ItemType Directory -Path $dir -Force | Out-Null
    # Forward slashes work with Gradle on Windows and avoid escape issues
    $sdkProp = ($SdkRoot -replace '\\', '/')
    $lines = @("sdk.dir=$sdkProp")
    if ($FlutterRoot) {
        $flProp = ($FlutterRoot -replace '\\', '/')
        $lines += "flutter.sdk=$flProp"
    }
    Set-Content -Path $lp -Value $lines -Encoding ASCII
}

function Set-MeisAndroidGradleJavaHome {
    param(
        [Parameter(Mandatory = $true)][string]$MobileDir,
        [Parameter(Mandatory = $true)][string]$JdkHome
    )
    $props = Join-Path $MobileDir 'android\gradle.properties'
    if (-not (Test-Path -LiteralPath $props)) {
        throw "Missing gradle.properties: $props"
    }
    $javaHomeProp = ($JdkHome -replace '\\', '/')
    $raw = Get-Content $props -Encoding UTF8
    $found = $false
    $out = foreach ($line in $raw) {
        if ($line -match '^\s*org\.gradle\.java\.home\s*=') {
            $found = $true
            "org.gradle.java.home=$javaHomeProp"
        } else {
            $line
        }
    }
    if (-not $found) {
        $out = @($out) + @('', "# Force JDK 17+ for Gradle (system JAVA_HOME may still be 8)", "org.gradle.java.home=$javaHomeProp")
    }
    Set-Content -Path $props -Value $out -Encoding UTF8
}

# Strip accidental quotes / whitespace from Flutter China mirror URLs (common Windows env mistake).
function Set-MeisFlutterMirrorEnv {
    param(
        [string]$Name,
        [string]$Default
    )
    $v = [Environment]::GetEnvironmentVariable($Name, 'Process')
    if (-not $v) { $v = [Environment]::GetEnvironmentVariable($Name, 'User') }
    if (-not $v) { $v = [Environment]::GetEnvironmentVariable($Name, 'Machine') }
    if ($v) {
        $v = $v.Trim().Trim('"').Trim("'").Trim()
    }
    if (-not $v -or $v -notmatch '^https?://') {
        $v = $Default
    }
    Set-Item -Path "Env:$Name" -Value $v
    return $v
}

function Resolve-MeisPackageNpm {
    if ($env:NPM_CMD -and (Test-Path $env:NPM_CMD)) {
        return $env:NPM_CMD
    }
    foreach ($c in @($env:NODE_HOME, $env:MEIS_NODE_HOME)) {
        if (-not $c) { continue }
        foreach ($name in @('npm.cmd', 'npm.exe')) {
            $cmd = Join-Path $c $name
            if (Test-Path $cmd) { return $cmd }
            $cmd = Join-Path $c "bin\$name"
            if (Test-Path $cmd) { return $cmd }
        }
    }
    # Prefer npm.cmd — npm.ps1 runs in PowerShell and inherits Cursor/VS Code debugger attach
    $cmdPath = Get-Command npm.cmd -ErrorAction SilentlyContinue
    if ($cmdPath) { return $cmdPath.Source }
    $inPath = Get-Command npm -ErrorAction SilentlyContinue
    if ($inPath) {
        $src = [string]$inPath.Source
        if ($src -like '*.ps1') {
            $asCmd = [System.IO.Path]::ChangeExtension($src, '.cmd')
            if (Test-Path $asCmd) { return $asCmd }
        }
        return $src
    }
    throw 'npm not found. Install Node.js LTS, or set NODE_HOME / NPM_CMD in package\env.txt'
}

# Run npm via cmd.exe with debugger env stripped (avoids Cursor/VS Code auto-attach).
function Invoke-MeisPackageNpm {
    param(
        [Parameter(Mandatory = $true)][string]$WorkingDirectory,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )
    $npm = Resolve-MeisPackageNpm
    if ($npm -like '*.ps1') {
        $asCmd = [System.IO.Path]::ChangeExtension($npm, '.cmd')
        if (Test-Path $asCmd) { $npm = $asCmd }
    }
    $argLine = ($Arguments | ForEach-Object {
        $a = [string]$_
        if ($a -match '[\s"]') { '"' + ($a -replace '"', '""') + '"' } else { $a }
    }) -join ' '
    Write-Host "  npm (isolated): $npm $argLine" -ForegroundColor DarkGray

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = "$env:SystemRoot\System32\cmd.exe"
    # Clear debugger vars inside cmd so npm/node children never see them
    $psi.Arguments = "/d /s /c `"set NODE_OPTIONS=& set VSCODE_INSPECTOR_OPTIONS=& set NODE_DEBUG=& set NODE_DEBUG_OPTION=& `"$npm`" $argLine`""
    $psi.WorkingDirectory = $WorkingDirectory
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $false
    foreach ($key in @('NODE_OPTIONS', 'VSCODE_INSPECTOR_OPTIONS', 'NODE_DEBUG', 'NODE_DEBUG_OPTION')) {
        try { $psi.EnvironmentVariables[$key] = '' } catch { }
    }
    $proc = [System.Diagnostics.Process]::Start($psi)
    if (-not $proc) { throw "Failed to start npm: $npm" }
    $proc.WaitForExit()
    if ($proc.ExitCode -ne 0) {
        throw "npm failed, exit=$($proc.ExitCode) ($npm $argLine)"
    }
}
