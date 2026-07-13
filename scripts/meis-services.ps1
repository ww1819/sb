# MEIS service list (shared by start/stop/restart scripts)
$script:MeisRoot = Split-Path $PSScriptRoot -Parent

function Resolve-MeisJavaHome {
    foreach ($jdkHome in @($env:MEIS_JAVA_HOME, 'E:\workspace\jdk-17', 'C:\Program Files\Java\jdk-17', 'D:\Program Files\Java\jdk-17')) {
        if (-not $jdkHome) { continue }
        $java = Join-Path $jdkHome 'bin\java.exe'
        if (Test-Path $java) { return $jdkHome }
    }
    throw 'JDK 17 not found. Install to E:\workspace\jdk-17 or set MEIS_JAVA_HOME.'
}

function Resolve-MeisMaven {
    foreach ($candidate in @(
        $env:MEIS_MAVEN_CMD,
        $env:MEIS_MAVEN_HOME,
        'D:\JAVA\apache-maven-3.8.4',
        'C:\apache-maven-3.8.4',
        'E:\workspace\apache-maven-3.8.4'
    )) {
        if (-not $candidate) { continue }
        if ($candidate -match '\\mvn\.cmd$' -and (Test-Path $candidate)) { return $candidate }
        $cmd = Join-Path $candidate 'bin\mvn.cmd'
        if (Test-Path $cmd) { return $cmd }
    }
    $inPath = Get-Command mvn -ErrorAction SilentlyContinue
    if ($inPath) { return $inPath.Source }
    throw 'Maven not found. Install to D:\JAVA\apache-maven-3.8.4 or set MEIS_MAVEN_HOME / MEIS_MAVEN_CMD.'
}

$script:MeisServicePorts = @(8082, 8081, 8083, 8084, 8085, 8086, 8087, 8088, 8089, 8090, 8091, 8092, 8093, 8094, 8080)

$script:MeisServices = @(
    @{ name = "meis-tenant"; port = 8082; debugPort = 5802 },
    @{ name = "meis-auth"; port = 8081; debugPort = 5801 },
    @{ name = "meis-system"; port = 8083; debugPort = 5803 },
    @{ name = "meis-purchase"; port = 8084; debugPort = 5804 },
    @{ name = "meis-asset"; port = 8085; debugPort = 5805 },
    @{ name = "meis-repair"; port = 8086; debugPort = 5806 },
    @{ name = "meis-maintain"; port = 8087; debugPort = 5807 },
    @{ name = "meis-qc"; port = 8088; debugPort = 5808 },
    @{ name = "meis-maintenance-contract"; port = 8089; debugPort = 5809 },
    @{ name = "meis-special"; port = 8090; debugPort = 5810 },
    @{ name = "meis-analytics"; port = 8091; debugPort = 5811 },
    @{ name = "meis-file"; port = 8092; debugPort = 5812 },
    @{ name = "meis-notification"; port = 8093; debugPort = 5813 },
    @{ name = "meis-integration"; port = 8094; debugPort = 5814 },
    @{ name = "meis-gateway"; port = 8080; debugPort = 5800 }
)

# Core backend for local dev: tenant/auth/system + common file API + gateway (gateway last)
$script:MeisCoreServiceNames = @(
    'meis-tenant',
    'meis-auth',
    'meis-system',
    'meis-file',
    'meis-gateway'
)

# 公共库模块（无 HTTP 端口，classes 打入各微服务 JAR）
$script:MeisLibraryModules = @(
    @{ name = 'meis-common'; artifactId = 'meis-common' },
    @{ name = 'meis-api'; artifactId = 'meis-api' }
)

$script:MeisServiceMetaCache = $null

function Get-MeisServiceMetaMap {
    if ($null -ne $script:MeisServiceMetaCache) { return $script:MeisServiceMetaCache }
    $metaPath = Join-Path $PSScriptRoot 'dev-panel\services-meta.json'
    if (-not (Test-Path $metaPath)) {
        $script:MeisServiceMetaCache = @{}
        return $script:MeisServiceMetaCache
    }
    $raw = Get-Content $metaPath -Raw -Encoding UTF8
    $script:MeisServiceMetaCache = @{}
    foreach ($prop in ($raw | ConvertFrom-Json).PSObject.Properties) {
        $script:MeisServiceMetaCache[$prop.Name] = @{
            labelZh = [string]$prop.Value.labelZh
            descZh  = [string]$prop.Value.descZh
        }
    }
    return $script:MeisServiceMetaCache
}

function Get-MeisServiceMetaEntry {
    param([Parameter(Mandatory = $true)][string]$ServiceName)
    $map = Get-MeisServiceMetaMap
    if ($map.ContainsKey($ServiceName)) {
        return $map[$ServiceName]
    }
    return @{ labelZh = $ServiceName; descZh = '' }
}

$script:MeisFrontendPort = 5173

function Stop-MeisServices {
    $killed = @{}

    foreach ($s in $script:MeisServices) {
        $n = Stop-MeisServiceByName $s.name
        if ($n -gt 0) {
            $killed["svc:$($s.name)"] = $n
        }
    }

    Start-Sleep -Seconds 1

    # Fallback: any java process running a meis jar
    Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue | ForEach-Object {
        if ($_.CommandLine -and $_.CommandLine -match 'meis-[a-z-]+.*\.jar') {
            $procId = $_.ProcessId
            if (-not $killed.ContainsKey($procId)) {
                Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
                $killed[$procId] = $true
                Write-Host "Stopped PID $procId (meis jar)"
            }
        }
    }

    Start-Sleep -Milliseconds 500

    # Fallback: listeners on known backend ports (skip System PID 4)
    foreach ($port in $script:MeisServicePorts) {
        foreach ($procId in (Get-MeisProcessIdsOnPort -Port $port)) {
            if ($procId -le 4 -or $killed.ContainsKey($procId)) { continue }
            Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
            $killed[$procId] = $true
            Write-Host "Stopped PID $procId (port $port)"
        }
    }

    $stillUp = @()
    foreach ($s in $script:MeisServices) {
        if (Test-MeisPortListening -Port $s.port) {
            $stillUp += $s.name
        }
    }

    $count = $killed.Count
    if ($count -eq 0 -and $stillUp.Count -eq 0) {
        Write-Host "No MEIS backend processes found."
    } else {
        Write-Host "Stopped $count process handle(s)."
    }
    if ($stillUp.Count -gt 0) {
        Write-Host "Still listening: $($stillUp -join ', ')" -ForegroundColor Yellow
    }

    $msg = if ($stillUp.Count -eq 0) { "all backend stopped ($count killed)" } else { "partial stop ($count killed), still up: $($stillUp -join ', ')" }
    Clear-MeisListeningPortCache
    return @{ ok = ($stillUp.Count -eq 0); message = $msg; killed = $count; stillUp = $stillUp }
}

$script:MeisListeningPortCache = $null
$script:MeisListeningPortCacheAt = [datetime]::MinValue
$script:MeisListeningPortCacheTtlMs = 800

