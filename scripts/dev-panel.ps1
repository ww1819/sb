# MEIS local dev control panel - http://localhost:5099
param(
    [int]$Port = 5099,
    [switch]$NoBrowser,
    [ValidateSet('', 'chrome', 'default')]
    [string]$Browser = ''
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\meis-services.ps1"

function Start-PanelBrowser {
    param([string]$Url)
    if ($Browser -eq 'chrome') {
        $chromePaths = @(
            "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
            ${env:ProgramFiles(x86)} + '\Google\Chrome\Application\chrome.exe',
            "$env:LOCALAPPDATA\Google\Chrome\Application\chrome.exe"
        )
        $chrome = $chromePaths | Where-Object { Test-Path $_ } | Select-Object -First 1
        if ($chrome) {
            Start-Process $chrome $Url
            return
        }
        Write-Host 'Chrome not found, opening default browser.' -ForegroundColor Yellow
    }
    Start-Process $Url
}

$panelDir = Join-Path $PSScriptRoot 'dev-panel'
$htmlPath = Join-Path $panelDir 'index.html'
if (-not (Test-Path $htmlPath)) { throw "Missing panel UI: $htmlPath" }
$script:PanelBuildPending = @{}
$script:PanelHotReloadResults = @{}
$script:PanelLibraryReloadResults = @{}
$script:PanelBackgroundJobs = [ordered]@{}

function Set-PanelLibraryReloadResult {
    param(
        [Parameter(Mandatory = $true)][string]$ModuleName,
        [Parameter(Mandatory = $true)][hashtable]$Result
    )
    $script:PanelLibraryReloadResults[$ModuleName] = [ordered]@{
        at        = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
        ok        = [bool]$Result.ok
        message   = [string]$Result.message
        steps     = @($Result.steps)
        reloaded  = @($Result.reloaded)
    }
}

function Set-PanelHotReloadResult {
    param(
        [Parameter(Mandatory = $true)][string]$ServiceName,
        [Parameter(Mandatory = $true)][hashtable]$Result
    )
    $script:PanelHotReloadResults[$ServiceName] = [ordered]@{
        at        = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
        ok        = [bool]$Result.ok
        message   = [string]$Result.message
        steps     = @($Result.steps)
        httpUp    = [bool]$Result.httpUp
        debugUp   = [bool]$Result.debugUp
        fileCount = if ($null -ne $Result.fileCount) { [int]$Result.fileCount } else { 0 }
    }
}

function Test-PanelClientDisconnectError {
    param([System.Exception]$Ex)
    if (-not $Ex) { return $false }
    $msg = $Ex.Message
    if ($msg -match 'network name|连接|已被关闭|aborted|reset|forcibly closed|transport|不再可用|broken pipe|远程主机') {
        return $true
    }
    if ($msg -match 'OperationCanceled|cancelled|canceled') {
        return $true
    }
    if ($Ex.InnerException) {
        return Test-PanelClientDisconnectError $Ex.InnerException
    }
    return $false
}

function Invoke-PanelRequestSafe {
    param([System.Net.HttpListenerContext]$Context)
    try {
        Handle-PanelRequest -Context $Context
    } catch {
        if (Test-PanelClientDisconnectError $_.Exception) {
            Close-HttpResponseSafe $Context.Response
            return
        }
        Write-Host "Request error: $($_.Exception.Message)" -ForegroundColor DarkYellow
        try {
            Write-JsonResponse -Response $Context.Response -StatusCode 500 -Data @{ ok = $false; message = $_.Exception.Message }
        } catch {
            Close-HttpResponseSafe $Context.Response
        }
    }
}

function Close-HttpResponseSafe {
    param([System.Net.HttpListenerResponse]$Response)
    if (-not $Response) { return }
    try { $Response.OutputStream.Close() } catch { }
    try { $Response.Close() } catch { }
}

function Write-HttpResponse {
    param(
        [System.Net.HttpListenerResponse]$Response,
        [int]$StatusCode = 200,
        [string]$ContentType = 'text/plain; charset=utf-8',
        [string]$Body = ''
    )
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Body)
    try {
        $Response.StatusCode = $StatusCode
        $Response.ContentType = $ContentType
        $Response.ContentLength64 = $bytes.Length
        if ($bytes.Length -gt 0) {
            $Response.OutputStream.Write($bytes, 0, $bytes.Length)
        }
    } catch {
        if (-not (Test-PanelClientDisconnectError $_.Exception)) { throw }
    } finally {
        Close-HttpResponseSafe $Response
    }
}

