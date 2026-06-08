$ErrorActionPreference = "Stop"

$matches = netstat -ano |
    Select-String ":8101" |
    Where-Object { $_.Line -match "LISTENING" } |
    ForEach-Object {
        $parts = ($_ -split "\s+") | Where-Object { $_ }
        $parts[-1]
    } |
    Where-Object { $_ -match '^[1-9]\d*$' } |
    Select-Object -Unique

if (-not $matches) {
    Write-Host "No process is listening on port 8101."
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

    if ($cmd -and $cmd -notlike "*takeout-user-service*" -and $cmd -notlike "*TakeoutUserApplication*") {
        Write-Host "Process $procId is using port 8101 but does not look like user-service. Skip stopping it." -ForegroundColor Yellow
        Write-Host $cmd
        continue
    }

    Write-Host "Stopping user service process $procId ..."
    Stop-Process -Id $procId -Force
    $stopped = $true
}

if (-not $stopped) {
    Write-Host "No user-service process was stopped."
    exit 1
}

Start-Sleep -Seconds 2
Write-Host "Port 8101 is now available."
