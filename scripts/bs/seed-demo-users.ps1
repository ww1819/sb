# Seed demo tenant users (backend must be running)
param(
    [int]$Count = 50,
    [int]$Start = 1,
    [string]$TenantCode = 'demo',
    [string]$AdminUser = 'admin',
    [string]$AdminPass = 'admin123',
    [string]$Password = '123456',
    [string]$Gateway = 'http://localhost:8080'
)

$ErrorActionPreference = 'Stop'
$pad = if ($Start + $Count - 1 -ge 1000) { 4 } elseif ($Start + $Count - 1 -ge 100) { 3 } else { 2 }

Write-Host "Login tenant: $TenantCode"
$login = Invoke-RestMethod -Uri "$Gateway/api/auth/login" -Method POST -ContentType 'application/json' -Body (@{
    tenantCode = $TenantCode
    username   = $AdminUser
    password   = $AdminPass
} | ConvertTo-Json)

$headers = @{ Authorization = "Bearer $($login.data.token)" }
$created = 0
$skipped = 0
$end = $Start + $Count - 1

Write-Host "Creating demo_user_* ($Start .. $end), password: $Password"

for ($i = $Start; $i -le $end; $i++) {
    $num = ('{0:D' + $pad + '}') -f $i
    $body = @{
        username    = "demo_user_$num"
        password    = $Password
        real_name   = "DemoUser$num"
        employee_no = "D$num"
        phone       = ('138{0:D8}' -f (10000000 + $i))
        is_active   = $true
    } | ConvertTo-Json

    try {
        $res = Invoke-RestMethod -Uri "$Gateway/api/system/users" -Method POST -ContentType 'application/json' -Headers $headers -Body $body
        if ($res.code -eq 0) {
            $created++
            if ($created % 50 -eq 0) { Write-Host "  created $created ..." }
        } else {
            $skipped++
        }
    } catch {
        $skipped++
    }
}

Write-Host "Done: created=$created skipped=$skipped"
$pageUrl = $Gateway + '/api/system/users/page?page=1' + '&size=1'
$total = (Invoke-RestMethod -Uri $pageUrl -Headers $headers).data.total
Write-Host "Total users: $total"
