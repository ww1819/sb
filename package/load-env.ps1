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
    $inPath = Get-Command npm -ErrorAction SilentlyContinue
    if ($inPath) { return $inPath.Source }
    throw 'npm not found. Install Node.js LTS, or set NODE_HOME / NPM_CMD in package\env.txt'
}
