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

  return (
    <div className="chart-card">
      <div className="chart-card-header">
        <div>
          <h2 className="chart-title">Entries by Content Type</h2>
          <p className="chart-subtitle">Distribution across syntax formats</p>
        </div>
      </div>

      <div className="chart-content">
        {data.length === 0 ? (
          <p className="metric-sub">No type data available</p>
        ) : (
          <ResponsiveContainer width="100%" height={260}>
            <BarChart
              data={data}
              margin={{ top: 12, right: 16, left: 0, bottom: 55 }}
              barCategoryGap="20%"
            >
              <XAxis
                dataKey="name"
                tick={{ fontSize: 11, fill: '#64748B' }}
                tickFormatter={(val: string) => formatLabel(val)}
                tickLine={false}
                axisLine={{ stroke: '#E2E8F0' }}
                interval={0}
                angle={-25}
                textAnchor="end"
                height={55}
                dx={-4}
                dy={4}
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
                {data.map((_, index) => (
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

