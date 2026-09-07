import React, { useCallback, useEffect, useState } from 'react';
import { ClipboardEntry, SearchParams } from '../types/clipboard';
import { searchClipboard } from '../api/clipboardApi';
import { ClipboardItem } from '../components/ClipboardItem';
import { Search, RotateCcw, SearchX, X } from 'lucide-react';
import { ErrorState } from '../components/ErrorState';

const TYPE_OPTIONS = [
  'TEXT',
  'CODE',
  'SQL',
  'JSON',
  'URL',
  'TERMINAL_COMMAND',
  'ERROR',
  'CONFIGURATION',
];

const TECH_OPTIONS = [
  'JAVA',
  'PYTHON',
  'DOCKER',
  'GIT',
  'MAVEN',
  'SPRING_BOOT',
  'SQL',
  'UNKNOWN',
];

const CATEGORY_OPTIONS = [
  'PROGRAMMING',
  'DATABASE',
  'DEVOPS',
  'WEB',
  'CONFIGURATION',
  'TROUBLESHOOTING',
  'GENERAL',
];

export const SearchPage: React.FC = () => {
  const [params, setParams] = useState<SearchParams>({
    q: '',
    type: '',
    technology: '',
    category: '',
  });

  const [results, setResults] = useState<ClipboardEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [hasSearched, setHasSearched] = useState(false);

  const executeSearch = useCallback(async (currentParams: SearchParams) => {
    setLoading(true);
    setError(null);
    setHasSearched(true);
    try {
      const data = await searchClipboard(currentParams);
      setResults(data);
    } catch (err: unknown) {
      const message =
        err instanceof Error
          ? err.message
          : 'Unable to search clipboard history. Make sure the ContextClip backend is running.';
      setError(message);
    } finally {
      setLoading(false);
    }
  }, []);

  // Initial load
  useEffect(() => {
    executeSearch(params);
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    executeSearch(params);
  };

  const handleFilterChange = (
    key: keyof SearchParams,
    value: string
  ) => {
    const updated = { ...params, [key]: value };
    setParams(updated);
    executeSearch(updated);
  };

  const handleClearFilters = () => {
    const cleared = { q: '', type: '', technology: '', category: '' };
    setParams(cleared);
    executeSearch(cleared);
  };

  const hasActiveFilters = Boolean(
    params.q || params.type || params.technology || params.category
  );

  return (
    <div className="main-wrapper">
      <header className="top-header">
        <div className="page-title-group">
          <h1>Search Clipboard</h1>
          <p>Filter by keywords, language, category, or command type</p>
        </div>
      </header>

      <main className="content-container">
        {/* Search controls bar */}
        <section className="search-controls-card">
          <form onSubmit={handleSearchSubmit}>
            <div className="search-input-wrapper">
              <Search className="search-icon" size={18} />
              <input
                type="text"
                className="search-input"
                placeholder="Search clipboard by keywords (e.g. docker, SELECT, main)..."
                value={params.q || ''}
                onChange={(e) => setParams({ ...params, q: e.target.value })}
                aria-label="Search clipboard input"
              />
              {params.q && (
                <button
                  type="button"
                  className="search-clear-inline"
                  onClick={() => {
                    const updated = { ...params, q: '' };
                    setParams(updated);
                    executeSearch(updated);
                  }}
                  aria-label="Clear search text"
                  style={{
                    position: 'absolute',
                    right: '110px',
                    background: 'transparent',
                    border: 'none',
                    color: 'var(--text-muted)',
                    cursor: 'pointer',
                    padding: '4px',
                    display: 'flex',
                    alignItems: 'center',
                  }}
                >
                  <X size={14} />
                </button>
              )}
              <button type="submit" className="btn-primary" disabled={loading}>
                Search
              </button>
            </div>
          </form>

          <div className="search-filters-row">
            <div className="filter-group">
              <label htmlFor="filter-type" className="filter-label">
                Type:
              </label>
              <select
                id="filter-type"
                className="filter-select"
                value={params.type || ''}
                onChange={(e) => handleFilterChange('type', e.target.value)}
                aria-label="Filter by Type"
              >
                <option value="">All Types</option>
                {TYPE_OPTIONS.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>
            </div>

            <div className="filter-group">
              <label htmlFor="filter-tech" className="filter-label">
                Technology:
              </label>
              <select
                id="filter-tech"
                className="filter-select"
                value={params.technology || ''}
                onChange={(e) => handleFilterChange('technology', e.target.value)}
                aria-label="Filter by Technology"
              >
                <option value="">All Technologies</option>
                {TECH_OPTIONS.map((tech) => (
                  <option key={tech} value={tech}>
                    {tech}
                  </option>
                ))}
              </select>
            </div>

            <div className="filter-group">
              <label htmlFor="filter-cat" className="filter-label">
                Category:
              </label>
              <select
                id="filter-cat"
                className="filter-select"
                value={params.category || ''}
                onChange={(e) => handleFilterChange('category', e.target.value)}
                aria-label="Filter by Category"
              >
                <option value="">All Categories</option>
                {CATEGORY_OPTIONS.map((cat) => (
                  <option key={cat} value={cat}>
                    {cat}
                  </option>
                ))}
              </select>
            </div>

            {hasActiveFilters && (
              <button
                type="button"
                className="btn-clear-filters"
                onClick={handleClearFilters}
                aria-label="Clear all filters"
              >
                <RotateCcw size={12} style={{ display: 'inline', marginRight: 4 }} />
                Clear filters
              </button>
            )}
          </div>
        </section>

        {/* Results Area */}
        {loading && (
          <div aria-label="Searching clipboard entries">
            <div className="skeleton skeleton-row" />
            <div className="skeleton skeleton-row" />
          </div>
        )}

        {error && !loading && (
          <ErrorState message={error} onRetry={() => executeSearch(params)} />
        )}

        {!loading && !error && hasSearched && results.length === 0 && (
          <div className="empty-state-card">
            <div className="empty-state-icon-wrap">
              <SearchX className="empty-state-icon" />
            </div>
            <h2 className="empty-state-title">No Clipboard Entries Matched Your Search</h2>
            <p className="empty-state-desc">
              Try adjusting your search query, or clear all filters to view your full clipboard
              history.
            </p>
            {hasActiveFilters && (
              <div className="empty-state-actions">
                <button className="btn-secondary" onClick={handleClearFilters}>
                  <RotateCcw size={14} />
                  <span>Clear Filters</span>
                </button>
              </div>
            )}
          </div>
        )}

        {!loading && results.length > 0 && (
          <div className="clipboard-list" data-testid="search-results-list">
            <div className="results-header">
              <span className="results-count">
                {results.length} {results.length === 1 ? 'match found' : 'matches found'}
              </span>
            </div>
            {results.map((entry) => (
              <ClipboardItem key={entry.id} entry={entry} />
            ))}
          </div>
        )}
      </main>
    </div>
  );
};
