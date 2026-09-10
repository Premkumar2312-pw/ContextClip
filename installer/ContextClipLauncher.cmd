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

REM Locate the Java runtime (prefer bundled runtime first)
set "JAVA_BIN="
if exist "%AGENT_DIR%runtime\bin\javaw.exe" (
    set "JAVA_BIN=%AGENT_DIR%runtime\bin\javaw.exe"
)

if not defined JAVA_BIN (
    if defined JAVA_HOME (
        if exist "%JAVA_HOME%\bin\javaw.exe" (
            set "JAVA_BIN=%JAVA_HOME%\bin\javaw.exe"
        )
    )
)

if not defined JAVA_BIN (
    where javaw >nul 2>nul
    if %ERRORLEVEL% equ 0 (
        set "JAVA_BIN=javaw"
    )
)

if not defined JAVA_BIN (
    echo ERROR: ContextClip Java runtime was not found.
    echo Bundled runtime is missing and no system Java was found.
    echo Please reinstall ContextClip Desktop from https://contextclip.app
    pause
    exit /b 1
)

REM Launch without a persistent console window using javaw.
REM When launched with contextclip://, the agent pairs with the backend and transitions
REM smoothly into the background System Tray monitoring state.
start "" /d "%AGENT_DIR%" "%JAVA_BIN%" -Djavax.accessibility.assistive_technologies= -jar "%AGENT_JAR%" %*
