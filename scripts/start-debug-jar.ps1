# Start one MEIS service JAR with JDWP enabled for VS Code attach debugging.
param(
    [Parameter(Mandatory = $true)]
    [string]$Service,
    [Parameter(Mandatory = $true)]
    [int]$DebugPort,
    [string]$Profile = 'dev'
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\meis-services.ps1"

$svc = $script:MeisServices | Where-Object { $_.name -eq $Service } | Select-Object -First 1
if (-not $svc) {
    throw "Unknown service: $Service"
}

$env:JAVA_HOME = Resolve-MeisJavaHome
$javaExe = Join-Path $env:JAVA_HOME 'bin\java.exe'
$root = $script:MeisRoot
$jar = Join-Path $root "$Service\target\$Service-1.0.0-SNAPSHOT.jar"
$logDir = Join-Path $root 'logs'
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }

if (-not (Test-Path $jar)) {
    throw "Missing $jar - run scripts\build.ps1 first"
}

$jarInfo = Get-Item $jar
if ($jarInfo.Length -lt 1MB) {
    throw @"
JAR too small ($([math]::Round($jarInfo.Length / 1KB)) KB): $jar
Likely only 'mvn compile' was run, or 'mvn package' failed mid-way.
Run: powershell -File scripts\build.ps1
(or: mvn -pl $Service -am clean package -DskipTests)
"@
}

$jarExe = Join-Path $env:JAVA_HOME 'bin\jar.exe'
if (Test-Path $jarExe) {
    $mfDir = Join-Path $env:TEMP "meis-jar-check-$([guid]::NewGuid().ToString('N'))"
    New-Item -ItemType Directory -Path $mfDir | Out-Null
    try {
        Push-Location $mfDir
        & $jarExe xf $jar META-INF/MANIFEST.MF 2>$null | Out-Null
        if (Test-Path 'META-INF/MANIFEST.MF') {
            $mf = Get-Content 'META-INF/MANIFEST.MF' -Raw
            if ($mf -notmatch 'Main-Class:\s*.+' -and $mf -notmatch 'Start-Class:\s*.+') {
                throw @"
Invalid JAR (no Main-Class): $jar
Rebuild with: powershell -File scripts\build.ps1
"@
            }
        }
    }
    finally {
        Pop-Location
        Remove-Item $mfDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}

if (Test-MeisPortListening -Port $DebugPort) {
    Write-Host "JDWP already listening on :$DebugPort ($Service)"
    exit 0
}

if (Test-MeisPortListening -Port $svc.port) {
    Write-Host "Service $Service already listening on :$($svc.port) (without JDWP attach)"
    throw "Stop existing service first: powershell -File scripts\stop.ps1"
}

$stdout = Join-Path $logDir "$Service.debug.out.log"
$stderr = Join-Path $logDir "$Service.debug.err.log"
$javaArgs = @(
    "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:$DebugPort",
    '-jar', $jar,
    "--spring.profiles.active=$Profile",
    '--spring.cloud.nacos.discovery.enabled=false'
)
if (-not (Test-MeisRedisAvailable)) {
    $javaArgs += '--meis.cache.enabled=false'
}

Write-Host "Starting $Service (port $($svc.port), JDWP :$DebugPort) ..."
Start-Process -FilePath $javaExe -ArgumentList $javaArgs -WorkingDirectory $root `
    -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr | Out-Null

$deadline = (Get-Date).AddSeconds(120)
$tick = 0
while ((Get-Date) -lt $deadline) {
    if ((Test-MeisPortListening -Port $DebugPort) -and (Test-MeisPortListening -Port $svc.port)) {
        Write-Host "Ready: $Service on :$($svc.port), attach debugger to :$DebugPort" -ForegroundColor Green
        exit 0
    }
    Start-Sleep -Seconds 1
    $tick++
    if ($tick -gt 0 -and ($tick % 15) -eq 0 -and (Test-Path $stderr) -and (Get-Item $stderr).Length -gt 0) {
        Write-Host "  still starting $Service ... recent stderr:" -ForegroundColor Yellow
        Get-Content $stderr -Tail 5 | ForEach-Object { Write-Host "    $_" -ForegroundColor Red }
    }
}

if (Test-Path $stderr) {
    $errTail = Get-Content $stderr -Tail 20 -ErrorAction SilentlyContinue
    if ($errTail) {
        Write-Host "=== $Service stderr (last 20 lines) ===" -ForegroundColor Red
        $errTail | ForEach-Object { Write-Host $_ -ForegroundColor Red }
    }
}

throw "Timeout starting $Service (service :$($svc.port), JDWP :$DebugPort). See $stderr"
