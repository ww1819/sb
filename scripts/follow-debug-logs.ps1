# Follow MEIS debug logs in terminal (backend *.debug.*.log + highlight errors)
param(
    [switch]$ErrorsOnly,
    [int]$PollMs = 500
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\meis-services.ps1"

$logDir = Join-Path $script:MeisRoot 'logs'
if (-not (Test-Path $logDir)) {
    New-Item -ItemType Directory -Path $logDir | Out-Null
}

function Test-LogLineInteresting {
    param([string]$Line)
    if (-not $ErrorsOnly) { return $true }
    return $Line -match '(?i)(ERROR|Exception|WARN|Failed|refused|Caused by:)'
}

function Get-LogColor {
    param([string]$Line)
    if ($Line -match '(?i)ERROR|Exception|Failed|refused') { return 'Red' }
    if ($Line -match '(?i)WARN') { return 'Yellow' }
    return 'Gray'
}

$positions = @{}
$patterns = @('*.debug.out.log', '*.debug.err.log')

Write-Host '=== MEIS debug log monitor (Ctrl+C to stop) ===' -ForegroundColor Cyan
Write-Host "Log dir: $logDir"
Write-Host 'Frontend (Vite) errors appear in the npm run dev terminal.' -ForegroundColor Cyan
Write-Host ''

while ($true) {
    foreach ($pattern in $patterns) {
        Get-ChildItem $logDir -Filter $pattern -ErrorAction SilentlyContinue | ForEach-Object {
            $path = $_.FullName
            if (-not $positions.ContainsKey($path)) {
                $positions[$path] = [math]::Max(0, $_.Length - 4096)
            }

            if (-not (Test-Path $path)) { return }

            $file = Get-Item $path
            if ($file.Length -lt $positions[$path]) {
                $positions[$path] = 0
            }
            if ($file.Length -le $positions[$path]) { return }

            $stream = [System.IO.File]::Open($path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
            try {
                $stream.Position = $positions[$path]
                $reader = New-Object System.IO.StreamReader($stream)
                while ($null -ne ($line = $reader.ReadLine())) {
                    if (-not (Test-LogLineInteresting $line)) { continue }
                    $tag = ($file.BaseName -replace '\.(debug\.(out|err))$', '')
                    $color = Get-LogColor $line
                    Write-Host "[$tag] $line" -ForegroundColor $color
                }
                $positions[$path] = $stream.Position
            } finally {
                $stream.Dispose()
            }
        }
    }
    Start-Sleep -Milliseconds $PollMs
}
