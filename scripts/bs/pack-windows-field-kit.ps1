# 打包 Windows 现场实施包（精简，不含开发面板/源码构建脚本）
# 用法（在仓库根目录，且已 mvn package + 前端 build）：
#   powershell -File scripts\bs\pack-windows-field-kit.ps1
# 产物：release\windows-field-kit\（scripts 展平，便于实施机沿用旧路径）
param(
    [string]$OutDir = '',
    [switch]$SkipFrontendCopy,
    [switch]$Zip
)

$ErrorActionPreference = 'Stop'
. (Join-Path (Split-Path $PSScriptRoot -Parent) 'common\meis-root.ps1')
$root = $script:MeisRoot
if (-not $OutDir) {
    $OutDir = Join-Path $root 'release\windows-field-kit'
}

$services = @(
    'meis-tenant', 'meis-auth', 'meis-system', 'meis-purchase', 'meis-asset',
    'meis-repair', 'meis-maintain', 'meis-qc', 'meis-maintenance-contract',
    'meis-special', 'meis-analytics', 'meis-file', 'meis-notification',
    'meis-integration', 'meis-gateway'
)

# 实施现场需要的脚本（从 bs/ 展平复制；另拷 common/meis-root.ps1）
$scriptFiles = @(
    'meis-services.ps1',
    'ops-panel.ps1',
    'stop-ops-panel.ps1',
    'start.ps1',
    'stop.ps1',
    'status.ps1',
    'restart.ps1',
    'logs.ps1',
    'health-check.ps1',
    'setup-postgres.ps1',
    'backup-db.ps1',
    'restore-db.ps1',
    'wait-backend.ps1'
)

Write-Host "Packing field kit -> $OutDir" -ForegroundColor Cyan
if (Test-Path $OutDir) {
    Remove-Item $OutDir -Recurse -Force
}
New-Item -ItemType Directory -Path $OutDir | Out-Null

$missing = @()
foreach ($name in $services) {
    $jarName = "$name-1.0.0-SNAPSHOT.jar"
    $src = Join-Path $root "$name\target\$jarName"
    if (-not (Test-Path $src)) {
        $missing += $src
        continue
    }
    $destDir = Join-Path $OutDir "$name\target"
    New-Item -ItemType Directory -Path $destDir -Force | Out-Null
    Copy-Item $src (Join-Path $destDir $jarName) -Force
    Write-Host "  + $jarName"
}
if ($missing.Count -gt 0) {
    throw ("Missing JARs. Run build first:`n  " + ($missing -join "`n  "))
}

$scriptsOut = Join-Path $OutDir 'scripts'
New-Item -ItemType Directory -Path $scriptsOut | Out-Null
foreach ($f in $scriptFiles) {
    $src = Join-Path $script:MeisScriptsBs $f
    if (-not (Test-Path $src)) { throw "Missing script: $src" }
    Copy-Item $src (Join-Path $scriptsOut $f) -Force
}
# Flat field-kit: meis-root next to meis-services (meis-services also probes same dir)
Copy-Item (Join-Path $script:MeisScriptsCommon 'meis-root.ps1') (Join-Path $scriptsOut 'meis-root.ps1') -Force
$opsUi = Join-Path $script:MeisScriptsBs 'ops-panel'
$opsOut = Join-Path $scriptsOut 'ops-panel'
New-Item -ItemType Directory -Path $opsOut | Out-Null
Copy-Item (Join-Path $opsUi 'index.html') (Join-Path $opsOut 'index.html') -Force
# 运维面板状态中文名（可选）
$meta = Join-Path $script:MeisScriptsBs 'dev-panel\services-meta.json'
if (Test-Path $meta) {
    $metaDir = Join-Path $scriptsOut 'dev-panel'
    New-Item -ItemType Directory -Path $metaDir -Force | Out-Null
    Copy-Item $meta (Join-Path $metaDir 'services-meta.json') -Force
}
Write-Host '  + scripts (slim ops set, flattened)'

$docsOut = Join-Path $OutDir 'docs'
New-Item -ItemType Directory -Path $docsOut | Out-Null
Copy-Item (Join-Path $root 'docs\windows-production-deploy.md') (Join-Path $docsOut 'windows-production-deploy.md') -Force

if (-not $SkipFrontendCopy) {
    $dist = Join-Path $root 'meis-web\dist'
    if (-not (Test-Path $dist)) {
        Write-Host 'WARN: meis-web\dist missing. Run: cd meis-web; npm ci; npm run build' -ForegroundColor Yellow
    } else {
        $www = Join-Path $OutDir 'www'
        New-Item -ItemType Directory -Path $www | Out-Null
        Copy-Item "$dist\*" $www -Recurse -Force
        Write-Host '  + www (frontend dist)'
    }
}

$readme = @'
MEIS Windows 现场实施包
====================

本包给【实施人员】用，不是完整开发仓库。

目录说明
--------
  meis-*/target/*.jar   后端服务
  www\                  前端静态资源（交给 Nginx/IIS）
  scripts\              精简运维脚本（含实施面板，已展平）
  docs\                 部署说明

不要做的事
----------
  - 不要在客户机跑 dev-panel.ps1（5099，开发专用）
  - 不要期望本包能 mvn/npm 编译（源码未包含）
  - 不要把 5098 运维面板对院网开放

推荐步骤
--------
1. 按 docs\windows-production-deploy.md 安装 JDK17 / PostgreSQL / Redis / MinIO / Nginx
2. 配置环境变量与库
3. 启动实施运维面板：
   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\ops-panel.ps1 -Token 现场口令
4. 浏览器打开 http://localhost:5098/
5. 前端：Nginx root 指向本包 www\

常用命令
--------
  powershell -File scripts\start.ps1
  powershell -File scripts\status.ps1
  powershell -File scripts\stop.ps1

说明：开发仓库脚本在 scripts\bs\、scripts\app\、scripts\common\；
本现场包 scripts\ 为展平副本，路径与上表一致即可。
'@
Set-Content -Path (Join-Path $OutDir 'README.txt') -Value $readme -Encoding UTF8

if ($Zip) {
    $zipPath = "$OutDir.zip"
    if (Test-Path $zipPath) { Remove-Item $zipPath -Force }
    Compress-Archive -Path (Join-Path $OutDir '*') -DestinationPath $zipPath
    Write-Host "Zip: $zipPath" -ForegroundColor Green
}

Write-Host "Done: $OutDir" -ForegroundColor Green
Write-Host 'Give implementers this folder (or zip). Do NOT give full scripts\ from repo.' -ForegroundColor DarkGray
