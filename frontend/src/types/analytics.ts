export interface AnalyticsOverview {
  totalEntries: number;
  mostUsedType: string | null;
  mostUsedTechnology: string | null;
  mostUsedCategory: string | null;
}

export interface AnalyticsCountItem {
  name: string;
  count: number;
}

export interface AnalyticsActivityItem {
  date: string;
  count: number;
}

export interface AnalyticsData {
  overview: AnalyticsOverview;
  byType: AnalyticsCountItem[];
  byTechnology: AnalyticsCountItem[];
  byCategory: AnalyticsCountItem[];
  activity: AnalyticsActivityItem[];
}

