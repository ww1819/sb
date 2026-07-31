# Compare tenant schema with migration scripts; write report to scripts/schema-diff.txt
param(
    [string]$Schema = "tenant_demo",
    [switch]$GeneratePatches
)

$ErrorActionPreference = "Stop"
$jar = Join-Path $env:USERPROFILE ".m2\repository\org\postgresql\postgresql\42.7.3\postgresql-42.7.3.jar"
Push-Location (Join-Path $PSScriptRoot ".")
try {
    javac -cp $jar PatchGenerator.java FullSchemaCompare.java ColumnDiff.java
    java -cp ".;$jar" FullSchemaCompare $Schema | Out-File -Encoding utf8 (Join-Path $PSScriptRoot "schema-diff.txt")
    java -cp ".;$jar" ColumnDiff $Schema | Out-File -Encoding utf8 -Append (Join-Path $PSScriptRoot "schema-diff.txt")
    if ($GeneratePatches) {
        java -cp ".;$jar" PatchGenerator $Schema
    }
    Write-Host "Report: scripts/schema-diff.txt"
    Write-Host "Patches: db/source/patches/tenant_column_patches.sql"
} finally {
    Pop-Location
}
