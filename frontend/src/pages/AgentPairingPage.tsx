import React, { useState } from 'react';
import { getAgentToken, createPairingCode } from '../api/clipboardApi';
import {
  MonitorSmartphone,
  KeyRound,
  Eye,
  EyeOff,
  Copy,
  Check,
  ChevronRight,
  ShieldCheck,
  AlertTriangle,
  Terminal,
  Sparkles,
  ExternalLink,
  Laptop,
  CheckCircle2,
} from 'lucide-react';

export const AgentPairingPage: React.FC = () => {
  const [generating, setGenerating] = useState(false);
  const [token, setToken] = useState<string | null>(null);
  const [tokenUsername, setTokenUsername] = useState<string | null>(null);
  const [showToken, setShowToken] = useState(false);
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Commercial One-Click Pairing State
  const [pairingLoading, setPairingLoading] = useState(false);
  const [pairingError, setPairingError] = useState<string | null>(null);
  const [pairingCode, setPairingCode] = useState<string | null>(null);
  const [pairUrl, setPairUrl] = useState<string | null>(null);
  const [pairingLaunched, setPairingLaunched] = useState(false);
  const [copiedCode, setCopiedCode] = useState(false);

  const handleConnectDesktop = async () => {
    setPairingLoading(true);
    setPairingError(null);
    try {
      const res = await createPairingCode();
      setPairingCode(res.code);
      setPairUrl(res.pairUrl);
      setPairingLaunched(true);

      // Attempt to launch desktop application via protocol handler
      try {
        if (typeof window !== 'undefined' && window.location) {
          window.location.assign(res.pairUrl);
        }
      } catch {
        // Fallback handled smoothly by UI guidance
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to generate pairing code. Please try again.';
      setPairingError(msg);
    } finally {
      setPairingLoading(false);
    }
  };

  const handleCopyPairingCode = async () => {
    if (!pairingCode) return;
    try {
      await navigator.clipboard.writeText(pairingCode);
      setCopiedCode(true);
      setTimeout(() => setCopiedCode(false), 2500);
    } catch {
      // ignore
    }
  };

  const handleGenerateToken = async () => {
    setGenerating(true);
    setError(null);
    setToken(null);
    setShowToken(false);
    setCopied(false);
    try {
      const result = await getAgentToken();
      setToken(result.token);
      setTokenUsername(result.username);
    } catch (err: unknown) {
      const msg =
        err instanceof Error
          ? err.message
          : 'Failed to generate agent token. Please try again.';
      setError(msg);
    } finally {
      setGenerating(false);
    }
  };

  const handleCopyToken = async () => {
    if (!token) return;
    try {
      await navigator.clipboard.writeText(token);
      setCopied(true);
      setTimeout(() => setCopied(false), 2500);
    } catch {
      // fallback: select text in input
    }
  };

  const maskedToken = token
    ? token.slice(0, 12) + '•'.repeat(Math.max(0, token.length - 24)) + token.slice(-12)
    : '';

  return (
    <div className="main-wrapper">
      <header className="top-header">
        <div className="page-title-group">
          <h1>Connect Your Desktop</h1>
          <p>Install the ContextClip Desktop Agent and pair this device for automatic clipboard capture</p>
        </div>
      </header>

      <main className="content-container">
        {/* Commercial One-Click Connect Banner */}
        <div className="agent-pairing-card commercial-hero-card" data-testid="connect-desktop-hero">
          <div className="agent-pairing-header">
            <div className="agent-pairing-icon" style={{ background: 'rgba(59, 130, 246, 0.1)', color: '#3B82F6' }}>
              <Laptop size={22} />
            </div>
            <div style={{ flex: 1 }}>
              <h2 className="agent-pairing-title">One-Click Desktop Pairing</h2>
              <p className="agent-pairing-subtitle">
                Pair your desktop agent directly from this browser without copying or pasting credentials.
              </p>
            </div>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
              <button
                className="btn-primary"
                onClick={handleConnectDesktop}
                disabled={pairingLoading}
                data-testid="connect-desktop-btn"
                style={{ height: 38, padding: '0 20px', fontSize: 13.5, fontWeight: 600 }}
              >
                {pairingLoading ? (
                  <>
                    <span className="btn-spinner" aria-hidden="true" />
                    Connecting…
                  </>
                ) : (
                  <>
                    <ExternalLink size={15} />
                    Connect Desktop
                  </>
                )}
              </button>

              {pairingLaunched && (
                <div style={{ display: 'inline-flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
                  <span style={{ fontSize: 13, color: 'var(--text-muted)', display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                    <CheckCircle2 size={14} style={{ color: 'var(--color-success)' }} />
                    Pairing request dispatched to <code className="inline-code">contextclip://</code>
                  </span>
                  {pairUrl && (
                    <a
                      href={pairUrl}
                      className="btn-secondary"
                      style={{ height: 32, padding: '0 12px', fontSize: 12, display: 'inline-flex', alignItems: 'center', gap: 5, textDecoration: 'none' }}
                      data-testid="relaunch-pairing-link"
                    >
                      <ExternalLink size={13} />
                      Retry Launch Protocol
                    </a>
                  )}
                </div>
              )}
            </div>

            {pairingError && (
              <div className="agent-error" role="alert" data-testid="pairing-error-message">
                <AlertTriangle size={14} />
                <span>{pairingError}</span>
              </div>
            )}

            {pairingLaunched && pairingCode && (
              <div className="agent-token-box" style={{ background: 'var(--bg-subtle)', border: '1px solid var(--border-subtle)', marginTop: 8 }}>
                <div className="agent-token-meta" style={{ color: 'var(--text-secondary)' }}>
                  <ShieldCheck size={14} className="agent-token-meta-icon" />
                  <span>
                    Short-Lived Pairing Code (valid for 5 minutes, single use)
                  </span>
                </div>
                <div className="agent-token-row">
                  <code className="agent-token-value" style={{ color: '#3B82F6' }} data-testid="pairing-code-value">
                    {pairingCode}
                  </code>
                  <button
                    className="btn-icon"
                    onClick={handleCopyPairingCode}
                    aria-label="Copy pairing code"
                    title="Copy pairing code"
                    data-testid="copy-pairing-code-btn"
                  >
                    {copiedCode ? (
                      <Check size={15} style={{ color: 'var(--color-success)' }} />
                    ) : (
                      <Copy size={15} />
                    )}
                  </button>
                </div>
                <p className="agent-token-caution" style={{ marginTop: 8, color: 'var(--text-muted)', fontSize: 12 }}>
                  If your browser did not automatically open the desktop agent, run the agent with:
                  <br />
                  <code className="inline-code" style={{ marginTop: 4, display: 'inline-block' }}>
                    java -jar desktop-agent-0.0.1-SNAPSHOT.jar --pair {pairingCode}
                  </code>
                </p>
              </div>
            )}
          </div>
        </div>

        {/* Pairing Workflow Card */}
        <div className="agent-pairing-card">
          <div className="agent-pairing-header">
            <div className="agent-pairing-icon">
              <MonitorSmartphone size={22} />
            </div>
            <div>
              <h2 className="agent-pairing-title">Device Pairing &amp; Setup (Manual / CLI Fallback)</h2>
              <p className="agent-pairing-subtitle">
                ContextClip uses a personal pairing token to securely associate background clipboard
                captures with your personal account.
              </p>
            </div>
          </div>

          <div className="agent-pairing-warning">
            <AlertTriangle size={15} />
            <span>
              <strong>Device-to-Account Pairing:</strong> Each pairing token is bound to your username.
              Captures made by the desktop agent will only appear in your account. If multiple users share
              this machine, re-pairing associates future captures with the new active user.
            </span>
          </div>

          {/* Step 1: Generate token */}
          <div className="agent-step">
            <div className="agent-step-num">1</div>
            <div className="agent-step-body">
              <h3 className="agent-step-title">Generate Your Personal Pairing Token</h3>
              <p className="agent-step-desc">
                Generate an authenticated personal pairing token for this device. This authorizes your local
                agent to securely synchronize captures with your account.
              </p>
              <button
                className="btn-primary agent-generate-btn"
                onClick={handleGenerateToken}
                disabled={generating}
                data-testid="generate-agent-token-btn"
              >
                {generating ? (
                  <>
                    <span className="btn-spinner" aria-hidden="true" />
                    Generating…
                  </>
                ) : (
                  <>
                    <KeyRound size={15} />
                    Generate Token
                    <ChevronRight size={14} />
                  </>
                )}
              </button>

              {error && (
                <div className="agent-error" role="alert">
                  <AlertTriangle size={14} />
                  <span>{error}</span>
                </div>
              )}

              {token && (
                <div className="agent-token-box" data-testid="agent-token-display">
                  <div className="agent-token-meta">
                    <ShieldCheck size={14} className="agent-token-meta-icon" />
                    <span>
                      Personal Pairing Token for <strong>{tokenUsername}</strong>
                    </span>
                  </div>
                  <div className="agent-token-row">
                    <code className="agent-token-value" data-testid="agent-token-value">
                      {showToken ? token : maskedToken}
                    </code>
                    <div className="agent-token-actions">
                      <button
                        className="btn-icon"
                        onClick={() => setShowToken((v) => !v)}
                        aria-label={showToken ? 'Hide token' : 'Reveal token'}
                        title={showToken ? 'Hide token' : 'Reveal token'}
                      >
                        {showToken ? <EyeOff size={15} /> : <Eye size={15} />}
                      </button>
                      <button
                        className="btn-icon"
                        onClick={handleCopyToken}
                        aria-label="Copy token to clipboard"
                        title="Copy token"
                        data-testid="copy-agent-token-btn"
                      >
                        {copied ? (
                          <Check size={15} style={{ color: 'var(--color-success)' }} />
                        ) : (
                          <Copy size={15} />
                        )}
                      </button>
                    </div>
                  </div>
                  <p className="agent-token-caution">
                    Keep this token secure. It serves as your personal authentication key for this device.
                  </p>
                </div>
              )}
            </div>
          </div>

          {/* Step 2: Configure agent */}
          <div className="agent-step">
            <div className="agent-step-num">2</div>
            <div className="agent-step-body">
              <h3 className="agent-step-title">Configure the Desktop Agent (Current MVP)</h3>
              <p className="agent-step-desc">
                Save your pairing token into the desktop agent's configuration using the command line:
              </p>
              <div className="agent-cmd-block">
                <Terminal size={14} className="agent-cmd-icon" />
                <code className="agent-cmd-text">
                  java -jar contextclip-agent.jar --set-token YOUR_TOKEN_HERE
                </code>
              </div>
              <p className="agent-step-desc" style={{ marginTop: 8 }}>
                The agent securely stores this token in your local profile at{' '}
                <code className="inline-code">%USERPROFILE%\.contextclip\agent.properties</code>.
              </p>
            </div>
          </div>

          {/* Step 3: Start and verify */}
          <div className="agent-step">
            <div className="agent-step-num">3</div>
            <div className="agent-step-body">
              <h3 className="agent-step-title">Start Automatic Clipboard Capture</h3>
              <p className="agent-step-desc">
                Launch the desktop agent. Copy any text, code snippet, or command. Then open{' '}
                <strong>Clipboard History</strong> to see captures appearing in real time.
              </p>
              <div className="agent-step-security-note">
                <ShieldCheck size={13} />
                <span>
                  All clipboard entries remain strictly isolated to your account. No other user can see,
                  search, or access your captures.
                </span>
              </div>
            </div>
          </div>
        </div>

        {/* Roadmap: MVP vs Commercial Onboarding */}
        <div className="agent-roadmap-card">
          <div className="agent-roadmap-header">
            <Sparkles size={18} className="agent-roadmap-icon" />
            <div>
              <h3 className="agent-roadmap-title">Onboarding Roadmap: MVP vs Commercial Experience</h3>
              <p className="agent-roadmap-subtitle">
                How ContextClip transitions from developer-oriented CLI pairing to consumer-grade desktop onboarding.
              </p>
            </div>
          </div>

          <div className="agent-comparison-grid">
            <div className="agent-comparison-col current-mvp">
              <div className="comparison-badge mvp">Current MVP</div>
              <h4 className="comparison-title">Developer CLI Pairing</h4>
              <ul className="comparison-list">
                <li>Web app generates personal pairing token on demand</li>
                <li>User runs <code className="inline-code">--set-token</code> via command line</li>
                <li>Agent stores token in <code className="inline-code">~/.contextclip/agent.properties</code></li>
                <li>Offline local buffering &amp; automatic reconnection</li>
                <li>System tray menu with Pause / Resume monitoring controls</li>
              </ul>
            </div>

            <div className="agent-comparison-col future-commercial">
              <div className="comparison-badge commercial">Future Commercial UX</div>
              <h4 className="comparison-title">Consumer Desktop App</h4>
              <ul className="comparison-list">
                <li>1. Download one-click installer (<code className="inline-code">.msi</code> / <code className="inline-code">.dmg</code>)</li>
                <li>2. Automatic system background service registration</li>
                <li>3. One-click browser sign-in &amp; secure device authorization</li>
                <li>4. Starts automatically with OS login</li>
                <li>5. Seamless background capture with smart privacy filters</li>
                <li>6. Native tray quick-search and status widget</li>
              </ul>
            </div>
          </div>
        </div>

        {/* Account switching card */}
        <div className="agent-info-card" style={{ marginTop: 20 }}>
          <h3 className="agent-info-title">Switching Accounts on the Same PC</h3>
          <p className="agent-info-desc">
            Because the desktop agent stores the pairing token locally, it remains paired to the configured
            user even if a different user signs into the web application. To switch accounts:
          </p>
          <ol className="agent-info-list">
            <li>Sign in as the target user in the web application</li>
            <li>Generate a new pairing token on this page</li>
            <li>Run <code className="inline-code">java -jar contextclip-agent.jar --set-token &lt;NEW_TOKEN&gt;</code></li>
            <li>Restart or resume monitoring from the system tray</li>
          </ol>
        </div>
      </main>
    </div>
  );
};
