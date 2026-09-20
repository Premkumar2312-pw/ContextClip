import React from 'react';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  Cell,
} from 'recharts';
import { AnalyticsCountItem } from '../types/analytics';
import { formatLabel } from '../utils/displayLabels';

interface TypeChartProps {
  data: AnalyticsCountItem[];
}

export const TypeChart: React.FC<TypeChartProps> = ({ data }) => {
  const colors = ['#2563EB', '#3B82F6', '#60A5FA', '#93C5FD', '#BFDBFE'];

  const aggregatedData = React.useMemo(() => {
    if (!data || data.length === 0) return [];
    const map = new Map<string, number>();

    for (const item of data) {
      const canonicalName = formatLabel(item.name);
      if (!canonicalName) continue;
      map.set(canonicalName, (map.get(canonicalName) || 0) + (item.count || 0));
    }

    return Array.from(map.entries()).map(([name, count]) => ({
      name,
      count,
    }));
  }, [data]);

  const renderCustomTick = (props: any) => {
    const { x, y, payload } = props;
    const rawLabel = formatLabel(String(payload?.value ?? ''));
    const words = rawLabel.split(' ');

    if (words.length > 1 && rawLabel.length > 9) {
      return (
        <g transform={`translate(${x},${y})`}>
          <text x={0} y={10} textAnchor="middle" fill="#64748B" fontSize={11} fontFamily="inherit">
            <tspan x={0} dy="0">{words[0]}</tspan>
            <tspan x={0} dy="13">{words.slice(1).join(' ')}</tspan>
          </text>
        </g>
      );
    }

    return (
      <g transform={`translate(${x},${y})`}>
        <text x={0} y={10} textAnchor="middle" fill="#64748B" fontSize={11} fontFamily="inherit">
          {rawLabel}
        </text>
      </g>
    );
  };

  return (
    <div className="chart-card">
      <div className="chart-card-header">
        <div>
          <h2 className="chart-title">Entries by Content Type</h2>
          <p className="chart-subtitle">Distribution across syntax formats</p>
        </div>
      </div>

      <div className="chart-content">
        {aggregatedData.length === 0 ? (
          <p className="metric-sub">No type data available</p>
        ) : (
          <ResponsiveContainer width="100%" height={260}>
            <BarChart
              data={aggregatedData}
              margin={{ top: 12, right: 16, left: 0, bottom: 40 }}
              barCategoryGap="20%"
            >
              <XAxis
                dataKey="name"
                tick={renderCustomTick}
                tickLine={false}
                axisLine={{ stroke: '#E2E8F0' }}
                interval={0}
                height={45}
              />
              <YAxis
                allowDecimals={false}
                width={32}
                tick={{ fontSize: 11, fill: '#64748B' }}
                tickLine={false}
                axisLine={{ stroke: '#E2E8F0' }}
              />
              <Tooltip
                cursor={{ fill: '#F1F5F9' }}
                formatter={(val: any) => [val ?? 0, 'Entries']}
                labelFormatter={(label: any) => formatLabel(String(label || ''))}
                contentStyle={{
                  backgroundColor: '#FFFFFF',
                  borderColor: '#E2E8F0',
                  borderRadius: '6px',
                  boxShadow: '0 2px 4px rgba(0,0,0,0.05)',
                  fontSize: '12px',
                }}
              />
              <Bar dataKey="count" radius={[4, 4, 0, 0]} maxBarSize={48}>
                {aggregatedData.map((_, index) => (
                  <Cell
                    key={`cell-${index}`}
                    fill={colors[index % colors.length]}
                  />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        )}
      </div>
    </div>
  );
};

