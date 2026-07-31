# Stop all MEIS backend microservices
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\meis-services.ps1"
Stop-MeisServices
