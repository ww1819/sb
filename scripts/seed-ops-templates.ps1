# Seed demo ops templates: maintain / inspect / PM
param(
    [string]$TenantCode = 'demo',
    [string]$AdminUser = 'admin',
    [string]$AdminPass = 'admin123',
    [string]$Gateway = 'http://localhost:8080'
)

$ErrorActionPreference = 'Stop'

function Invoke-Api {
    param([string]$Method, [string]$Path, $Body = $null)
    $uri = "$Gateway/api$Path"
    $params = @{
        Uri         = $uri
        Method      = $Method
        Headers     = $script:headers
        ContentType = 'application/json; charset=utf-8'
    }
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 10 -Compress
        $params.Body = [System.Text.Encoding]::UTF8.GetBytes($json)
    }
    return Invoke-RestMethod @params
}

function Ensure-ListItem {
    param([string]$ListPath, [string]$CodeKey, [string]$Code, $Body)
    $list = Invoke-Api -Method GET -Path ($ListPath + '/list?limit=200')
    $rows = @($list.data)
    $found = $rows | Where-Object { $_.$CodeKey -eq $Code } | Select-Object -First 1
    if ($found) { return $found }
    $created = Invoke-Api -Method POST -Path $ListPath -Body $Body
    if ($null -ne $created.code -and $created.code -ne 0 -and $created.code -ne 200) {
        throw "create failed $ListPath/$Code : $($created.message)"
    }
    return $created.data
}

Write-Host "Login tenant=$TenantCode ..."
$login = Invoke-Api -Method POST -Path '/auth/login' -Body @{
    tenantCode = $TenantCode
    username   = $AdminUser
    password   = $AdminPass
}
if ($login.code -ne 0) { throw "login failed: $($login.message)" }

$script:headers = @{
    Authorization      = "Bearer $($login.data.token)"
    'X-Tenant-Id'      = $login.data.tenantId
    'X-Tenant-Schema'  = $login.data.schemaName
    'X-User-Id'        = $login.data.userId
    'X-Username'       = $login.data.username
}
Write-Host "Schema=$($login.data.schemaName)"

$levelL1 = Ensure-ListItem -ListPath '/maintain/maintenance_level' -CodeKey 'level_code' -Code 'L1' -Body @{
    level_code='L1'; level_name='一级保养'; sort_order=1; is_active=$true; description='日常一级保养'
}
$levelL2 = Ensure-ListItem -ListPath '/maintain/maintenance_level' -CodeKey 'level_code' -Code 'L2' -Body @{
    level_code='L2'; level_name='二级保养'; sort_order=2; is_active=$true; description='定期二级保养'
}
$inspDaily = Ensure-ListItem -ListPath '/inspect/inspection_type' -CodeKey 'type_code' -Code 'ROUTINE' -Body @{
    type_code='ROUTINE'; type_name='日常巡检'; sort_order=1; is_active=$true
}
$inspSafety = Ensure-ListItem -ListPath '/inspect/inspection_type' -CodeKey 'type_code' -Code 'SAFETY' -Body @{
    type_code='SAFETY'; type_name='安全巡检'; sort_order=2; is_active=$true
}
$pmAnnual = Ensure-ListItem -ListPath '/pm/pm_type' -CodeKey 'type_code' -Code 'ANNUAL' -Body @{
    type_code='ANNUAL'; type_name='年度PM'; sort_order=1; is_active=$true; risk_level='medium'
}
$pmSemi = Ensure-ListItem -ListPath '/pm/pm_type' -CodeKey 'type_code' -Code 'SEMI' -Body @{
    type_code='SEMI'; type_name='半年度PM'; sort_order=2; is_active=$true; risk_level='high'
}

function Find-Template([string]$ListPath, [string]$Code) {
    $list = Invoke-Api -Method GET -Path ($ListPath + '/list?limit=200')
    return @($list.data) | Where-Object { $_.template_code -eq $Code } | Select-Object -First 1
}

function Upsert-Template([string]$SavePath, [string]$ListPath, $Payload) {
    $existing = Find-Template $ListPath $Payload.template_code
    if ($existing) {
        $Payload.id = $existing.id
        Write-Host "  update $($Payload.template_code)"
    } else {
        Write-Host "  create $($Payload.template_code)"
    }
    $res = Invoke-Api -Method POST -Path $SavePath -Body $Payload
    if ($null -ne $res.code -and $res.code -ne 0 -and $res.code -ne 200) {
        throw "save failed $($Payload.template_code): $($res.message)"
    }
    return $res.data
}

