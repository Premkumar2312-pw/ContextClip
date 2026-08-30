import React from 'react';

export const LoadingSkeleton: React.FC = () => {
  return (
    <div aria-label="Loading analytics data">
      <div className="metrics-grid">
        <div className="metric-card skeleton skeleton-card" />
        <div className="metric-card skeleton skeleton-card" />
        <div className="metric-card skeleton skeleton-card" />
        <div className="metric-card skeleton skeleton-card" />
      </div>

      <div className="charts-grid-primary">
        <div className="chart-card skeleton skeleton-chart" />
        <div className="chart-card skeleton skeleton-chart" />
      </div>

      <div className="charts-grid-secondary">
        <div className="chart-card skeleton skeleton-chart" />
        <div className="chart-card skeleton skeleton-chart" />
      </div>
    </div>
  );
};

