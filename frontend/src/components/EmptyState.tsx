import React from 'react';
import { ClipboardList, RefreshCw } from 'lucide-react';

interface EmptyStateProps {
  onRefresh: () => void;
}

export const EmptyState: React.FC<EmptyStateProps> = ({ onRefresh }) => {
  return (
    <div className="state-container">
      <ClipboardList className="state-icon" />
      <h2 className="state-title">No Clipboard Activity Yet</h2>
      <p className="state-description">
        Your clipboard history is currently empty. Copy code snippets, commands, or text with the ContextClip desktop agent running to start tracking analytics.
      </p>
      <button className="btn-secondary" onClick={onRefresh}>
        <RefreshCw size={14} style={{ display: 'inline', marginRight: 6 }} />
        Check for New Entries
      </button>
    </div>
  );
};
