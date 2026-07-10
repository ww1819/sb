function Test-SymlinkSupport {
    $dir = Join-Path $env:TEMP "meis-flutter-symlink-test"
    Remove-Item $dir -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Path $dir -Force | Out-Null
    $target = Join-Path $dir "target"
    New-Item -ItemType File -Path $target -Force | Out-Null
    $link = Join-Path $dir "link"
    try {
        New-Item -ItemType SymbolicLink -Path $link -Target $target -ErrorAction Stop | Out-Null
        return $true
    } catch {
        return $false
    } finally {
        Remove-Item $dir -Recurse -Force -ErrorAction SilentlyContinue
    }
}

function Set-DeveloperModeRegistry {
    param([Microsoft.Win32.RegistryHive]$Hive)

    $view = if ([Environment]::Is64BitOperatingSystem) {
        [Microsoft.Win32.RegistryView]::Registry64
    } else {
        [Microsoft.Win32.RegistryView]::Default
    }

    $baseKey = [Microsoft.Win32.RegistryKey]::OpenBaseKey($Hive, $view)
    $key = $baseKey.CreateSubKey(
        'SOFTWARE\Microsoft\Windows\CurrentVersion\AppModelUnlock',
        $true
    )
    $key.SetValue('AllowDevelopmentWithoutDevLicense', 1, [Microsoft.Win32.RegistryValueKind]::DWord)
    $key.Close()
    $baseKey.Close()
}

function Enable-DeveloperMode {
    Write-Host "[MEIS] Enabling Windows Developer Mode (required for Flutter plugins)..."

    try {
        Set-DeveloperModeRegistry -Hive CurrentUser
        Write-Host "  HKCU registry updated"
    } catch {
        Write-Host "  HKCU update failed: $($_.Exception.Message)"
    }

    try {
        Set-DeveloperModeRegistry -Hive LocalMachine
        Write-Host "  HKLM registry updated"
        return $true
    } catch {
        Write-Host "  Admin rights required, requesting UAC elevation..."
        $elevatedCommand = @'
$view = if ([Environment]::Is64BitOperatingSystem) { [Microsoft.Win32.RegistryView]::Registry64 } else { [Microsoft.Win32.RegistryView]::Default }
$baseKey = [Microsoft.Win32.RegistryKey]::OpenBaseKey([Microsoft.Win32.RegistryHive]::LocalMachine, $view)
$key = $baseKey.CreateSubKey('SOFTWARE\Microsoft\Windows\CurrentVersion\AppModelUnlock', $true)
$key.SetValue('AllowDevelopmentWithoutDevLicense', 1, [Microsoft.Win32.RegistryValueKind]::DWord)
$key.Close()
$baseKey.Close()
'@
        $proc = Start-Process powershell -ArgumentList @(
            '-NoProfile',
            '-ExecutionPolicy', 'Bypass',
            '-Command',
            $elevatedCommand
        ) -Verb RunAs -Wait -PassThru
        return $proc.ExitCode -eq 0
    }
}

function Ensure-DeveloperMode {
    if (Test-SymlinkSupport) {
        Write-Host "[OK] Symlink support is ready"
        return
    }

    Write-Host "[WARN] Symlink support is missing (flutter pub get will fail with plugins)"
    [void](Enable-DeveloperMode)

    if (Test-SymlinkSupport) {
        Write-Host "[OK] Symlink support is ready"
        return
    }

    Write-Host ""
    Write-Host "Still cannot create symlinks. Please enable Developer Mode manually:"
    Write-Host "  Settings -> Privacy and security -> For developers -> Developer Mode ON"
    Write-Host "  Or run: start ms-settings:developers"
    Write-Host ""
    Start-Process "ms-settings:developers"
    Read-Host "After enabling Developer Mode, press Enter to continue"

    if (-not (Test-SymlinkSupport)) {
        throw @"
Symlink support is still unavailable.

1. Open Settings -> Developer Mode and turn it ON
2. Re-run scripts\setup-mobile.bat

Admin CMD alternative:
  reg add "HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\AppModelUnlock" /v AllowDevelopmentWithoutDevLicense /t REG_DWORD /d 1 /f
"@
    }

    Write-Host "[OK] Symlink support is ready"
}