function ConvertTo-PanelJsonData {
    param([object]$Value)
    if ($null -eq $Value) { return $null }
    if ($Value -is [System.Collections.IDictionary]) {
        $obj = [ordered]@{}
        foreach ($key in $Value.Keys) {
            $obj[$key] = ConvertTo-PanelJsonData $Value[$key]
        }
        return [PSCustomObject]$obj
    }
    if ($Value -is [System.Collections.IEnumerable] -and $Value -isnot [string]) {
        $items = @($Value | ForEach-Object { ConvertTo-PanelJsonData $_ })
        # 单元素数组在赋给 PSCustomObject 属性时会被 PowerShell 展平为标量，导致 JSON 不是数组
        return ,$items
    }
    return $Value
}

function Write-JsonResponse {
    param(
        [System.Net.HttpListenerResponse]$Response,
        [object]$Data,
        [int]$StatusCode = 200
    )
    $json = (ConvertTo-PanelJsonData $Data) | ConvertTo-Json -Depth 6 -Compress
    Write-HttpResponse -Response $Response -StatusCode $StatusCode -ContentType 'application/json; charset=utf-8' -Body $json
}

function Sync-PanelBackgroundJobs {
    $running = [System.Collections.ArrayList]@()
    foreach ($label in @($script:PanelBackgroundJobs.Keys)) {
        $job = $script:PanelBackgroundJobs[$label]
        if ($null -eq $job) {
            $script:PanelBackgroundJobs.Remove($label) | Out-Null
            continue
        }
        $state = $job.State
        if ($state -eq 'Running') {
            [void]$running.Add([string]$label)
            continue
        }
        try {
            $null = Receive-Job $job -ErrorAction SilentlyContinue
            if ($state -eq 'Failed') {
                Add-MeisPanelEvent ($label + ' FAILED (background job)')
            } else {
                Add-MeisPanelEvent ($label + ' -> done')
            }
        } catch {
            Add-MeisPanelEvent ($label + ' FAILED: ' + $_.Exception.Message)
        }
        Remove-Job $job -Force -ErrorAction SilentlyContinue
        $script:PanelBackgroundJobs.Remove($label) | Out-Null
    }
    return @($running)
}

function Start-PanelBackgroundJob {
    param(
        [Parameter(Mandatory = $true)][string]$Label,
        [Parameter(Mandatory = $true)][ValidateSet('start-all', 'start-core', 'start-debug-all', 'start-debug-core', 'restart-all', 'stop-all', 'build-backend', 'build-install', 'build-compile', 'build-clean')]
        [string]$Action
    )
    $scriptsDir = $PSScriptRoot
    $root = $script:MeisRoot
    $job = Start-Job -ScriptBlock {
        param($ActionName, $ScriptsDir, $Root)
        Set-Location $Root
        . (Join-Path $ScriptsDir 'meis-services.ps1')
        switch ($ActionName) {
            'start-all' { Start-MeisServices -Profile 'dev' }
            'start-core' { Start-MeisServices -Profile 'dev' -CoreOnly }
            'start-debug-all' { Start-MeisServices -Profile 'dev' -EnableJdwp }
            'start-debug-core' { Start-MeisServices -Profile 'dev' -CoreOnly -EnableJdwp }
            'restart-all' {
                Stop-MeisServices
                Start-Sleep -Seconds 2
                Start-MeisServices -Profile 'dev'
            }
            'stop-all' { Stop-MeisServices | Out-Null }
            'build-clean' {
                Invoke-MeisMavenReactor -Goal clean -Quiet | Out-Null
            }
            'build-compile' {
                Invoke-MeisMavenReactor -Goal compile -Quiet | Out-Null
            }
            'build-backend' {
                Invoke-MeisMavenReactor -Goal package -Quiet | Out-Null
            }
            'build-install' {
                Invoke-MeisMavenReactor -Goal install | Out-Null
            }
        }
    } -ArgumentList $Action, $scriptsDir, $root
    $script:PanelBackgroundJobs[$Label] = $job
    return @{ ok = $true; message = ($Label + ' started in background') }
}