function Clear-MeisListeningPortCache {
    $script:MeisListeningPortCache = $null
    $script:MeisListeningPortCacheAt = [datetime]::MinValue
}

# 单次 netstat 解析全部 LISTENING 端口，供状态轮询复用（避免每服务一次 netstat 导致 /api/status 阻塞数秒）。
function Get-MeisListeningPortSet {
    param([switch]$ForceRefresh)
    if (-not $ForceRefresh -and $null -ne $script:MeisListeningPortCache) {
        $ageMs = ((Get-Date) - $script:MeisListeningPortCacheAt).TotalMilliseconds
        if ($ageMs -lt $script:MeisListeningPortCacheTtlMs) {
            return $script:MeisListeningPortCache
        }
    }
    $ports = New-Object 'System.Collections.Generic.HashSet[int]'
    foreach ($line in (netstat -ano 2>$null)) {
        if ($line -notmatch 'LISTENING') { continue }
        if ($line -match ':(\d+)\s+\S+\s+LISTENING') {
            [void]$ports.Add([int]$Matches[1])
        }
    }
    $script:MeisListeningPortCache = $ports
    $script:MeisListeningPortCacheAt = Get-Date
    return $ports
}

function Test-MeisPortListening {
    param([int]$Port, [switch]$ForceRefresh)
    $set = Get-MeisListeningPortSet -ForceRefresh:$ForceRefresh
    return $set.Contains($Port)
}

function Test-MeisRedisAvailable {
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $client.ReceiveTimeout = 2000
        $client.SendTimeout = 2000
        $client.Connect('127.0.0.1', 6379)
        $stream = $client.GetStream()
        $ping = [System.Text.Encoding]::ASCII.GetBytes("*1`r`n`$4`r`nPING`r`n")
        $stream.Write($ping, 0, $ping.Length)
        $buf = New-Object byte[] 128
        $read = $stream.Read($buf, 0, $buf.Length)
        $client.Close()
        if ($read -le 0) { return $false }
        $resp = [System.Text.Encoding]::ASCII.GetString($buf, 0, $read)
        return $resp -match 'PONG'
    } catch {
        return $false
    }
}

function Ensure-MeisRedis {
    if (Test-MeisRedisAvailable) {
        Write-Host "  OK redis PING/PONG :6379" -ForegroundColor Green
        return $true
    }
    Write-Host "  WARN redis not ready on :6379 - disabling cache for this session" -ForegroundColor Yellow
    $compose = Join-Path $script:MeisRoot "deploy\docker-compose\docker-compose.yml"
    if (Get-Command docker -ErrorAction SilentlyContinue) {
        if (Test-Path $compose) {
            Write-Host "  Trying docker compose redis ..."
            try {
                docker compose -f $compose up -d redis 2>&1 | Out-Null
            } catch {
                # docker may print warnings to stderr; ignore if container starts
            }
            for ($i = 0; $i -lt 15; $i++) {
                if (Test-MeisRedisAvailable) {
                    Write-Host "  OK redis PING/PONG (docker)" -ForegroundColor Green
                    return $true
                }
                Start-Sleep -Seconds 1
            }
        }
    }
    Write-Host "  Tip: install/start Redis, or services run with --meis.cache.enabled=false" -ForegroundColor Yellow
    return $false
}

function Get-MeisCoreServiceDefinitions {
    return @($script:MeisCoreServiceNames | ForEach-Object { Get-MeisServiceDefinition $_ })
}

function Get-MeisServicesStartList {
    param(
        [switch]$CoreOnly,
        [string[]]$ServiceNames = $null
    )
    if ($CoreOnly) {
        return @(Get-MeisCoreServiceDefinitions)
    }
    if ($ServiceNames -and $ServiceNames.Count -gt 0) {
        return @($ServiceNames | ForEach-Object { Get-MeisServiceDefinition $_ })
    }
    return @($script:MeisServices)
}

