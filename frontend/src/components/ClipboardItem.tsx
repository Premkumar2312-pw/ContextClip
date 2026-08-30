import React, { useState } from 'react';
import { ClipboardEntry } from '../types/clipboard';
import { explainClipboardEntry, summarizeClipboardEntry } from '../api/clipboardApi';
import { MarkdownView } from './MarkdownView';
import {
  Sparkles,
  FileText,
  Copy,
  Check,
  X,
  RefreshCw,
  Clock,
} from 'lucide-react';

interface ClipboardItemProps {
  entry: ClipboardEntry;
}

export const ClipboardItem: React.FC<ClipboardItemProps> = ({ entry }) => {
  const [copied, setCopied] = useState(false);
  const [isExpanded, setIsExpanded] = useState(false);

  // AI Explain state
  const [explanation, setExplanation] = useState<string | null>(null);
  const [explainLoading, setExplainLoading] = useState(false);
  const [explainError, setExplainError] = useState<string | null>(null);

  // AI Summarize state
  const [summary, setSummary] = useState<string | null>(null);
  const [summaryLoading, setSummaryLoading] = useState(false);
  const [summaryError, setSummaryError] = useState<string | null>(null);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(entry.content);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // fallback
    }
  };

  const handleExplain = async () => {
    if (explanation) {
      setExplanation(null);
      return;
    }
    setExplainLoading(true);
    setExplainError(null);
    try {
      const res = await explainClipboardEntry(entry.id);
      setExplanation(res.explanation);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'AI service is unavailable. Make sure the AI service is running and try again.';
      setExplainError(msg);
    } finally {
      setExplainLoading(false);
    }
  };

  const handleSummarize = async () => {
    if (summary) {
      setSummary(null);
      return;
    }
    setSummaryLoading(true);
    setSummaryError(null);
    try {
      const res = await summarizeClipboardEntry(entry.id);
      setSummary(res.summary);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'AI service is unavailable. Make sure the AI service is running and try again.';
      setSummaryError(msg);
    } finally {
      setSummaryLoading(false);
    }
  };

  const formattedDate = entry.capturedAt
    ? new Date(entry.capturedAt).toLocaleString([], {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    : '';

  const isLongContent = entry.content.length > 220 || entry.content.split('\n').length > 5;

  return (
    <div className="clipboard-card" data-testid={`clipboard-entry-${entry.id}`}>
      <div className="clipboard-card-header">
        <div className="entry-meta-badges">
          <span className="badge badge-id">#{entry.id}</span>
          <span className="badge badge-type">{entry.type || 'TEXT'}</span>
          <span className="badge badge-tech">{entry.technology || 'UNKNOWN'}</span>
          <span className="badge badge-cat">{entry.category || 'GENERAL'}</span>
        </div>
        {formattedDate && (
          <span className="entry-timestamp" title={entry.capturedAt}>
            <Clock size={12} style={{ display: 'inline', marginRight: 4 }} />
            {formattedDate}
          </span>
        )}
      </div>

      <div className="clipboard-card-body">
        <div className={`code-container ${isExpanded ? 'expanded' : ''}`}>
          <code>{entry.content}</code>
        </div>
        {isLongContent && (
          <button
            className="code-expand-toggle"
            onClick={() => setIsExpanded(!isExpanded)}
          >
            {isExpanded ? 'Show less' : 'Show more'}
          </button>
        )}
      </div>

      <div className="clipboard-card-footer">
        <div className="entry-actions">
          <button
            className="btn-ai-explain"
            onClick={handleExplain}
            disabled={explainLoading}
            aria-label={`Explain entry ${entry.id}`}
          >
            {explainLoading ? (
              <RefreshCw size={14} className="animate-spin" />
            ) : (
              <Sparkles size={14} />
            )}
            <span>{explainLoading ? 'Explaining...' : explanation ? 'Hide Explain' : 'Explain'}</span>
          </button>

          <button
            className="btn-ai-summarize"
            onClick={handleSummarize}
            disabled={summaryLoading}
            aria-label={`Summarize entry ${entry.id}`}
          >
            {summaryLoading ? (
              <RefreshCw size={14} className="animate-spin" />
            ) : (
              <FileText size={14} />
            )}
            <span>{summaryLoading ? 'Summarizing...' : summary ? 'Hide Summary' : 'Summarize'}</span>
          </button>
        </div>

        <button className="btn-copy" onClick={handleCopy} aria-label="Copy snippet">
          {copied ? <Check size={14} color="#10B981" /> : <Copy size={14} />}
          <span>{copied ? 'Copied' : 'Copy'}</span>
        </button>
      </div>

      {/* AI Explanation Drawer */}
      {explainLoading && (
        <div className="ai-panel explain">
          <div className="ai-loading-indicator">
            <RefreshCw size={14} className="animate-spin" />
            <span>Consulting Gemini for detailed explanation...</span>
          </div>
        </div>
      )}

      {explainError && (
        <div className="ai-panel explain">
          <div className="ai-error-indicator">
            <span>{explainError}</span>
            <button className="ai-panel-close" onClick={() => setExplainError(null)}>
              <X size={14} />
            </button>
          </div>
        </div>
      )}

      {explanation && !explainLoading && (
        <div className="ai-panel explain">
          <div className="ai-panel-header">
            <span className="ai-panel-title">
              <Sparkles size={14} />
              AI Explanation
            </span>
            <button
              className="ai-panel-close"
              onClick={() => setExplanation(null)}
              aria-label="Close explanation"
            >
              <X size={14} />
            </button>
          </div>
          <div className="ai-panel-content">
            <MarkdownView content={explanation} />
          </div>
        </div>
      )}

      {/* AI Summary Drawer */}
      {summaryLoading && (
        <div className="ai-panel summary">
          <div className="ai-loading-indicator">
            <RefreshCw size={14} className="animate-spin" />
            <span>Generating concise summary with Gemini...</span>
          </div>
        </div>
      )}

      {summaryError && (
        <div className="ai-panel summary">
          <div className="ai-error-indicator">
            <span>{summaryError}</span>
            <button className="ai-panel-close" onClick={() => setSummaryError(null)}>
              <X size={14} />
            </button>
          </div>
        </div>
      )}

      {summary && !summaryLoading && (
        <div className="ai-panel summary">
          <div className="ai-panel-header">
            <span className="ai-panel-title">
              <FileText size={14} />
              AI Summary
            </span>
            <button
              className="ai-panel-close"
              onClick={() => setSummary(null)}
              aria-label="Close summary"
            >
              <X size={14} />
            </button>
          </div>
          <div className="ai-panel-content">
            <MarkdownView content={summary} />
          </div>
        </div>
      )}
    </div>
  );
};
