# Generate MEIS microservice modules
$root = "E:\workspace\sb"
$services = @(
    @{ Name="meis-api"; Port=0; Tables=@() },
    @{ Name="meis-gateway"; Port=8080; Tables=@() },
    @{ Name="meis-auth"; Port=8081; Tables=@() },
    @{ Name="meis-tenant"; Port=8082; Tables=@() },
    @{ Name="meis-system"; Port=8083; Tables=@("campus","building","department","sys_user","sys_role","sys_operation_log","sys_config","sys_dict","medical_device_category","supplier","manufacturer") },
    @{ Name="meis-purchase"; Port=8084; Tables=@("purchase_plan","purchase_plan_item","purchase_project","purchase_contract","contract_payment") },
    @{ Name="meis-asset"; Port=8085; Tables=@("medical_device","device_accessory","device_entry","device_entry_item","asset_transfer","inventory_check","inventory_check_item","device_scrap") },
    @{ Name="meis-repair"; Port=8086; Tables=@("fault_type_dict","engineer","repair_workorder","spare_part","spare_part_usage") },
    @{ Name="meis-maintain"; Port=8087; Tables=@("maintenance_template","maintenance_plan","maintenance_record") },
    @{ Name="meis-qc"; Port=8088; Tables=@("risk_assessment","adverse_event","metrology_record","performance_test") },
    @{ Name="meis-maintenance-contract"; Port=8089; Tables=@("maintenance_contract","maintenance_contract_fulfillment","maintenance_contract_payment") },
    @{ Name="meis-special"; Port=8090; Tables=@("life_support_device","emergency_device_pool","emergency_device_allocation","special_device","leased_device") },
    @{ Name="meis-analytics"; Port=8091; Tables=@("device_usage_record","device_cost_record","device_benefit_summary") },
    @{ Name="meis-file"; Port=8092; Tables=@() },
    @{ Name="meis-notification"; Port=8093; Tables=@("sys_notification") },
    @{ Name="meis-integration"; Port=8094; Tables=@() }
)

foreach ($svc in $services) {
    $name = $svc.Name
    $port = $svc.Port
    $dir = Join-Path $root $name
    New-Item -ItemType Directory -Force -Path "$dir\src\main\java\com\meis\saas\boot","$dir\src\main\resources" | Out-Null

    if ($name -eq "meis-api") {
        @"
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent><groupId>com.meis.saas</groupId><artifactId>meis-saas</artifactId><version>1.0.0-SNAPSHOT</version></parent>
  <artifactId>meis-api</artifactId>
  <dependencies>
    <dependency><groupId>com.meis.saas</groupId><artifactId>meis-common</artifactId></dependency>
    <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-openfeign</artifactId></dependency>
    <dependency><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><optional>true</optional></dependency>
  </dependencies>
</project>
"@ | Set-Content "$dir\pom.xml" -Encoding UTF8
        continue
    }

    $extraDeps = ""
    if ($name -eq "meis-gateway") {
        $extraDeps = @"
        <dependency><groupId>com.meis.saas</groupId><artifactId>meis-common</artifactId>
          <exclusions><exclusion><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></exclusion></exclusions>
        </dependency>
        <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-gateway</artifactId></dependency>
"@
    } elseif ($name -eq "meis-file") {
        $extraDeps = @"
        <dependency><groupId>com.meis.saas</groupId><artifactId>meis-common</artifactId></dependency>
        <dependency><groupId>io.minio</groupId><artifactId>minio</artifactId></dependency>
"@
    } else {
        $extraDeps = "<dependency><groupId>com.meis.saas</groupId><artifactId>meis-common</artifactId></dependency>"
    }

    @"
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent><groupId>com.meis.saas</groupId><artifactId>meis-saas</artifactId><version>1.0.0-SNAPSHOT</version></parent>
  <artifactId>$name</artifactId>
  <dependencies>
    $extraDeps
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
    <dependency><groupId>com.alibaba.cloud</groupId><artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
    <dependency><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><optional>true</optional></dependency>
  </dependencies>
  <build><plugins><plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin></plugins></build>
</project>
"@ | Set-Content "$dir\pom.xml" -Encoding UTF8

    if ($port -gt 0 -and $name -notin @("meis-gateway","meis-auth","meis-tenant","meis-file","meis-integration")) {
        $tables = ($svc.Tables | ForEach-Object { '"' + $_ + '"' }) -join ","
        $className = ($name -replace '-','') + "Controller"
        @"
package com.meis.saas.web;

import com.meis.saas.common.web.GenericTableController;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Set;

@RestController
@RequestMapping("/api/$($name -replace 'meis-','')")
public class $className extends GenericTableController {
    private final JdbcTemplate jdbcTemplate;
    private static final Set<String> TABLES = Set.of($tables);

    public ${className}(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }
    @Override protected JdbcTemplate jdbc() { return jdbcTemplate; }
    @Override protected Set<String> tables() { return TABLES; }
}
"@ | Set-Content "$dir\src\main\java\com\meis\saas\web\$className.java" -Encoding UTF8
    }

    if ($port -gt 0) {
        $appClass = ($name -replace '-','') + "Application"
        @"
package com.meis.saas.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.meis.saas")
@EnableDiscoveryClient
public class $appClass {
    public static void main(String[] args) { SpringApplication.run($appClass.class, args); }
}
"@ | Set-Content "$dir\src\main\java\com\meis\saas\boot\$appClass.java" -Encoding UTF8

        @"
server:
  port: $port
spring:
  application:
    name: $name
  datasource:
    url: jdbc:postgresql://`${POSTGRES_HOST:localhost}:`${POSTGRES_PORT:5432}/`${POSTGRES_DB:meis}
    username: `${POSTGRES_USER:med}
    password: `${POSTGRES_PASSWORD:med123456}
  cloud:
    nacos:
      discovery:
        server-addr: `${NACOS_SERVER:localhost:8848}
meis:
  jwt:
    secret: meis-saas-jwt-secret-change-in-production-256bits
management:
  endpoints:
    web:
      exposure:
        include: health,info
"@ | Set-Content "$dir\src\main\resources\application.yml" -Encoding UTF8
    }
}
Write-Host "Generated modules"
