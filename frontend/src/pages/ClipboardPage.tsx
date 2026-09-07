import React, { useCallback, useEffect, useState } from 'react';
import { ClipboardEntry } from '../types/clipboard';
import { clearClipboardHistory, getClipboardEntries } from '../api/clipboardApi';
import { ClipboardItem } from '../components/ClipboardItem';
import { DeleteConfirmModal } from '../components/DeleteConfirmModal';
import { RefreshCw, ClipboardList, Trash2, X, Zap } from 'lucide-react';
import { ErrorState } from '../components/ErrorState';

interface ClipboardPageProps {
  targetEntryId?: number | null;
}

export const ClipboardPage: React.FC<ClipboardPageProps> = ({ targetEntryId }) => {
  const [entries, setEntries] = useState<ClipboardEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [clearing, setClearing] = useState(false);
  const [showClearModal, setShowClearModal] = useState(false);
  const [clearError, setClearError] = useState<string | null>(null);
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

  // Handle scrolling and highlighting targetEntryId
  useEffect(() => {
    if (targetEntryId && entries.length > 0) {
      setTimeout(() => {
        const el = document.getElementById(`clipboard-entry-${targetEntryId}`);
        if (el) {
          el.scrollIntoView({ behavior: 'smooth', block: 'center' });
          el.classList.add('entry-highlight');
          setTimeout(() => el.classList.remove('entry-highlight'), 3000);
        }
      }, 100);
    }
  }, [targetEntryId, entries]);

  const handleOpenClearModal = () => {
    setClearError(null);
    setShowClearModal(true);
  };

  const handleConfirmClear = async () => {
    setClearing(true);
    setClearError(null);
    try {
      await clearClipboardHistory();
      setEntries([]);
      setLastUpdated(new Date());
      setShowClearModal(false);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to clear clipboard history.';
      setClearError(msg);
    } finally {
      setClearing(false);
    }
  };

  const handleDeleteEntry = (id: number) => {
    setEntries((prev) => prev.filter((entry) => entry.id !== id));
  };

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
            disabled={loading || clearing}
            aria-label="Refresh Clipboard"
          >
            <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
            <span>Refresh</span>
          </button>
          <button
            className="btn-secondary btn-danger-outline"
            onClick={handleOpenClearModal}
            disabled={loading || clearing || entries.length === 0}
            aria-label="Clear History"
            data-testid="clear-history-btn"
          >
            {clearing ? (
              <RefreshCw size={14} className="animate-spin" />
            ) : (
              <Trash2 size={14} />
            )}
            <span>Clear History</span>
          </button>
        </div>
      </header>

      <main className="content-container">
        {clearError && (
          <div className="entry-delete-error" role="alert" style={{ margin: '0 0 16px 0' }}>
            <span>{clearError}</span>
            <button onClick={() => setClearError(null)} aria-label="Dismiss error">
              <X size={14} />
            </button>
          </div>
        )}

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
          <div className="empty-state-card" data-testid="empty-clipboard-state">
            <div className="empty-state-icon-wrap">
              <ClipboardList className="empty-state-icon" />
            </div>
            <h2 className="empty-state-title">No Clipboard Entries Found</h2>
            <p className="empty-state-desc">
              Copy text, commands, or code snippets with the ContextClip desktop agent running
              to start capturing your history here.
            </p>
            <div className="empty-state-actions">
              <div className="empty-state-hint-card">
                <Zap size={14} className="empty-state-hint-icon" />
                <span>
                  Make sure the Desktop Agent is running and connected with your personal
                  AGENT_TOKEN.
                </span>
              </div>
              <button className="btn-secondary" onClick={loadEntries}>
                <RefreshCw size={14} />
                <span>Check for New Entries</span>
              </button>
            </div>
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
              <ClipboardItem
                key={entry.id}
                entry={entry}
                onDelete={handleDeleteEntry}
              />
            ))}
          </div>
        )}

        {/* Clear History Confirmation Modal */}
        <DeleteConfirmModal
          isOpen={showClearModal}
          title="Clear all clipboard history?"
          message="Are you sure you want to clear your entire clipboard history? All captured snippets and code will be permanently deleted. This action cannot be undone."
          isDeleting={clearing}
          onConfirm={handleConfirmClear}
          onCancel={() => {
            if (!clearing) {
              setShowClearModal(false);
              setClearError(null);
            }
          }}
        />
      </main>
    </div>
  );
};
