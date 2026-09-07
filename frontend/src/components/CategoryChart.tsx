import React from 'react';
import { AnalyticsCountItem } from '../types/analytics';

interface CategoryChartProps {
  data: AnalyticsCountItem[];
}

/** Formats category name to clean Title Case. */
function formatCategoryLabel(name: string): string {
  if (!name) return 'General';
  const upper = name.toUpperCase();
  if (upper === 'DEVOPS') return 'DevOps';
  return name
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(' ');
}

export const CategoryChart: React.FC<CategoryChartProps> = ({ data }) => {
  const maxCount = Math.max(...data.map((d) => d.count), 1);

  const colors = [
    '#2563EB', // Blue
    '#10B981', // Emerald
    '#8B5CF6', // Violet
    '#F59E0B', // Amber
    '#EC4899', // Pink
    '#06B6D4', // Cyan
    '#6366F1', // Indigo
  ];

  return (
    <div className="chart-card">
      <div className="chart-card-header">
        <div>
          <h2 className="chart-title">Entries by Category</h2>
          <p className="chart-subtitle">Primary engineering domains</p>
        </div>
      </div>

      <div className="chart-content">
        {data.length === 0 ? (
          <p className="metric-sub">No category data available</p>
        ) : (
          <div className="ranked-list">
            {data.map((item, index) => {
              const percentage = Math.round((item.count / maxCount) * 100);
              const barColor = colors[index % colors.length];
              const label = formatCategoryLabel(item.name);

              return (
                <div key={item.name} className="ranked-item">
                  <div className="ranked-item-info">
                    <div className="ranked-item-left">
                      <span
                        className="ranked-item-dot"
                        style={{ backgroundColor: barColor }}
                      />
                      <span className="ranked-item-name">{label}</span>
                    </div>
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
