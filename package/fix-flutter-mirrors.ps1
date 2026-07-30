# Fix quoted Flutter China mirror env vars (User + Process).
# Wrong:  "https://storage.flutter-io.cn"
# Right:  https://storage.flutter-io.cn
$ErrorActionPreference = 'Stop'

$map = @{
    'PUB_HOSTED_URL'            = 'https://pub.flutter-io.cn'
    'FLUTTER_STORAGE_BASE_URL'  = 'https://storage.flutter-io.cn'
}

foreach ($name in $map.Keys) {
    $want = $map[$name]
    foreach ($scope in @('User', 'Machine')) {
        $cur = [Environment]::GetEnvironmentVariable($name, $scope)
        if (-not $cur) { continue }
        $clean = $cur.Trim().Trim('"').Trim("'").Trim()
        if ($clean -ne $cur -or $clean -notmatch '^https?://') {
            try {
                if ($clean -notmatch '^https?://') { $clean = $want }
                [Environment]::SetEnvironmentVariable($name, $clean, $scope)
                Write-Host "Fixed $scope $name -> $clean" -ForegroundColor Green
            } catch {
                Write-Host "WARN: cannot write $scope $name (need Admin for Machine): $($_.Exception.Message)" -ForegroundColor Yellow
            }
        } else {
            Write-Host "OK $scope $name = $clean" -ForegroundColor DarkGray
        }
    }
    # Always set process for current session
    Set-Item -Path "Env:$name" -Value $want
    Write-Host "Process $name -> $want" -ForegroundColor Cyan
}

Write-Host ''
Write-Host 'Done. Close this window and open a NEW cmd/PowerShell, then run:' -ForegroundColor Cyan
Write-Host '  cd E:\workspace\sb\meis-mobile'
Write-Host '  flutter build apk --release'
Write-Host 'Or double-click package\打包apk.bat'
