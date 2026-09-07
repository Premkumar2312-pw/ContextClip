import React from 'react';
import { RefreshCw } from 'lucide-react';

interface HeaderProps {
  lastUpdated: Date | null;
  loading: boolean;
  onRefresh: () => void;
  title?: string;
  subtitle?: string;
}

export const Header: React.FC<HeaderProps> = ({
  lastUpdated,
  loading,
  onRefresh,
  title = 'Clipboard Analytics',
  subtitle = 'Insights into captured snippets, languages, categories, and patterns',
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
        <h1>{title}</h1>
        <p>{subtitle}</p>
      </div>

      <div className="header-actions">
        {lastUpdated && (
          <span className="last-updated-pill">
            <span className="pulse-indicator" />
            Updated {formattedTime}
          </span>
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

