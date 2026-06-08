$ErrorActionPreference = "Stop"

$matches = netstat -ano |
    Select-String ":8102" |
    Where-Object { $_.Line -match "LISTENING" } |
    ForEach-Object {
    $parts = ($_ -split "\s+") | Where-Object { $_ }
    $parts[-1]
} |
    Where-Object { $_ -match '^[1-9]\d*$' } |
    Select-Object -Unique

if (-not $matches) {
    Write-Host "No process is listening on port 8102."
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

    if ($cmd -and $cmd -notlike "*takeout-merchant-service*" -and $cmd -notlike "*TakeoutMerchantApplication*") {
        Write-Host "Process $procId is using port 8102 but does not look like merchant-service. Skip stopping it." -ForegroundColor Yellow
        Write-Host $cmd
        continue
    }

    Write-Host "Stopping merchant service process $procId ..."
    Stop-Process -Id $procId -Force
    $stopped = $true
}

if (-not $stopped) {
    Write-Host "No merchant-service process was stopped."
    exit 1
}

Start-Sleep -Seconds 2
Write-Host "Port 8102 is now available."