function Start-MeisServices {
    param(
        [string]$Profile = "dev",
        [switch]$FollowLogs,
        [switch]$CoreOnly,
        [switch]$EnableJdwp,
        [string[]]$ServiceNames = $null
    )

    $env:JAVA_HOME = Resolve-MeisJavaHome
    $javaExe = Join-Path $env:JAVA_HOME 'bin\java.exe'
    $env:Path = "$env:JAVA_HOME\bin;" + $env:Path
    Write-Host "Using JAVA_HOME: $env:JAVA_HOME"
    $root = $script:MeisRoot
    $logDir = Join-Path $root "logs"
    if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }

    $targets = Get-MeisServicesStartList -CoreOnly:$CoreOnly -ServiceNames $ServiceNames
    $scopeLabel = if ($CoreOnly) { 'core backend' } else { 'all backend' }
    $modeLabel = if ($EnableJdwp) { 'debug' } else { 'normal' }
    Write-Host "Starting MEIS $scopeLabel services ($modeLabel, profile: $Profile) ..."
    $redisOk = Ensure-MeisRedis

    $skipped = @()
    $launched = @()
    foreach ($s in $targets) {
        if (Test-MeisPortListening -Port $s.port) {
            $skipped += $s.name
            $hint = if ($EnableJdwp) { " (already listening :$($s.port), use restart-debug to attach JDWP)" } else { " (already listening :$($s.port))" }
            Write-Host "  skip $($s.name)$hint" -ForegroundColor DarkGray
            continue
        }
        $jar = Join-Path $root "$($s.name)\target\$($s.name)-1.0.0-SNAPSHOT.jar"
        if (-not (Test-Path $jar)) {
            throw "Missing $jar - run scripts\build.ps1 first"
        }
        $suffix = if ($EnableJdwp) { '.debug' } else { '' }
        $stdout = Join-Path $logDir "$($s.name)$suffix.out.log"
        $stderr = Join-Path $logDir "$($s.name)$suffix.err.log"
        $javaArgs = @()
        if ($EnableJdwp) {
            if (-not $s.debugPort) { throw "No debug port for $($s.name)" }
            $javaArgs += "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:$($s.debugPort)"
        }
        $javaArgs += @(
            "-jar", $jar,
            "--spring.profiles.active=$Profile",
            "--spring.cloud.nacos.discovery.enabled=false"
        )
        if (-not $redisOk) {
            $javaArgs += "--meis.cache.enabled=false"
        }
        Start-Process -FilePath $javaExe -ArgumentList $javaArgs -WorkingDirectory $root `
            -WindowStyle Minimized -RedirectStandardOutput $stdout -RedirectStandardError $stderr | Out-Null
        $jdwpInfo = if ($EnableJdwp) { ", JDWP :$($s.debugPort)" } else { '' }
        Write-Host "  launch $($s.name) -> port $($s.port)$jdwpInfo"
        $launched += $s.name
        Start-Sleep -Seconds 1
    }

    if ($launched.Count -eq 0 -and $skipped.Count -gt 0) {
        Write-Host "All $($targets.Count) service(s) already running, nothing to start." -ForegroundColor Green
        return @{
            ok = $true
            message = "all $($targets.Count) already running"
            skipped = $skipped
            launched = $launched
        }
    }

    if ($launched.Count -eq 0) {
        Write-Host "No services to start." -ForegroundColor Yellow
        return @{ ok = $true; message = 'no services to start'; skipped = $skipped; launched = $launched }
    }

    Write-Host "Waiting for newly started services to bind ports ..."
    $failed = @()
    foreach ($s in $targets) {
        if ($s.name -in $skipped) {
            Write-Host "  OK $($s.name) :$($s.port) (already running)" -ForegroundColor Green
            continue
        }
        if ($s.name -notin $launched) { continue }
        $ready = $false
        for ($i = 0; $i -lt 20; $i++) {
            $httpReady = Test-MeisPortListening -Port $s.port
            $debugReady = (-not $EnableJdwp) -or ((-not $s.debugPort) -or (Test-MeisPortListening -Port $s.debugPort))
            if ($httpReady -and $debugReady) {
                $ready = $true
                $jdwpInfo = if ($EnableJdwp -and $s.debugPort) { ", JDWP :$($s.debugPort)" } else { '' }
                Write-Host "  OK $($s.name) :$($s.port)$jdwpInfo" -ForegroundColor Green
                break
            }
            Start-Sleep -Seconds 1
        }
        if (-not $ready) {
            $failed += $s
            Write-Host "  FAIL $($s.name) :$($s.port)" -ForegroundColor Red
        }
    }

    if ($failed.Count -gt 0) {
        Write-Host ''
        Write-Host 'The following services did not open their ports:' -ForegroundColor Red
        foreach ($s in $failed) {
            $errLog = Join-Path $logDir "$($s.name).err.log"
            $outLog = Join-Path $logDir "$($s.name).out.log"
            Write-Host "  - $($s.name) :$($s.port)" -ForegroundColor Red
            Write-Host "    stderr: $errLog"
            Write-Host "    stdout: $outLog"
        }
        Write-Host 'Common causes: Flyway migration error, port in use, jar not rebuilt' -ForegroundColor Yellow
    } else {
        Write-Host ''
        $summary = "started $($launched.Count), skipped $($skipped.Count)"
        Write-Host "Services ready ($summary)." -ForegroundColor Green
    }
    Write-Host 'Gateway: http://localhost:8080'
    Write-Host ''
    Write-Host 'Note: Spring Boot logs are written to files (not this terminal):' -ForegroundColor Cyan
    Write-Host "  $logDir\*.out.log"
    Write-Host ''
    Write-Host 'Commands:' -ForegroundColor Cyan
    Write-Host '  powershell -File scripts\status.ps1'
    Write-Host '  powershell -File scripts\logs.ps1 -Service gateway -Follow'
    Write-Host '  powershell -File scripts\logs.ps1 -List'

    if ($FollowLogs) {
        $gwLog = Join-Path $logDir 'meis-gateway.out.log'
        if (Test-Path $gwLog) {
            Write-Host ''
            Write-Host '=== meis-gateway (live) Ctrl+C to stop ===' -ForegroundColor Yellow
            Get-Content $gwLog -Tail 40 -Wait
        }
    }

    $msg = "launched $($launched.Count), skipped $($skipped.Count)"
    if ($failed.Count -gt 0) { $msg += ", failed $($failed.Count)" }
    Clear-MeisListeningPortCache
    return @{ ok = ($failed.Count -eq 0); message = $msg; skipped = $skipped; launched = $launched; failed = @($failed | ForEach-Object { $_.name }) }
}

function Get-MeisServiceDefinition {
    param([Parameter(Mandatory = $true)][string]$ServiceName)
    $svc = $script:MeisServices | Where-Object { $_.name -eq $ServiceName } | Select-Object -First 1
    if (-not $svc) { throw "Unknown service: $ServiceName" }
    return $svc
}

function Get-MeisProcessIdsOnPort {
    param([int]$Port)
    $pids = @()
    $lines = netstat -ano | Select-String ":\s*$Port\s+.*LISTENING"
    foreach ($line in $lines) {
        if ($line -match '\s+(\d+)\s*$') {
            $procId = [int]$matches[1]
            if ($procId -gt 0) { $pids += $procId }
        }
    }
    return $pids | Select-Object -Unique
}

function Stop-MeisServiceByName {
    param([Parameter(Mandatory = $true)][string]$ServiceName)
    $svc = Get-MeisServiceDefinition $ServiceName
    $killed = @{}

    Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue | ForEach-Object {
        if ($_.CommandLine -and $_.CommandLine -match "$([regex]::Escape($ServiceName))-1\.0\.0-SNAPSHOT\.jar") {
            $procId = $_.ProcessId
            if (-not $killed.ContainsKey($procId)) {
                Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
                $killed[$procId] = $true
            }
        }
    }

    Start-Sleep -Milliseconds 400
    foreach ($procId in (Get-MeisProcessIdsOnPort -Port $svc.port)) {
        if (-not $killed.ContainsKey($procId)) {
            Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
            $killed[$procId] = $true
        }
    }
    if ($svc.debugPort) {
        foreach ($procId in (Get-MeisProcessIdsOnPort -Port $svc.debugPort)) {
            if (-not $killed.ContainsKey($procId)) {
                Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
                $killed[$procId] = $true
            }
        }
    }
    return $killed.Count
}

function Start-MeisServiceByName {
    param(
        [Parameter(Mandatory = $true)][string]$ServiceName,
        [string]$Profile = 'dev',
        [switch]$EnableJdwp
    )

    $svc = Get-MeisServiceDefinition $ServiceName
    if (Test-MeisPortListening -Port $svc.port) {
        return @{ ok = $true; message = "already running on :$($svc.port)" }
    }

    $env:JAVA_HOME = Resolve-MeisJavaHome
    $javaExe = Join-Path $env:JAVA_HOME 'bin\java.exe'
    $root = $script:MeisRoot
    $jar = Join-Path $root "$ServiceName\target\$ServiceName-1.0.0-SNAPSHOT.jar"
    $jarHealth = Test-MeisServiceJarHealthy $ServiceName
    if (-not $jarHealth.ok) {
        throw $jarHealth.message
    }

    $logDir = Join-Path $root 'logs'
    if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }
    $suffix = if ($EnableJdwp) { '.debug' } else { '' }
    $stdout = Join-Path $logDir "$ServiceName$suffix.out.log"
    $stderr = Join-Path $logDir "$ServiceName$suffix.err.log"

    $javaArgs = @()
    if ($EnableJdwp) {
        if (-not $svc.debugPort) { throw "No debug port for $ServiceName" }
        $javaArgs += "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:$($svc.debugPort)"
    }
    $javaArgs += @(
        '-jar', $jar,
        "--spring.profiles.active=$Profile",
        '--spring.cloud.nacos.discovery.enabled=false'
    )
    if (-not (Test-MeisRedisAvailable)) {
        $javaArgs += '--meis.cache.enabled=false'
    }

    Start-Process -FilePath $javaExe -ArgumentList $javaArgs -WorkingDirectory $root `
        -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr | Out-Null

    $deadline = (Get-Date).AddSeconds(120)
    while ((Get-Date) -lt $deadline) {
        $httpReady = Test-MeisPortListening -Port $svc.port
        $debugReady = (-not $EnableJdwp) -or (Test-MeisPortListening -Port $svc.debugPort)
        if ($httpReady -and $debugReady) {
            $msg = if ($EnableJdwp) { "running on :$($svc.port), JDWP :$($svc.debugPort)" } else { "running on :$($svc.port)" }
            return @{ ok = $true; message = $msg }
        }
        Start-Sleep -Seconds 1
    }
    throw "Timeout starting $ServiceName - see $stderr"
}

