import React from 'react';
import { AnalyticsCountItem } from '../types/analytics';

interface TechnologyChartProps {
  data: AnalyticsCountItem[];
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
    '#4B5563', // Gray
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
              const percentage = Math.round((item.count / maxCount) * 100);
              const barColor = colors[index % colors.length];

              return (
                <div key={item.name} className="ranked-item">
                  <div className="ranked-item-info">
                    <span className="ranked-item-name">{item.name}</span>
                    <span className="ranked-item-count">{item.count}</span>
                  </div>
                  <div className="ranked-bar-bg">
                    <div
                      className="ranked-bar-fill"
                      style={{
                        width: `${percentage}%`,
                        backgroundColor: barColor,
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

