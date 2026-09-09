<#
.SYNOPSIS
    ContextClip Desktop Installer Build Script — Phase 15

.DESCRIPTION
    Builds the ContextClip Desktop Agent shaded JAR and compiles the
    Inno Setup installer script to produce ContextClipDesktopSetup.exe.

    Steps:
      1. Build desktop-agent shaded JAR via Maven (mvn clean package -DskipTests)
      2. Copy JAR to installer\dist\
      3. Locate Inno Setup compiler (iscc.exe)
      4. Compile ContextClipDesktop.iss → output\ContextClipDesktopSetup.exe

.NOTES
    Requirements:
      - Java 11+ in PATH (tested with Temurin JDK 25)
      - Apache Maven in PATH
      - Inno Setup 6 installed (https://jrsoftware.org/isdl.php)
        If not installed, this script will print instructions and exit.

    Usage:
      cd installer
      .\build-installer.ps1

      # Skip JAR rebuild (use existing target JAR):
      .\build-installer.ps1 -SkipJarBuild

      # Build JAR only, skip Inno Setup:
      .\build-installer.ps1 -SkipInnoSetup
#>

[CmdletBinding()]
param(
    [switch]$SkipJarBuild,
    [switch]$SkipInnoSetup
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# ─── Paths ────────────────────────────────────────────────────────────────────

$ScriptDir     = $PSScriptRoot
$RepoRoot      = Split-Path $ScriptDir -Parent
$AgentDir      = Join-Path $RepoRoot "desktop-agent"
$JarName       = "desktop-agent-0.0.1-SNAPSHOT.jar"
$JarSourcePath = Join-Path $AgentDir "target\$JarName"
$DistDir       = Join-Path $ScriptDir "dist"
$JarDestPath   = Join-Path $DistDir $JarName
$IssFile       = Join-Path $ScriptDir "ContextClipDesktop.iss"
$OutputDir     = Join-Path $ScriptDir "output"

# ─── Banner ───────────────────────────────────────────────────────────────────

Write-Host ""
Write-Host "====================================================" -ForegroundColor Cyan
Write-Host "  ContextClip Desktop Installer Builder - Phase 15  " -ForegroundColor Cyan
Write-Host "====================================================" -ForegroundColor Cyan
Write-Host ""

# --- Step 1: Build Desktop Agent JAR -----------------------------------------

if (-not $SkipJarBuild) {
    Write-Host "[1/3] Building Desktop Agent shaded JAR..." -ForegroundColor Yellow

    $mavenCmd = Get-Command mvn -ErrorAction SilentlyContinue
    if (-not $mavenCmd) {
        Write-Error "ERROR: 'mvn' not found in PATH. Please install Apache Maven and add it to PATH."
        exit 1
    }

    Push-Location $AgentDir
    try {
        & mvn clean package -DskipTests --no-transfer-progress
        if ($LASTEXITCODE -ne 0) {
            Write-Error "ERROR: Maven build failed with exit code $LASTEXITCODE"
            exit $LASTEXITCODE
        }
    } finally {
        Pop-Location
    }

    Write-Host "    JAR built successfully." -ForegroundColor Green
} else {
    Write-Host "[1/3] Skipping JAR build (-SkipJarBuild specified)." -ForegroundColor DarkGray
}

# --- Step 2: Copy JAR to dist/ -----------------------------------------------

Write-Host "[2/3] Copying JAR to installer\dist\..." -ForegroundColor Yellow

if (-not (Test-Path $JarSourcePath)) {
    Write-Error "ERROR: JAR not found at: $JarSourcePath`nRun without -SkipJarBuild to rebuild."
    exit 1
}

if (-not (Test-Path $DistDir)) {
    New-Item -ItemType Directory -Path $DistDir | Out-Null
}

Copy-Item -Path $JarSourcePath -Destination $JarDestPath -Force
$jarSize = (Get-Item $JarDestPath).Length
$jarSizeMB = [math]::Round($jarSize / 1048576, 1)
Write-Host "    Copied: $JarDestPath ($jarSizeMB MB)" -ForegroundColor Green

# --- Step 3: Compile Inno Setup installer -----------------------------------

if (-not $SkipInnoSetup) {
    Write-Host "[3/3] Compiling Inno Setup installer..." -ForegroundColor Yellow

    # Search for iscc.exe in standard Inno Setup installation paths
    $innoSearchPaths = @(
        "$env:LOCALAPPDATA\Programs\Inno Setup 6\iscc.exe",
        "$env:LOCALAPPDATA\Programs\Inno Setup 5\iscc.exe",
        "C:\Program Files (x86)\Inno Setup 6\iscc.exe",
        "C:\Program Files\Inno Setup 6\iscc.exe",
        "C:\Program Files (x86)\Inno Setup 5\iscc.exe",
        "C:\Program Files\Inno Setup 5\iscc.exe"
    )

    $isccPath = $null
    foreach ($path in $innoSearchPaths) {
        if (Test-Path $path) {
            $isccPath = $path
            break
        }
    }

    # Also try PATH
    if (-not $isccPath) {
        $isccCmd = Get-Command iscc.exe -ErrorAction SilentlyContinue
        if ($isccCmd) {
            $isccPath = $isccCmd.Source
        }
    }

    if (-not $isccPath) {
        Write-Host ""
        Write-Host "------------------------------------------------------" -ForegroundColor Yellow
        Write-Host "  Inno Setup not found. Cannot compile installer." -ForegroundColor Yellow
        Write-Host ""
        Write-Host "  To build the installer:" -ForegroundColor White
        Write-Host "  1. Download Inno Setup 6 (free):" -ForegroundColor White
        Write-Host "     https://jrsoftware.org/isdl.php" -ForegroundColor Cyan
        Write-Host "  2. Install with default options."
        Write-Host "  3. Re-run this script:"
        Write-Host "       cd installer" -ForegroundColor Cyan
        Write-Host "       .\build-installer.ps1 -SkipJarBuild" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "  The JAR has been staged at:" -ForegroundColor White
        Write-Host "    $JarDestPath" -ForegroundColor Cyan
        Write-Host "------------------------------------------------------" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "[Result] JAR staged. Installer build skipped (Inno Setup not installed)." -ForegroundColor DarkYellow
        exit 0
    }

    Write-Host "    Found Inno Setup at: $isccPath" -ForegroundColor DarkGray

    if (-not (Test-Path $OutputDir)) {
        New-Item -ItemType Directory -Path $OutputDir | Out-Null
    }

    & $isccPath $IssFile
    if ($LASTEXITCODE -ne 0) {
        Write-Error "ERROR: Inno Setup compilation failed with exit code $LASTEXITCODE"
        exit $LASTEXITCODE
    }

    $outputExe = Join-Path $OutputDir "ContextClipDesktopSetup.exe"
    if (Test-Path $outputExe) {
        $exeSize = (Get-Item $outputExe).Length
        $exeSizeMB = [math]::Round($exeSize / 1048576, 1)
        Write-Host "    Installer: $outputExe ($exeSizeMB MB)" -ForegroundColor Green
    }
} else {
    Write-Host "[3/3] Skipping Inno Setup compilation (-SkipInnoSetup specified)." -ForegroundColor DarkGray
}

# --- Done -------------------------------------------------------------------

Write-Host ""
Write-Host "====================================================" -ForegroundColor Green
Write-Host "  BUILD COMPLETE" -ForegroundColor Green
Write-Host "====================================================" -ForegroundColor Green
Write-Host ""
if (-not $SkipInnoSetup -and (Test-Path (Join-Path $OutputDir "ContextClipDesktopSetup.exe"))) {
    Write-Host "  Installer: installer\output\ContextClipDesktopSetup.exe" -ForegroundColor Cyan
}
Write-Host "  JAR:       installer\dist\$JarName" -ForegroundColor Cyan
Write-Host ""
Write-Host "  To install, run: installer\output\ContextClipDesktopSetup.exe" -ForegroundColor White
Write-Host ""