function Get-MeisServiceMeta {
    param([Parameter(Mandatory = $true)][string]$ServiceName)
    $svc = Get-MeisServiceDefinition $ServiceName
    $meta = Get-MeisServiceMetaEntry $ServiceName
    return @{
        name    = $svc.name
        labelZh = $meta.labelZh
        descZh  = $meta.descZh
    }
}

function Invoke-MeisMavenModule {
    param(
        [Parameter(Mandatory = $true)][string]$Module,
        [switch]$Clean,
        [switch]$Package,
        [switch]$Compile,
        [switch]$Install,
        [switch]$AlsoMake
    )
    $mvn = Resolve-MeisMaven
    $env:JAVA_HOME = Resolve-MeisJavaHome
    $goals = @()
    if ($Clean) { $goals += 'clean' }
    if ($Package) {
        $goals += 'package'
    } elseif ($Compile) {
        $goals += 'compile'
    } elseif ($Install) {
        $goals += 'install'
    } elseif ($goals.Count -eq 0) {
        $goals += 'package'
    }
    $mvnArgs = $goals + @('-DskipTests', '-pl', $Module)
    if ($AlsoMake) { $mvnArgs += '-am' }
    Push-Location $script:MeisRoot
    try {
        & $mvn -q @mvnArgs
        if ($LASTEXITCODE -ne 0) {
            throw "Maven $($goals -join ' ') failed: $Module"
        }
    } finally {
        Pop-Location
    }
    return @{ ok = $true; message = ($Module + ': mvn ' + ($goals -join ' ') + ' OK') }
}

# 全量 reactor 构建（根 pom，编译/打包/安装全部模块）
function Invoke-MeisMavenReactor {
    param(
        [Parameter(Mandatory = $true)][ValidateSet('clean', 'compile', 'package', 'install')]
        [string]$Goal,
        [switch]$Quiet
    )
    $mvn = Resolve-MeisMaven
    $env:JAVA_HOME = Resolve-MeisJavaHome
    $mvnArgs = @()
    if ($Quiet) { $mvnArgs += '-q' }
    $mvnArgs += $Goal
    if ($Goal -ne 'clean') { $mvnArgs += '-DskipTests' }
    Push-Location $script:MeisRoot
    try {
        & $mvn @mvnArgs
        if ($LASTEXITCODE -ne 0) {
            throw "Maven $Goal (reactor) failed"
        }
    } finally {
        Pop-Location
    }
    return @{ ok = $true; message = ('reactor mvn ' + $Goal + ' OK') }
}

function Get-MeisServiceClassSourceModules {
    param([Parameter(Mandatory = $true)][string]$ServiceName)
    $modules = @('meis-common')
    if ($ServiceName -in @('meis-tenant', 'meis-auth')) { $modules += 'meis-api' }
    $modules += $ServiceName
    return @($modules | Select-Object -Unique)
}

function Get-MeisLibraryModuleDefinition {
    param([Parameter(Mandatory = $true)][string]$ModuleName)
    foreach ($m in $script:MeisLibraryModules) {
        if ($m.name -eq $ModuleName) { return $m }
    }
    throw "Unknown library module: $ModuleName"
}

function Get-MeisServicesForLibraryModule {
    param([Parameter(Mandatory = $true)][string]$ModuleName)
    $names = @()
    foreach ($s in $script:MeisServices) {
        $sources = Get-MeisServiceClassSourceModules $s.name
        if ($sources -contains $ModuleName) {
            $names += $s.name
        }
    }
    return $names
}

