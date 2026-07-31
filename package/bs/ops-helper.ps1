# MEIS package ops helper - http://localhost:5098
# Paths come from package\env.txt
param(
    [int]$Port = 0,
    [string]$Token = '',
    [string]$Profile = '',
    [switch]$NoBrowser
)

$ErrorActionPreference = 'Stop'
$script:PkgRoot = Split-Path $PSScriptRoot -Parent
$script:JarsDir = Join-Path $script:PkgRoot 'jars'
$script:LogsDir = Join-Path $script:PkgRoot 'logs'
$script:ServicesFile = Join-Path $script:PkgRoot 'services.json'
$htmlPath = Join-Path $script:PkgRoot 'index.html'

. (Join-Path $script:PkgRoot 'common\load-env.ps1')
Import-MeisPackageEnv -EnvFile (Join-Path $script:PkgRoot 'env.txt')

if ($Port -le 0) {
    if ($env:OPS_PORT -match '^\d+$') { $Port = [int]$env:OPS_PORT } else { $Port = 5098 }
}
if ([string]::IsNullOrWhiteSpace($Token)) {
    if ($env:OPS_TOKEN) { $Token = [string]$env:OPS_TOKEN }
    elseif ($env:MEIS_OPS_TOKEN) { $Token = [string]$env:MEIS_OPS_TOKEN }
    else { $Token = 'meis-ops' }
}
if ([string]::IsNullOrWhiteSpace($Profile)) {
    if ($env:PROFILE) { $Profile = [string]$env:PROFILE } else { $Profile = 'dev' }
}

if (-not (Test-Path $htmlPath)) { throw "Missing index.html" }
if (-not (Test-Path $script:ServicesFile)) { throw "Missing services.json" }

$script:OpsToken = $Token
$script:OpsProfile = $Profile
$script:OpsJavaHome = Resolve-MeisPackageJavaHome
$env:JAVA_HOME = $script:OpsJavaHome
$env:MEIS_JAVA_HOME = $script:OpsJavaHome
$script:OpsJobs = [ordered]@{}

New-Item -ItemType Directory -Path $script:JarsDir -Force | Out-Null
New-Item -ItemType Directory -Path $script:LogsDir -Force | Out-Null

function Get-PkgServices {
    return @(Get-Content $script:ServicesFile -Raw -Encoding UTF8 | ConvertFrom-Json)
}

function Get-PkgJarPath([string]$Name) {
    Join-Path $script:JarsDir "$Name-1.0.0-SNAPSHOT.jar"
}

function Test-PkgPortUp([int]$PortNum) {
    try {
        $c = Get-NetTCPConnection -LocalPort $PortNum -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
        return $null -ne $c
    } catch {
        return $null -ne (netstat -ano | Select-String (":$PortNum\s+.*LISTENING"))
    }
}

function Sync-PkgJobs {
    foreach ($k in @($script:OpsJobs.Keys)) {
        $j = $script:OpsJobs[$k]
        if ($j.state -ne 'running') { continue }

        $alive = $false
        if ($j.pid) {
            $p = Get-Process -Id ([int]$j.pid) -ErrorAction SilentlyContinue
            if ($p) { $alive = $true }
        }

        $resultPath = [string]$j.resultFile
        if ($resultPath -and (Test-Path $resultPath)) {
            try {
                $obj = (Get-Content $resultPath -Raw -Encoding UTF8) | ConvertFrom-Json
                $script:OpsJobs[$k] = [ordered]@{
                    id = $j.id
                    label = $j.label
                    state = if ($obj.ok) { 'ok' } else { 'error' }
                    message = [string]$obj.message
                    startedAt = $j.startedAt
                    finishedAt = [string]$obj.finishedAt
                }
            } catch {
                if (-not $alive) {
                    $script:OpsJobs[$k] = [ordered]@{
                        id = $j.id; label = $j.label; state = 'error'
                        message = "bad result: $($_.Exception.Message)"
                        startedAt = $j.startedAt
                        finishedAt = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
                    }
                }
            }
            continue
        }

        if (-not $alive) {
            $errTail = ''
            if ($j.errLog -and (Test-Path $j.errLog)) {
                $errTail = ((Get-Content $j.errLog -Tail 8 -ErrorAction SilentlyContinue) -join ' | ')
            }
            if (-not $errTail -and $j.outLog -and (Test-Path $j.outLog)) {
                $errTail = ((Get-Content $j.outLog -Tail 8 -ErrorAction SilentlyContinue) -join ' | ')
            }
            $script:OpsJobs[$k] = [ordered]@{
                id = $j.id; label = $j.label; state = 'error'
                message = if ($errTail) { $errTail } else { 'process exited without result' }
                startedAt = $j.startedAt
                finishedAt = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
            }
        }
    }
}

