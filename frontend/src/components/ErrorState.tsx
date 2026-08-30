import React from 'react';
import { AlertTriangle, RefreshCw } from 'lucide-react';

interface ErrorStateProps {
  message: string;
  onRetry: () => void;
}

export const ErrorState: React.FC<ErrorStateProps> = ({ message, onRetry }) => {
  return (
    <div className="state-container" role="alert">
      <AlertTriangle className="state-icon" style={{ color: '#EF4444' }} />
      <h2 className="state-title">Unable to Load Analytics</h2>
      <p className="state-description">
        {message ||
          'Unable to load analytics. Make sure the ContextClip backend is running.'}
      </p>
      <button className="state-action" onClick={onRetry}>
        <RefreshCw size={14} style={{ display: 'inline', marginRight: 6 }} />
        Retry Connection
      </button>
    </div>
  );
};
