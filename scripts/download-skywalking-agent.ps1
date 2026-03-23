param(
    [string]$Version = "9.6.0",
    [string]$InstallDir = ""
)

$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if ([string]::IsNullOrWhiteSpace($InstallDir)) {
    $InstallDir = Join-Path $projectRoot "tools\skywalking-agent"
}

$InstallDir = [System.IO.Path]::GetFullPath($InstallDir)
$agentJar = Join-Path $InstallDir "skywalking-agent.jar"
if (Test-Path $agentJar) {
    Write-Host "SkyWalking agent already exists: $agentJar"
    exit 0
}

$archiveName = "apache-skywalking-java-agent-$Version.tgz"
$downloadUrl = "https://dlcdn.apache.org/skywalking/java-agent/$Version/$archiveName"
$tempRoot = Join-Path $projectRoot "tools\_tmp"
$tempId = [System.Guid]::NewGuid().ToString("N")
$tempArchive = Join-Path $tempRoot ("skywalking-agent-" + $tempId + ".tgz")

New-Item -ItemType Directory -Path $InstallDir -Force | Out-Null
New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null

Write-Host "Downloading SkyWalking Java Agent $Version ..."
Invoke-WebRequest -Uri $downloadUrl -OutFile $tempArchive

Write-Host "Extracting package to $InstallDir ..."
tar -xzf $tempArchive -C $InstallDir --strip-components 1

if (-not (Test-Path $agentJar)) {
    throw "Cannot find skywalking-agent.jar in extracted package."
}

Remove-Item -Path $tempArchive -Force -ErrorAction SilentlyContinue

Write-Host "SkyWalking agent is ready: $agentJar"
