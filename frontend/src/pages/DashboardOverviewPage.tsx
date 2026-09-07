import React, { useEffect, useState } from 'react';
import { useAnalytics } from '../hooks/useAnalytics';
import { getClipboardEntries } from '../api/clipboardApi';
import { ClipboardEntry } from '../types/clipboard';
import { Header } from '../components/Header';
import { OverviewMetrics } from '../components/OverviewMetrics';
import { ActivityChart } from '../components/ActivityChart';
import { CategoryChart } from '../components/CategoryChart';
import { ClipboardItem } from '../components/ClipboardItem';
import { LoadingSkeleton } from '../components/LoadingSkeleton';
import { ErrorState } from '../components/ErrorState';
import { EmptyState } from '../components/EmptyState';
import { ArrowRight, Sparkles } from 'lucide-react';

interface DashboardOverviewPageProps {
  onNavigateToClipboard: () => void;
}

export const DashboardOverviewPage: React.FC<DashboardOverviewPageProps> = ({
  onNavigateToClipboard,
}) => {
  const { data, loading, error, lastUpdated, refresh } = useAnalytics();
  const [recentEntries, setRecentEntries] = useState<ClipboardEntry[]>([]);

  useEffect(() => {
    getClipboardEntries()
      .then((entries) => setRecentEntries(entries.slice(0, 3)))
      .catch(() => {});
  }, [data]);

  const isEmpty =
    data &&
    data.overview.totalEntries === 0 &&
    data.byType.length === 0 &&
    data.byTechnology.length === 0;

  return (
    <div className="main-wrapper dashboard-overview-page">
      <Header
        title="Dashboard Overview"
        subtitle="Real-time clipboard activity, analytics, and recent captures"
        lastUpdated={lastUpdated}
        loading={loading}
        onRefresh={refresh}
      />

      <main className="content-container">
        {loading && !data && <LoadingSkeleton />}

        {error && !data && <ErrorState message={error} onRetry={refresh} />}

        {data && isEmpty && (
          <EmptyState
            onRefresh={refresh}
            onNavigate={onNavigateToClipboard}
            navigateLabel="View Clipboard"
          />
        )}

        {data && !isEmpty && (
          <>
            <OverviewMetrics overview={data.overview} />

            <div className="charts-grid-primary">
              <ActivityChart data={data.activity} />
              <CategoryChart data={data.byCategory} />
            </div>

            {recentEntries.length > 0 && (
              <div style={{ marginTop: 24 }}>
                <div className="results-header">
                  <span className="results-count" style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <Sparkles size={14} color="#2563EB" />
                    Recent Clipboard Captures
                  </span>
                  <button
                    className="btn-secondary"
                    onClick={onNavigateToClipboard}
                    style={{ fontSize: 12 }}
                  >
                    <span>View all entries</span>
                    <ArrowRight size={12} />
                  </button>
                </div>

                <div className="clipboard-list">
                  {recentEntries.map((entry) => (
                    <ClipboardItem key={entry.id} entry={entry} />
                  ))}
                </div>
              </div>
            )}
          </>
        )}
      </main>
    </div>
  );
};
