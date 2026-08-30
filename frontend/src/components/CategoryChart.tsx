import React from 'react';
import { AnalyticsCountItem } from '../types/analytics';

interface CategoryChartProps {
  data: AnalyticsCountItem[];
}

export const CategoryChart: React.FC<CategoryChartProps> = ({ data }) => {
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
          <div className="category-chips-grid">
            {data.map((item) => (
              <div key={item.name} className="category-chip-card">
                <span className="category-chip-name">{item.name}</span>
                <span className="category-chip-count">{item.count}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

