# ContextClip Desktop — Windows Installer & Protocol Registration

**Phase 15 — Windows Commercial Desktop Installer Foundation**

This document describes how the ContextClip Desktop Agent is packaged into a Windows installer, how the `contextclip://` custom URI protocol is registered with Windows, and how the complete one-click browser-to-desktop pairing flow works end-to-end after installation.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Technology Choice](#2-technology-choice)
3. [Installer Build Instructions](#3-installer-build-instructions)
4. [Installation Process](#4-installation-process)
5. [contextclip:// Protocol Registration](#5-contextclip-protocol-registration)
6. [Pairing Flow (End-to-End)](#6-pairing-flow-end-to-end)
7. [Startup Behavior](#7-startup-behavior)
8. [Upgrade Behavior](#8-upgrade-behavior)
9. [Uninstall Behavior](#9-uninstall-behavior)
10. [Security Model](#10-security-model)
11. [CLI Fallback (Always Available)](#11-cli-fallback-always-available)
12. [Known Limitations](#12-known-limitations)
13. [Troubleshooting](#13-troubleshooting)
14. [Manual Verification Tests](#14-manual-verification-tests)

---

## 1. Architecture Overview

```
User Downloads ContextClipDesktopSetup.exe
        │
        ▼
Inno Setup Installer Wizard
        │ installs to %LOCALAPPDATA%\Programs\ContextClip Desktop\
        │   ├── desktop-agent-0.0.1-SNAPSHOT.jar   (shaded executable JAR)
        │   └── ContextClipLauncher.cmd             (protocol dispatch launcher)
        │
        ├── writes HKCU registry keys for contextclip:// protocol
        │
        └── (optional) writes startup entry to Windows Startup folder
               %APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\
                   ContextClipAgent.bat

──────────────────────────────────────────

Runtime Protocol Flow:

Browser → contextclip://pair?code=pair_abc123
        │
        ▼ Windows registry lookup (HKCU)
ContextClipLauncher.cmd "contextclip://pair?code=pair_abc123"
        │
        ▼ javaw -jar desktop-agent-0.0.1-SNAPSHOT.jar contextclip://pair?code=pair_abc123
ClipboardAgentApplication.main(args)
        │ args[0].startsWith("contextclip://")
        ▼
PairingHandler.handlePairing(uri, apiEndpointUrl)
        │ POST /api/agent/pairing/exchange {"code":"pair_abc123"}
        ▼
Backend validates & atomically consumes code
        │ returns {"token":"<AGENT_JWT>","username":"alice","role":"AGENT"}
        ▼
AgentConfig.saveUserToken(token)
        │ writes to ~/.contextclip/agent.properties
        ▼
Desktop Agent prints SUCCESS and exits.
Running monitoring instance picks up new token on next restart.
```

---

## 2. Technology Choice

### Selected: **Inno Setup 6**

**Why Inno Setup?**

| Requirement | Inno Setup | jpackage alone |
|-------------|-----------|----------------|
| Custom URI protocol (HKCU registry) | ✅ Native `[Registry]` section | ❌ Not supported |
| Per-user install (no admin) | ✅ `PrivilegesRequired=lowest` | ⚠️ Complex |
| Script in source control | ✅ `.iss` is plain text | ✅ CLI args |
| Startup folder management | ✅ Pascal code section | ❌ Not supported |
| Upgrade/uninstall handling | ✅ Built-in versioned AppId | ✅ Limited |
| Self-contained JRE bundle | ⚠️ External (needs runtime copy) | ✅ jlink integration |
| Free, no license required | ✅ Yes | ✅ Yes (JDK tool) |

**Why not jpackage alone?**
- `jpackage` cannot register Windows custom URI protocol handlers natively
- Bundling a self-contained JRE via `jlink` requires `module-info.java` declarations that the current agent project does not have
- Inno Setup is simpler, widely used, and production-proven for this exact pattern

**Why not WiX?**
- WiX is significantly more complex (XML-heavy, requires .NET toolchain)
- Inno Setup produces equivalent results with far less configuration

### Installer Files

| File | Purpose |
|------|---------|
| `installer/ContextClipDesktop.iss` | Inno Setup script (source of truth for installer) |
| `installer/ContextClipLauncher.cmd` | Protocol dispatch launcher registered with Windows |
| `installer/build-installer.ps1` | PowerShell build driver |
| `installer/dist/` | Staging area for built JAR (gitignored) |
| `installer/output/` | Final installer EXE output (gitignored) |

---

## 3. Installer Build Instructions

### Prerequisites

- **Java 11+** in `PATH` (tested: Temurin JDK 25)
- **Apache Maven** in `PATH`
- **Inno Setup 6** installed — download free from https://jrsoftware.org/isdl.php

### Build Command

```powershell
cd installer
.\build-installer.ps1
```

This script:
1. Runs `mvn clean package -DskipTests` in `desktop-agent/`
2. Copies the shaded JAR to `installer/dist/`
3. Detects `iscc.exe` (Inno Setup compiler)
4. Compiles `ContextClipDesktop.iss` → `installer/output/ContextClipDesktopSetup.exe`

### Build Options

```powershell
# Skip Maven build (use existing target JAR):
.\build-installer.ps1 -SkipJarBuild

# Build JAR only, skip Inno Setup compilation:
.\build-installer.ps1 -SkipInnoSetup
```

### If Inno Setup is Not Installed

The script will print download instructions and stage the JAR. You can then:
1. Install Inno Setup from https://jrsoftware.org/isdl.php
2. Re-run: `.\build-installer.ps1 -SkipJarBuild`

---

## 4. Installation Process

1. Run `ContextClipDesktopSetup.exe`
2. Accept the license agreement
3. Choose installation directory (default: `%LOCALAPPDATA%\Programs\ContextClip Desktop`)
4. Optional: check **"Start ContextClip Desktop Agent automatically when I log in"**
5. Click Install
6. Optional: check **"Launch ContextClip Desktop Agent now"** on the finish page

After installation:
- The Desktop Agent JAR is installed in the chosen directory
- `contextclip://` is registered as a per-user Windows URI protocol (no admin required)
- Start Menu shortcuts are created under `ContextClip Desktop`

---

## 5. contextclip:// Protocol Registration

The installer registers the following Windows registry keys under `HKEY_CURRENT_USER` (no administrator privileges required):

```
HKEY_CURRENT_USER\Software\Classes\contextclip
  (Default)    = "URL:ContextClip Protocol"
  URL Protocol = ""

HKEY_CURRENT_USER\Software\Classes\contextclip\DefaultIcon
  (Default)    = "<install dir>\ContextClipLauncher.cmd,0"

HKEY_CURRENT_USER\Software\Classes\contextclip\shell\open\command
  (Default)    = "\"<install dir>\ContextClipLauncher.cmd\" \"%1\""
```

When Windows receives a `contextclip://` navigation (from any browser), it:
1. Looks up `HKCU\Software\Classes\contextclip\shell\open\command`
2. Invokes: `"<install dir>\ContextClipLauncher.cmd" "contextclip://pair?code=pair_..."`
3. The launcher locates `javaw` and calls: `start "" /d "<install dir>" javaw -jar desktop-agent-0.0.1-SNAPSHOT.jar "contextclip://pair?code=pair_..."`
4. `ClipboardAgentApplication` routes to `PairingHandler`, exchanges the pairing code, saves the token, and transitions into clipboard monitoring in the background.

### Verify Protocol Registration (PowerShell)

```powershell
Get-ItemProperty "HKCU:\Software\Classes\contextclip" | Select-Object "(default)"
Get-ItemProperty "HKCU:\Software\Classes\contextclip\shell\open\command" | Select-Object "(default)"
```

### Manual Protocol Test (Without Browser)

```powershell
Start-Process "contextclip://pair?code=pair_test123"
```

---

## 6. Pairing Flow (End-to-End)

### After Installation

1. Open ContextClip in your browser and log in
2. Navigate to **Settings → Connect Desktop** (or click the Connect Agent link)
3. Click **[ Connect Desktop ]**
4. The frontend calls `POST /api/agent/pairing` (authenticated) → receives a short-lived pairing code
5. The frontend dispatches `contextclip://pair?code=pair_abc123...`
6. Windows looks up the registered protocol handler and invokes `ContextClipLauncher.cmd`
7. The launcher starts the Desktop Agent with the URI as an argument
8. The Desktop Agent calls `POST /api/agent/pairing/exchange` with `{"code":"pair_abc123..."}`
9. The backend validates and atomically consumes the code → returns an AGENT JWT
10. The Desktop Agent saves the token to `%USERPROFILE%\.contextclip\agent.properties`
11. The Desktop Agent prints `SUCCESS: Desktop agent successfully paired for user: <username>` and exits

### Start the Monitoring Agent

After pairing, launch the agent for clipboard monitoring:

**From Start Menu:** ContextClip Desktop → ContextClip Desktop Agent

**From PowerShell:**
```powershell
cd "<install dir>"
java -jar desktop-agent-0.0.1-SNAPSHOT.jar
```

The agent reads the saved token from `agent.properties` and starts monitoring automatically.

---

## 7. Startup Behavior

### Installer-Managed Startup

During installation, if you check **"Start ContextClip Desktop Agent automatically when I log in"**, the installer writes:

```
%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\ContextClipAgent.bat
```

Content:
```batch
@echo off
start "" javaw -jar "<install dir>\desktop-agent-0.0.1-SNAPSHOT.jar"
```

This runs the agent in the background at every Windows login.

### CLI Startup Management

You can also control startup manually using the existing CLI commands:

```powershell
# Enable startup (same startup folder entry as installer option):
java -jar desktop-agent-0.0.1-SNAPSHOT.jar --install-startup

# Disable startup:
java -jar desktop-agent-0.0.1-SNAPSHOT.jar --uninstall-startup
```

### Startup vs. Service

The agent runs as a **user-level process** (not a Windows service), because:
- It reads `%USERPROFILE%\.contextclip\agent.properties` — a user-specific file
- It interacts with the user's Windows clipboard via AWT
- The system tray icon is displayed in the user's desktop session
- Running as `SYSTEM` would have no access to the user's clipboard

---

## 8. Upgrade Behavior

Re-running `ContextClipDesktopSetup.exe` on a machine with ContextClip Desktop already installed:

1. Inno Setup detects the existing installation via `AppId` GUID
2. Prompts the user to upgrade
3. Replaces application files (JAR, launcher script)
4. Registry keys are updated to point to the new installation directory
5. Startup entry (if present) is updated with the new JAR path
6. User configuration (`agent.properties`) is **not touched**

---

## 9. Uninstall Behavior

Via **Settings → Apps → ContextClip Desktop → Uninstall** (or Inno Setup uninstaller):

| Item | Action |
|------|--------|
| Application files (JAR, launcher) | Removed |
| `contextclip://` registry keys (HKCU) | Removed |
| Start Menu shortcuts | Removed |
| Startup entry (`ContextClipAgent.bat`) | Removed if present |
| `%USERPROFILE%\.contextclip\agent.properties` | **Preserved** (user data) |
| `%USERPROFILE%\.contextclip\` directory | **Preserved** (user data) |

The user's pairing token and API configuration are intentionally preserved across uninstall and reinstall. This matches standard desktop application conventions (e.g., VS Code, Slack, etc.).

---

## 10. Security Model

### What the Installer Does (and Does NOT Do)

✅ Installs application files  
✅ Registers `contextclip://` under HKCU  
✅ Optionally writes a per-user startup entry  
❌ Does NOT store any JWT, token, or credential  
❌ Does NOT set AGENT_TOKEN in environment variables or registry  
❌ Does NOT hardcode any user credentials  
❌ Does NOT require administrator privileges  

### URI Protocol Security

- The `contextclip://` URI contains only a **short-lived single-use pairing code** (`pair_...`)
- Pairing codes expire after **5 minutes** and are **atomically consumed** (single-use enforced at backend)
- The pairing code is **never** a long-lived JWT
- Even if a malicious application intercepts the protocol URI, the code is single-use and expires

### Input Validation

The Desktop Agent validates all incoming protocol URIs:

```java
// In PairingHandler.extractCode():
if (uriOrCode == null || uriOrCode.isBlank()) return null;
// Must begin with pair_
if (code == null || code.isBlank() || !code.startsWith("pair_")) {
    return PairingResult(false, null, "Invalid pairing code...");
}
```

Malformed URIs and codes with wrong prefixes are rejected without crashing the agent.

### Zero Secret Logging

- Raw pairing codes are **never** written to logs or stdout
- JWTs are **never** logged — only token length, claims subject, and role are reported
- The complete `contextclip://` URI is **never** logged

---

## 11. CLI Fallback (Always Available)

If the `contextclip://` protocol handler is not registered (e.g., JAR used directly without installer), or if the browser fails to launch the protocol, the web UI displays a **fallback panel** with the pairing code and the CLI command:

```powershell
java -jar desktop-agent-0.0.1-SNAPSHOT.jar --pair "pair_abc123..."
```

All existing CLI commands remain fully functional:

| Command | Purpose |
|---------|---------|
| `java -jar desktop-agent-0.0.1-SNAPSHOT.jar` | Run agent (reads from agent.properties) |
| `--pair <URI_or_CODE>` | Exchange pairing code, save token |
| `--set-token <JWT>` | Manually set AGENT token |
| `--diagnose-auth` | Inspect token, role, subject, backend URL |
| `--install-startup` | Add startup entry to Windows Startup folder |
| `--uninstall-startup` | Remove startup entry |

---

## 12. Known Limitations

### Single-Instance Behavior (Phase 15)

If the Desktop Agent is already running (monitoring clipboard) and the user clicks **Connect Desktop** in the browser:

1. Windows launches a **second JVM instance** to handle the `contextclip://` URI
2. The second instance exchanges the pairing code and saves the new token to `agent.properties`
3. The second instance exits after pairing
4. The **running monitoring instance** continues using its in-memory token (unchanged)
5. To apply the new pairing, the user must **restart the tray agent**

This is safe because:
- `Properties.store()` is a complete atomic file write
- The running instance is unaffected
- No clipboard data is lost during the brief overlap

**Workaround:** After pairing, right-click the ContextClip tray icon → **Exit ContextClip Agent**, then relaunch.

**Future:** Phase 16 will implement single-instance IPC (Windows named pipe) so protocol invocations are forwarded to the running instance directly.

### Bundled JRE (Phase 15)

The installer does **not** bundle a Java runtime. Java (JDK 11+) must be installed and in `PATH`. This requirement will be eliminated in Phase 16 by bundling a JRE via `jpackage --runtime-image`.

### Brief cmd.exe Window (Phase 15)

Because the protocol handler invokes `cmd.exe /c ContextClipLauncher.cmd`, a very brief console window may appear when the protocol is triggered. `start ""` in the launcher suppresses the persistent window. This will be eliminated in Phase 16 with a native `.exe` launcher.

---

## 13. Troubleshooting

### "contextclip:// is not registered" or Browser shows "No application found"

**Cause:** Protocol not registered (agent not installed via installer, or installed as different user).

**Fix:**
```powershell
# Verify registry entry:
Get-Item "HKCU:\Software\Classes\contextclip" -ErrorAction SilentlyContinue

# If missing, re-run the installer or register manually:
$installDir = "C:\Users\<you>\AppData\Local\Programs\ContextClip Desktop"
$cmd = "`"$installDir\ContextClipLauncher.cmd`" `"%1`""
New-Item -Path "HKCU:\Software\Classes\contextclip" -Force | Set-ItemProperty -Name "(Default)" -Value "URL:ContextClip Protocol"
New-ItemProperty -Path "HKCU:\Software\Classes\contextclip" -Name "URL Protocol" -Value "" -Force
New-Item -Path "HKCU:\Software\Classes\contextclip\shell\open\command" -Force | Set-ItemProperty -Name "(Default)" -Value $cmd
```

### Pairing fails: "Pairing code has expired or has already been used"

**Cause:** The 5-minute TTL elapsed, or the code was already exchanged.

**Fix:** Click **Connect Desktop** again to generate a fresh code.

### Agent starts but shows "Unconfigured (Pairing required)"

**Cause:** `agent.properties` does not contain a valid token.

**Fix:** Complete the pairing flow (browser → Connect Desktop → Connect Desktop button), or run:
```powershell
java -jar "desktop-agent-0.0.1-SNAPSHOT.jar" --diagnose-auth
```

### `javaw` not found when agent starts from protocol handler

**Cause:** Java is not in `PATH` for the user session.

**Fix:** Ensure Java (JDK 11+) is installed and `JAVA_HOME\bin` is in your user `PATH`. Or reinstall using a bundled JRE installer (Phase 16).

### Startup agent doesn't run at login

**Cause:** Startup entry missing or Java not in PATH at login time.

**Fix:**
```powershell
java -jar "desktop-agent-0.0.1-SNAPSHOT.jar" --install-startup
# Then log out and log back in.
```

---

## 14. Manual Verification Tests

### TEST 1 — Existing CLI (auth diagnostic)
```powershell
cd desktop-agent
java -jar target\desktop-agent-0.0.1-SNAPSHOT.jar --diagnose-auth
```
Expected: Shows token source, JWT parts, role, subject (or "none" if unconfigured).

### TEST 2 — Existing --pair (manual pairing)
1. Log into ContextClip in a browser
2. Navigate to Connect Desktop
3. Click Connect Desktop to generate a pairing code
4. Copy the pairing code shown in the fallback panel (e.g. `pair_abc123...`)
5. Run:
```powershell
java -jar target\desktop-agent-0.0.1-SNAPSHOT.jar --pair "pair_abc123..."
```
Expected: `SUCCESS: Desktop agent successfully paired for user: <username>`

### TEST 3 — Installed protocol (after running ContextClipDesktopSetup.exe)
1. Install via `installer\output\ContextClipDesktopSetup.exe`
2. Verify registry:
```powershell
Get-Item "HKCU:\Software\Classes\contextclip\shell\open\command"
```
3. Log into ContextClip in browser
4. Click Connect Desktop
5. Verify Windows launches the installed ContextClipLauncher.cmd
6. Verify pairing code reaches the Desktop Agent (check `agent.properties`)
7. Run `--diagnose-auth` to verify JWT role=AGENT and correct subject

### TEST 4 — Expired code
1. Generate a pairing code (Click Connect Desktop)
2. Wait 5+ minutes
3. Try to use the code: `--pair "<expired_code>"`
4. Expected: "Pairing code has expired or has already been used."

### TEST 5 — Reused code
1. Successfully pair with a code
2. Immediately try to pair again with the same code
3. Expected: Rejection with expired/consumed error

### TEST 6 — Malformed URI
```powershell
java -jar target\desktop-agent-0.0.1-SNAPSHOT.jar "contextclip://pair?code=invalid_prefix_no_pair"
java -jar target\desktop-agent-0.0.1-SNAPSHOT.jar "contextclip://pair?code="
java -jar target\desktop-agent-0.0.1-SNAPSHOT.jar "not_a_uri"
```
Expected: Each prints a clear error message and exits without crashing.

### TEST 7 — Startup
1. Run `--install-startup` or check the startup box during install
2. Log out of Windows and log back in
3. Verify `ContextClipAgent.bat` ran (check for agent process in Task Manager)

### TEST 8 — Clipboard capture
1. Pair successfully (TEST 2 or 3)
2. Launch agent: `java -jar target\desktop-agent-0.0.1-SNAPSHOT.jar`
3. Copy any text to clipboard
4. Check ContextClip dashboard → Clipboard History → new entry appears

### TEST 9 — User isolation
1. Pair as user A → verify captures appear in user A's history
2. Pair as user B (same machine) → captures now go to user B
3. Verify user A's captures are not visible to user B

### TEST 10 — Existing workflows
Verify all CLI commands continue to function:
```powershell
java -jar target\desktop-agent-0.0.1-SNAPSHOT.jar --diagnose-auth
java -jar target\desktop-agent-0.0.1-SNAPSHOT.jar --set-token <JWT>
java -jar target\desktop-agent-0.0.1-SNAPSHOT.jar --pair <CODE>
java -jar target\desktop-agent-0.0.1-SNAPSHOT.jar --install-startup
java -jar target\desktop-agent-0.0.1-SNAPSHOT.jar --uninstall-startup
```
