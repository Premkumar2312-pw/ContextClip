import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import App from './App';
import * as analyticsApi from './api/analyticsApi';

describe('ContextClip Analytics Dashboard', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('renders loading state initially', () => {
    vi.spyOn(analyticsApi, 'fetchAllAnalytics').mockReturnValue(
      new Promise(() => {}) // never resolves to keep loading
    );

    render(<App />);
    expect(screen.getByLabelText(/loading analytics data/i)).toBeInTheDocument();
  });

  it('renders error state when backend API call fails', async () => {
    vi.spyOn(analyticsApi, 'fetchAllAnalytics').mockRejectedValue(
      new Error('Unable to connect to backend server')
    );

    render(<App />);

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument();
    });

    expect(screen.getByText(/Unable to connect to backend server/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /retry connection/i })).toBeInTheDocument();
  });

  it('renders empty state when totalEntries is 0 and lists are empty', async () => {
    vi.spyOn(analyticsApi, 'fetchAllAnalytics').mockResolvedValue({
      overview: {
        totalEntries: 0,
        mostUsedType: null,
        mostUsedTechnology: null,
        mostUsedCategory: null,
      },
      byType: [],
      byTechnology: [],
      byCategory: [],
      activity: [],
    });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText(/No Clipboard Activity Yet/i)).toBeInTheDocument();
    });

    expect(screen.getByText(/Your clipboard history is currently empty/i)).toBeInTheDocument();
  });

  it('renders full dashboard metrics and charts when data is available', async () => {
    vi.spyOn(analyticsApi, 'fetchAllAnalytics').mockResolvedValue({
      overview: {
        totalEntries: 12,
        mostUsedType: 'TERMINAL_COMMAND',
        mostUsedTechnology: 'DOCKER',
        mostUsedCategory: 'DEVOPS',
      },
      byType: [
        { name: 'TERMINAL_COMMAND', count: 8 },
        { name: 'SQL', count: 2 },
        { name: 'CODE', count: 1 },
        { name: 'TEXT', count: 1 },
      ],
      byTechnology: [
        { name: 'DOCKER', count: 4 },
        { name: 'GIT', count: 3 },
        { name: 'SQL', count: 2 },
      ],
      byCategory: [
        { name: 'DEVOPS', count: 8 },
        { name: 'DATABASE', count: 2 },
      ],
      activity: [{ date: '2026-08-29', count: 12 }],
    });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText('12')).toBeInTheDocument();
    });

    // Check Overview metrics & Charts
    expect(screen.getAllByText('DOCKER').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('TERMINAL_COMMAND').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('DEVOPS').length).toBeGreaterThanOrEqual(1);

    // Check Chart Sections
    expect(screen.getByText(/Entries by Technology/i)).toBeInTheDocument();
    expect(screen.getByText(/Entries by Content Type/i)).toBeInTheDocument();
    expect(screen.getByText(/Entries by Category/i)).toBeInTheDocument();
    expect(screen.getByText(/Clipboard Activity Over Time/i)).toBeInTheDocument();
  });
});
