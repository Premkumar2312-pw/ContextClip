# ContextClip Desktop Agent

The ContextClip Desktop Agent is a lightweight Java-based clipboard capture utility that securely monitors developer clipboards, automatically transmits code snippets to the user's personal ContextClip library, and provides seamless control via the Windows System Tray.

---

## 1. Architecture Overview

`
                  +-------------------------------+
                  |      Operating System         |
                  |       Windows Clipboard       |
                  +---------------+---------------+
                                  | AWT FlavorListener
                                  v
                    +---------------------------+
                    |    ClipboardMonitor       |
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
    |       TrayManager        |    |      BackendClient      |
    |  - System Tray Icon      |    |  - Personal JWT Auth    |
    |  - Status Display        |    |  - Offline Buffer (100) |
    |  - Pause / Resume        |    |  - Reconnect Auto-Flush |
    |  - Exit                  |    |  - Error Classification |
    +--------------------------+    +-------------+-----------+
                                                  | HTTP POST
                                                  v
                                    +-------------------------+
                                    |    ContextClip Backend  |
                                    |  POST /api/clipboard    |
                                    +-------------------------+
`

### Core Components
- **ClipboardAgentApplication**: Entrypoint for CLI flag handling (--set-token, --pair, --install-startup, etc.) and runtime initialization.
- **PairingHandler**: Secure single-use pairing code exchange handler that parses contextclip://pair?code=... URIs and persists the negotiated personal ole=AGENT token.
- **AgentConfig**: Hierarchical configuration provider with secure JWT validation and sanitization.
- **ClipboardMonitor**: Synchronized clipboard observer with thread-safe pause/resume and lock contention retries.
- **TrayManager**: Windows system tray integration displaying connection, pause, and unconfigured states, complete with headless fallback.
- **BackendClient**: Resilient HTTP client managing JWT-authenticated requests, offline queuing (up to 100 entries), and error classification.

---

## 2. Pairing Workflows

### A. Commercial Browser-to-Desktop Pairing Flow (Phase 14 Foundation)
This flow enables seamless pairing directly from the web dashboard:

1. **Initiate Pairing from Dashboard**:
   - User signs into ContextClip in their browser.
   - User navigates to **Connect Desktop** and clicks **[ Connect Desktop ]**.
2. **Short-Lived Pairing Code Generation**:
   - The frontend calls POST /api/agent/pairing (authenticated via Web JWT).
   - Backend issues a cryptographically secure, 5-minute single-use pairing code (pair_...).
3. **Browser Protocol Launch**:
   - The browser dispatches contextclip://pair?code=pair_....
4. **Desktop Agent Exchange**:
   - When launched or invoked with contextclip://pair?code=... (or CLI java -jar desktop-agent.jar --pair <URI_or_code>), PairingHandler exchanges the code with POST /api/agent/pairing/exchange.
   - The backend atomically consumes the code (enforcing single-use) and returns a user-scoped ole=AGENT token.
5. **Secure Persistence & Monitoring**:
   - The agent writes the token into ~/.contextclip/agent.properties and enters the CONNECTED state.

> **Phase 15 — Windows Installer & Protocol Registration (Complete)**:
> The `contextclip://` URI protocol is now registered with Windows by the commercial installer (`installer/ContextClipDesktopSetup.exe`). When a user clicks **Connect Desktop** in the browser, Windows dispatches `contextclip://pair?code=pair_...` directly to the installed Desktop Agent via the registered protocol handler. See [`docs/WINDOWS_INSTALLER.md`](../docs/WINDOWS_INSTALLER.md) for the full architecture.
>
> For development and testing without the installer, the CLI fallback remains fully supported:
> ```bash
> java -jar target/desktop-agent-0.0.1-SNAPSHOT.jar --pair "contextclip://pair?code=pair_..."
> ```

---

### B. Developer / MVP Flow (Backward Compatible)
The manual token configuration remains fully supported for headless environments or manual developer scripting:

1. **Obtain Personal Token**:
   - In **Connect Desktop**, expand the manual pairing panel and click **Generate Token**.
2. **Save Token via CLI**:
   `ash
   java -jar desktop-agent-0.0.1-SNAPSHOT.jar --set-token <YOUR_AGENT_TOKEN>
   `
3. **Verify Configuration**:
   `ash
   java -jar desktop-agent-0.0.1-SNAPSHOT.jar --diagnose-auth
   `
4. **Launch Agent**:
   `ash
   java -jar desktop-agent-0.0.1-SNAPSHOT.jar
   `

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
| java -jar desktop-agent-0.0.1-SNAPSHOT.jar --pair <URI_or_CODE> | Exchanges single-use pairing code with backend and configures agent automatically |
| java -jar desktop-agent-0.0.1-SNAPSHOT.jar --set-token <JWT> | Validates syntax and saves token to user config |
| java -jar desktop-agent-0.0.1-SNAPSHOT.jar --diagnose-auth | Inspects token presence, length, claims, and backend URL without logging the JWT |
| java -jar desktop-agent-0.0.1-SNAPSHOT.jar --install-startup | Adds automated startup launcher batch file to Windows Startup folder |
| java -jar desktop-agent-0.0.1-SNAPSHOT.jar --uninstall-startup | Removes automated startup launcher from Windows Startup folder |

---

## 5. Security & Privacy Guarantees

1. **Short-Lived, Single-Use Pairing Credentials**:
   - Pairing codes expire after 5 minutes and can only be consumed once.
   - Long-lived JWT tokens are never exposed in browser URLs or protocol parameters.
2. **Zero Secret Logging**:
   - Raw JWT strings and pairing codes are **never** logged to stdout, stderr, or log files.
   - Diagnostic output only prints token length, claims subject, and parts count.
3. **Sanitized Clipboard Logging**:
   - Captured clipboard text content is **never** logged to console/logs. Only content length and backend IDs are reported.
4. **Multi-Tenant User Isolation**:
   - All backend ingestion calls and pairing exchanges are strictly user-scoped.
   - Cross-user pairing is strictly prevented; tokens issued correspond exclusively to the user who requested the pairing code.