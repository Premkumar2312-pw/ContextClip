import React, { useCallback, useEffect, useRef } from 'react';
import {
  MessageSquare,
  RotateCcw,
  X,
  Clipboard as ClipboardIcon,
  ArrowUpRight,
  Copy,
  Check,
} from 'lucide-react';
import { askClipboard, getClipboardEntries } from '../api/clipboardApi';
import { ClipboardEntry } from '../types/clipboard';
import { MarkdownView } from '../components/MarkdownView';

import { useAskClipboard } from '../context/AskClipboardContext';

const MAX_QUESTION_LENGTH = 2000;

const EXAMPLE_PROMPTS = [
  'What Docker commands have I copied?',
  'What SQL queries did I save?',
  'What Java code have I copied?',
  'Which Git commands are in my history?',
];

interface AskClipboardPageProps {
  onNavigateToClipboard?: (entryId?: number) => void;
}

export const AskClipboardPage: React.FC<AskClipboardPageProps> = ({
  onNavigateToClipboard,
}) => {
  const {
    question,
    setQuestion,
    validationError,
    setValidationError,
    state,
    setState,
    result,
    setResult,
    errorMessage,
    setErrorMessage,
    copiedAnswer,
    setCopiedAnswer,
    historyEntries,
    setHistoryEntries,
    entriesLoaded,
    setEntriesLoaded,
    clearAskState,
  } = useAskClipboard();

  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const copyTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Load clipboard entries once or refresh silently in background without resetting session
  useEffect(() => {
    let isMounted = true;
    getClipboardEntries()
      .then((data) => {
        if (isMounted) {
          setHistoryEntries(data);
          setEntriesLoaded(true);
        }
      })
      .catch(() => {
        if (isMounted) {
          setEntriesLoaded(true); // still allow ask even if we can't load entries
        }
      });

    return () => {
      isMounted = false;
      if (copyTimeoutRef.current) {
        clearTimeout(copyTimeoutRef.current);
      }
    };
  }, [setEntriesLoaded, setHistoryEntries]);

  const validate = (q: string): string | null => {
    if (!q.trim()) return 'Please enter a question.';
    if (q.length > MAX_QUESTION_LENGTH)
      return `Question must be ${MAX_QUESTION_LENGTH} characters or fewer.`;
    return null;
  };

  const handleAsk = useCallback(async () => {
    const err = validate(question);
    if (err) {
      setValidationError(err);
      textareaRef.current?.focus();
      return;
    }
    setValidationError(null);
    setState('loading');
    setErrorMessage(null);
    try {
      const response = await askClipboard({ question: question.trim() });
      setResult(response);
      setState('success');
    } catch (e: unknown) {
      const msg =
        e instanceof Error
          ? e.message
          : 'AI service is unavailable. Make sure the AI service is running and try again.';
      setErrorMessage(msg);
      setState('error');
    }
  }, [question]);

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
      e.preventDefault();
      handleAsk();
    }
  };

  const handleClear = () => {
    clearAskState();
    textareaRef.current?.focus();
  };

  const handleCopyAnswer = async () => {
    if (!result?.answer) return;
    try {
      if (copyTimeoutRef.current) {
        clearTimeout(copyTimeoutRef.current);
      }
      if (navigator.clipboard && typeof navigator.clipboard.writeText === 'function' && window.isSecureContext) {
        await navigator.clipboard.writeText(result.answer);
      } else {
        const textArea = document.createElement('textarea');
        textArea.value = result.answer;
        textArea.style.position = 'fixed';
        textArea.style.left = '-999999px';
        textArea.style.top = '-999999px';
        textArea.setAttribute('readonly', '');
        document.body.appendChild(textArea);
        try {
          textArea.focus();
          textArea.select();
          document.execCommand('copy');
        } finally {
          if (textArea.parentNode) {
            document.body.removeChild(textArea);
          }
        }
      }
      setCopiedAnswer(true);
      copyTimeoutRef.current = setTimeout(() => setCopiedAnswer(false), 2000);
    } catch {
      // fallback failed
    }
  };

  const handleExampleClick = (prompt: string) => {
    setQuestion(prompt);
    setValidationError(null);
    textareaRef.current?.focus();
  };

  const getSourceEntry = (id: number): ClipboardEntry | undefined =>
    historyEntries.find((e) => e.id === id);

  const truncate = (text: string, max = 60): string =>
    text.length <= max ? text : text.slice(0, max).trimEnd() + '…';

  const hasActiveSession = Boolean(result || question.trim() || state === 'loading' || state === 'error');
  const hasEntries = entriesLoaded && historyEntries.length > 0;
  const noHistory = entriesLoaded && historyEntries.length === 0 && !hasActiveSession;

  return (
    <div className="main-wrapper">
      <header className="top-header">
        <div className="page-title-group">
          <h1>Ask My Clipboard</h1>
          <p>Ask questions about your saved clipboard history.</p>
        </div>
      </header>

      <main className="content-container">
        {/* Empty state — no clipboard history */}
        {noHistory && (
          <div className="empty-state-card" data-testid="ask-empty-state">
            <div className="empty-state-icon-wrap">
              <ClipboardIcon className="empty-state-icon" />
            </div>
            <h2 className="empty-state-title">No clipboard history available yet.</h2>
            <p className="empty-state-desc">
              Start capturing clipboard entries with the Desktop Agent, then come back to ask
              questions about your history.
            </p>
            {onNavigateToClipboard && (
              <div className="empty-state-actions">
                <button className="btn-primary empty-state-cta" onClick={() => onNavigateToClipboard()}>
                  Go to Clipboard
                </button>
              </div>
            )}
          </div>
        )}

        {/* Main ask panel — shown when history exists or still loading entries */}
        {(hasEntries || !entriesLoaded) && (
          <div className="ask-page-layout">
            {/* Question panel */}
            <section className="ask-question-card" aria-label="Ask a question">
              <div className="ask-textarea-wrapper">
                <textarea
                  ref={textareaRef}
                  className={`ask-textarea${validationError ? ' ask-textarea--error' : ''}`}
                  placeholder="Ask a question about your clipboard history…"
                  value={question}
                  onChange={(e) => {
                    setQuestion(e.target.value);
                    if (validationError) setValidationError(null);
                  }}
                  onKeyDown={handleKeyDown}
                  maxLength={MAX_QUESTION_LENGTH}
                  disabled={state === 'loading'}
                  rows={4}
                  aria-label="Question input"
                  data-testid="ask-question-input"
                />
                <div className="ask-char-count">
                  {question.length}/{MAX_QUESTION_LENGTH}
                </div>
              </div>

              {validationError && (
                <p className="ask-validation-error" role="alert" data-testid="ask-validation-error">
                  {validationError}
                </p>
              )}

              <div className="ask-actions">
                <button
                  className="btn-primary ask-submit-btn"
                  onClick={handleAsk}
                  disabled={state === 'loading'}
                  aria-label="Ask question"
                  data-testid="ask-submit-btn"
                >
                  {state === 'loading' ? (
                    <>
                      <span className="ask-spinner" aria-hidden="true" />
                      Thinking…
                    </>
                  ) : (
                    <>
                      <MessageSquare size={14} />
                      Ask
                    </>
                  )}
                </button>

                {(result || state === 'error') && (
                  <button
                    className="btn-secondary ask-clear-btn"
                    onClick={handleClear}
                    aria-label="Clear question and answer"
                    data-testid="ask-clear-btn"
                  >
                    <X size={14} />
                    Clear
                  </button>
                )}
              </div>

              {/* Example prompts */}
              {state === 'idle' && !result && (
                <div className="ask-examples" data-testid="ask-examples">
                  <p className="ask-examples-label">Try asking:</p>
                  <div className="ask-examples-list">
                    {EXAMPLE_PROMPTS.map((prompt) => (
                      <button
                        key={prompt}
                        className="ask-example-chip"
                        onClick={() => handleExampleClick(prompt)}
                        aria-label={`Use example: ${prompt}`}
                      >
                        {prompt}
                      </button>
                    ))}
                  </div>
                </div>
              )}
            </section>

            {/* Loading skeleton */}
            {state === 'loading' && (
              <section aria-label="Loading answer" className="ask-result-card ask-result-loading">
                <div className="skeleton skeleton-row" />
                <div className="skeleton skeleton-row" style={{ width: '80%' }} />
                <div className="skeleton skeleton-row" style={{ width: '65%' }} />
              </section>
            )}

            {/* Error state */}
            {state === 'error' && errorMessage && (
              <section className="ask-result-card ask-result-error" data-testid="ask-error-panel">
                <div className="error-state-inline">
                  <p className="error-message" role="alert" data-testid="ask-error-message">
                    {errorMessage}
                  </p>
                  <button
                    className="btn-secondary"
                    onClick={handleAsk}
                    aria-label="Retry question"
                    data-testid="ask-retry-btn"
                  >
                    <RotateCcw size={13} />
                    Retry
                  </button>
                </div>
              </section>
            )}

            {/* Success — answer + sources */}
            {state === 'success' && result && (
              <section className="ask-result-card ask-result-success" data-testid="ask-result-panel">
                <div className="ask-result-header">
                  <div className="ask-result-question" data-testid="ask-result-question">
                    <span className="ask-result-q-label">Q:</span>
                    <span className="ask-result-q-text">{question.trim()}</span>
                  </div>

                  <div className="ask-result-actions">
                    <button
                      className="btn-secondary ask-copy-btn"
                      onClick={handleCopyAnswer}
                      aria-label="Copy AI response"
                      title="Copy response to clipboard"
                    >
                      {copiedAnswer ? <Check size={14} color="#10B981" /> : <Copy size={14} />}
                      <span>{copiedAnswer ? 'Copied' : 'Copy'}</span>
                    </button>
                    <button
                      className="btn-secondary ask-clear-btn"
                      onClick={handleClear}
                      aria-label="Clear question and answer"
                      data-testid="ask-clear-btn"
                    >
                      <X size={14} />
                      <span>Clear</span>
                    </button>
                  </div>
                </div>

                {/* Answer */}
                <div className="ask-answer-panel" data-testid="ask-answer-panel">
                  <MarkdownView content={result.answer} />
                </div>

                {/* Sources */}
                {result.sources && result.sources.length > 0 && (
                  <div className="ask-sources-panel" data-testid="ask-sources-panel">
                    <h3 className="ask-sources-heading">Sources from clipboard history</h3>
                    <div className="ask-sources-list">
                      {result.sources.map((id) => {
                        const entry = getSourceEntry(id);
                        const tag = entry?.technology && entry.technology !== 'UNKNOWN'
                          ? entry.technology
                          : entry?.type && entry.type !== 'TEXT' && entry.type !== 'UNKNOWN'
                            ? entry.type
                            : null;
                        return (
                          <button
                            key={id}
                            className="ask-source-chip"
                            onClick={() => onNavigateToClipboard?.(id)}
                            aria-label={`Source entry ${id}`}
                            title={`Jump to clipboard entry #${id}`}
                            data-testid={`ask-source-${id}`}
                          >
                            <div className="ask-source-top-row">
                              <span className="ask-source-id">#{id}</span>
                              {tag && <span className="ask-source-tag">{tag}</span>}
                              <ArrowUpRight size={13} className="ask-source-icon" aria-hidden="true" />
                            </div>
                            <span className="ask-source-preview">
                              {entry ? truncate(entry.content, 90) : `Entry #${id}`}
                            </span>
                          </button>
                        );
                      })}
                    </div>
                  </div>
                )}
              </section>
            )}
          </div>
        )}
      </main>
    </div>
  );
};