function Get-PkgJobViews {
    Sync-PkgJobs
    $views = @()
    foreach ($k in @($script:OpsJobs.Keys)) {
        $j = $script:OpsJobs[$k]
        $views += [ordered]@{
            id = $j.id; label = $j.label; state = $j.state
            message = $j.message; startedAt = $j.startedAt; finishedAt = $j.finishedAt
        }
    }
    return $views
}

function Get-PkgStatusPayload {
    $raw = Get-Content $script:ServicesFile -Raw -Encoding UTF8 | ConvertFrom-Json
    $list = @()
    $missing = @()
    foreach ($s in @($raw)) {
        $name = [string]$s.name
        $jar = Get-PkgJarPath $name
        $exists = Test-Path $jar
        $sizeKb = if ($exists) { [math]::Round((Get-Item $jar).Length / 1KB) } else { 0 }
        $healthy = $exists -and $sizeKb -ge 200
        if (-not $exists) { $missing += "$name-1.0.0-SNAPSHOT.jar" }
        $portNum = [int]("$($s.port)".Trim())
        $list += [ordered]@{
            name = $name
            label = [string]$s.label
            port = $portNum
            core = ($s.core -eq $true)
            order = [int]("$($s.order)".Trim())
            jarExists = $exists
            jarHealthy = $healthy
            jarSizeKb = $sizeKb
            httpUp = (Test-PkgPortUp $portNum)
        }
    }
    $list = @($list | Sort-Object { $_['order'] })
    return [ordered]@{
        timestamp = (Get-Date).ToString('o')
        packageRoot = $script:PkgRoot
        jarsDir = $script:JarsDir
        javaHome = $script:OpsJavaHome
        gatewayUrl = 'http://localhost:8080'
        panelPort = $Port
        profile = $script:OpsProfile
        complete = ($missing.Count -eq 0)
        expectedCount = @($raw).Count
        presentCount = @($list | Where-Object { $_.jarExists }).Count
        missingJars = $missing
        services = $list
        jobs = @(Get-PkgJobViews)
        hint = 'Set JAVA_HOME/MAVEN_HOME/OPS_TOKEN in package\env.txt'
    }
}

