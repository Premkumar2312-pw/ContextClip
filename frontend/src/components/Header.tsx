import React from 'react';
import { RefreshCw } from 'lucide-react';

interface HeaderProps {
  lastUpdated: Date | null;
  loading: boolean;
  onRefresh: () => void;
}

export const Header: React.FC<HeaderProps> = ({
  lastUpdated,
  loading,
  onRefresh,
}) => {
  const formattedTime = lastUpdated
    ? lastUpdated.toLocaleTimeString([], {
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      })
    : '--:--:--';

  return (
    <header className="top-header">
      <div className="page-title-group">
        <h1>Clipboard Analytics</h1>
        <p>Insights into captured snippets, languages, categories, and patterns</p>
      </div>

      <div className="header-actions">
        {lastUpdated && (
          <span className="last-updated">Updated {formattedTime}</span>
        )}
        <button
          className="btn-secondary"
          onClick={onRefresh}
          disabled={loading}
          aria-label="Refresh Analytics"
        >
          <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
          <span>Refresh</span>
        </button>
      </div>
    </header>
  );
};

