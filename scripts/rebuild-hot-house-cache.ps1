param(
    [string]$BaseUrl = "http://localhost:8084"
)

$ErrorActionPreference = "Stop"
$uri = "$BaseUrl/house/hot/rebuild"

try {
    $response = Invoke-RestMethod -Method Post -Uri $uri -TimeoutSec 30

    Write-Host "Hot-house Redis cache rebuild succeeded."

    if ($null -ne $response -and $null -ne $response.message -and $response.message.ToString().Trim().Length -gt 0) {
        Write-Host $response.message
    }

    exit 0
} catch {
    Write-Error ("Hot-house Redis cache rebuild failed: " + $_.Exception.Message)
    exit 1
}