function Start-PanelServiceBackgroundJob {
    param(
        [Parameter(Mandatory = $true)][string]$ServiceName,
        [Parameter(Mandatory = $true)][ValidateSet('start', 'start-debug', 'restart', 'build', 'reload-classes')]
        [string]$Action,
        [string]$BuildMode = '',
        [bool]$DoEnableJdwp = $false
    )
    $scriptsDir = $PSScriptRoot
    $root = $script:MeisRoot
    if ($Action -eq 'build') {
        $script:PanelBuildPending[$ServiceName] = (Get-Date)
    }
    $null = Start-Job -ScriptBlock {
        param($ActionName, $Name, $ScriptsDir, $Root, $BuildMode, $DoEnableJdwp)
        Set-Location $Root
        . (Join-Path $ScriptsDir 'meis-services.ps1')
        try {
            switch ($ActionName) {
                'start' {
                    Start-MeisServiceByName -ServiceName $Name -Profile 'dev' | Out-Null
                }
                'start-debug' {
                    Start-MeisServiceByName -ServiceName $Name -Profile 'dev' -EnableJdwp | Out-Null
                }
                'restart' {
                    Stop-MeisServiceByName $Name | Out-Null
                    Start-Sleep -Seconds 1
                    Start-MeisServiceByName -ServiceName $Name -Profile 'dev' -EnableJdwp:$DoEnableJdwp | Out-Null
                }
                'build' {
                    switch ($BuildMode) {
                        'clean-package' {
                            Invoke-MeisMavenModule -Module $Name -Clean -Package -AlsoMake | Out-Null
                        }
                        'package' {
                            Invoke-MeisMavenModule -Module $Name -Package -AlsoMake | Out-Null
                        }
                        'quick' {
                            $health = Test-MeisServiceJarHealthy $Name
                            if (-not $health.ok) {
                                Invoke-MeisMavenModule -Module $Name -Package -AlsoMake | Out-Null
                            }
                            Invoke-MeisMavenModule -Module $Name -Compile -AlsoMake | Out-Null
                            Sync-MeisServiceClassesToJar -ServiceName $Name | Out-Null
                        }
                        default {
                            throw ('Unknown build mode: ' + $BuildMode)
                        }
                    }
                }
                'reload-classes' {
                    Invoke-MeisServiceHotReload -ServiceName $Name | Out-Null
                }
            }
        } catch {
            Add-MeisPanelEvent (($ActionName.ToUpper() + ' ' + $Name + ' FAILED: ' + $_.Exception.Message))
            throw
        }
    } -ArgumentList $Action, $ServiceName, $scriptsDir, $root, $BuildMode, [bool]$DoEnableJdwp
    $verb = switch ($Action) {
        'start' { 'starting' }
        'start-debug' { 'starting (debug)' }
        'restart' { 'restarting' }
        'build' { 'building' }
        'reload-classes' { 'hot-reloading' }
    }
    return @{ ok = $true; message = ($ServiceName + ' ' + $verb + ' in background') }
}