function Get-MeisLibraryModuleStatusList {
    $list = @()
    foreach ($mod in $script:MeisLibraryModules) {
        $name = $mod.name
        $classesDir = Join-Path $script:MeisRoot "$name\target\classes"
        $jar = Join-Path $script:MeisRoot "$name\target\$($mod.artifactId)-1.0.0-SNAPSHOT.jar"
        $classesExists = Test-Path $classesDir
        $jarExists = Test-Path $jar
        $classesMtime = ''
        if ($classesExists) {
            $classesMtime = (Get-Item $classesDir).LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss')
        }
        $jarMtime = if ($jarExists) { (Get-Item $jar).LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss') } else { '' }
        $jarSizeKb = if ($jarExists) { [math]::Round((Get-Item $jar).Length / 1KB) } else { 0 }
        $dependents = @(Get-MeisServicesForLibraryModule $name)
        $debugDependents = @()
        foreach ($svcName in $dependents) {
            $svc = Get-MeisServiceDefinition $svcName
            $httpUp = Test-MeisPortListening -Port $svc.port
            $debugUp = $svc.debugPort -and (Test-MeisPortListening -Port $svc.debugPort)
            if ($httpUp -and $debugUp) { $debugDependents += $svcName }
        }
        $meta = Get-MeisServiceMetaEntry $name
        $list += [ordered]@{
            name                = $name
            labelZh             = $meta.labelZh
            descZh              = $meta.descZh
            kind                = 'library'
            classesExists       = $classesExists
            classesMtime        = $classesMtime
            jarExists           = $jarExists
            jarMtime            = $jarMtime
            jarSizeKb           = $jarSizeKb
            dependentCount      = $dependents.Count
            dependents          = $dependents
            debugDependents     = $debugDependents
            debugDependentCount = $debugDependents.Count
        }
    }
    return $list
}

function Invoke-MeisLibraryHotReloadDependents {
    param([Parameter(Mandatory = $true)][string]$ModuleName)

    Get-MeisLibraryModuleDefinition $ModuleName | Out-Null
    $steps = [System.Collections.ArrayList]@()
    try {
        Invoke-MeisMavenModule -Module $ModuleName -Compile | Out-Null
        [void]$steps.Add(@{ step = 'compile'; ok = $true; message = ($ModuleName + ' mvn compile OK') })
    } catch {
        [void]$steps.Add(@{ step = 'compile'; ok = $false; message = $_.Exception.Message })
        return @{ ok = $false; message = ($ModuleName + ' compile failed'); steps = @($steps); reloaded = @() }
    }

    $reloaded = [System.Collections.ArrayList]@()
    foreach ($svcName in (Get-MeisServicesForLibraryModule $ModuleName)) {
        $svc = Get-MeisServiceDefinition $svcName
        $debugRunning = (Test-MeisPortListening -Port $svc.port) -and $svc.debugPort -and (Test-MeisPortListening -Port $svc.debugPort)
        if (-not $debugRunning) {
            [void]$reloaded.Add(@{ service = $svcName; ok = $true; skipped = $true; message = 'not in debug mode' })
            continue
        }
        try {
            $r = Invoke-MeisServiceHotReload -ServiceName $svcName
            [void]$reloaded.Add(@{
                service = $svcName
                ok      = [bool]$r.ok
                skipped = $false
                message = [string]$r.message
            })
        } catch {
            [void]$reloaded.Add(@{ service = $svcName; ok = $false; skipped = $false; message = $_.Exception.Message })
        }
    }

    $failed = @($reloaded | Where-Object { -not $_.ok -and -not $_.skipped })
    $done = @($reloaded | Where-Object { $_.ok -and -not $_.skipped })
    $skipped = @($reloaded | Where-Object { $_.skipped })
    $ok = $failed.Count -eq 0
    $msg = $ModuleName + ': reloaded ' + $done.Count + ' service(s)'
    if ($skipped.Count -gt 0) { $msg += ', skipped ' + $skipped.Count }
    if ($failed.Count -gt 0) { $msg += ', failed ' + $failed.Count }
    return @{
        ok       = $ok
        message  = $msg
        steps    = @($steps)
        reloaded = @($reloaded)
    }
}

function Test-MeisServiceJarHealthy {
    param([Parameter(Mandatory = $true)][string]$ServiceName)

    $jar = Join-Path $script:MeisRoot "$ServiceName\target\$ServiceName-1.0.0-SNAPSHOT.jar"
    if (-not (Test-Path $jar)) {
        return @{ ok = $false; message = 'Missing JAR. Select full package mode and build first.' }
    }

    $size = (Get-Item $jar).Length
    if ($size -lt 1048576) {
        $kb = [math]::Round($size / 1024)
        return @{ ok = $false; message = "JAR too small ($kb KB). Quick-update may have corrupted it; run full package build." }
    }

    $env:JAVA_HOME = Resolve-MeisJavaHome
    $jarExe = Join-Path $env:JAVA_HOME 'bin\jar.exe'
    if (-not (Test-Path $jarExe)) {
        return @{ ok = $false; message = 'jar.exe not found under JAVA_HOME' }
    }

    $listing = @(& $jarExe tf $jar 2>$null)
    if ($LASTEXITCODE -ne 0 -or $listing.Count -eq 0) {
        return @{ ok = $false; message = 'JAR unreadable. Run full package build.' }
    }
    $hasLib = $false
    $hasManifest = $false
    foreach ($line in $listing) {
        if ($line -like 'BOOT-INF/lib/*') { $hasLib = $true }
        if ($line -eq 'META-INF/MANIFEST.MF') { $hasManifest = $true }
        if ($hasLib -and $hasManifest) { break }
    }
    if (-not $hasManifest) {
        return @{ ok = $false; message = 'JAR missing MANIFEST.MF. Run full package build.' }
    }
    if (-not $hasLib) {
        return @{ ok = $false; message = 'JAR missing BOOT-INF/lib. Run full package build.' }
    }

    return @{ ok = $true; message = 'ok' }
}

function Wait-MeisServicePortReleased {
    param(
        [Parameter(Mandatory = $true)][int]$Port,
        [int]$TimeoutSec = 25
    )
    for ($i = 0; $i -lt $TimeoutSec; $i++) {
        if (-not (Test-MeisPortListening -Port $Port)) { return $true }
        Start-Sleep -Seconds 1
    }
    return $false
}

function Sync-MeisServiceClassesToJar {
    param([Parameter(Mandatory = $true)][string]$ServiceName)

    $health = Test-MeisServiceJarHealthy $ServiceName
    if (-not $health.ok) { throw $health.message }

    $env:JAVA_HOME = Resolve-MeisJavaHome
    $jarExe = Join-Path $env:JAVA_HOME 'bin\jar.exe'
    if (-not (Test-Path $jarExe)) { throw 'jar.exe not found under JAVA_HOME' }

    $root = $script:MeisRoot
    $jar = Join-Path $root "$ServiceName\target\$ServiceName-1.0.0-SNAPSHOT.jar"
    $sources = Get-MeisServiceClassSourceModules $ServiceName
    $updateRoot = Join-Path $env:TEMP "meis-jar-update-$ServiceName-$(Get-Random)"
    $bootClasses = Join-Path $updateRoot 'BOOT-INF\classes'
    New-Item -ItemType Directory -Path $bootClasses -Force | Out-Null

    try {
        $fileCount = 0
        foreach ($mod in $sources) {
            $cls = Join-Path $root "$mod\target\classes"
            if (-not (Test-Path $cls)) { continue }
            Get-ChildItem $cls -Recurse -File | ForEach-Object {
                $rel = $_.FullName.Substring($cls.Length + 1)
                $dest = Join-Path $bootClasses $rel
                $destDir = Split-Path $dest -Parent
                if (-not (Test-Path $destDir)) { New-Item -ItemType Directory -Path $destDir -Force | Out-Null }
                Copy-Item $_.FullName $dest -Force
                $fileCount++
            }
        }

        if ($fileCount -eq 0) {
            throw 'no compiled classes found; run mvn compile first'
        }

        Push-Location $updateRoot
        try {
            & $jarExe uf $jar 'BOOT-INF/classes'
            if ($LASTEXITCODE -ne 0) {
                throw ('jar uf failed for ' + $ServiceName)
            }
        } finally {
            Pop-Location
        }

        $verify = Test-MeisServiceJarHealthy $ServiceName
        if (-not $verify.ok) {
            throw ('JAR unhealthy after update: ' + $verify.message)
        }

        return @{ ok = $true; message = ($ServiceName + ': updated ' + $fileCount + ' classes/resources in JAR'); fileCount = $fileCount }
    } finally {
        Remove-Item $updateRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}

function Invoke-MeisServiceHotReload {
    param([Parameter(Mandatory = $true)][string]$ServiceName)

    $steps = [System.Collections.ArrayList]@()
    $svc = Get-MeisServiceDefinition $ServiceName
    $wasRunning = Test-MeisPortListening -Port $svc.port
    $useJdwp = $wasRunning -and $svc.debugPort -and (Test-MeisPortListening -Port $svc.debugPort)
    $fileCount = 0
    $stoppedForSync = $false

    try {
        $health = Test-MeisServiceJarHealthy $ServiceName
        if (-not $health.ok) {
            Invoke-MeisMavenModule -Module $ServiceName -Package -AlsoMake | Out-Null
        }
        Invoke-MeisMavenModule -Module $ServiceName -Compile -AlsoMake | Out-Null
        [void]$steps.Add(@{ step = 'compile'; ok = $true; message = 'mvn compile OK' })
    } catch {
        [void]$steps.Add(@{ step = 'compile'; ok = $false; message = $_.Exception.Message })
        return @{
            ok      = $false
            message = ($ServiceName + ' hot-reload failed at compile')
            steps   = @($steps)
            httpUp  = Test-MeisPortListening -Port $svc.port
            debugUp = $useJdwp -and (Test-MeisPortListening -Port $svc.debugPort)
        }
    }

    if ($wasRunning) {
        try {
            Stop-MeisServiceByName $ServiceName | Out-Null
            $released = Wait-MeisServicePortReleased -Port $svc.port
            if (-not $released) {
                throw ('port :' + $svc.port + ' still listening after stop')
            }
            $stoppedForSync = $true
            [void]$steps.Add(@{ step = 'stop'; ok = $true; message = 'stopped before JAR sync' })
        } catch {
            [void]$steps.Add(@{ step = 'stop'; ok = $false; message = $_.Exception.Message })
            return @{
                ok      = $false
                message = ($ServiceName + ' hot-reload failed at stop')
                steps   = @($steps)
                httpUp  = Test-MeisPortListening -Port $svc.port
                debugUp = $useJdwp -and (Test-MeisPortListening -Port $svc.debugPort)
            }
        }
    }

    try {
        $sync = Sync-MeisServiceClassesToJar -ServiceName $ServiceName
        $fileCount = if ($null -ne $sync.fileCount) { [int]$sync.fileCount } else { 0 }
        [void]$steps.Add(@{
            step      = 'sync'
            ok        = $true
            message   = [string]$sync.message
            fileCount = $fileCount
        })
    } catch {
        [void]$steps.Add(@{ step = 'sync'; ok = $false; message = $_.Exception.Message })
        if ($stoppedForSync) {
            try {
                Start-MeisServiceByName -ServiceName $ServiceName -Profile 'dev' -EnableJdwp:$useJdwp | Out-Null
                [void]$steps.Add(@{ step = 'restart'; ok = $true; message = 'restarted with previous JAR after sync failure' })
            } catch {
                [void]$steps.Add(@{ step = 'restart'; ok = $false; message = $_.Exception.Message })
            }
        }
        return @{
            ok        = $false
            message   = ($ServiceName + ' hot-reload failed at JAR sync')
            steps     = @($steps)
            httpUp    = Test-MeisPortListening -Port $svc.port
            debugUp   = $useJdwp -and (Test-MeisPortListening -Port $svc.debugPort)
            fileCount = $fileCount
        }
    }

    if (-not $wasRunning) {
        [void]$steps.Add(@{ step = 'restart'; ok = $true; message = 'service not running, restart skipped' })
        [void]$steps.Add(@{ step = 'health'; ok = $true; message = 'new classes will load on next start' })
        return @{
            ok        = $true
            message   = ($ServiceName + ' compiled and synced to JAR (service not running)')
            steps     = @($steps)
            httpUp    = $false
            debugUp   = $false
            fileCount = $fileCount
        }
    }

    try {
        $start = Start-MeisServiceByName -ServiceName $ServiceName -Profile 'dev' -EnableJdwp:$useJdwp
        [void]$steps.Add(@{ step = 'restart'; ok = $true; message = $start.message })
    } catch {
        [void]$steps.Add(@{ step = 'restart'; ok = $false; message = $_.Exception.Message })
        [void]$steps.Add(@{ step = 'health'; ok = $false; message = 'service port not listening after restart' })
        return @{
            ok        = $false
            message   = ($ServiceName + ' hot-reload failed at restart')
            steps     = @($steps)
            httpUp    = Test-MeisPortListening -Port $svc.port
            debugUp   = $useJdwp -and (Test-MeisPortListening -Port $svc.debugPort)
            fileCount = $fileCount
        }
    }

    $httpUp = Test-MeisPortListening -Port $svc.port
    $debugUp = (-not $useJdwp) -or (Test-MeisPortListening -Port $svc.debugPort)
    $healthOk = $httpUp -and $debugUp
    if ($healthOk) {
        $healthMsg = 'HTTP :' + $svc.port + ' ready'
        if ($useJdwp) { $healthMsg += ', JDWP :' + $svc.debugPort }
    } else {
        $healthMsg = 'port not ready, check logs'
    }
    [void]$steps.Add(@{ step = 'health'; ok = $healthOk; message = $healthMsg })

    $failed = @($steps | Where-Object { -not $_.ok })
    $allOk = $failed.Count -eq 0
    if ($allOk) {
        $summary = $ServiceName + ' hot-reload OK (' + $fileCount + ' classes/resources, service restarted)'
    } else {
        $failedSteps = ($failed | ForEach-Object { $_.step }) -join ', '
        $summary = $ServiceName + ' hot-reload incomplete: ' + $failedSteps
    }
    return @{
        ok        = $allOk
        message   = $summary
        steps     = @($steps)
        httpUp    = $httpUp
        debugUp   = $debugUp
        fileCount = $fileCount
    }
}

function Build-MeisServiceModule {
    param(
        [Parameter(Mandatory = $true)][string]$ServiceName,
        [switch]$Clean,
        [switch]$Package,
        [switch]$Compile,
        [switch]$LoadClasses
    )
    if (-not $Clean -and -not $Package -and -not $Compile -and -not $LoadClasses) {
        throw 'Select at least one build step: clean, package, compile or loadClasses'
    }
    if ($LoadClasses) {
        $health = Test-MeisServiceJarHealthy $ServiceName
        if (-not $health.ok) {
            Invoke-MeisMavenModule -Module $ServiceName -Package -AlsoMake | Out-Null
        }
        if ($Clean) {
            Invoke-MeisMavenModule -Module $ServiceName -Clean -AlsoMake | Out-Null
        }
        Invoke-MeisMavenModule -Module $ServiceName -Compile -AlsoMake | Out-Null
        return Sync-MeisServiceClassesToJar -ServiceName $ServiceName
    }
    return Invoke-MeisMavenModule -Module $ServiceName -Clean:$Clean -Package:$Package -Compile:$Compile -AlsoMake
}

function Build-MeisFrontendProject {
    param(
        [switch]$NpmInstall,
        [switch]$TypeCheck,
        [switch]$Build
    )
    if (-not $NpmInstall -and -not $TypeCheck -and -not $Build) {
        throw 'Select at least one build step: npmInstall, typecheck or build'
    }
    $webDir = Join-Path $script:MeisRoot 'meis-web'
    if (-not (Test-Path (Join-Path $webDir 'package.json'))) {
        throw "meis-web not found: $webDir"
    }
    Push-Location $webDir
    try {
        $steps = @()
        if ($NpmInstall) {
            & npm install
            if ($LASTEXITCODE -ne 0) { throw 'npm install failed' }
            $steps += 'npm install'
        }
        if ($TypeCheck) {
            & npx vue-tsc -b
            if ($LASTEXITCODE -ne 0) { throw 'vue-tsc failed' }
            $steps += 'vue-tsc'
        }
        if ($Build) {
            & npm run build
            if ($LASTEXITCODE -ne 0) { throw 'npm run build failed' }
            $steps += 'vite build'
        }
    } finally {
        Pop-Location
    }
    return @{ ok = $true; message = ('meis-web: ' + ($steps -join ' + ') + ' OK') }
}

function Invoke-MeisServiceBuildSteps {
    param(
        [Parameter(Mandatory = $true)][string]$ServiceName,
        [switch]$Clean,
        [switch]$Package,
        [switch]$Compile,
        [switch]$LoadClasses
    )
    if (-not $Clean -and -not $Package -and -not $Compile -and -not $LoadClasses) { return $null }
    return Build-MeisServiceModule -ServiceName $ServiceName -Clean:$Clean -Package:$Package -Compile:$Compile -LoadClasses:$LoadClasses
}

function Invoke-MeisFrontendBuildSteps {
    param(
        [switch]$NpmInstall,
        [switch]$TypeCheck,
        [switch]$Build
    )
    if (-not $NpmInstall -and -not $TypeCheck -and -not $Build) { return $null }
    return Build-MeisFrontendProject -NpmInstall:$NpmInstall -TypeCheck:$TypeCheck -Build:$Build
}

function Restart-MeisServiceByName {
    param(
        [Parameter(Mandatory = $true)][string]$ServiceName,
        [string]$Profile = 'dev',
        [switch]$EnableJdwp,
        [switch]$Clean,
        [switch]$Package,
        [switch]$Compile,
        [switch]$LoadClasses
    )
    Invoke-MeisServiceBuildSteps -ServiceName $ServiceName -Clean:$Clean -Package:$Package -Compile:$Compile -LoadClasses:$LoadClasses | Out-Null
    Stop-MeisServiceByName $ServiceName | Out-Null
    Start-Sleep -Seconds 1
    return Start-MeisServiceByName -ServiceName $ServiceName -Profile $Profile -EnableJdwp:$EnableJdwp
}

function Start-MeisServiceByNameWithBuild {
    param(
        [Parameter(Mandatory = $true)][string]$ServiceName,
        [string]$Profile = 'dev',
        [switch]$EnableJdwp,
        [switch]$Clean,
        [switch]$Package,
        [switch]$Compile,
        [switch]$LoadClasses
    )
    Invoke-MeisServiceBuildSteps -ServiceName $ServiceName -Clean:$Clean -Package:$Package -Compile:$Compile -LoadClasses:$LoadClasses | Out-Null
    return Start-MeisServiceByName -ServiceName $ServiceName -Profile $Profile -EnableJdwp:$EnableJdwp
}

function Get-MeisServiceStatusList {
    $ports = Get-MeisListeningPortSet
    $list = @()
    foreach ($s in $script:MeisServices) {
        $httpUp = $ports.Contains($s.port)
        $debugUp = $false
        if ($s.debugPort) { $debugUp = $ports.Contains($s.debugPort) }
        $jar = Join-Path $script:MeisRoot "$($s.name)\target\$($s.name)-1.0.0-SNAPSHOT.jar"
        $classesDir = Join-Path $script:MeisRoot "$($s.name)\target\classes"
        $classesExists = Test-Path $classesDir
        $jarExists = Test-Path $jar
        $jarSizeKb = if ($jarExists) { [math]::Round((Get-Item $jar).Length / 1KB) } else { 0 }
        $jarHealthy = $jarExists -and $jarSizeKb -ge 1024
        $meta = Get-MeisServiceMetaEntry $s.name
        $list += [ordered]@{
            name          = $s.name
            labelZh       = $meta.labelZh
            descZh        = $meta.descZh
            port          = $s.port
            debugPort     = $s.debugPort
            httpUp        = $httpUp
            debugUp       = $debugUp
            classesExists = $classesExists
            classesMtime  = if ($classesExists) { (Get-Item $classesDir).LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss') } else { '' }
            jarExists     = $jarExists
            jarSizeKb  = $jarSizeKb
            jarHealthy = $jarHealthy
            jarMtime   = if ($jarExists) { (Get-Item $jar).LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss') } else { '' }
        }
    }
    return $list
}

function Get-MeisFrontendStatus {
    $meta = Get-MeisServiceMetaEntry 'meis-web'
    return [ordered]@{
        name    = 'meis-web'
        labelZh = $meta.labelZh
        descZh  = $meta.descZh
        port    = $script:MeisFrontendPort
        httpUp  = Test-MeisPortListening -Port $script:MeisFrontendPort
        url     = "http://localhost:$($script:MeisFrontendPort)"
    }
}

function Stop-MeisFrontend {
    $killed = 0
    foreach ($procId in (Get-MeisProcessIdsOnPort -Port $script:MeisFrontendPort)) {
        Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        $killed++
    }
    $pidFile = Join-Path $script:MeisRoot 'logs\frontend-dev.pid'
    if (Test-Path $pidFile) {
        $saved = Get-Content $pidFile -ErrorAction SilentlyContinue
        if ($saved -match '^\d+$') {
            Stop-Process -Id ([int]$saved) -Force -ErrorAction SilentlyContinue
        }
        Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
    }
    return $killed
}

function Start-MeisFrontend {
    if (Test-MeisPortListening -Port $script:MeisFrontendPort) {
        return @{ ok = $true; message = "already running on :$($script:MeisFrontendPort)" }
    }
    $webDir = Join-Path $script:MeisRoot 'meis-web'
    if (-not (Test-Path (Join-Path $webDir 'package.json'))) {
        throw "meis-web not found: $webDir"
    }
    $logDir = Join-Path $script:MeisRoot 'logs'
    if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }
    $stdout = Join-Path $logDir 'meis-web.dev.out.log'
    $stderr = Join-Path $logDir 'meis-web.dev.err.log'
    $proc = Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', 'npm run dev' -WorkingDirectory $webDir `
        -WindowStyle Hidden -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    Set-Content (Join-Path $logDir 'frontend-dev.pid') $proc.Id

    $deadline = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $deadline) {
        if (Test-MeisPortListening -Port $script:MeisFrontendPort) {
            return @{ ok = $true; message = "running on :$($script:MeisFrontendPort)" }
        }
        Start-Sleep -Seconds 1
    }
    throw "Timeout starting frontend - see $stderr"
}

function Restart-MeisFrontend {
    param(
        [switch]$NpmInstall,
        [switch]$TypeCheck,
        [switch]$Build
    )
    Invoke-MeisFrontendBuildSteps -NpmInstall:$NpmInstall -TypeCheck:$TypeCheck -Build:$Build | Out-Null
    Stop-MeisFrontend | Out-Null
    Start-Sleep -Seconds 1
    return Start-MeisFrontend
}

function Start-MeisFrontendWithBuild {
    param(
        [switch]$NpmInstall,
        [switch]$TypeCheck,
        [switch]$Build
    )
    Invoke-MeisFrontendBuildSteps -NpmInstall:$NpmInstall -TypeCheck:$TypeCheck -Build:$Build | Out-Null
    return Start-MeisFrontend
}

function Get-MeisServiceLogTail {
    param(
        [Parameter(Mandatory = $true)][string]$ServiceName,
        [int]$Lines = 40,
        [switch]$DebugLogs
    )
    $entries = Get-MeisPanelLogEntries -ServiceName $ServiceName -Lines $Lines -DebugLogs:$DebugLogs
    return @($entries | ForEach-Object { $_.text })
}

function Add-MeisPanelEvent {
    param([Parameter(Mandatory = $true)][string]$Message)
    $logDir = Join-Path $script:MeisRoot 'logs'
    if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }
    $line = (Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + ' [PANEL] ' + $Message
    Add-Content -Path (Join-Path $logDir 'dev-panel-events.log') -Value $line -Encoding UTF8
}

function Read-MeisLogFileEntries {
    param(
        [Parameter(Mandatory = $true)][string]$ServiceName,
        [Parameter(Mandatory = $true)][string]$LogPath,
        [Parameter(Mandatory = $true)][string]$Stream,
        [int]$Lines = 80
    )
    if (-not (Test-Path $LogPath)) { return @() }
    $fileTime = (Get-Item $LogPath).LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss')
    $rawLines = @(Get-Content $LogPath -Tail $Lines -Encoding UTF8 -ErrorAction SilentlyContinue)
    $entries = @()
    foreach ($line in $rawLines) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $ts = $fileTime
        $level = 'INFO'
        if ($line -match '^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2}:\d{2})') {
            $ts = $Matches[1] + ' ' + $Matches[2]
        }
        if ($line -match '\s(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\s') {
            $level = $Matches[1]
        } elseif ($line -match '(?i)\b(error|exception|failed|failure)\b') {
            $level = 'ERROR'
        } elseif ($line -match '(?i)\bwarn(ing)?\b') {
            $level = 'WARN'
        }
        if ($Stream -eq 'event') {
            $level = 'EVENT'
        } elseif ($Stream -eq 'stderr' -and $level -eq 'INFO') {
            $level = 'WARN'
        }
        $entries += [ordered]@{
            ts      = $ts
            service = $ServiceName
            stream  = $Stream
            level   = $level
            text    = $line
        }
    }
    return $entries
}

function Test-MeisServiceInWatchList {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [AllowNull()][string[]]$Watched
    )
    if ($null -eq $Watched) { return $true }
    return $Name -in $Watched
}

