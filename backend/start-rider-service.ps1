param(
    [switch]$ForceRestart
)

$ErrorActionPreference = "Stop"

$backendRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$commonPom = Join-Path $backendRoot "takeout-common\pom.xml"
$riderPom = Join-Path $backendRoot "takeout-rider-service\pom.xml"
$stopScript = Join-Path $backendRoot "stop-rider-service.ps1"
$portUsage = netstat -ano | Select-String ":8103"

if ($portUsage) {
    if (-not $ForceRestart) {
        Write-Host "Port 8103 is already in use. Run with -ForceRestart or stop the existing rider service first." -ForegroundColor Yellow
        $portUsage | ForEach-Object { Write-Host $_.Line }
        exit 1
    }

    Write-Host "[0/3] Stop existing rider service on port 8103..."
    powershell -ExecutionPolicy Bypass -File $stopScript
}

Push-Location $backendRoot
try {
    Write-Host "[1/3] Install backend parent POM..."
    mvn -q -N install

    Write-Host "[2/3] Install takeout-common into local Maven repo..."
    mvn -q -f $commonPom -DskipTests install

    Write-Host "[3/3] Start takeout-rider-service..."
    mvn -q -f $riderPom -DskipTests spring-boot:run
} finally {
    Pop-Location
}
