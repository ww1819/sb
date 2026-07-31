# MEIS 实施运维面板 — http://localhost:5098
# 面向现场实施：启停/查看 JAR 状态；无打包、无热加载、无源码监视。
# 仅绑定 localhost；变更操作需口令（Header: X-Meis-Ops-Token）。
param(
    [int]$Port = 5098,
    [string]$Token = '',
    [string]$Profile = 'dev',
    [switch]$NoBrowser
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\meis-services.ps1"

if ([string]::IsNullOrWhiteSpace($Token)) {
    if ($env:MEIS_OPS_TOKEN) {
        $Token = [string]$env:MEIS_OPS_TOKEN
    } else {
        $Token = 'meis-ops-change-me'
        Write-Host 'WARNING: using default ops token. Pass -Token or set MEIS_OPS_TOKEN.' -ForegroundColor Yellow
    }
}

$panelDir = Join-Path $PSScriptRoot 'ops-panel'
$htmlPath = Join-Path $panelDir 'index.html'
if (-not (Test-Path $htmlPath)) { throw "Missing panel UI: $htmlPath" }

$script:OpsToken = $Token
$script:OpsProfile = $Profile
$script:OpsRoot = $script:MeisRoot
$script:OpsJobs = [ordered]@{}

function Write-OpsHttp {
    param(
        [Parameter(Mandatory = $true)][System.Net.HttpListenerResponse]$Response,
        [int]$StatusCode = 200,
        [string]$ContentType = 'text/plain; charset=utf-8',
        [string]$Body = ''
    )
    $bytes = [Text.Encoding]::UTF8.GetBytes($Body)
    $Response.StatusCode = $StatusCode
    $Response.ContentType = $ContentType
    $Response.ContentLength64 = $bytes.Length
    $Response.Headers.Add('Cache-Control', 'no-store')
    $Response.OutputStream.Write($bytes, 0, $bytes.Length)
    $Response.OutputStream.Close()
}

function Write-OpsJson {
    param(
        [Parameter(Mandatory = $true)][System.Net.HttpListenerResponse]$Response,
        [object]$Data,
        [int]$StatusCode = 200
    )
    $json = $Data | ConvertTo-Json -Depth 8 -Compress
    Write-OpsHttp -Response $Response -StatusCode $StatusCode -ContentType 'application/json; charset=utf-8' -Body $json
}

function Test-OpsToken {
    param([System.Net.HttpListenerRequest]$Request)
    $got = $Request.Headers['X-Meis-Ops-Token']
    if (-not $got) { $got = $Request.QueryString['token'] }
    return ($got -eq $script:OpsToken)
}

function Sync-OpsJobs {
    $keys = @($script:OpsJobs.Keys)
    foreach ($k in $keys) {
        $j = $script:OpsJobs[$k]
        if ($null -eq $j -or $null -eq $j.job) { continue }
        $state = [string]$j.job.State
        if ($state -in @('Completed', 'Failed', 'Stopped')) {
            $err = $null
            $out = $null
            try {
                $out = Receive-Job -Job $j.job -ErrorAction SilentlyContinue | Out-String
            } catch {
                $err = $_.Exception.Message
            }
            if ($state -eq 'Failed' -and -not $err) {
                $err = ($j.job.ChildJobs | ForEach-Object { $_.JobStateInfo.Reason.Message }) -join '; '
            }
            $script:OpsJobs[$k] = [ordered]@{
                id        = $k
                label     = $j.label
                state     = if ($state -eq 'Completed' -and -not $err) { 'ok' } else { 'error' }
                message   = if ($err) { $err } elseif ($out) { ($out.Trim() -split "`n" | Select-Object -Last 3) -join ' ' } else { $state }
                finishedAt = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
            }
            try { Remove-Job -Job $j.job -Force -ErrorAction SilentlyContinue } catch { }
        }
    }
}

function Start-OpsJob {
    param(
        [Parameter(Mandatory = $true)][string]$Label,
        [hashtable]$ArgumentMap = @{}
    )
    $id = ([guid]::NewGuid().ToString('N')).Substring(0, 8)
    $root = $script:OpsRoot
    $profile = $script:OpsProfile
    $job = Start-Job -Name "meis-ops-$id" -ScriptBlock {
        param($Root, $ProfileName, $Action, $ServiceName, $CoreOnly)
        $ErrorActionPreference = 'Stop'
        Set-Location $Root
        $svc = Join-Path $Root 'scripts\bs\meis-services.ps1'
        if (-not (Test-Path -LiteralPath $svc)) {
            $svc = Join-Path $Root 'scripts\meis-services.ps1'
        }
        . $svc
        switch ($Action) {
            'start-one' {
                $r = Start-MeisServiceByName -ServiceName $ServiceName -Profile $ProfileName
                "OK $($r.message)"
            }
            'stop-one' {
                $n = Stop-MeisServiceByName -ServiceName $ServiceName
                "stopped handles=$n"
            }
            'restart-one' {
                Stop-MeisServiceByName -ServiceName $ServiceName | Out-Null
                Start-Sleep -Seconds 1
                $r = Start-MeisServiceByName -ServiceName $ServiceName -Profile $ProfileName
                "OK $($r.message)"
            }
            'start-all' {
                Start-MeisServices -Profile $ProfileName -CoreOnly:$CoreOnly
                'start finished'
            }
            'stop-all' {
                Stop-MeisServices
                'all stopped'
            }
            default { throw "unknown action $Action" }
        }
    } -ArgumentList @(
        $root,
        $profile,
        [string]$ArgumentMap.Action,
        [string]$ArgumentMap.ServiceName,
        [bool]$ArgumentMap.CoreOnly
    )
    $script:OpsJobs[$id] = [ordered]@{
        id     = $id
        label  = $Label
        state  = 'running'
        job    = $job
        message = 'running'
        startedAt = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
    }
    return [ordered]@{ ok = $true; jobId = $id; label = $Label; message = 'started in background' }
}

function Get-OpsServiceRows {
    $rows = @()
    foreach ($s in (Get-MeisServiceStatusList)) {
        $rows += [ordered]@{
            name     = $s.name
            labelZh  = $s.labelZh
            descZh   = $s.descZh
            isCore   = $s.isCore
            port     = $s.port
            httpUp   = $s.httpUp
            jarExists = $s.jarExists
            jarHealthy = $s.jarHealthy
            jarMtime = $s.jarMtime
        }
    }
    return $rows
}

function Handle-OpsRequest {
    param([System.Net.HttpListenerContext]$Context)
    $req = $Context.Request
    $res = $Context.Response
    $path = $req.Url.AbsolutePath.TrimEnd('/')
    if (-not $path) { $path = '/' }
    $method = $req.HttpMethod.ToUpperInvariant()

    if ($method -eq 'GET' -and $path -eq '/') {
        $html = Get-Content $htmlPath -Raw -Encoding UTF8
        Write-OpsHttp -Response $res -ContentType 'text/html; charset=utf-8' -Body $html
        return
    }
    if ($method -eq 'GET' -and $path -eq '/favicon.ico') {
        Write-OpsHttp -Response $res -StatusCode 204 -Body ''
        return
    }

    Sync-OpsJobs

    if ($method -eq 'GET' -and $path -eq '/api/status') {
        $jobs = @()
        foreach ($k in @($script:OpsJobs.Keys)) {
            $j = $script:OpsJobs[$k]
            $jobs += [ordered]@{
                id = $j.id
                label = $j.label
                state = $j.state
                message = $j.message
                startedAt = $j.startedAt
                finishedAt = $j.finishedAt
            }
        }
        Write-OpsJson -Response $res -Data ([ordered]@{
            timestamp   = (Get-Date).ToString('o')
            panelPort   = $Port
            gatewayUrl  = 'http://localhost:8080'
            profile     = $script:OpsProfile
            redisUp     = Test-MeisRedisAvailable
            tokenRequired = $true
            services    = @(Get-OpsServiceRows)
            jobs        = $jobs
            hint        = '变更操作请在页面填写口令；面板仅监听本机。'
        })
        return
    }

    if ($method -eq 'GET' -and $path -match '^/api/logs/([a-z0-9-]+)$') {
        $name = $Matches[1]
        $lines = 80
        if ($req.QueryString['lines'] -match '^\d+$') { $lines = [int]$req.QueryString['lines'] }
        $tail = Get-MeisServiceLogTail -ServiceName $name -Lines $lines
        Write-OpsJson -Response $res -Data ([ordered]@{
            name = $name
            lines = @($tail)
            fetchedAt = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
        })
        return
    }

    if ($method -eq 'POST') {
        if (-not (Test-OpsToken -Request $req)) {
            Write-OpsJson -Response $res -StatusCode 401 -Data @{ ok = $false; message = '口令错误或未提供（Header X-Meis-Ops-Token）' }
            return
        }

        if ($path -eq '/api/start-all') {
            $r = Start-OpsJob -Label '启动全部服务' -ArgumentMap @{ Action = 'start-all'; CoreOnly = $false }
            Write-OpsJson -Response $res -Data $r
            return
        }
        if ($path -eq '/api/start-core') {
            $r = Start-OpsJob -Label '启动核心服务' -ArgumentMap @{ Action = 'start-all'; CoreOnly = $true }
            Write-OpsJson -Response $res -Data $r
            return
        }
        if ($path -eq '/api/stop-all') {
            $r = Start-OpsJob -Label '停止全部服务' -ArgumentMap @{ Action = 'stop-all' }
            Write-OpsJson -Response $res -Data $r
            return
        }
        if ($path -match '^/api/service/([a-z0-9-]+)/(start|stop|restart)$') {
            $name = $Matches[1]
            $action = $Matches[2]
            try { Get-MeisServiceDefinition $name | Out-Null } catch {
                Write-OpsJson -Response $res -StatusCode 404 -Data @{ ok = $false; message = "未知服务: $name" }
                return
            }
            $map = @{
                start   = 'start-one'
                stop    = 'stop-one'
                restart = 'restart-one'
            }
            $labelMap = @{ start = "启动 $name"; stop = "停止 $name"; restart = "重启 $name" }
            $r = Start-OpsJob -Label $labelMap[$action] -ArgumentMap @{
                Action = $map[$action]
                ServiceName = $name
            }
            Write-OpsJson -Response $res -Data $r
            return
        }
    }

    Write-OpsHttp -Response $res -StatusCode 404 -Body 'Not Found'
}

$prefix = "http://localhost:$Port/"

$existing = Get-CimInstance Win32_Process -Filter "Name='powershell.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -like '*ops-panel.ps1*' -and $_.ProcessId -ne $PID }
foreach ($p in @($existing)) {
    Write-Host "Stopping stale ops panel PID $($p.ProcessId) ..." -ForegroundColor Yellow
    Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
}
if (@($existing).Count -gt 0) { Start-Sleep -Seconds 1 }

$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add($prefix)
try {
    $listener.Start()
} catch {
    throw "Cannot bind ${prefix}: $($_.Exception.Message)"
}

Write-Host "MEIS Ops Panel: $prefix" -ForegroundColor Cyan
Write-Host "Token required for start/stop. Profile=$Profile" -ForegroundColor DarkGray
Write-Host "Press Ctrl+C to stop the panel (services keep running)." -ForegroundColor DarkGray

if (-not $NoBrowser) {
    try { Start-Process $prefix } catch { }
}

try {
    while ($listener.IsListening) {
        $ctx = $listener.GetContext()
        try {
            Handle-OpsRequest -Context $ctx
        } catch {
            try {
                Write-OpsJson -Response $ctx.Response -StatusCode 500 -Data @{
                    ok = $false
                    message = $_.Exception.Message
                }
            } catch { }
        }
    }
} finally {
    try { if ($listener.IsListening) { $listener.Stop() } } catch { }
    Get-Job -Name 'meis-ops-*' -ErrorAction SilentlyContinue | Remove-Job -Force -ErrorAction SilentlyContinue
}