function Start-PkgJob {
    param([string]$Label, [hashtable]$Map)
    $id = ([guid]::NewGuid().ToString('N')).Substring(0, 8)
    $root = $script:PkgRoot
    $action = [string]$Map.Action
    $serviceName = ''
    if ($Map.ContainsKey('ServiceName') -and $Map.ServiceName) {
        $serviceName = [string]$Map.ServiceName
    }
    $coreOnlyFlag = '0'
    if ($Map.CoreOnly) { $coreOnlyFlag = '1' }

    $jobsDir = Join-Path $script:LogsDir 'jobs'
    New-Item -ItemType Directory -Path $jobsDir -Force | Out-Null
    $resultFile = Join-Path $jobsDir "$id.json"
    $outLog = Join-Path $jobsDir "$id.out.log"
    $errLog = Join-Path $jobsDir "$id.err.log"
    $runner = Join-Path $PSScriptRoot 'job-runner.ps1'

    $argLine = "-NoProfile -ExecutionPolicy Bypass -File `"$runner`" -Root `"$root`" -Action `"$action`" -ServiceName `"$serviceName`" -CoreOnlyFlag `"$coreOnlyFlag`" -ResultFile `"$resultFile`""
    $proc = Start-Process -FilePath 'powershell.exe' -ArgumentList $argLine `
        -WorkingDirectory $root -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $outLog -RedirectStandardError $errLog

    $script:OpsJobs[$id] = [ordered]@{
        id = $id
        label = $Label
        state = 'running'
        message = 'running'
        pid = $proc.Id
        resultFile = $resultFile
        outLog = $outLog
        errLog = $errLog
        startedAt = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
    }
    return [ordered]@{ ok = $true; jobId = $id; label = $Label; message = 'job submitted' }
}

function Write-PkgHttp($Response, $StatusCode = 200, $ContentType = 'text/plain; charset=utf-8', $Body = '') {
    $bytes = [Text.Encoding]::UTF8.GetBytes($Body)
    $Response.StatusCode = $StatusCode
    $Response.ContentType = $ContentType
    $Response.ContentLength64 = $bytes.Length
    $Response.Headers.Add('Cache-Control', 'no-store')
    $Response.Headers.Add('Access-Control-Allow-Origin', '*')
    $Response.OutputStream.Write($bytes, 0, $bytes.Length)
    $Response.OutputStream.Close()
}
function Write-PkgJson($Response, $Data, $StatusCode = 200) {
    Write-PkgHttp $Response $StatusCode 'application/json; charset=utf-8' ($Data | ConvertTo-Json -Depth 8 -Compress)
}
function Test-PkgToken($Request) {
    $got = $Request.Headers['X-Meis-Ops-Token']
    if (-not $got) { $got = $Request.QueryString['token'] }
    return ($got -eq $script:OpsToken)
}

function Handle-PkgRequest($Context) {
    $req = $Context.Request
    $res = $Context.Response
    $path = $req.Url.AbsolutePath.TrimEnd('/')
    if (-not $path) { $path = '/' }
    $method = $req.HttpMethod.ToUpperInvariant()

    if ($method -eq 'OPTIONS') {
        $res.AddHeader('Access-Control-Allow-Origin', '*')
        $res.AddHeader('Access-Control-Allow-Headers', 'Content-Type, X-Meis-Ops-Token')
        $res.AddHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        Write-PkgHttp $res 204 'text/plain' ''
        return
    }
    if ($method -eq 'GET' -and $path -eq '/') {
        Write-PkgHttp $res 200 'text/html; charset=utf-8' (Get-Content $htmlPath -Raw -Encoding UTF8)
        return
    }
    if ($method -eq 'GET' -and $path -eq '/favicon.ico') { Write-PkgHttp $res 204 'text/plain' ''; return }
    if ($method -eq 'GET' -and $path -eq '/api/status') { Write-PkgJson $res (Get-PkgStatusPayload); return }
    if ($method -eq 'GET' -and $path -match '^/api/logs/([a-z0-9-]+)$') {
        $name = $Matches[1]
        $log = Join-Path $script:LogsDir "$name.out.log"
        $lines = @()
        if (Test-Path $log) { $lines = @(Get-Content $log -Tail 100 -Encoding UTF8 -ErrorAction SilentlyContinue) }
        Write-PkgJson $res ([ordered]@{ name = $name; lines = $lines; fetchedAt = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss') })
        return
    }
    if ($method -eq 'POST') {
        if (-not (Test-PkgToken $req)) {
            Write-PkgJson $res @{ ok = $false; message = 'bad token (see OPS_TOKEN in env.txt)' } 401
            return
        }
        if ($path -eq '/api/start-all') { Write-PkgJson $res (Start-PkgJob 'start-all' @{ Action = 'start-all'; CoreOnly = $false }); return }
        if ($path -eq '/api/start-core') { Write-PkgJson $res (Start-PkgJob 'start-core' @{ Action = 'start-all'; CoreOnly = $true }); return }
        if ($path -eq '/api/stop-all') { Write-PkgJson $res (Start-PkgJob 'stop-all' @{ Action = 'stop-all' }); return }
        if ($path -match '^/api/service/([a-z0-9-]+)/(start|stop|restart)$') {
            $name = $Matches[1]; $act = $Matches[2]
            $mapAct = @{ start = 'start-one'; stop = 'stop-one'; restart = 'restart-one' }
            Write-PkgJson $res (Start-PkgJob "$act $name" @{ Action = $mapAct[$act]; ServiceName = $name })
            return
        }
    }
    Write-PkgHttp $res 404 'text/plain' 'Not Found'
}

$prefix = "http://localhost:$Port/"
Get-CimInstance Win32_Process -Filter "Name='powershell.exe'" -ErrorAction SilentlyContinue |
    Where-Object { ($_.CommandLine -like '*ops-helper.ps1*' -or $_.CommandLine -like '*运维助手.ps1*') -and $_.ProcessId -ne $PID } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add($prefix)
try { $listener.Start() } catch { throw "Cannot bind ${prefix}: $($_.Exception.Message)" }

Write-Host "MEIS Package Ops: $prefix" -ForegroundColor Cyan
Write-Host "JAVA_HOME=$($script:OpsJavaHome)" -ForegroundColor DarkGray
Write-Host "Token=$Token  Profile=$Profile" -ForegroundColor DarkGray
if (-not $NoBrowser) { try { Start-Process $prefix } catch { } }

try {
    while ($listener.IsListening) {
        $ctx = $listener.GetContext()
        try { Handle-PkgRequest $ctx } catch {
            try { Write-PkgJson $ctx.Response @{ ok = $false; message = $_.Exception.Message } 500 } catch { }
        }
    }
} finally {
    try { if ($listener.IsListening) { $listener.Stop() } } catch { }
}