function Start-PanelFrontendBackgroundJob {
    param(
        [Parameter(Mandatory = $true)][ValidateSet('start', 'restart', 'build')]
        [string]$Action,
        [string]$BuildMode = ''
    )
    $scriptsDir = $PSScriptRoot
    $root = $script:MeisRoot
    if ($Action -eq 'build') {
        $script:PanelBuildPending['meis-web'] = Get-Date
    }
    $null = Start-Job -ScriptBlock {
        param($ActionName, $Mode, $ScriptsDir, $Root)
        Set-Location $Root
        . (Join-Path $ScriptsDir 'meis-services.ps1')
        try {
            switch ($ActionName) {
                'start' {
                    Start-MeisFrontend | Out-Null
                }
                'restart' {
                    Stop-MeisFrontend | Out-Null
                    Start-Sleep -Seconds 1
                    Start-MeisFrontend | Out-Null
                }
                'build' {
                    switch ($Mode) {
                        'install' {
                            Build-MeisFrontendProject -NpmInstall | Out-Null
                        }
                        'typecheck' {
                            Build-MeisFrontendProject -TypeCheck | Out-Null
                        }
                        'build' {
                            Build-MeisFrontendProject -Build | Out-Null
                        }
                        default {
                            throw ('Unknown frontend build mode: ' + $Mode)
                        }
                    }
                }
            }
        } catch {
            Add-MeisPanelEvent (('FRONTEND ' + $ActionName.ToUpper() + ' FAILED: ' + $_.Exception.Message))
            throw
        }
    } -ArgumentList $Action, $BuildMode, $scriptsDir, $root
    $verb = switch ($Action) {
        'start' { 'starting dev server' }
        'restart' { 'restarting dev server' }
        'build' { 'building' }
    }
    return @{ ok = $true; message = ('meis-web ' + $verb + ' in background') }
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
        $ok = $true
        if ($result -and $result.message) { $msg = [string]$result.message }
        if ($result -and $null -ne $result.ok) { $ok = [bool]$result.ok }
        if ($EventMessage) { Add-MeisPanelEvent ($EventMessage + ' -> ' + $msg) }
        return @{ ok = $ok; data = $result; message = $msg }
    } catch {
        if ($EventMessage) { Add-MeisPanelEvent ($EventMessage + ' FAILED: ' + $_.Exception.Message) }
        return @{ ok = $false; message = $_.Exception.Message }
    }
}

function Get-PanelFrontendStatus {
    $fe = Get-MeisFrontendStatus
    $building = $false
    if ($script:PanelBuildPending.ContainsKey('meis-web')) {
        $started = $script:PanelBuildPending['meis-web']
        if (((Get-Date) - $started).TotalSeconds -lt 600) {
            $building = $true
        } else {
            $script:PanelBuildPending.Remove('meis-web') | Out-Null
        }
    }
    $fe.buildInProgress = $building
    return $fe
}

function Test-MeisClassesUpdatedSince {
    param(
        [Parameter(Mandatory = $true)][string]$ModuleName,
        [Parameter(Mandatory = $true)][datetime]$Since
    )
    $classesDir = Join-Path $script:MeisRoot "$ModuleName\target\classes"
    if (-not (Test-Path $classesDir)) { return $false }
    $recent = Get-ChildItem $classesDir -Recurse -File -ErrorAction SilentlyContinue |
        Where-Object { $_.LastWriteTime -ge $Since } |
        Select-Object -First 1
    return $null -ne $recent
}

function Clear-PanelBuildPending {
    foreach ($key in @($script:PanelBuildPending.Keys)) {
        $script:PanelBuildPending.Remove($key) | Out-Null
    }
}

function Get-PanelServiceStatusList {
    $list = @(Get-MeisServiceStatusList)
    $now = Get-Date
    foreach ($item in $list) {
        $name = [string]$item.name
        $building = $false
        if ($script:PanelBuildPending.ContainsKey($name)) {
            $started = $script:PanelBuildPending[$name]
            $elapsed = ($now - $started).TotalSeconds
            $jar = Join-Path $script:MeisRoot "$name\target\$name-1.0.0-SNAPSHOT.jar"
            $classesReady = Test-MeisClassesUpdatedSince -ModuleName $name -Since $started
            if ($elapsed -lt 300 -and -not (Test-Path $jar) -and -not $classesReady) {
                $building = $true
            } else {
                $script:PanelBuildPending.Remove($name) | Out-Null
            }
        }
        $item.buildInProgress = $building
        if ($script:PanelHotReloadResults.ContainsKey($name)) {
            $item.hotReload = $script:PanelHotReloadResults[$name]
        }
    }
    return $list
}

