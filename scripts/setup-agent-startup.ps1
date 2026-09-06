# ContextClip Desktop Agent - Windows Startup & Token Configuration Helper
param (
    [string]$AgentToken,
    [switch]$InstallStartup,
    [switch]$UninstallStartup
)

$JarPath = Join-Path $PSScriptRoot "..\desktop-agent\target\desktop-agent-0.0.1-SNAPSHOT.jar"
if (!(Test-Path $JarPath)) {
    Write-Error "Desktop agent JAR not found at $JarPath. Please build it first with 'mvn clean package -DskipTests'."
    exit 1
}

if ($UninstallStartup) {
    java -jar $JarPath --uninstall-startup
    exit 0
}

if ($AgentToken) {
    java -jar $JarPath --set-token $AgentToken
}

if ($InstallStartup) {
    java -jar $JarPath --install-startup
}

if (-not $AgentToken -and -not $InstallStartup) {
    Write-Host "ContextClip Desktop Agent Helper"
    Write-Host "Usage:"
    Write-Host "  .\setup-agent-startup.ps1 -AgentToken '<YOUR_AGENT_JWT>' -InstallStartup"
    Write-Host "  .\setup-agent-startup.ps1 -UninstallStartup"
}
