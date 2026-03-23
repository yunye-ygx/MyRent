param(
    [string]$ServiceName = "myrent-backend",
    [string]$BackendServices = "192.168.100.128:11800",
    [string]$AgentDir = "",
    [string]$AppJarPath = "",
    [string]$AgentVersion = "9.6.0",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if ([string]::IsNullOrWhiteSpace($AgentDir)) {
    $AgentDir = Join-Path $projectRoot "tools\skywalking-agent"
}

$AgentDir = [System.IO.Path]::GetFullPath($AgentDir)
$agentJar = Join-Path $AgentDir "skywalking-agent.jar"

if (-not (Test-Path $agentJar)) {
    & (Join-Path $PSScriptRoot "download-skywalking-agent.ps1") -Version $AgentVersion -InstallDir $AgentDir
}

$optionalPlugin = Get-ChildItem -Path (Join-Path $AgentDir "optional-plugins") -Filter "apm-springmvc-annotation-6.x-plugin-*.jar" -ErrorAction SilentlyContinue |
    Select-Object -First 1
if ($optionalPlugin) {
    $enabledPlugin = Join-Path $AgentDir ("plugins\" + $optionalPlugin.Name)
    if (-not (Test-Path $enabledPlugin)) {
        Copy-Item -Path $optionalPlugin.FullName -Destination $enabledPlugin
        Write-Host "Enabled optional plugin: $($optionalPlugin.Name)"
    }
}

if (-not $SkipBuild) {
    Push-Location $projectRoot
    try {
        mvn -DskipTests package
    } finally {
        Pop-Location
    }
}

if ([string]::IsNullOrWhiteSpace($AppJarPath)) {
    $appJar = Get-ChildItem -Path (Join-Path $projectRoot "target") -Filter *.jar -File |
        Where-Object { $_.Name -notlike "*.original" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $appJar) {
        throw "Cannot find application jar under target/. Run mvn package first or pass -AppJarPath."
    }
    $AppJarPath = $appJar.FullName
} else {
    $AppJarPath = [System.IO.Path]::GetFullPath($AppJarPath)
}

$agentLogDir = Join-Path $projectRoot "runtime-logs\skywalking-agent"
New-Item -ItemType Directory -Path $agentLogDir -Force | Out-Null

$env:SW_AGENT_NAME = $ServiceName
$env:SW_AGENT_INSTANCE_NAME = "$env:COMPUTERNAME-$PID"
$env:SW_AGENT_COLLECTOR_BACKEND_SERVICES = $BackendServices
$env:SW_LOGGING_DIR = $agentLogDir

Write-Host "Starting application with SkyWalking Agent ..."
Write-Host "  service:  $env:SW_AGENT_NAME"
Write-Host "  instance: $env:SW_AGENT_INSTANCE_NAME"
Write-Host "  backend:  $env:SW_AGENT_COLLECTOR_BACKEND_SERVICES"
Write-Host "  app jar:  $AppJarPath"
Write-Host "  agent:    $agentJar"

Push-Location $projectRoot
try {
    & java "-javaagent:$agentJar" -jar $AppJarPath
} finally {
    Pop-Location
}
