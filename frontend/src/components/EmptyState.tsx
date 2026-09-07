import React from 'react';
import { ClipboardList, RefreshCw, ArrowRight } from 'lucide-react';

interface EmptyStateProps {
  onRefresh: () => void;
  onNavigate?: () => void;
  navigateLabel?: string;
}

export const EmptyState: React.FC<EmptyStateProps> = ({
  onRefresh,
  onNavigate,
  navigateLabel = 'View Clipboard',
}) => {
  return (
    <div className="empty-state-card">
      <div className="empty-state-icon-wrap">
        <ClipboardList className="empty-state-icon" />
      </div>
      <h2 className="empty-state-title">No Clipboard Activity Yet</h2>
      <p className="empty-state-desc">
        Copy code snippets, commands, or text with the ContextClip desktop agent running to start
        tracking your clipboard history.
      </p>
      <div className="empty-state-actions">
        {onNavigate && (
          <button className="btn-primary empty-state-cta" onClick={onNavigate}>
            <span>{navigateLabel}</span>
            <ArrowRight size={14} />
          </button>
        )}
        <button className="btn-secondary" onClick={onRefresh}>
          <RefreshCw size={14} />
          <span>Check Again</span>
        </button>
      </div>
    </div>
  );
};
