# Resolve MEIS repo root and scripts subdirs (common / bs / app)
# Also supports field-kit layout: <kit>/scripts + <kit>/meis-gateway/target
function Get-MeisRepoRootFrom {
    param([string]$StartDir)
    $d = $StartDir
    while ($d) {
        $pom = Join-Path $d 'pom.xml'
        $web = Join-Path $d 'meis-web'
        if ((Test-Path -LiteralPath $pom) -and (Test-Path -LiteralPath $web)) {
            return $d
        }
        $gw = Join-Path $d 'meis-gateway\target'
        $scr = Join-Path $d 'scripts'
        if ((Test-Path -LiteralPath $gw) -and (Test-Path -LiteralPath $scr)) {
            return $d
        }
        $parent = Split-Path $d -Parent
        if (-not $parent -or $parent -eq $d) { break }
        $d = $parent
    }
    throw ('MEIS repo root not found from: ' + $StartDir)
}

if (-not $script:MeisRoot) {
    $script:MeisRoot = Get-MeisRepoRootFrom -StartDir $PSScriptRoot
}
$script:MeisScriptsRoot = Join-Path $script:MeisRoot 'scripts'
$script:MeisScriptsCommon = Join-Path $script:MeisScriptsRoot 'common'
$script:MeisScriptsBs = Join-Path $script:MeisScriptsRoot 'bs'
$script:MeisScriptsApp = Join-Path $script:MeisScriptsRoot 'app'

# Field-kit flatten: scripts live in one folder (no common/bs/app)
if (-not (Test-Path -LiteralPath $script:MeisScriptsBs)) {
    $script:MeisScriptsBs = $script:MeisScriptsRoot
}
if (-not (Test-Path -LiteralPath $script:MeisScriptsCommon)) {
    $script:MeisScriptsCommon = $script:MeisScriptsRoot
}
if (-not (Test-Path -LiteralPath $script:MeisScriptsApp)) {
    $script:MeisScriptsApp = $script:MeisScriptsRoot
}
