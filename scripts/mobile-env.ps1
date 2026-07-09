# Normalize Flutter China mirror env vars (strip accidental quotes)
param(
    [string]$FlutterRoot = "E:\flutter"
)

$psDir = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0'
$sys32 = Join-Path $env:SystemRoot 'System32'
$env:FLUTTER_ROOT = $FlutterRoot
$env:Path = "$psDir;$sys32;$(Join-Path $FlutterRoot 'bin');$env:Path"

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

Fix-FlutterMirrorEnv 'PUB_HOSTED_URL' 'https://pub.flutter-io.cn' | Out-Null
Fix-FlutterMirrorEnv 'FLUTTER_STORAGE_BASE_URL' 'https://storage.flutter-io.cn' | Out-Null

$script:FlutterBat = Join-Path $FlutterRoot 'bin\flutter.bat'
if (-not (Test-Path $script:FlutterBat)) {
    throw "Flutter not found: $script:FlutterBat"
}
