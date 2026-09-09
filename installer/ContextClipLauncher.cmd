@echo off
REM ContextClip Desktop Agent Launcher
REM This script is registered as the handler for the contextclip:// URI protocol.
REM Windows passes the full protocol URI as %1, e.g.:
REM   contextclip://pair?code=pair_abc123...
REM
REM The script forwards all arguments to the Desktop Agent JAR.
REM DO NOT modify this file manually; it is managed by the installer.

set "AGENT_DIR=%~dp0"
set "AGENT_JAR=%AGENT_DIR%desktop-agent-0.0.1-SNAPSHOT.jar"

if not exist "%AGENT_JAR%" (
    echo ERROR: ContextClip Desktop Agent JAR not found at: %AGENT_JAR%
    echo Please reinstall ContextClip Desktop from https://contextclip.app
    pause
    exit /b 1
)

REM Locate the Java runtime (javaw)
set "JAVA_BIN=javaw"
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\javaw.exe" (
        set "JAVA_BIN=%JAVA_HOME%\bin\javaw.exe"
    )
)

where "%JAVA_BIN%" >nul 2>nul
if %ERRORLEVEL% neq 0 (
    if not exist "%JAVA_BIN%" (
        echo ERROR: Java runtime javaw was not found in PATH or JAVA_HOME.
        echo Please ensure Java 11 or newer is installed and added to your PATH.
        pause
        exit /b 1
    )
)

REM Launch without a persistent console window using javaw.
REM When launched with contextclip://, the agent pairs with the backend and transitions
REM smoothly into the background System Tray monitoring state.
start "" /d "%AGENT_DIR%" "%JAVA_BIN%" -jar "%AGENT_JAR%" %*
