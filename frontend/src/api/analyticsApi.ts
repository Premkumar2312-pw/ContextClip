import {
  AnalyticsActivityItem,
  AnalyticsCountItem,
  AnalyticsData,
  AnalyticsOverview,
} from '../types/analytics';

const BASE_URL = '/api/analytics';

export async function fetchOverview(): Promise<AnalyticsOverview> {
  const response = await fetch(`${BASE_URL}/overview`);
  if (!response.ok) {
    throw new Error(`Failed to fetch overview: ${response.status} ${response.statusText}`);
  }
  return response.json();
}

export async function fetchByType(): Promise<AnalyticsCountItem[]> {
  const response = await fetch(`${BASE_URL}/by-type`);
  if (!response.ok) {
    throw new Error(`Failed to fetch type analytics: ${response.status} ${response.statusText}`);
  }
  return response.json();
}

export async function fetchByTechnology(): Promise<AnalyticsCountItem[]> {
  const response = await fetch(`${BASE_URL}/by-technology`);
  if (!response.ok) {
    throw new Error(`Failed to fetch technology analytics: ${response.status} ${response.statusText}`);
  }
  return response.json();
}

export async function fetchByCategory(): Promise<AnalyticsCountItem[]> {
  const response = await fetch(`${BASE_URL}/by-category`);
  if (!response.ok) {
    throw new Error(`Failed to fetch category analytics: ${response.status} ${response.statusText}`);
  }
  return response.json();
}

export async function fetchActivity(): Promise<AnalyticsActivityItem[]> {
  const response = await fetch(`${BASE_URL}/activity`);
  if (!response.ok) {
    throw new Error(`Failed to fetch activity analytics: ${response.status} ${response.statusText}`);
  }
  return response.json();
}

export async function fetchAllAnalytics(): Promise<AnalyticsData> {
  const [overview, byType, byTechnology, byCategory, activity] = await Promise.all([
    fetchOverview(),
    fetchByType(),
    fetchByTechnology(),
    fetchByCategory(),
    fetchActivity(),
  ]);

  return {
    overview,
    byType,
    byTechnology,
    byCategory,
    activity,
  };
}
