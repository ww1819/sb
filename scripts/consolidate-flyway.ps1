# Consolidate Flyway migrations into tables / extensions / seed_data
param(
    [Parameter(Mandatory = $true)][string]$SourceDir,
    [Parameter(Mandatory = $true)][string]$TargetDir
)

function Get-VersionNumber([string]$name) {
    if ($name -match '^V(\d+)__') { return [int]$matches[1] }
    return 9999
}

function Split-SqlStatements([string]$content) {
    $statements = New-Object System.Collections.ArrayList
    $current = New-Object System.Text.StringBuilder
    $lines = $content -split "`r?`n"
    foreach ($line in $lines) {
        if ($line -match '^\s*--' -and $current.Length -eq 0) {
            [void]$current.AppendLine($line)
            continue
        }
        [void]$current.AppendLine($line)
        if ($line -match ';\s*$') {
            $stmt = $current.ToString().Trim()
            if ($stmt) { [void]$statements.Add($stmt) }
            $current = New-Object System.Text.StringBuilder
        }
    }
    $tail = $current.ToString().Trim()
    if ($tail) { [void]$statements.Add($tail) }
    return $statements
}

function Classify-Statement([string]$stmt) {
    $body = ($stmt -replace '(?m)^\s*--.*$', '').Trim()
    if (-not $body) { return 'skip' }
    $upper = $body.ToUpperInvariant()
    if ($upper -match '^\s*CREATE\s+EXTENSION\b') { return 'tables' }
    if ($upper -match '^\s*CREATE\s+(OR\s+REPLACE\s+)?(TABLE|VIEW)\b') { return 'tables' }
    if ($upper -match '^\s*ALTER\s+TABLE\b') { return 'extensions' }
    if ($upper -match '^\s*CREATE\s+(UNIQUE\s+)?INDEX\b') { return 'extensions' }
    if ($upper -match '^\s*COMMENT\s+ON\b') { return 'extensions' }
    if ($upper -match '^\s*(INSERT|UPDATE|DELETE)\b') { return 'data' }
    return 'tables'
}

New-Item -ItemType Directory -Force -Path $TargetDir | Out-Null
$tables = New-Object System.Collections.ArrayList
$extensions = New-Object System.Collections.ArrayList
$data = New-Object System.Collections.ArrayList

$files = Get-ChildItem $SourceDir -Filter 'V*.sql' | Sort-Object { Get-VersionNumber $_.Name }
foreach ($file in $files) {
    $content = Get-Content $file.FullName -Raw -Encoding UTF8
    foreach ($stmt in (Split-SqlStatements $content)) {
        $kind = Classify-Statement $stmt
        if ($kind -eq 'skip') { continue }
        $chunk = "-- [$($file.Name)]`r`n$stmt`r`n"
        switch ($kind) {
            'tables' { [void]$tables.Add($chunk) }
            'extensions' { [void]$extensions.Add($chunk) }
            'data' { [void]$data.Add($chunk) }
        }
    }
}

$header = @'
-- MEIS consolidated Flyway migration (auto-generated, do not split into per-feature files)
-- Categories: V1 tables | V2 extensions | V3 seed data

'@

Set-Content -Path (Join-Path $TargetDir 'V1__tables.sql') -Value ($header + "`r`n" + ($tables -join "`r`n")) -Encoding UTF8 -NoNewline
Set-Content -Path (Join-Path $TargetDir 'V2__extensions.sql') -Value ($header + "`r`n" + ($extensions -join "`r`n")) -Encoding UTF8 -NoNewline
Set-Content -Path (Join-Path $TargetDir 'V3__seed_data.sql') -Value ($header + "`r`n" + ($data -join "`r`n")) -Encoding UTF8 -NoNewline

Write-Host "Consolidated $($files.Count) files -> V1($($tables.Count)) V2($($extensions.Count)) V3($($data.Count)) statements in $TargetDir"
