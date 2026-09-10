import React from 'react';
import { AlertTriangle, RefreshCw, WifiOff } from 'lucide-react';

interface ErrorStateProps {
  message: string;
  onRetry: () => void | Promise<void>;
  title?: string;
  retryLabel?: string;
}

const OFFLINE_PATTERNS = [
  'Failed to fetch',
  'NetworkError',
  'backend is running',
  'backend is currently offline',
  'Connection refused',
];

function isNetworkOffline(message: string): boolean {
  return OFFLINE_PATTERNS.some((p) => message.includes(p));
}

export const ErrorState: React.FC<ErrorStateProps> = ({
  message,
  onRetry,
  title = 'Unable to Load Analytics',
  retryLabel = 'Retry Connection',
}) => {
  const offline = isNetworkOffline(message);

  return (
    <div className="state-container" role="alert">
      {offline ? (
        <WifiOff className="state-icon" style={{ color: '#F59E0B' }} />
      ) : (
        <AlertTriangle className="state-icon" style={{ color: '#EF4444' }} />
      )}
      <h2 className="state-title">{title}</h2>
      <p className="state-description">
        {offline
          ? 'Backend is currently offline. Please ensure the server is running and try again.'
          : message ||
            'Unable to load data. Make sure the ContextClip backend is running.'}
      </p>
      <button className="state-action" onClick={onRetry}>
        <RefreshCw size={14} style={{ display: 'inline', marginRight: 6 }} />
        {retryLabel}
      </button>
    </div>
  );
};
