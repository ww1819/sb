# MEIS local dev control panel - http://localhost:5099
param(
    [int]$Port = 5099,
    [switch]$NoBrowser
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\meis-services.ps1"

$panelDir = Join-Path $PSScriptRoot 'dev-panel'
$htmlPath = Join-Path $panelDir 'index.html'
if (-not (Test-Path $htmlPath)) { throw "Missing panel UI: $htmlPath" }

function Write-HttpResponse {
    param(
        [System.Net.HttpListenerResponse]$Response,
        [int]$StatusCode = 200,
        [string]$ContentType = 'text/plain; charset=utf-8',
        [string]$Body = ''
    )
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Body)
    $Response.StatusCode = $StatusCode
    $Response.ContentType = $ContentType
    $Response.ContentLength64 = $bytes.Length
    $Response.OutputStream.Write($bytes, 0, $bytes.Length)
    $Response.OutputStream.Close()
}

function Write-JsonResponse {
    param(
        [System.Net.HttpListenerResponse]$Response,
        [object]$Data,
        [int]$StatusCode = 200
    )
    $json = $Data | ConvertTo-Json -Depth 8 -Compress
    Write-HttpResponse -Response $Response -StatusCode $StatusCode -ContentType 'application/json; charset=utf-8' -Body $json
}

function Start-PanelBackgroundJob {
    param(
        [Parameter(Mandatory = $true)][string]$Label,
        [Parameter(Mandatory = $true)][ValidateSet('start-all', 'restart-all', 'build-backend', 'build-install')]
        [string]$Action
    )
    $scriptsDir = $PSScriptRoot
    $root = $script:MeisRoot
    $null = Start-Job -ScriptBlock {
        param($ActionName, $ScriptsDir, $Root)
        Set-Location $Root
        . (Join-Path $ScriptsDir 'meis-services.ps1')
        switch ($ActionName) {
            'start-all' { Start-MeisServices -Profile 'dev' }
            'restart-all' {
                Stop-MeisServices
                Start-Sleep -Seconds 2
                Start-MeisServices -Profile 'dev'
            }
            'build-backend' {
                $mvn = Resolve-MeisMaven
                $env:JAVA_HOME = Resolve-MeisJavaHome
                & $mvn -q package -DskipTests
                if ($LASTEXITCODE -ne 0) { throw 'Maven package failed' }
            }
            'build-install' {
                $mvn = Resolve-MeisMaven
                $env:JAVA_HOME = Resolve-MeisJavaHome
                & $mvn install -DskipTests
                if ($LASTEXITCODE -ne 0) { throw 'Maven install failed' }
            }
        }
    } -ArgumentList $Action, $scriptsDir, $root
    return @{ ok = $true; message = ($Label + ' started in background') }
}

function Invoke-PanelAction {
    param(
        [scriptblock]$Action,
        [string]$EventMessage
    )
    try {
        if ($EventMessage) { Add-MeisPanelEvent $EventMessage }
        $result = & $Action
        $msg = 'done'
        if ($result -and $result.message) { $msg = [string]$result.message }
        if ($EventMessage) { Add-MeisPanelEvent ($EventMessage + ' -> ' + $msg) }
        return @{ ok = $true; data = $result; message = $msg }
    } catch {
        if ($EventMessage) { Add-MeisPanelEvent ($EventMessage + ' FAILED: ' + $_.Exception.Message) }
        return @{ ok = $false; message = $_.Exception.Message }
    }
}

