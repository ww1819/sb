# Normalize Flutter China mirror env vars and locate Flutter SDK
param(
    [string]$FlutterRoot = ""
)

$psDir = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0'
$sys32 = Join-Path $env:SystemRoot 'System32'

function Fix-FlutterMirrorEnv {
    param(
        [string]$Name,
        [string]$Default
    )

    $v = $null
    foreach ($scope in @('Process', 'User', 'Machine')) {
        $candidate = [Environment]::GetEnvironmentVariable($Name, $scope)
        if ($candidate) { $v = $candidate; break }
    }

    if ($v) {
        $v = $v.Trim()
        while ($v.Length -gt 0 -and ($v[0] -eq '"' -or $v[0] -eq "'")) {
            $v = $v.Substring(1)
        }
        while ($v.Length -gt 0 -and ($v[-1] -eq '"' -or $v[-1] -eq "'")) {
            $v = $v.Substring(0, $v.Length - 1)
        }
        $v = $v.Trim()
    }

    if (-not $v -or $v -notmatch '^https?://') {
        $v = $Default
    }

    Set-Item -Path "env:$Name" -Value $v
    return $v
}

function Get-FlutterBatCandidates {
    param([string]$HintRoot)

    $candidates = @()

    if ($HintRoot) {
        $candidates += Join-Path $HintRoot 'bin\flutter.bat'
        $candidates += Join-Path $HintRoot 'flutter\bin\flutter.bat'
    }

    if ($env:FLUTTER_ROOT) {
        $candidates += Join-Path $env:FLUTTER_ROOT 'bin\flutter.bat'
    }

    $cmd = Get-Command flutter -ErrorAction SilentlyContinue
    if ($cmd -and $cmd.Source) {
        $candidates += $cmd.Source
    }

    $candidates += @(
        (Join-Path $env:LOCALAPPDATA 'flutter\bin\flutter.bat'),
        (Join-Path $env:LOCALAPPDATA 'flutter\flutter\bin\flutter.bat'),
        'C:\src\flutter\bin\flutter.bat',
        'C:\flutter\bin\flutter.bat',
        'D:\flutter\bin\flutter.bat',
        'E:\flutter\bin\flutter.bat',
        'E:\flutter\flutter\bin\flutter.bat',
        (Join-Path $env:USERPROFILE 'flutter\bin\flutter.bat'),
        (Join-Path $env:USERPROFILE 'development\flutter\bin\flutter.bat'),
        (Join-Path $env:USERPROFILE 'dev\flutter\bin\flutter.bat')
    )

    foreach ($segment in ($env:Path -split ';')) {
        if (-not $segment) { continue }
        $candidates += Join-Path $segment.Trim('"') 'flutter.bat'
    }

    return $candidates | Select-Object -Unique
}

function Find-FlutterBat {
    param([string]$HintRoot)

    foreach ($bat in (Get-FlutterBatCandidates -HintRoot $HintRoot)) {
        if ($bat -and (Test-Path -LiteralPath $bat)) {
            return (Resolve-Path -LiteralPath $bat).Path
        }
    }

    return $null
}

Fix-FlutterMirrorEnv 'PUB_HOSTED_URL' 'https://pub.flutter-io.cn' | Out-Null
Fix-FlutterMirrorEnv 'FLUTTER_STORAGE_BASE_URL' 'https://storage.flutter-io.cn' | Out-Null

$script:FlutterBat = Find-FlutterBat -HintRoot $FlutterRoot
if (-not $script:FlutterBat) {
    $searched = (Get-FlutterBatCandidates -HintRoot $FlutterRoot) -join "`n  - "
    throw @"
Flutter not found. Searched:
  - $searched

Install Flutter and either:
  1. Add its bin folder to PATH, or
  2. Set FLUTTER_ROOT to the SDK directory, or
  3. Pass -FlutterRoot to setup-mobile.ps1 / run-mobile.ps1
"@
}

$script:FlutterRoot = Split-Path (Split-Path $script:FlutterBat -Parent) -Parent
$env:FLUTTER_ROOT = $script:FlutterRoot
$flutterBinDir = Split-Path $script:FlutterBat -Parent
$env:Path = "$psDir;$sys32;$flutterBinDir;$env:Path"
