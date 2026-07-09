# Generate db/source/create/tenant_tables.sql from Flyway V1 (idempotent CREATE)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$src = Join-Path $root 'meis-tenant\src\main\resources\db\migrations\tenant\V1__tables.sql'
$out = Join-Path $root 'db\source\create\tenant_tables.sql'

$content = Get-Content $src -Raw -Encoding UTF8
$content = [regex]::Replace($content, '(?im)^CREATE TABLE\s+(?!IF NOT EXISTS)', 'CREATE TABLE IF NOT EXISTS ')
$content = [regex]::Replace($content, '(?im)^CREATE VIEW\s+', 'CREATE OR REPLACE VIEW ')
$content = [regex]::Replace($content, '(?im)^CREATE UNIQUE INDEX\s+(?!IF NOT EXISTS)', 'CREATE UNIQUE INDEX IF NOT EXISTS ')
$content = [regex]::Replace($content, '(?im)^CREATE INDEX\s+(?!IF NOT EXISTS)', 'CREATE INDEX IF NOT EXISTS ')

$header = @'
-- =============================================================================
-- MEIS tenant create script (from V1__tables.sql, idempotent)
-- Run 00_extensions.sql first, then this file, then patches if upgrading legacy DB
-- =============================================================================

'@

Set-Content -Path $out -Value ($header + $content) -Encoding UTF8 -NoNewline
Write-Host "Generated $out"
