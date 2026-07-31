# Field-kit service starter. Params are plain strings only.
param(
    [Parameter(Mandatory = $true)][string]$Root,
    [Parameter(Mandatory = $true)][string]$Action,
    [string]$ServiceName = '',
    [string]$CoreOnlyFlag = '0',
    [string]$ResultFile = ''
)

$ErrorActionPreference = 'Stop'
Set-Location $Root

. (Join-Path $Root 'common\load-env.ps1')
Import-MeisPackageEnv -EnvFile (Join-Path $Root 'env.txt')

$javaHome = Resolve-MeisPackageJavaHome
$env:JAVA_HOME = $javaHome
$env:MEIS_JAVA_HOME = $javaHome
$javaExe = Join-Path $javaHome 'bin\java.exe'
if (-not (Test-Path $javaExe)) { throw "java.exe not found: $javaExe" }

$jarsDir = Join-Path $Root 'jars'
$logsDir = Join-Path $Root 'logs'
New-Item -ItemType Directory -Path $logsDir -Force | Out-Null

$profileName = 'dev'
if ($env:PROFILE) { $profileName = [string]$env:PROFILE }

# Force a flat object list (avoid PS array-unrolling / .port/.order aggregation bugs)
$raw = Get-Content (Join-Path $Root 'services.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$serviceList = New-Object System.Collections.ArrayList
foreach ($item in @($raw)) {
    [void]$serviceList.Add($item)
}

function Find-ServiceByName {
    param([string]$Name)
    for ($i = 0; $i -lt $serviceList.Count; $i++) {
        $item = $serviceList[$i]
        if ([string]$item.name -eq $Name) { return $item }
    }
    throw "unknown service: $Name"
}

function Read-IntField {
    param($Item, [string]$FieldName)
    $v = $Item.$FieldName
    if ($null -eq $v) { throw "missing field $FieldName on $($Item.name)" }
    # Guard against accidental multi-value aggregation ("8082 8081 ..." / "1 2 3 ...")
    $s = "$v".Trim()
    if ($s -match '\s') {
        throw "field $FieldName has multiple values '$s' (internal list bug)"
    }
    return [int]$s
}

function Test-PortListen {
    param([int]$PortNum)
    try {
        $c = Get-NetTCPConnection -LocalPort $PortNum -State Listen -ErrorAction SilentlyContinue |
            Select-Object -First 1
        return $null -ne $c
    } catch {
        return $null -ne (netstat -ano | Select-String (":$PortNum\s+.*LISTENING"))
    }
}

function Get-JarProcessIds {
    param([string]$Name)
    $jar = "$Name-1.0.0-SNAPSHOT.jar"
    $ids = New-Object System.Collections.ArrayList
    Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue | ForEach-Object {
        if ($_.CommandLine -and $_.CommandLine -like "*$jar*") {
            [void]$ids.Add([int]$_.ProcessId)
        }
    }
    return ,$ids.ToArray()
}

function Stop-OneService {
    param([string]$Name)
    $n = 0
    foreach ($procId in @(Get-JarProcessIds -Name $Name)) {
        Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        $n++
    }
    return $n
}

function Start-OneService {
    param([string]$Name)
    $svc = Find-ServiceByName -Name $Name
    $portNum = Read-IntField -Item $svc -FieldName 'port'
    $jar = Join-Path $jarsDir "$Name-1.0.0-SNAPSHOT.jar"
    if (-not (Test-Path $jar)) { throw "missing jar: $jar" }
    if (Test-PortListen -PortNum $portNum) {
        return "already :$portNum"
    }

    $xmx = '512m'
    if ($Name -eq 'meis-analytics') { $xmx = '1024m' }
    $out = Join-Path $logsDir "$Name.out.log"
    $err = Join-Path $logsDir "$Name.err.log"
    $argLine = "-Xms128m -Xmx$xmx -jar `"$jar`" --spring.profiles.active=$profileName --spring.cloud.nacos.discovery.enabled=false"

    Start-Process -FilePath $javaExe -ArgumentList $argLine -WorkingDirectory $Root `
        -WindowStyle Hidden -RedirectStandardOutput $out -RedirectStandardError $err | Out-Null

    $deadline = (Get-Date).AddSeconds(120)
    $started = Get-Date
    while ((Get-Date) -lt $deadline) {
        if (Test-PortListen -PortNum $portNum) {
            return "OK :$portNum"
        }
        $still = @(Get-JarProcessIds -Name $Name)
        $elapsed = ((Get-Date) - $started).TotalSeconds
        if ($elapsed -ge 3 -and $still.Count -eq 0) {
            $tail = ''
            if (Test-Path $err) {
                $tail = ((Get-Content $err -Tail 12 -ErrorAction SilentlyContinue) -join ' | ')
            }
            throw "process exited early: $Name. $tail"
        }
        Start-Sleep -Seconds 1
    }
    throw "timeout $Name, see logs\$Name.err.log"
}

function Get-OrderedTargets {
    $selected = New-Object System.Collections.ArrayList
    for ($i = 0; $i -lt $serviceList.Count; $i++) {
        $item = $serviceList[$i]
        $isCore = ($item.core -eq $true) -or ([string]$item.core -eq 'true')
        if ($CoreOnlyFlag -eq '1') {
            if ($isCore) { [void]$selected.Add($item) }
        } else {
            [void]$selected.Add($item)
        }
    }
    # Manual sort by order (no Sort-Object scriptblock)
    for ($a = 0; $a -lt $selected.Count; $a++) {
        for ($b = $a + 1; $b -lt $selected.Count; $b++) {
            $oa = Read-IntField -Item $selected[$a] -FieldName 'order'
            $ob = Read-IntField -Item $selected[$b] -FieldName 'order'
            if ($ob -lt $oa) {
                $tmp = $selected[$a]
                $selected[$a] = $selected[$b]
                $selected[$b] = $tmp
            }
        }
    }
    return ,$selected
}

$messages = New-Object System.Collections.ArrayList
try {
    switch ($Action) {
        'start-one' {
            [void]$messages.Add((Start-OneService -Name $ServiceName))
        }
        'stop-one' {
            [void]$messages.Add("stopped=$(Stop-OneService -Name $ServiceName)")
        }
        'restart-one' {
            Stop-OneService -Name $ServiceName | Out-Null
            Start-Sleep -Seconds 1
            [void]$messages.Add((Start-OneService -Name $ServiceName))
        }
        'start-all' {
            $targets = Get-OrderedTargets
            for ($i = 0; $i -lt $targets.Count; $i++) {
                $n = [string]$targets[$i].name
                [void]$messages.Add("starting $n")
                [void]$messages.Add((Start-OneService -Name $n))
            }
            [void]$messages.Add('all start done')
        }
        'stop-all' {
            $targets = Get-OrderedTargets
            for ($i = $targets.Count - 1; $i -ge 0; $i--) {
                $n = [string]$targets[$i].name
                Stop-OneService -Name $n | Out-Null
                [void]$messages.Add("stopped $n")
            }
            [void]$messages.Add('all stopped')
        }
        default { throw "unknown action: $Action" }
    }
    $ok = $true
    $text = ($messages -join '; ')
} catch {
    $ok = $false
    $text = $_.Exception.Message
}

Write-Output $text
if ($ResultFile) {
    $payload = [ordered]@{
        ok = $ok
        message = $text
        finishedAt = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
    }
    ($payload | ConvertTo-Json -Compress) | Set-Content -Path $ResultFile -Encoding UTF8
}
if (-not $ok) { exit 1 }
