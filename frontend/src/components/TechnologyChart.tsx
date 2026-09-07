import React from 'react';
import { AnalyticsCountItem } from '../types/analytics';

interface TechnologyChartProps {
  data: AnalyticsCountItem[];
}

/** Renders a human-friendly label for a technology name. */
function formatTechLabel(name: string): string {
  if (!name || name.toUpperCase() === 'UNKNOWN') {
    return 'Other / Unknown';
  }
  if (name.toUpperCase() === 'SQL') {
    return 'SQL';
  }
  return name
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(' ');
}

export const TechnologyChart: React.FC<TechnologyChartProps> = ({ data }) => {
  const maxCount = Math.max(...data.map((d) => d.count), 1);

  const colors = [
    '#2563EB', // Blue
    '#059669', // Emerald
    '#7C3AED', // Violet
    '#D97706', // Amber
    '#DB2777', // Pink
    '#0891B2', // Cyan
    '#6B7280', // Gray (used for UNKNOWN/Other)
  ];

  return (
    <div className="chart-card">
      <div className="chart-card-header">
        <div>
          <h2 className="chart-title">Entries by Technology</h2>
          <p className="chart-subtitle">Languages, frameworks, and tooling</p>
        </div>
      </div>

      <div className="chart-content">
        {data.length === 0 ? (
          <p className="metric-sub">No technology data available</p>
        ) : (
          <div className="ranked-list">
            {data.map((item, index) => {
              const isUnknown = !item.name || item.name.toUpperCase() === 'UNKNOWN';
              const percentage = Math.round((item.count / maxCount) * 100);
              // UNKNOWN always gets the gray color regardless of position
              const barColor = isUnknown ? '#6B7280' : colors[index % (colors.length - 1)];
              const label = formatTechLabel(item.name);

              return (
                <div key={item.name} className="ranked-item">
                  <div className="ranked-item-info">
                    <div className="ranked-item-left">
                      <span className="ranked-item-dot" style={{ backgroundColor: barColor }} />
                      <span
                        className={`ranked-item-name${isUnknown ? ' ranked-item-name--muted' : ''}`}
                      >
                        {label}
                      </span>
                    </div>
                    <span className="ranked-item-count">{item.count}</span>
                  </div>
                  <div className="ranked-bar-bg">
                    <div
                      className="ranked-bar-fill"
                      style={{
                        width: `${percentage}%`,
                        backgroundColor: barColor,
                        opacity: isUnknown ? 0.65 : 1,
                      }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
};