function Handle-PanelRequest {
    param([System.Net.HttpListenerContext]$Context)

    $req = $Context.Request
    $res = $Context.Response
    $path = $req.Url.AbsolutePath.TrimEnd('/')
    if (-not $path) { $path = '/' }
    $method = $req.HttpMethod.ToUpperInvariant()

    if ($method -eq 'GET' -and $path -eq '/') {
        $html = Get-Content $htmlPath -Raw -Encoding UTF8
        Write-HttpResponse -Response $res -ContentType 'text/html; charset=utf-8' -Body $html
        return
    }

    if ($method -eq 'GET' -and $path -eq '/api/status') {
        $payload = [ordered]@{
            timestamp = (Get-Date).ToString('o')
            gatewayUrl = 'http://localhost:8080'
            panelPort = $Port
            redisUp = Test-MeisRedisAvailable
            frontend = Get-MeisFrontendStatus
            services = @(Get-MeisServiceStatusList)
        }
        Write-JsonResponse -Response $res -Data $payload
        return
    }

    if ($method -eq 'GET' -and $path -eq '/api/logs/stream') {
        $service = $req.QueryString['service']
        if (-not $service) { $service = 'all' }
        $lines = 100
        if ($req.QueryString['lines'] -match '^\d+$') { $lines = [int]$req.QueryString['lines'] }
        $debug = $req.QueryString['debug'] -eq '1'
        $errorsOnly = $req.QueryString['errorsOnly'] -eq '1'
        $entries = Get-MeisPanelLogEntries -ServiceName $service -Lines $lines -Debug:$debug -ErrorsOnly:$errorsOnly
        Write-JsonResponse -Response $res -Data @{
            service = $service
            entries = $entries
            services = @(Get-MeisPanelLogServices)
            fetchedAt = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
        }
        return
    }

    if ($method -eq 'GET' -and $path -match "^/api/logs/([a-z0-9-]+)$") {
        $name = $Matches[1]
        $debug = $req.QueryString['debug'] -eq '1'
        $lines = 80
        if ($req.QueryString['lines'] -match '^\d+$') { $lines = [int]$req.QueryString['lines'] }
        $entries = Get-MeisPanelLogEntries -ServiceName $name -Lines $lines -Debug:$debug
        Write-JsonResponse -Response $res -Data @{
            name = $name
            entries = $entries
            lines = @($entries | ForEach-Object { $_.text })
            fetchedAt = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
        }
        return
    }

    if ($method -eq 'POST') {
        if ($path -eq '/api/backend/stop-all') {
            $r = Invoke-PanelAction { Stop-MeisServices; @{ message = 'all backend stopped' } } -EventMessage 'STOP all backend'
            Write-JsonResponse -Response $res -Data $r
            return
        }
        if ($path -eq '/api/backend/start-all') {
            Add-MeisPanelEvent 'START all backend (background)'
            $r = Start-PanelBackgroundJob -Label 'start-all-backend' -Action 'start-all'
            Write-JsonResponse -Response $res -Data $r
            return
        }
        if ($path -eq '/api/backend/restart-all') {
            Add-MeisPanelEvent 'RESTART all backend (background)'
            $r = Start-PanelBackgroundJob -Label 'restart-all-backend' -Action 'restart-all'
            Write-JsonResponse -Response $res -Data $r
            return
        }
        if ($path -eq '/api/frontend/start') {
            $r = Invoke-PanelAction { Start-MeisFrontend } -EventMessage 'START frontend'
            Write-JsonResponse -Response $res -Data $r
            return
        }
        if ($path -eq '/api/frontend/stop') {
            $r = Invoke-PanelAction { Stop-MeisFrontend; @{ message = 'frontend stopped' } } -EventMessage 'STOP frontend'
            Write-JsonResponse -Response $res -Data $r
            return
        }
        if ($path -eq '/api/frontend/restart') {
            $r = Invoke-PanelAction { Restart-MeisFrontend } -EventMessage 'RESTART frontend'
            Write-JsonResponse -Response $res -Data $r
            return
        }
        if ($path -eq '/api/build/backend') {
            Add-MeisPanelEvent 'BUILD backend (background)'
            $r = Start-PanelBackgroundJob -Label 'build-backend' -Action 'build-backend'
            Write-JsonResponse -Response $res -Data $r
            return
        }
        if ($path -eq '/api/build/install') {
            Add-MeisPanelEvent 'MAVEN INSTALL (background)'
            $r = Start-PanelBackgroundJob -Label 'maven-install' -Action 'build-install'
            Write-JsonResponse -Response $res -Data $r
            return
        }

        if ($path -match "^/api/service/([a-z0-9-]+)/(stop|start|restart|start-debug)$") {
            $name = $Matches[1]
            $action = $Matches[2]
            $eventLabel = ($action.ToUpper() + ' ' + $name)
            $r = switch ($action) {
                'stop' { Invoke-PanelAction { Stop-MeisServiceByName $name; @{ message = ($name + ' stopped') } } -EventMessage $eventLabel }
                'start' { Invoke-PanelAction { Start-MeisServiceByName -ServiceName $name } -EventMessage $eventLabel }
                'start-debug' { Invoke-PanelAction { Start-MeisServiceByName -ServiceName $name -Debug } -EventMessage ($eventLabel + ' (debug)') }
                'restart' { Invoke-PanelAction { Restart-MeisServiceByName -ServiceName $name } -EventMessage $eventLabel }
            }
            Write-JsonResponse -Response $res -Data $r
            return
        }
        if ($path -match "^/api/service/([a-z0-9-]+)/restart-debug$") {
            $name = $Matches[1]
            $r = Invoke-PanelAction { Restart-MeisServiceByName -ServiceName $name -Debug } -EventMessage ("RESTART-DEBUG " + $name)
            Write-JsonResponse -Response $res -Data $r
            return
        }
    }

    Write-HttpResponse -Response $res -StatusCode 404 -Body 'Not Found'
}

$prefix = "http://localhost:$Port/"

function Test-DevPanelRunning {
    try {
        $resp = Invoke-WebRequest -UseBasicParsing "$prefix/api/status" -TimeoutSec 2
        return $resp.StatusCode -eq 200
    } catch {
        return $false
    }
}

if (Test-DevPanelRunning) {
    Write-Host "MEIS Dev Panel already running: $prefix" -ForegroundColor Yellow
    if (-not $NoBrowser) { Start-Process $prefix }
    exit 0
}

$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add($prefix)

try {
    $listener.Start()
} catch {
    if (Test-DevPanelRunning) {
        Write-Host "MEIS Dev Panel already running: $prefix" -ForegroundColor Yellow
        if (-not $NoBrowser) { Start-Process $prefix }
        exit 0
    }
    throw "Cannot bind ${prefix} - $($_.Exception.Message)"
}

Write-Host "MEIS Dev Panel: $prefix" -ForegroundColor Cyan
Write-Host 'Press Ctrl+C to stop the panel server.' -ForegroundColor DarkGray

if (-not $NoBrowser) {
    Start-Process $prefix
}

try {
    while ($listener.IsListening) {
        $ctx = $listener.GetContext()
        try {
            Handle-PanelRequest -Context $ctx
        } catch {
            Write-Host "Request error: $($_.Exception.Message)" -ForegroundColor Red
            try {
                Write-JsonResponse -Response $ctx.Response -StatusCode 500 -Data @{ ok = $false; message = $_.Exception.Message }
            } catch {
                # ignore response errors
            }
        }
    }
} finally {
    $listener.Stop()
    $listener.Close()
}
