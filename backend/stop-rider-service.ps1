$ErrorActionPreference = "Stop"

$matches = netstat -ano |
    Select-String ":8103" |
    Where-Object { $_.Line -match "LISTENING" } |
    ForEach-Object {
        $parts = ($_ -split "\s+") | Where-Object { $_ }
        $parts[-1]
    } |
    Where-Object { $_ -match '^[1-9]\d*$' } |
    Select-Object -Unique

if (-not $matches) {
    Write-Host "No process is listening on port 8103."
    exit 0
}

$stopped = $false

foreach ($idText in $matches) {
    $procId = [int]$idText
    $cmd = ""

    try {
        $proc = Get-CimInstance Win32_Process -Filter "ProcessId = $procId"
        $cmd = [string]$proc.CommandLine
    } catch {
        $cmd = ""
    }

    if ($cmd -and $cmd -notlike "*takeout-rider-service*" -and $cmd -notlike "*TakeoutRiderApplication*") {
        Write-Host "Process $procId is using port 8103 but does not look like rider-service. Skip stopping it." -ForegroundColor Yellow
        Write-Host $cmd
        continue
    }

    Write-Host "Stopping rider service process $procId ..."
    Stop-Process -Id $procId -Force
    $stopped = $true
}

if (-not $stopped) {
    Write-Host "No rider-service process was stopped."
    exit 1
}

Start-Sleep -Seconds 2
Write-Host "Port 8103 is now available."
