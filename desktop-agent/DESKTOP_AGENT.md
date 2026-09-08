# ContextClip Desktop Agent

The ContextClip Desktop Agent is a lightweight Java-based clipboard capture utility that securely monitors developer clipboards, automatically transmits code snippets to the user's personal ContextClip library, and provides seamless control via the Windows System Tray.

---

## 1. Architecture Overview

```text
                  +-------------------------------+
                  |       Operating System        |
                  |        Windows Clipboard      |
                  +---------------+---------------+
                                  | AWT FlavorListener
                                  v
                    +---------------------------+
                    |     ClipboardMonitor      |
                    | (Pause/Resume/OS Debounce)|
                    +-------------+-------------+
                                  | New Text Detected
                                  v
                    +---------------------------+
                    |  ClipboardAgentApplication|
                    |  (Lifecycle / Routing)    |
                    +-------------+-------------+
                                  |
                 +----------------+----------------+
                 v                                 v
    +--------------------------+    +-------------------------+
    |        TrayManager       |    |      BackendClient      |
    |  - System Tray Icon      |    |  - Personal JWT Auth    |
    |  - Status Display        |    |  - Offline Buffer (100) |
    |  - Pause / Resume        |    |  - Reconnect Auto-Flush |
    |  - Exit                  |    |  - Error Classification |
    +--------------------------+    +-------------+-----------+
                                                  | HTTP POST
                                                  v
                                    +-------------------------+
                                    |    ContextClip Backend  |
                                    |   POST /api/clipboard   |
                                    +-------------------------+
```
`

### Core Components
- **ClipboardAgentApplication**: Entrypoint for CLI flag handling (--set-token, --install-startup, etc.) and runtime initialization.
- **AgentConfig**: Hierarchical configuration provider with secure JWT validation and sanitization.
- **ClipboardMonitor**: Synchronized clipboard observer with thread-safe pause/resume and lock contention retries.
- **TrayManager**: Windows system tray integration displaying connection and pause states, complete with headless fallback.
- **BackendClient**: Resilient HTTP client managing JWT-authenticated requests, offline queuing (up to 100 entries), and error classification.

---

## 2. Pairing Workflows

### Current MVP / Developer Flow (Active)
The MVP architecture uses CLI-based token pairing.

1. **Obtain Personal Token**:
   - The user logs in to ContextClip via browser.
   - Navigate to the **Connect Desktop** / Pairing page (/dashboard/pairing).
   - Copy the generated AGENT_TOKEN (a user-scoped JWT with 
ole=AGENT).

2. **Save Token to Desktop Configuration**:
   `ash
   java -jar desktop-agent-0.0.1-SNAPSHOT.jar --set-token <YOUR_AGENT_TOKEN>
   `
   *This saves the token to %USERPROFILE%\.contextclip\agent.properties (~/.contextclip/agent.properties).*

3. **Verify Configuration**:
   `ash
   java -jar desktop-agent-0.0.1-SNAPSHOT.jar --diagnose-auth
   `

4. **Launch Agent**:
   `ash
   java -jar desktop-agent-0.0.1-SNAPSHOT.jar
   `

---

### Future Commercial UX Roadmap (Planned)
The future consumer/enterprise flow removes all CLI requirements:

1. **One-Click Installer (MSI / EXE)**:
   - Native Windows installer installs bundled runtime and creates start menu entries.
   - Registers custom URI scheme: contextclip://
2. **First Run**:
   - The agent launches into tray with an unconfigured state and opens the browser to login/pair.
3. **Browser Pairing**:
   - In the web dashboard, clicking "Connect Desktop" invokes contextclip://pair?token=<JWT>.
   - The desktop agent captures the URI, saves the token to gent.properties, and transitions to CONNECTED status automatically.
4. **Zero-Touch Startup**:
   - Agent runs silently in the background on Windows boot.

---

## 3. Configuration Reference

The desktop agent evaluates configuration in the following order:

| Priority | Source | Property / Variable | Description |
| :--- | :--- | :--- | :--- |
| **1** | Environment | AGENT_TOKEN | Overrides token via environment variable |
| **2** | Environment | DESKTOP_AGENT_TOKEN | Secondary environment token fallback |
| **3** | File | ~/.contextclip/agent.properties | Primary user persistent configuration (gent.token=...) |
| **4** | File | ./agent.properties | Local working directory configuration |
| **Default** | Embedded | Backend URL: http://localhost:8080/api/clipboard | Target ContextClip backend ingestion endpoint |

---

## 4. CLI Command Reference

| Command | Purpose |
| :--- | :--- |
| java -jar desktop-agent-0.0.1-SNAPSHOT.jar | Run the agent normally (reads config from ~/.contextclip/agent.properties or env) |
| java -jar desktop-agent-0.0.1-SNAPSHOT.jar --set-token <JWT> | Validates syntax and saves token to user config |
| java -jar desktop-agent-0.0.1-SNAPSHOT.jar --diagnose-auth | Inspects token presence, length, claims, and backend URL without logging the JWT |
| java -jar desktop-agent-0.0.1-SNAPSHOT.jar --install-startup | Adds automated startup launcher batch file to Windows Startup folder |
| java -jar desktop-agent-0.0.1-SNAPSHOT.jar --uninstall-startup | Removes automated startup launcher from Windows Startup folder |

---

## 5. Security & Privacy Guarantees

1. **Zero Secret Logging**:
   - Raw JWT strings are **never** logged to stdout, stderr, or log files.
   - Diagnostic output only prints token length, claims subject, and parts count.
2. **Sanitized Clipboard Logging**:
   - Captured clipboard text content is **never** logged to console/logs. Only content length and backend IDs are reported.
3. **Multi-Tenant User Isolation**:
   - All backend ingestion calls are scoped to the authenticated user owning the AGENT_TOKEN.
   - The backend validates that 
ole=AGENT is present and maps clips exclusively to the token subject.