function Get-PanelLibraryStatusList {
    $list = @(Get-MeisLibraryModuleStatusList)
    $now = Get-Date
    foreach ($item in $list) {
        $name = [string]$item.name
        $building = $false
        if ($script:PanelBuildPending.ContainsKey($name)) {
            $started = $script:PanelBuildPending[$name]
            $classesReady = Test-MeisClassesUpdatedSince -ModuleName $name -Since $started
            if ($classesReady -or (($now - $started).TotalSeconds -ge 300)) {
                $script:PanelBuildPending.Remove($name) | Out-Null
            } else {
                $building = $true
            }
        }
        $item.buildInProgress = $building
        if ($script:PanelLibraryReloadResults.ContainsKey($name)) {
            $item.hotReload = $script:PanelLibraryReloadResults[$name]
        }
    }
    return $list
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

    if ($method -eq 'GET' -and $path -eq '/favicon.ico') {
        Write-HttpResponse -Response $res -StatusCode 204 -Body ''
        return
    }

    if ($method -eq 'GET' -and $path -eq '/api/status') {
        $backgroundJobs = Sync-PanelBackgroundJobs
        $payload = [ordered]@{
            timestamp = (Get-Date).ToString('o')
            gatewayUrl = 'http://localhost:8080'
            panelPort = $Port
            redisUp = Test-MeisRedisAvailable
            backgroundJobs = $backgroundJobs
            frontend = Get-PanelFrontendStatus
            coreServices = @($script:MeisCoreServiceNames)
            libraries = @(Get-PanelLibraryStatusList)
            services = @(Get-PanelServiceStatusList)
        }
        Write-JsonResponse -Response $res -Data $payload
        return
    }

    if ($method -eq 'GET' -and $path -eq '/api/logs/stream') {
        $service = $req.QueryString['service']
        if (-not $service) { $service = 'all' }
        $lines = 100
        if ($req.QueryString['lines'] -match '^\d+$') { $lines = [int]$req.QueryString['lines'] }
        $debugLogs = $req.QueryString['debug'] -eq '1'
        $errorsOnly = $req.QueryString['errorsOnly'] -eq '1'
        $watchedRaw = $req.QueryString['watched']
        $watched = $null
        if ($null -ne $watchedRaw) {
            $watched = @($watchedRaw.Split(',') | ForEach-Object { $_.Trim() } | Where-Object { $_ })
        }
        $filterRaw = $req.QueryString['filter']
        $filtered = $null
        if ($null -ne $filterRaw) {
            $filtered = @($filterRaw.Split(',') | ForEach-Object { $_.Trim() } | Where-Object { $_ })
        }
        $entries = Get-MeisPanelLogEntries -ServiceName $service -Lines $lines -DebugLogs:$debugLogs -ErrorsOnly:$errorsOnly -Watched $watched -Filtered $filtered
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
        $debugLogs = $req.QueryString['debug'] -eq '1'
        $lines = 80
        if ($req.QueryString['lines'] -match '^\d+$') { $lines = [int]$req.QueryString['lines'] }
        $entries = Get-MeisPanelLogEntries -ServiceName $name -Lines $lines -DebugLogs:$debugLogs
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
            Add-MeisPanelEvent 'STOP all backend (background)'
            $r = Start-PanelBackgroundJob -Label 'stop-all-backend' -Action 'stop-all'
            Write-JsonResponse -Response $res -Data $r
            return
        }
        if ($path -eq '/api/backend/start-all') {
            Add-MeisPanelEvent 'START all backend (background, skip running)'
            $r = Start-PanelBackgroundJob -Label 'start-all-backend' -Action 'start-all'
            Write-JsonResponse -Response $res -Data $r
            return
        }
        if ($path -eq '/api/backend/start-core') {
            Add-MeisPanelEvent 'START core backend (background, skip running)'
            $r = Start-PanelBackgroundJob -Label 'start-core-backend' -Action 'start-core'
            Write-JsonResponse -Response $res -Data $r
            return
        }
        if ($path -eq '/api/backend/start-debug-all') {
            Add-MeisPanelEvent 'START all backend with JDWP (background, skip running)'
            $r = Start-PanelBackgroundJob -Label 'start-debug-all-backend' -Action 'start-debug-all'
            Write-JsonResponse -Response $res -Data $r
            return
        }
        if ($path -eq '/api/backend/start-debug-core') {
            Add-MeisPanelEvent 'START core backend with JDWP (background, skip running)'
            $r = Start-PanelBackgroundJob -Label 'start-debug-core-backend' -Action 'start-debug-core'
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
            Add-MeisPanelEvent 'START frontend dev server (background)'
            $r = Start-PanelFrontendBackgroundJob -Action 'start'
            Write-JsonResponse -Response $res -Data $r
            return
        }
        if ($path -eq '/api/frontend/stop') {
            $r = Invoke-PanelAction { Stop-MeisFrontend; @{ message = 'frontend stopped' } } -EventMessage 'STOP frontend'
            Write-JsonResponse -Response $res -Data $r
            return
        }
        if ($path -eq '/api/frontend/restart') {
            Add-MeisPanelEvent 'RESTART frontend dev server (background)'
            $r = Start-PanelFrontendBackgroundJob -Action 'restart'
            Write-JsonResponse -Response $res -Data $r
            return
        }
        if ($path -eq '/api/frontend/build') {
            $mode = [string]$req.QueryString.Get('mode')
            if ([string]::IsNullOrWhiteSpace($mode)) { $mode = 'typecheck' }
            Add-MeisPanelEvent ('BUILD frontend (background, mode=' + $mode + ')')
            $r = Start-PanelFrontendBackgroundJob -Action 'build' -BuildMode $mode
            Write-JsonResponse -Response $res -Data $r
            return
        }
        if ($path -eq '/api/build/clean') {
            Add-MeisPanelEvent 'MAVEN CLEAN ALL (background)'
            Clear-PanelBuildPending
            $r = Start-PanelBackgroundJob -Label 'reactor-clean' -Action 'build-clean'
            Write-JsonResponse -Response $res -Data $r
            return
        }
        if ($path -eq '/api/build/backend') {
            Add-MeisPanelEvent 'BUILD backend (background)'
            Clear-PanelBuildPending
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
        if ($path -eq '/api/panel/shutdown') {
            Add-MeisPanelEvent 'SHUTDOWN panel'
            Write-JsonResponse -Response $res -Data @{ ok = $true; message = 'panel shutting down' }
            Start-Job -ScriptBlock {
                param($PanelPid)
                Start-Sleep -Milliseconds 400
                Stop-Process -Id $PanelPid -Force -ErrorAction SilentlyContinue
            } -ArgumentList $PID | Out-Null
            try {
                if ($script:DevPanelListener -and $script:DevPanelListener.IsListening) {
                    $script:DevPanelListener.Stop()
                }
            } catch { }
            return
        }

        if ($path -match "^/api/library/([a-z0-9-]+)/(compile|reload-dependents)$") {
            $name = $Matches[1]
            $action = $Matches[2]
            if ($action -eq 'compile') {
                $script:PanelBuildPending[$name] = Get-Date
                $r = Invoke-PanelAction {
                    Invoke-MeisMavenModule -Module $name -Compile | Out-Null
                    @{ ok = $true; message = ($name + ' compile OK') }
                } -EventMessage ('COMPILE library ' + $name)
                if (-not $r.ok) {
                    $script:PanelBuildPending.Remove($name) | Out-Null
                }
                Write-JsonResponse -Response $res -Data $r
                return
            }
            if ($action -eq 'reload-dependents') {
                $r = Invoke-PanelAction {
                    $result = Invoke-MeisLibraryHotReloadDependents -ModuleName $name
                    Set-PanelLibraryReloadResult -ModuleName $name -Result $result
                    $result
                } -EventMessage ('RELOAD-DEPENDENTS ' + $name)
                Write-JsonResponse -Response $res -Data $r
                return
            }
        }

        if ($path -match "^/api/service/([a-z0-9-]+)/(stop|start|restart|start-debug|build|reload-classes)$") {
            $name = $Matches[1]
            $action = $Matches[2]
            $eventLabel = ($action.ToUpper() + ' ' + $name)
            $r = switch ($action) {
                'stop' { Invoke-PanelAction { Stop-MeisServiceByName $name; @{ message = ($name + ' stopped') } } -EventMessage $eventLabel }
                'start' {
                    Add-MeisPanelEvent ($eventLabel + ' (background)')
                    Start-PanelServiceBackgroundJob -ServiceName $name -Action 'start'
                }
                'start-debug' {
                    Add-MeisPanelEvent ($eventLabel + ' (background, debug)')
                    Start-PanelServiceBackgroundJob -ServiceName $name -Action 'start-debug'
                }
                'restart' {
                    Add-MeisPanelEvent ($eventLabel + ' (background)')
                    Start-PanelServiceBackgroundJob -ServiceName $name -Action 'restart'
                }
                'build' {
                    $modeLabel = [string]$req.QueryString.Get('mode')
                    if ([string]::IsNullOrWhiteSpace($modeLabel)) { $modeLabel = 'quick' }
                    Add-MeisPanelEvent ('BUILD ' + $name + ' (background, mode=' + $modeLabel + ')')
                    Start-PanelServiceBackgroundJob -ServiceName $name -Action 'build' -BuildMode $modeLabel
                }
                'reload-classes' {
                    Invoke-PanelAction {
                        $r = Invoke-MeisServiceHotReload -ServiceName $name
                        Set-PanelHotReloadResult -ServiceName $name -Result $r
                        $r
                    } -EventMessage $eventLabel
                }
            }
            Write-JsonResponse -Response $res -Data $r
            return
        }
        if ($path -match "^/api/service/([a-z0-9-]+)/restart-debug$") {
            $name = $Matches[1]
            Add-MeisPanelEvent ("RESTART-DEBUG " + $name + ' (background)')
            $r = Start-PanelServiceBackgroundJob -ServiceName $name -Action 'restart' -DoEnableJdwp:$true
            Write-JsonResponse -Response $res -Data $r
            return
        }
    }

    Write-HttpResponse -Response $res -StatusCode 404 -Body 'Not Found'
}

