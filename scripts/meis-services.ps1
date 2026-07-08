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

$script:MeisFrontendPort = 5173

function Stop-MeisServices {
    $killed = @{}

    # 1) by jar name in java command line
    Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue | ForEach-Object {
        if ($_.CommandLine -and $_.CommandLine -match 'meis-[a-z-]+-1\.0\.0-SNAPSHOT\.jar') {
            $procId = $_.ProcessId
            if (-not $killed.ContainsKey($procId)) {
                Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
                $killed[$procId] = $true
                Write-Host "Stopped PID $procId (meis jar)"
            }
        }
    }

    Start-Sleep -Seconds 1

    # 2) by listen port (fallback)
    foreach ($port in $script:MeisServicePorts) {
        $lines = netstat -ano | Select-String ":\s*$port\s+.*LISTENING"
        foreach ($line in $lines) {
            if ($line -match '\s+(\d+)\s*$') {
                $procId = [int]$matches[1]
                if ($procId -gt 0 -and -not $killed.ContainsKey($procId)) {
                    Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
                    $killed[$procId] = $true
                    Write-Host "Stopped PID $procId (port $port)"
                }
            }
        }
    }

    if ($killed.Count -eq 0) {
        Write-Host "No MEIS backend processes found."
    } else {
        Write-Host "Stopped $($killed.Count) process(es)."
    }
}

function Test-MeisPortListening {
    param([int]$Port)
    return [bool](netstat -ano | Select-String ":\s*$Port\s+.*LISTENING")
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

function Start-MeisServices {
    param(
        [string]$Profile = "dev",
        [switch]$FollowLogs
    )

    $env:JAVA_HOME = Resolve-MeisJavaHome
    $javaExe = Join-Path $env:JAVA_HOME 'bin\java.exe'
    $env:Path = "$env:JAVA_HOME\bin;" + $env:Path
    Write-Host "Using JAVA_HOME: $env:JAVA_HOME"
    $root = $script:MeisRoot
    $logDir = Join-Path $root "logs"
    if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }

    Write-Host "Starting MEIS services (profile: $Profile) ..."
    $redisOk = Ensure-MeisRedis
    foreach ($s in $script:MeisServices) {
        $jar = Join-Path $root "$($s.name)\target\$($s.name)-1.0.0-SNAPSHOT.jar"
        if (-not (Test-Path $jar)) {
            throw "Missing $jar - run scripts\build.ps1 first"
        }
        $stdout = Join-Path $logDir "$($s.name).out.log"
        $stderr = Join-Path $logDir "$($s.name).err.log"
        $javaArgs = @(
            "-jar", $jar,
            "--spring.profiles.active=$Profile",
            "--spring.cloud.nacos.discovery.enabled=false"
        )
        if (-not $redisOk) {
            $javaArgs += "--meis.cache.enabled=false"
        }
        Start-Process -FilePath $javaExe -ArgumentList $javaArgs -WorkingDirectory $root `
            -WindowStyle Minimized -RedirectStandardOutput $stdout -RedirectStandardError $stderr | Out-Null
        Write-Host "  launch $($s.name) -> port $($s.port)"
        Start-Sleep -Seconds 1
    }

    Write-Host "Waiting for services to bind ports ..."
    $failed = @()
    foreach ($s in $script:MeisServices) {
        $ready = $false
        for ($i = 0; $i -lt 20; $i++) {
            if (Test-MeisPortListening -Port $s.port) {
                $ready = $true
                Write-Host "  OK $($s.name) :$($s.port)" -ForegroundColor Green
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
        Write-Host 'All services are listening.' -ForegroundColor Green
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
        [switch]$Debug
    )

    $svc = Get-MeisServiceDefinition $ServiceName
    if (Test-MeisPortListening -Port $svc.port) {
        return @{ ok = $true; message = "already running on :$($svc.port)" }
    }

    $env:JAVA_HOME = Resolve-MeisJavaHome
    $javaExe = Join-Path $env:JAVA_HOME 'bin\java.exe'
    $root = $script:MeisRoot
    $jar = Join-Path $root "$ServiceName\target\$ServiceName-1.0.0-SNAPSHOT.jar"
    if (-not (Test-Path $jar)) {
        throw "Missing $jar - run scripts\build.ps1 first"
    }

    $logDir = Join-Path $root 'logs'
    if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }
    $suffix = if ($Debug) { '.debug' } else { '' }
    $stdout = Join-Path $logDir "$ServiceName$suffix.out.log"
    $stderr = Join-Path $logDir "$ServiceName$suffix.err.log"

    $javaArgs = @()
    if ($Debug) {
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
        $debugReady = (-not $Debug) -or (Test-MeisPortListening -Port $svc.debugPort)
        if ($httpReady -and $debugReady) {
            $msg = if ($Debug) { "running on :$($svc.port), JDWP :$($svc.debugPort)" } else { "running on :$($svc.port)" }
            return @{ ok = $true; message = $msg }
        }
        Start-Sleep -Seconds 1
    }
    throw "Timeout starting $ServiceName - see $stderr"
}

function Restart-MeisServiceByName {
    param(
        [Parameter(Mandatory = $true)][string]$ServiceName,
        [string]$Profile = 'dev',
        [switch]$Debug
    )
    Stop-MeisServiceByName $ServiceName | Out-Null
    Start-Sleep -Seconds 1
    return Start-MeisServiceByName -ServiceName $ServiceName -Profile $Profile -Debug:$Debug
}

function Get-MeisServiceStatusList {
    $list = @()
    foreach ($s in $script:MeisServices) {
        $httpUp = Test-MeisPortListening -Port $s.port
        $debugUp = $false
        if ($s.debugPort) { $debugUp = Test-MeisPortListening -Port $s.debugPort }
        $jar = Join-Path $script:MeisRoot "$($s.name)\target\$($s.name)-1.0.0-SNAPSHOT.jar"
        $list += [ordered]@{
            name       = $s.name
            port       = $s.port
            debugPort  = $s.debugPort
            httpUp     = $httpUp
            debugUp    = $debugUp
            jarExists  = Test-Path $jar
            jarSizeKb  = if (Test-Path $jar) { [math]::Round((Get-Item $jar).Length / 1KB) } else { 0 }
        }
    }
    return $list
}

function Get-MeisFrontendStatus {
    return [ordered]@{
        name    = 'meis-web'
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
    Stop-MeisFrontend | Out-Null
    Start-Sleep -Seconds 1
    return Start-MeisFrontend
}

function Get-MeisServiceLogTail {
    param(
        [Parameter(Mandatory = $true)][string]$ServiceName,
        [int]$Lines = 40,
        [switch]$Debug
    )
    $suffix = if ($Debug) { '.debug' } else { '' }
    $log = Join-Path $script:MeisRoot "logs\$ServiceName$suffix.err.log"
    if (-not (Test-Path $log)) {
        $log = Join-Path $script:MeisRoot "logs\$ServiceName$suffix.out.log"
    }
    if (-not (Test-Path $log)) { return @() }
    return @(Get-Content $log -Tail $Lines -ErrorAction SilentlyContinue)
}
