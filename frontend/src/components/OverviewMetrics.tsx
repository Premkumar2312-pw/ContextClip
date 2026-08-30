import React from 'react';
import { AnalyticsOverview } from '../types/analytics';
import { MetricCard } from './MetricCard';
import { FileText, Cpu, FolderGit2, Hash } from 'lucide-react';

interface OverviewMetricsProps {
  overview: AnalyticsOverview;
}

export const OverviewMetrics: React.FC<OverviewMetricsProps> = ({
  overview,
}) => {
  return (
    <section className="metrics-grid" aria-label="Overview Metrics">
      <MetricCard
        title="Total Entries"
        value={overview.totalEntries}
        subtext="Total clipboard items recorded"
        icon={<Hash size={18} />}
      />
      <MetricCard
        title="Top Technology"
        value={overview.mostUsedTechnology}
        subtext="Most active ecosystem/tool"
        icon={<Cpu size={18} />}
      />
      <MetricCard
        title="Top Content Type"
        value={overview.mostUsedType}
        subtext="Dominant classification"
        icon={<FileText size={18} />}
      />
      <MetricCard
        title="Top Category"
        value={overview.mostUsedCategory}
        subtext="Primary workflow domain"
        icon={<FolderGit2 size={18} />}
      />
    </section>
  );
};