Write-Host "Seeding maintenance templates..."
$null = Upsert-Template '/maintain/template' '/maintain/maintenance_template' @{
    template_code='MT-DEMO-L1'; template_name='通用设备一级保养模板'; maintenance_level='L1'; maintenance_level_id=$levelL1.id
    description='演示：外观清洁、通电自检、记录运行状态'; estimated_duration=30; is_active=$true
    items=@(
        @{ item_code='MT1-01'; item_name='外观清洁'; item_content='擦拭机身表面，清除灰尘与污渍'; standard_value='无可见污渍'; check_method='目视'; sort_order=1; is_required=$true }
        @{ item_code='MT1-02'; item_name='通电自检'; item_content='开机自检通过，无报警'; standard_value='自检通过'; check_method='通电'; sort_order=2; is_required=$true }
        @{ item_code='MT1-03'; item_name='附件检查'; item_content='电源线、探头/附件完好'; standard_value='完好'; check_method='目视+手检'; sort_order=3; is_required=$true }
    )
}
$null = Upsert-Template '/maintain/template' '/maintain/maintenance_template' @{
    template_code='MT-DEMO-L2'; template_name='监护类设备二级保养模板'; maintenance_level='L2'; maintenance_level_id=$levelL2.id
    description='演示：性能核对、电池与传感器保养'; estimated_duration=60; is_active=$true
    items=@(
        @{ item_code='MT2-01'; item_name='性能核对'; item_content='按厂家手册核对主要参数'; standard_value='符合说明书'; check_method='功能测试'; sort_order=1; is_required=$true }
        @{ item_code='MT2-02'; item_name='电池保养'; item_content='充放电循环，检查续航'; standard_value='续航正常'; check_method='实测'; sort_order=2; is_required=$true }
        @{ item_code='MT2-03'; item_name='传感器校准确认'; item_content='确认传感器/模块工作正常'; standard_value='正常'; check_method='比对'; sort_order=3; is_required=$false }
    )
}

Write-Host "Seeding inspection templates..."
$null = Upsert-Template '/inspect/template' '/inspect/inspection_template' @{
    template_code='INS-DEMO-DAILY'; template_name='病区日常巡检模板'; inspection_type_id=$inspDaily.id
    description='演示：病区通用日常巡检短清单'; estimated_duration=15; is_active=$true
    items=@(
        @{ item_code='INS1-01'; item_name='在位状态'; item_content='设备在位、标识清晰'; standard_value='在位'; check_method='目视'; sort_order=1; is_required=$true }
        @{ item_code='INS1-02'; item_name='运行指示'; item_content='指示灯/屏幕显示正常'; standard_value='正常'; check_method='目视'; sort_order=2; is_required=$true }
        @{ item_code='INS1-03'; item_name='环境安全'; item_content='周围无遮挡、无积水、通风正常'; standard_value='安全'; check_method='目视'; sort_order=3; is_required=$true }
    )
}
$null = Upsert-Template '/inspect/template' '/inspect/inspection_template' @{
    template_code='INS-DEMO-SAFETY'; template_name='生命支持设备安全巡检模板'; inspection_type_id=$inspSafety.id
    description='演示：呼吸机/监护等高风险设备安全巡检'; estimated_duration=20; is_active=$true
    items=@(
        @{ item_code='INS2-01'; item_name='报警功能'; item_content='测试报警可触发并可消音复位'; standard_value='可报警'; check_method='功能测试'; sort_order=1; is_required=$true }
        @{ item_code='INS2-02'; item_name='气路/管路'; item_content='管路连接牢固无泄漏'; standard_value='无泄漏'; check_method='目视+听诊'; sort_order=2; is_required=$true }
        @{ item_code='INS2-03'; item_name='应急电源'; item_content='市电断开后可切换应急供电'; standard_value='可切换'; check_method='实测'; sort_order=3; is_required=$true }
    )
}

Write-Host "Seeding PM templates..."
$null = Upsert-Template '/pm/template' '/pm/pm_template' @{
    template_code='PM-DEMO-ANNUAL'; template_name='厂家年度预防性维护模板'; pm_type='ANNUAL'; pm_type_id=$pmAnnual.id
    description='演示：合同年度 PM 检查项'; estimated_duration=120; is_active=$true
    items=@(
        @{ item_code='PM1-01'; item_name='电气安全'; item_content='漏电流、接地电阻检测'; standard_value='符合 GB 9706'; check_method='仪器检测'; sort_order=1; is_required=$true }
        @{ item_code='PM1-02'; item_name='机械部件'; item_content='传动/升降/刹车机构润滑与紧固'; standard_value='灵活可靠'; check_method='手检'; sort_order=2; is_required=$true }
        @{ item_code='PM1-03'; item_name='软件/固件版本'; item_content='记录当前版本并确认在保内'; standard_value='已记录'; check_method='系统查询'; sort_order=3; is_required=$false }
    )
}
$null = Upsert-Template '/pm/template' '/pm/pm_template' @{
    template_code='PM-DEMO-SEMI'; template_name='半年度法规合规 PM 模板'; pm_type='SEMI'; pm_type_id=$pmSemi.id
    description='演示：半年度合规维护'; estimated_duration=90; is_active=$true
    items=@(
        @{ item_code='PM2-01'; item_name='计量/校准状态'; item_content='确认计量有效期内或安排送检'; standard_value='在有效期'; check_method='台账核对'; sort_order=1; is_required=$true }
        @{ item_code='PM2-02'; item_name='关键附件更换'; item_content='按厂家周期更换滤芯/密封件等'; standard_value='已更换或无需'; check_method='按手册'; sort_order=2; is_required=$true }
        @{ item_code='PM2-03'; item_name='服务报告归档'; item_content='上传或归档 PM 服务报告'; standard_value='已归档'; check_method='文件检查'; sort_order=3; is_required=$true }
    )
}

Write-Host ""
Write-Host "Done."
Write-Host "  Maintain: MT-DEMO-L1 / MT-DEMO-L2"
Write-Host "  Inspect:  INS-DEMO-DAILY / INS-DEMO-SAFETY"
Write-Host "  PM:       PM-DEMO-ANNUAL / PM-DEMO-SEMI"