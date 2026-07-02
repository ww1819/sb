$root = Split-Path $PSScriptRoot -Parent
$total = (Get-ChildItem $root -Recurse -File -Force -ErrorAction SilentlyContinue | Measure-Object Length -Sum).Sum
Write-Host "TOTAL: $([math]::Round($total/1GB,2)) GB ($([math]::Round($total/1MB,0)) MB)"

Write-Host "`n--- Top folders ---"
Get-ChildItem $root -Directory -Force -ErrorAction SilentlyContinue | ForEach-Object {
    $s = (Get-ChildItem $_.FullName -Recurse -File -Force -ErrorAction SilentlyContinue | Measure-Object Length -Sum).Sum
    [PSCustomObject]@{ MB = [math]::Round($s/1MB, 1); Folder = $_.Name }
} | Sort-Object MB -Descending | Format-Table -AutoSize

Write-Host "`n--- All target/ folders ---"
Get-ChildItem $root -Recurse -Directory -Filter target -Force -ErrorAction SilentlyContinue | ForEach-Object {
    $s = (Get-ChildItem $_.FullName -Recurse -File -Force -ErrorAction SilentlyContinue | Measure-Object Length -Sum).Sum
    if ($s -gt 1MB) { Write-Host "$([math]::Round($s/1MB,1)) MB  $($_.FullName)" }
} | Sort-Object

Write-Host "`n--- node_modules ---"
Get-ChildItem $root -Recurse -Directory -Filter node_modules -Force -ErrorAction SilentlyContinue | ForEach-Object {
    $s = (Get-ChildItem $_.FullName -Recurse -File -Force -ErrorAction SilentlyContinue | Measure-Object Length -Sum).Sum
    Write-Host "$([math]::Round($s/1MB,1)) MB  $($_.FullName)"
}

Write-Host "`n--- Large file types (top 10) ---"
Get-ChildItem $root -Recurse -File -Force -ErrorAction SilentlyContinue |
    Group-Object Extension | ForEach-Object {
        $sum = ($_.Group | Measure-Object Length -Sum).Sum
        [PSCustomObject]@{ Ext = $_.Name; MB = [math]::Round($sum/1MB,1); Count = $_.Count }
    } | Sort-Object MB -Descending | Select-Object -First 12 | Format-Table -AutoSize
