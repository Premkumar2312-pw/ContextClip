import React from 'react';
import { useAnalytics } from '../hooks/useAnalytics';
import { Header } from '../components/Header';
import { OverviewMetrics } from '../components/OverviewMetrics';
import { TechnologyChart } from '../components/TechnologyChart';
import { TypeChart } from '../components/TypeChart';
import { CategoryChart } from '../components/CategoryChart';
import { ActivityChart } from '../components/ActivityChart';
import { LoadingSkeleton } from '../components/LoadingSkeleton';
import { ErrorState } from '../components/ErrorState';
import { EmptyState } from '../components/EmptyState';

export const DashboardPage: React.FC = () => {
  const { data, loading, error, lastUpdated, refresh } = useAnalytics();

  const isEmpty =
    data &&
    data.overview.totalEntries === 0 &&
    data.byType.length === 0 &&
    data.byTechnology.length === 0 &&
    data.byCategory.length === 0 &&
    data.activity.length === 0;

  return (
    <div className="main-wrapper">
      <Header
        lastUpdated={lastUpdated}
        loading={loading}
        onRefresh={refresh}
      />

      <main className="content-container">
        {loading && !data && <LoadingSkeleton />}

        {error && !data && (
          <ErrorState message={error} onRetry={refresh} />
        )}

        {data && isEmpty && <EmptyState onRefresh={refresh} />}

        {data && !isEmpty && (
          <>
            <OverviewMetrics overview={data.overview} />

            <div className="charts-grid-primary">
              <ActivityChart data={data.activity} />
              <CategoryChart data={data.byCategory} />
            </div>

            <div className="charts-grid-secondary">
              <TechnologyChart data={data.byTechnology} />
              <TypeChart data={data.byType} />
            </div>
          </>
        )}
      </main>
    </div>
  );
};
