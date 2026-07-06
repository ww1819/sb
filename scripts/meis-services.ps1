# MEIS service list (shared by start/stop/restart scripts)
$script:MeisRoot = Split-Path $PSScriptRoot -Parent

function Resolve-MeisJavaHome {
    foreach ($jdkHome in @($env:MEIS_JAVA_HOME, 'E:\workspace\jdk-17', 'C:\Program Files\Java\jdk-17')) {
        if (-not $jdkHome) { continue }
        $java = Join-Path $jdkHome 'bin\java.exe'
        if (Test-Path $java) { return $jdkHome }
    }
    throw 'JDK 17 not found. Install to E:\workspace\jdk-17 or set MEIS_JAVA_HOME.'
}

$script:MeisServicePorts = @(8082, 8081, 8083, 8084, 8085, 8086, 8087, 8088, 8089, 8090, 8091, 8092, 8093, 8094, 8080)

$script:MeisServices = @(
    @{ name = "meis-tenant"; port = 8082 },
    @{ name = "meis-auth"; port = 8081 },
    @{ name = "meis-system"; port = 8083 },
    @{ name = "meis-purchase"; port = 8084 },
    @{ name = "meis-asset"; port = 8085 },
    @{ name = "meis-repair"; port = 8086 },
    @{ name = "meis-maintain"; port = 8087 },
    @{ name = "meis-qc"; port = 8088 },
    @{ name = "meis-maintenance-contract"; port = 8089 },
    @{ name = "meis-special"; port = 8090 },
    @{ name = "meis-analytics"; port = 8091 },
    @{ name = "meis-file"; port = 8092 },
    @{ name = "meis-notification"; port = 8093 },
    @{ name = "meis-integration"; port = 8094 },
    @{ name = "meis-gateway"; port = 8080 }
)

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
