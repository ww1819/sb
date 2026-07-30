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
    foreach ($c in @(
        $env:MEIS_JAVA_HOME,
        $env:JAVA_HOME
    )) {
        if (-not $c) { continue }
        $java = Join-Path $c 'bin\java.exe'
        if (Test-Path $java) { return $c }
    }
    throw 'JDK not found. Set JAVA_HOME in package\env.txt'
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
