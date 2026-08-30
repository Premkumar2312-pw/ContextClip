import React, { useCallback, useEffect, useRef, useState } from 'react';
import { MessageSquare, RotateCcw, X, Clipboard as ClipboardIcon } from 'lucide-react';
import { askClipboard, getClipboardEntries } from '../api/clipboardApi';
import { ClipboardAskResponse, ClipboardEntry } from '../types/clipboard';
import { MarkdownView } from '../components/MarkdownView';

const MAX_QUESTION_LENGTH = 2000;

const EXAMPLE_PROMPTS = [
  'What Docker commands have I copied?',
  'What SQL queries did I save?',
  'What Java code have I copied?',
  'Which Git commands are in my history?',
];

interface AskClipboardPageProps {
  onNavigateToClipboard?: () => void;
}

type AskState = 'idle' | 'loading' | 'success' | 'error';

export const AskClipboardPage: React.FC<AskClipboardPageProps> = ({
  onNavigateToClipboard,
}) => {
  const [question, setQuestion] = useState('');
  const [validationError, setValidationError] = useState<string | null>(null);
  const [state, setState] = useState<AskState>('idle');
  const [result, setResult] = useState<ClipboardAskResponse | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [entries, setEntries] = useState<ClipboardEntry[]>([]);
  const [entriesLoaded, setEntriesLoaded] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // Load clipboard entries once for source detail lookup
  useEffect(() => {
    getClipboardEntries()
      .then((data) => {
        setEntries(data);
        setEntriesLoaded(true);
      })
      .catch(() => {
        setEntriesLoaded(true); // still allow ask even if we can't load entries
      });
  }, []);

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
    setQuestion('');
    setResult(null);
    setState('idle');
    setErrorMessage(null);
    setValidationError(null);
    textareaRef.current?.focus();
  };

  const handleExampleClick = (prompt: string) => {
    setQuestion(prompt);
    setValidationError(null);
    textareaRef.current?.focus();
  };

  const getSourceEntry = (id: number): ClipboardEntry | undefined =>
    entries.find((e) => e.id === id);

  const truncate = (text: string, max = 60): string =>
    text.length <= max ? text : text.slice(0, max).trimEnd() + '…';

  const hasEntries = entriesLoaded && entries.length > 0;
  const noHistory = entriesLoaded && entries.length === 0;

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
          <div className="state-container" data-testid="ask-empty-state">
            <ClipboardIcon className="state-icon" />
            <h2 className="state-title">No clipboard history available yet.</h2>
            <p className="state-description">
              Start capturing clipboard entries with the Desktop Agent, then come back to ask
              questions about your history.
            </p>
            {onNavigateToClipboard && (
              <button className="btn-secondary" onClick={onNavigateToClipboard}>
                Go to Clipboard
              </button>
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
                {/* Question echo */}
                <div className="ask-result-question" data-testid="ask-result-question">
                  <span className="ask-result-q-label">Q:</span>
                  <span className="ask-result-q-text">{question.trim()}</span>
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
                        return (
                          <button
                            key={id}
                            className="ask-source-chip"
                            onClick={onNavigateToClipboard}
                            aria-label={`Source entry ${id}`}
                            data-testid={`ask-source-${id}`}
                          >
                            <span className="ask-source-id">#{id}</span>
                            {entry && (
                              <span className="ask-source-preview">
                                {truncate(entry.content)}
                              </span>
                            )}
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
