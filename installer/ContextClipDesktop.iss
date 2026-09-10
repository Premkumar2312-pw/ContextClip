; ============================================================
; ContextClip Desktop Installer — Inno Setup Script
; Phase 15 — Windows Commercial Desktop Installer
; ============================================================
;
; Build requirements:
;   - Inno Setup 6 (https://jrsoftware.org/isdl.php)
;   - desktop-agent-0.0.1-SNAPSHOT.jar must exist in installer\dist\
;
; Registry approach:
;   - Uses HKCU (HKEY_CURRENT_USER) so no administrator privileges
;     are required for installation or protocol registration.
;
; contextclip:// protocol registration:
;   The protocol handler is registered per-user under:
;     HKEY_CURRENT_USER\Software\Classes\contextclip
;   Windows dispatches contextclip://pair?code=... to:
;     ContextClipLauncher.cmd "%1"
;   which forwards the complete URI to the Desktop Agent JAR.
;
; Security note:
;   The URI contains only a short-lived single-use pairing code
;   (prefix: pair_). Long-lived JWTs are never placed in registry
;   values, URIs, or command-line arguments.
; ============================================================

#define AppName        "ContextClip Desktop"
#define AppVersion     "0.0.1"
#define AppPublisher   "ContextClip"
#define AppURL         "https://contextclip.app"
#define AppExeName     "ContextClipLauncher.cmd"
#define JarName        "desktop-agent-0.0.1-SNAPSHOT.jar"
#define AppGUID        "{{7F2E3B1A-C4D5-4E6F-8A9B-0C1D2E3F4A5B}"

[Setup]
AppId={#AppGUID}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#AppPublisher}
AppPublisherURL={#AppURL}
AppSupportURL={#AppURL}
AppUpdatesURL={#AppURL}
DefaultDirName={userpf}\ContextClip Desktop
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
; Per-user install — no UAC elevation required
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
OutputDir=output
OutputBaseFilename=ContextClipDesktopSetup
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
UninstallDisplayName={#AppName}
; Prevent uninstall from showing as "modified" after cleanup
CloseApplications=yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
; Offer to run agent automatically at Windows login (per-user Startup folder)
Name: "startup"; Description: "Start ContextClip Desktop Agent automatically when I log in"; \
    GroupDescription: "Startup:"; Flags: unchecked

[Files]
; Desktop Agent JAR (shaded, self-contained)
Source: "dist\{#JarName}"; DestDir: "{app}"; Flags: ignoreversion
; Launcher script — registered as the contextclip:// protocol handler
Source: "ContextClipLauncher.cmd"; DestDir: "{app}"; Flags: ignoreversion
; Bundled Java Runtime — private, zero external Java dependency
Source: "dist\runtime\*"; DestDir: "{app}\runtime"; Flags: ignoreversion recursesubdirs createallsubdirs

[Registry]
; =====================================================================
; contextclip:// custom URI protocol registration (HKCU — no admin)
; =====================================================================

; Root key — declare as URL protocol
Root: HKCU; Subkey: "Software\Classes\contextclip"; \
    ValueType: string; ValueName: ""; \
    ValueData: "URL:ContextClip Protocol"; \
    Flags: uninsdeletekey

Root: HKCU; Subkey: "Software\Classes\contextclip"; \
    ValueType: string; ValueName: "URL Protocol"; \
    ValueData: ""

; Default icon (points at our launcher script, icon index 0)
; Note: .cmd files don't carry icons, so Windows uses a generic shell icon.
Root: HKCU; Subkey: "Software\Classes\contextclip\DefaultIcon"; \
    ValueType: string; ValueName: ""; \
    ValueData: "{app}\{#AppExeName},0"

; Shell command — Windows invokes this when contextclip:// is dispatched
; %1 receives the full URI, e.g.: contextclip://pair?code=pair_abc123
Root: HKCU; Subkey: "Software\Classes\contextclip\shell\open\command"; \
    ValueType: string; ValueName: ""; \
    ValueData: """{app}\{#AppExeName}"" ""%1"""

[Icons]
; Optional: Start Menu shortcut to launch the agent directly
Name: "{group}\ContextClip Desktop Agent"; Filename: "{app}\{#AppExeName}"; \
    WorkingDir: "{app}"
Name: "{group}\Uninstall {#AppName}"; Filename: "{uninstallexe}"

[Run]
; After install: offer to launch the agent immediately
Filename: "{app}\{#AppExeName}"; Description: "Launch ContextClip Desktop Agent now"; \
    Flags: postinstall shellexec skipifsilent unchecked

[UninstallRun]
; On uninstall: stop any running agent instances gracefully
; (best-effort — agent may not be running)
Filename: "taskkill"; Parameters: "/F /IM javaw.exe /FI ""WINDOWTITLE eq ContextClipAgent*"""; \
    Flags: runhidden; RunOnceId: "StopAgent"

[Code]
// Pascal script for installer logic

// ----------------------------------------------------------------
// Startup folder management
// ----------------------------------------------------------------
function GetStartupFile: string;
begin
  Result := ExpandConstant('{userstartup}\ContextClipAgent.bat');
end;

procedure WriteStartupEntry;
var
  StartupFile, Content: string;
  LauncherCmd: string;
begin
  StartupFile := GetStartupFile;
  LauncherCmd := ExpandConstant('{app}\{#AppExeName}');
  Content     := '@echo off' + #13#10 +
                 'start "" "' + LauncherCmd + '"' + #13#10;
  SaveStringToFile(StartupFile, Content, False);
end;

procedure DeleteStartupEntry;
var
  StartupFile: string;
begin
  StartupFile := GetStartupFile;
  if FileExists(StartupFile) then
    DeleteFile(StartupFile);
end;

// ----------------------------------------------------------------
// Post-install: write startup entry if task was selected
// ----------------------------------------------------------------
procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssPostInstall then
  begin
    if WizardIsTaskSelected('startup') then
      WriteStartupEntry;
  end;
end;

// ----------------------------------------------------------------
// Uninstall: remove startup entry
// (User's agent.properties / token is intentionally NOT removed)
// ----------------------------------------------------------------
procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if CurUninstallStep = usPostUninstall then
  begin
    DeleteStartupEntry;
    // NOTE: %USERPROFILE%\.contextclip\agent.properties is intentionally
    // preserved. It contains the user's pairing token and API configuration.
    // Deleting it on uninstall would break any re-install or upgrade.
  end;
end;
