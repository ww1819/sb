# Merge V2 ALTER into V1; embed COMMENT ON in V1/V2; V4 backfills legacy objects.
param(
    [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
    [switch]$CommentsOnly
)

$ErrorActionPreference = 'Stop'
$labelsPath = Join-Path $PSScriptRoot 'schema-comment-labels.json'
$labels = Get-Content $labelsPath -Raw -Encoding UTF8 | ConvertFrom-Json
$TableLabels = @{}
$labels.tables.PSObject.Properties | ForEach-Object { $TableLabels[$_.Name] = $_.Value }
$ColumnHints = @{}
$labels.columns.PSObject.Properties | ForEach-Object { $ColumnHints[$_.Name] = $_.Value }
$Templates = @{}
$labels.templates.PSObject.Properties | ForEach-Object { $Templates[$_.Name] = $_.Value }
$Prefixes = @{}
$labels.prefixes.PSObject.Properties | ForEach-Object { $Prefixes[$_.Name] = $_.Value }

function Resolve-PrefixLabel([string]$base) {
    if ($TableLabels.ContainsKey($base)) { return $TableLabels[$base] }
    if ($Prefixes.ContainsKey($base)) { return $Prefixes[$base] }
    return ($base -replace '_', '')
}

function Get-ColumnComment([string]$table, [string]$column) {
    if ($ColumnHints.ContainsKey($column)) { return $ColumnHints[$column] }
    if ($column -match '^(\w+)_(code|name|no)$') {
        $base = $Matches[1]; $kind = $Matches[2]
        $label = Resolve-PrefixLabel $base
        if ($Templates.ContainsKey($kind)) { return [string]::Format($Templates[$kind], $label) }
    }
    if ($column.EndsWith('_id')) {
        $base = $column.Substring(0, $column.Length - 3)
        $label = Resolve-PrefixLabel $base
        return [string]::Format($Templates['ref'], $label)
    }
    if ($column.EndsWith('_at')) {
        $stem = $column.Substring(0, $column.Length - 3) -replace '_', ''
        return [string]::Format($Templates['at'], $stem)
    }
    if ($column.EndsWith('_date')) {
        $stem = $column.Substring(0, $column.Length - 5) -replace '_', ''
        return [string]::Format($Templates['date'], $stem)
    }
    if ($column.StartsWith('is_')) {
        return [string]::Format($Templates['is'], ($column.Substring(3) -replace '_', ''))
    }
    if ($column.EndsWith('_url')) {
        $stem = $column.Substring(0, $column.Length - 4) -replace '_', ''
        return [string]::Format($Templates['url'], $stem)
    }
    if ($column.EndsWith('_amount')) {
        $stem = $column.Substring(0, $column.Length - 7) -replace '_', ''
        return [string]::Format($Templates['amount'], $stem)
    }
    if ($column.EndsWith('_count')) {
        $stem = $column.Substring(0, $column.Length - 6) -replace '_', ''
        return [string]::Format($Templates['count'], $stem)
    }
    return ($column -replace '_', ' ')
}

function Get-TableLabel([string]$table) {
    if ($TableLabels.ContainsKey($table)) { return $TableLabels[$table] }
    return ($table -replace '_', ' ')
}

function Escape-SqlLiteral([string]$s) {
    return $s.Replace("'", "''")
}

function Strip-LineComment([string]$line) {
    return ($line -replace '\s*--(?![^'']*'').*$', '').TrimEnd()
}

function Remove-DbCommentStatements([string]$sql) {
    return [regex]::Replace($sql, '(?m)^COMMENT ON (TABLE|COLUMN|INDEX) .*?;\s*\r?\n', '')
}

function Strip-InlineColumnComments([string]$sql) {
    $out = New-Object System.Collections.ArrayList
    foreach ($raw in ($sql -split "`r?`n")) {
        if ($raw -match '^\s*COMMENT ON\b') { continue }
        if ($raw -match '^\s*--') {
            [void]$out.Add($raw)
            continue
        }
        $cleaned = Strip-LineComment $raw
        if ($cleaned) { [void]$out.Add($cleaned) }
    }
    return ($out -join "`n")
}

function Parse-CreateTables([string]$sql) {
    $tables = [System.Collections.ArrayList]@()
    $lines = $sql -split "`r?`n"
    for ($i = 0; $i -lt $lines.Length; $i++) {
        if ($lines[$i] -notmatch 'CREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?\s+(\w+)\s*\(') { continue }
        $table = $Matches[1]
        $tLabel = Get-TableLabel $table
        for ($j = $i - 1; $j -ge 0; $j--) {
            $prev = $lines[$j].Trim()
            if ($prev -match '^--\s*[\d.]+\s*(.+)$') { $tLabel = $Matches[1].Trim(); break }
            if ($prev -match '^CREATE\s+TABLE') { break }
        }
        $cols = [System.Collections.ArrayList]@()
        $i++
        while ($i -lt $lines.Length -and $lines[$i] -notmatch '^\s*\);\s*$') {
            $line = Strip-LineComment $lines[$i].Trim()
            if ($line -and $line -notmatch '^(UNIQUE|PRIMARY|CONSTRAINT)\b' -and $line -match '^(\w+)\s+') {
                [void]$cols.Add($Matches[1])
            }
            $i++
        }
        [void]$tables.Add([pscustomobject]@{ Name = $table; Label = $tLabel; Columns = $cols })
    }
    return $tables
}

function Build-TableCommentBlock($tableInfo) {
    $lines = [System.Collections.ArrayList]@()
    [void]$lines.Add("COMMENT ON TABLE $($tableInfo.Name) IS '$(Escape-SqlLiteral $tableInfo.Label)';")
    foreach ($col in $tableInfo.Columns) {
        $cComment = Get-ColumnComment $tableInfo.Name $col
        [void]$lines.Add("COMMENT ON COLUMN $($tableInfo.Name).$col IS '$(Escape-SqlLiteral $cComment)';")
    }
    return ($lines -join "`n")
}

function Format-V1WithDbComments([string]$sql, [string]$relDir) {
    $sql = Remove-DbCommentStatements $sql
    $sql = Strip-InlineColumnComments $sql
    $sql = [regex]::Replace($sql, '(?m)^--\s*\[V\d+__[^\]]+\]\s*\r?\n', '')
    $sql = [regex]::Replace($sql, '(?m)^-- Categories:.*\r?\n', '')
    $sql = [regex]::Replace($sql, '^-- MEIS[^\n]*\n', '', 1)

    $tableMap = @{}
    foreach ($t in (Parse-CreateTables $sql)) { $tableMap[$t.Name] = $t }

    $sb = New-Object System.Text.StringBuilder
    $header = if ($relDir -eq 'tenant') {
        "-- MEIS tenant: CREATE TABLE + COMMENT ON (visible in database catalog)`n`n"
    } else {
        "-- MEIS public: CREATE TABLE + COMMENT ON (visible in database catalog)`n`n"
    }
    [void]$sb.Append($header)

    $pendingTable = $null
    foreach ($line in ($sql -split "`r?`n")) {
        if (-not $line.Trim()) { continue }
        [void]$sb.AppendLine($line)
        if ($line -match 'CREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?\s+(\w+)\s*\(') {
            $pendingTable = $Matches[1]
        }
        if ($pendingTable -and $line -match '^\s*\);\s*$') {
            $t = $tableMap[$pendingTable]
            if ($t) {
                [void]$sb.Append((Build-TableCommentBlock $t) + "`n")
            }
            [void]$sb.AppendLine()
            $pendingTable = $null
        }
    }
    return $sb.ToString().TrimEnd() + "`n"
}

function Get-IndexComment([string]$line) {
    if ($line -notmatch 'CREATE\s+(?:UNIQUE\s+)?INDEX\s+(?:IF\s+NOT\s+EXISTS\s+)?\w+\s+ON\s+(\w+)\s*(?:USING\s+\w+\s*)?\(([^)]+)\)') {
        return $null
    }
    $table = $Matches[1]
    $colsRaw = $Matches[2]
    $firstCol = ($colsRaw -split ',')[0].Trim() -replace '\s+(ASC|DESC)$', ''
    $tableLabel = Get-TableLabel $table
    $colComment = Get-ColumnComment $table $firstCol
    $prefix = [char]0x7D22 + [char]0x5F15 + [char]0xFF1A
    return "$prefix$tableLabel.$colComment"
}

function Format-V2WithDbComments([string]$sql) {
    $sql = Remove-DbCommentStatements $sql
    $lines = New-Object System.Collections.ArrayList
    [void]$lines.Add('-- MEIS extensions: CREATE INDEX + COMMENT ON INDEX (visible in database catalog)')
    [void]$lines.Add('')

    foreach ($raw in ($sql -split "`r?`n")) {
        $line = Strip-LineComment $raw
        if (-not $line) { continue }
        if ($line -match '^\s*--\s*MEIS') { continue }
        if ($line -notmatch '^\s*CREATE\s+(?:UNIQUE\s+)?INDEX\b') { continue }

        $idxLine = $line.TrimEnd().TrimEnd(';') + ';'
        [void]$lines.Add($idxLine)
        if ($idxLine -match 'CREATE\s+(?:UNIQUE\s+)?INDEX\s+(?:IF\s+NOT\s+EXISTS\s+)?(\w+)') {
            $idxName = $Matches[1]
            $comment = Get-IndexComment $idxLine
            if ($comment) {
                [void]$lines.Add("COMMENT ON INDEX $idxName IS '$(Escape-SqlLiteral $comment)';")
            }
        }
        [void]$lines.Add('')
    }
    return (($lines -join "`n").TrimEnd()) + "`n"
}

function Build-V4Backfill([string]$v1, [string]$schemaLabel) {
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.AppendLine("-- MEIS ${schemaLabel}: backfill COMMENT ON for objects created before V1 carried metadata comments")
    [void]$sb.AppendLine('-- Safe to re-run: COMMENT ON overwrites existing descriptions')
    [void]$sb.AppendLine()
    foreach ($t in (Parse-CreateTables $v1)) {
        [void]$sb.Append((Build-TableCommentBlock $t) + "`n")
    }
    return $sb.ToString().TrimEnd() + "`n"
}

function Parse-AlterColumns([string]$v2) {
    $alters = @{}
    $matches = [regex]::Matches($v2, 'ALTER\s+TABLE\s+(\w+)\s+(.*?);', 'IgnoreCase, Singleline')
    foreach ($m in $matches) {
        $body = $m.Groups[2].Value
        if ($body -notmatch 'ADD\s+COLUMN') { continue }
        $table = $m.Groups[1].Value
        if (-not $alters.ContainsKey($table)) { $alters[$table] = [System.Collections.ArrayList]@() }
        $parts = [regex]::Split($body, 'ADD\s+COLUMN\s+IF\s+NOT\s+EXISTS\s+', 'IgnoreCase')
        for ($i = 1; $i -lt $parts.Length; $i++) {
            $part = $parts[$i].Trim().TrimEnd(',')
            if ($part -match '^(\w+)\s+(.*)$') {
                $col = $Matches[1]
                $def = $Matches[2].Trim().TrimEnd(',')
                $existing = @($alters[$table] | ForEach-Object { $_.Col })
                if ($existing -notcontains $col) {
                    [void]$alters[$table].Add([pscustomobject]@{ Col = $col; Def = $def })
                }
            }
        }
    }
    return $alters
}

function Merge-Columns([string]$v1, $alters) {
    foreach ($table in @($alters.Keys)) {
        $pat = "CREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?\s+$table\s*\((.*?)\n\);"
        $m = [regex]::Match($v1, $pat, 'IgnoreCase, Singleline')
        if (-not $m.Success) { Write-Host "  [warn] table not found: $table"; continue }
        $full = $m.Value
        $body = $m.Groups[1].Value
        $existing = [regex]::Matches($body, '(?m)^\s*(\w+)\s+') | ForEach-Object { $_.Groups[1].Value }
        $adds = @()
        foreach ($col in $alters[$table]) {
            if ($existing -notcontains $col.Col) { $adds += "    $($col.Col) $($col.Def)" }
        }
        if ($adds.Count -eq 0) { continue }
        $newBody = $body.TrimEnd() + ",`n" + ($adds -join ",`n")
        $newFull = $full -replace [regex]::Escape($body), $newBody
        $v1 = $v1.Remove($m.Index, $m.Length).Insert($m.Index, $newFull)
    }
    return $v1
}

function Strip-V2([string]$v2) {
    $lines = New-Object System.Collections.ArrayList
    $skip = $false
    foreach ($line in ($v2 -split "`r?`n")) {
        $upper = $line.Trim().ToUpperInvariant()
        if (-not $skip -and $upper.StartsWith('ALTER TABLE')) { $skip = $true }
        if ($skip) { if ($line.TrimEnd().EndsWith(';')) { $skip = $false }; continue }
        if ($upper.StartsWith('COMMENT ON')) { continue }
        [void]$lines.Add($line)
    }
    return (($lines -join "`n").Trim())
}

function Process-Schema([string]$RelDir, [string]$SchemaLabel, [switch]$CommentsOnly) {
    $base = Join-Path $Root "meis-tenant\src\main\resources\db\migrations\$RelDir"
    $v1Path = Join-Path $base 'V1__tables.sql'
    $v2Path = Join-Path $base 'V2__indexes.sql'
    $v4Path = Join-Path $base 'V4__comments.sql'

    $v1 = [IO.File]::ReadAllText($v1Path)
    $v2 = [IO.File]::ReadAllText($v2Path)

    if (-not $CommentsOnly) {
        $alters = Parse-AlterColumns $v2
        $colCount = ($alters.Values | ForEach-Object { $_.Count } | Measure-Object -Sum).Sum
        if ($colCount -gt 0) {
            Write-Host "[$RelDir] merging $colCount columns into V1"
            $v1 = Merge-Columns $v1 $alters
        }
        $v2 = Strip-V2 $v2
    }

    $v1Out = Format-V1WithDbComments $v1 $RelDir
    $v2Out = Format-V2WithDbComments $v2
    $v4Out = Build-V4Backfill $v1Out $SchemaLabel

    [IO.File]::WriteAllText($v1Path, $v1Out, [Text.UTF8Encoding]::new($true))
    [IO.File]::WriteAllText($v2Path, $v2Out, [Text.UTF8Encoding]::new($true))
    [IO.File]::WriteAllText($v4Path, $v4Out, [Text.UTF8Encoding]::new($true))

    Write-Host "[$RelDir] V1 tables+COMMENT ON, V2 indexes+COMMENT ON INDEX, V4 backfill ($(($v4Out -split "`n").Count) lines)"
}

Process-Schema 'tenant' 'tenant' -CommentsOnly
Process-Schema 'public' 'public' -CommentsOnly
