import React, { useCallback, useEffect, useState } from 'react';
import { ClipboardEntry } from '../types/clipboard';
import { getClipboardEntries } from '../api/clipboardApi';
import { ClipboardItem } from '../components/ClipboardItem';
import { RefreshCw, ClipboardList } from 'lucide-react';
import { ErrorState } from '../components/ErrorState';

export const ClipboardPage: React.FC = () => {
  const [entries, setEntries] = useState<ClipboardEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

  const loadEntries = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await getClipboardEntries();
      setEntries(data);
      setLastUpdated(new Date());
    } catch (err: unknown) {
      const message =
        err instanceof Error
          ? err.message
          : 'Unable to load clipboard entries. Make sure the ContextClip backend is running.';
      setError(message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadEntries();
  }, [loadEntries]);

  const formattedTime = lastUpdated
    ? lastUpdated.toLocaleTimeString([], {
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      })
    : '';

  return (
    <div className="main-wrapper">
      <header className="top-header">
        <div className="page-title-group">
          <h1>Clipboard History</h1>
          <p>Chronological feed of captured snippets, commands, and code</p>
        </div>

        <div className="header-actions">
          {formattedTime && <span className="last-updated">Updated {formattedTime}</span>}
          <button
            className="btn-secondary"
            onClick={loadEntries}
            disabled={loading}
            aria-label="Refresh Clipboard"
          >
            <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
            <span>Refresh</span>
          </button>
        </div>
      </header>

      <main className="content-container">
        {loading && entries.length === 0 && (
          <div aria-label="Loading clipboard entries">
            <div className="skeleton skeleton-row" />
            <div className="skeleton skeleton-row" />
            <div className="skeleton skeleton-row" />
          </div>
        )}

        {error && entries.length === 0 && (
          <ErrorState message={error} onRetry={loadEntries} />
        )}

        {!loading && !error && entries.length === 0 && (
          <div className="state-container">
            <ClipboardList className="state-icon" />
            <h2 className="state-title">No Clipboard Entries Found</h2>
            <p className="state-description">
              Copy content to your clipboard with the ContextClip desktop agent running to view and manage snippets here.
            </p>
            <button className="btn-secondary" onClick={loadEntries}>
              <RefreshCw size={14} style={{ display: 'inline', marginRight: 6 }} />
              Check for New Entries
            </button>
          </div>
        )}

        {entries.length > 0 && (
          <div className="clipboard-list" data-testid="clipboard-items-list">
            <div className="results-header">
              <span className="results-count">
                {entries.length} {entries.length === 1 ? 'entry' : 'entries'} captured
              </span>
            </div>
            {entries.map((entry) => (
              <ClipboardItem key={entry.id} entry={entry} />
            ))}
          </div>
        )}
      </main>
    </div>
  );
};