$prefix = "http://localhost:$Port/"

function Get-DevPanelProcesses {
    $self = $PID
    Get-CimInstance Win32_Process -Filter "Name='powershell.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -like '*dev-panel.ps1*' -and $_.ProcessId -ne $self }
}

function Stop-StaleDevPanel {
    $stale = @(Get-DevPanelProcesses)
    foreach ($p in $stale) {
        Write-Host "Stopping stale dev panel PID $($p.ProcessId) ..." -ForegroundColor Yellow
        Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
    }
    if ($stale.Count -gt 0) {
        Start-Sleep -Seconds 2
    }
    return $stale.Count
}

function Test-DevPanelPortBusy {
    return Test-MeisPortListening -Port $Port
}

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
    if (-not $NoBrowser) { Start-PanelBrowser $prefix }
    exit 0
}

if ((@(Get-DevPanelProcesses)).Count -gt 0 -or (Test-DevPanelPortBusy)) {
    Write-Host "Port $Port is busy but panel API is not responding - recycling stale instance ..." -ForegroundColor Yellow
    Stop-StaleDevPanel | Out-Null
}

$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add($prefix)

try {
    $listener.Start()
} catch {
    if (Test-DevPanelRunning) {
        Write-Host "MEIS Dev Panel already running: $prefix" -ForegroundColor Yellow
        if (-not $NoBrowser) { Start-PanelBrowser $prefix }
        exit 0
    }
    Write-Host "Port $Port bind failed, retrying after cleanup ..." -ForegroundColor Yellow
    Stop-StaleDevPanel | Out-Null
    Start-Sleep -Seconds 2
    $listener = New-Object System.Net.HttpListener
    $listener.Prefixes.Add($prefix)
    try {
        $listener.Start()
    } catch {
        throw "Cannot bind ${prefix} - $($_.Exception.Message). Try: Get-Process powershell | Where CommandLine -like '*dev-panel*' | Stop-Process -Force"
    }
}

$script:DevPanelListener = $listener

Write-Host "MEIS Dev Panel: $prefix" -ForegroundColor Cyan
Write-Host 'Press Ctrl+C to stop the panel server.' -ForegroundColor DarkGray
Write-Host 'Or run: powershell -File scripts\stop-dev-panel.ps1' -ForegroundColor DarkGray

if (-not $NoBrowser) {
    Start-PanelBrowser $prefix
}

try {
    while ($listener.IsListening) {
        try {
            $ctx = $listener.GetContext()
        } catch {
            break
        }
        Invoke-PanelRequestSafe -Context $ctx
    }
} finally {
    try { if ($listener.IsListening) { $listener.Stop() } } catch { }
    try { $listener.Close() } catch { }
    $script:DevPanelListener = $null
}