function Add-MeisServiceLogEntries {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$LogDir,
        [int]$Lines,
        [switch]$DebugLogs,
        [switch]$PreferDebug
    )

    $suffix = if ($DebugLogs -or $PreferDebug) { '.debug' } else { '' }
    $errLog = Join-Path $LogDir "$Name$suffix.err.log"
    $outLog = Join-Path $LogDir "$Name$suffix.out.log"
    $result = @()
    $result += Read-MeisLogFileEntries -ServiceName $Name -LogPath $errLog -Stream 'stderr' -Lines $Lines
    $result += Read-MeisLogFileEntries -ServiceName $Name -LogPath $outLog -Stream 'stdout' -Lines $Lines
    return $result
}

function Test-MeisServiceInFilterList {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [AllowNull()][string[]]$Filtered
    )
    if ($null -eq $Filtered) { return $true }
    return $Name -in $Filtered
}

function Get-MeisPanelLogEntries {
    param(
        [string]$ServiceName = 'all',
        [int]$Lines = 100,
        [switch]$DebugLogs,
        [switch]$ErrorsOnly,
        [AllowNull()][string[]]$Watched = $null,
        [AllowNull()][string[]]$Filtered = $null
    )

    $entries = @()
    $logDir = Join-Path $script:MeisRoot 'logs'
    $ports = Get-MeisListeningPortSet
    $debugUpByName = @{}
    foreach ($s in $script:MeisServices) {
        if ($s.debugPort -and $ports.Contains($s.debugPort)) {
            $debugUpByName[$s.name] = $true
        }
    }

    $includePanel = (Test-MeisServiceInWatchList -Name 'panel' -Watched $Watched) -and (Test-MeisServiceInFilterList -Name 'panel' -Filtered $Filtered)
    if ($includePanel) {
        $panelLog = Join-Path $logDir 'dev-panel-events.log'
        if (Test-Path $panelLog) {
            $entries += Read-MeisLogFileEntries -ServiceName 'panel' -LogPath $panelLog -Stream 'event' -Lines $Lines
        }
    }

    $includeWeb = (Test-MeisServiceInWatchList -Name 'meis-web' -Watched $Watched) -and (Test-MeisServiceInFilterList -Name 'meis-web' -Filtered $Filtered)
    if ($includeWeb -and ($ServiceName -eq 'all' -or $ServiceName -eq 'meis-web' -or $null -ne $Filtered)) {
        $feErr = Join-Path $logDir 'meis-web.dev.err.log'
        $feOut = Join-Path $logDir 'meis-web.dev.out.log'
        $entries += Read-MeisLogFileEntries -ServiceName 'meis-web' -LogPath $feErr -Stream 'stderr' -Lines $Lines
        $entries += Read-MeisLogFileEntries -ServiceName 'meis-web' -LogPath $feOut -Stream 'stdout' -Lines $Lines
    }

    if ($ServiceName -ne 'meis-web') {
        $targets = if ($ServiceName -eq 'all' -or $null -ne $Filtered) { $script:MeisServices } else { @(Get-MeisServiceDefinition $ServiceName) }
        foreach ($s in $targets) {
            $name = $s.name
            if (-not (Test-MeisServiceInWatchList -Name $name -Watched $Watched)) { continue }
            if (-not (Test-MeisServiceInFilterList -Name $name -Filtered $Filtered)) { continue }
            $preferDebug = $debugUpByName.ContainsKey($name)
            $entries += Add-MeisServiceLogEntries -Name $name -LogDir $logDir -Lines $Lines -DebugLogs:$DebugLogs -PreferDebug:$preferDebug
        }
    }

    if ($ErrorsOnly) {
        $entries = @($entries | Where-Object {
            $_.level -in @('ERROR', 'WARN') -or $_.text -match '(?i)exception|error|failed|failure|caused by:'
        })
    }

    return @($entries | Sort-Object { $_.ts + $_.service + $_.text } -Descending | Select-Object -First ($Lines * 4) | ForEach-Object {
        [PSCustomObject]@{
            ts      = [string]$_.ts
            service = [string]$_.service
            stream  = [string]$_.stream
            level   = [string]$_.level
            text    = [string]$_.text
        }
    })
}

function Get-MeisPanelLogServices {
    $panelMeta = Get-MeisServiceMetaEntry 'panel'
    $webMeta = Get-MeisServiceMetaEntry 'meis-web'
    $list = @([ordered]@{ name = 'panel'; labelZh = $panelMeta.labelZh })
    $list += [ordered]@{ name = 'meis-web'; labelZh = $webMeta.labelZh }
    foreach ($s in $script:MeisServices) {
        $m = Get-MeisServiceMetaEntry $s.name
        $list += [ordered]@{ name = $s.name; labelZh = $m.labelZh }
    }
    return $list
}
