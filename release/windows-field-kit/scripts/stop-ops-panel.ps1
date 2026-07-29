# Stop MEIS ops panel (does not stop backend JARs)
$ErrorActionPreference = 'Stop'
Get-CimInstance Win32_Process -Filter "Name='powershell.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -like '*ops-panel.ps1*' } |
    ForEach-Object {
        Write-Host "Stopping ops panel PID $($_.ProcessId)"
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
    }
Write-Host 'Done. Backend services (if any) are still running.'
