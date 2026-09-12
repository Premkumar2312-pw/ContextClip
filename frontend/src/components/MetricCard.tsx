import React from 'react';

interface MetricCardProps {
  title: string;
  value: string | number | null;
  subtext?: string;
  icon?: React.ReactNode;
}

export const MetricCard: React.FC<MetricCardProps> = ({
  title,
  value,
  subtext,
  icon,
}) => {
  const displayValue =
    value === null || value === undefined || value === '' ? '—' : value;
  const isLongText = typeof displayValue === 'string' && displayValue.length > 10;

  return (
    <div className="metric-card">
      <div className="metric-header">
        <span className="metric-title">{title}</span>
        {icon && <div className="metric-icon">{icon}</div>}
      </div>
      <div
        className={`metric-value${isLongText ? ' metric-value--long' : ''}`}
        title={String(displayValue)}
      >
        {displayValue}
      </div>
      {subtext && <div className="metric-sub">{subtext}</div>}
    </div>
  );
};

