import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import App from './App';
import * as analyticsApi from './api/analyticsApi';
import * as clipboardApi from './api/clipboardApi';

const mockAnalyticsData = {
  overview: {
    totalEntries: 12,
    mostUsedType: 'TERMINAL_COMMAND',
    mostUsedTechnology: 'DOCKER',
    mostUsedCategory: 'DEVOPS',
  },
  byType: [
    { name: 'TERMINAL_COMMAND', count: 8 },
    { name: 'SQL', count: 2 },
  ],
  byTechnology: [
    { name: 'DOCKER', count: 4 },
    { name: 'GIT', count: 3 },
  ],
  byCategory: [
    { name: 'DEVOPS', count: 8 },
    { name: 'DATABASE', count: 2 },
  ],
  activity: [{ date: '2026-08-29', count: 12 }],
};

const mockClipboardEntries = [
  {
    id: 11,
    content: 'docker compose up --build',
    capturedAt: '2026-08-29T10:00:00Z',
    type: 'TERMINAL_COMMAND',
    technology: 'DOCKER',
    category: 'DEVOPS',
  },
  {
    id: 9,
    content: 'git push -u origin main',
    capturedAt: '2026-08-29T09:30:00Z',
    type: 'TERMINAL_COMMAND',
    technology: 'GIT',
    category: 'DEVOPS',
  },
  {
    id: 3,
    content: 'SELECT * FROM users WHERE active = true;',
    capturedAt: '2026-08-29T08:15:00Z',
    type: 'SQL',
    technology: 'SQL',
    category: 'DATABASE',
  },
];

describe('ContextClip Full Product UI Tests', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.spyOn(analyticsApi, 'fetchAllAnalytics').mockResolvedValue(mockAnalyticsData);
    vi.spyOn(clipboardApi, 'getClipboardEntries').mockResolvedValue(mockClipboardEntries);
    vi.spyOn(clipboardApi, 'searchClipboard').mockResolvedValue(mockClipboardEntries);
  });

  describe('1. Navigation & Sidebar', () => {
    it('contains no "Soon" badges and allows seamless navigation across all 4 sections', async () => {
      render(<App />);

      expect(screen.queryByText(/Soon/i)).toBeNull();

      expect(screen.getByRole('button', { name: /dashboard navigation/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /clipboard navigation/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /search navigation/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /analytics navigation/i })).toBeInTheDocument();

      // Navigate to Clipboard
      fireEvent.click(screen.getByRole('button', { name: /clipboard navigation/i }));
      expect(screen.getByText(/Clipboard History/i)).toBeInTheDocument();

      // Navigate to Search
      fireEvent.click(screen.getByRole('button', { name: /search navigation/i }));
      expect(screen.getByText(/Search Clipboard/i)).toBeInTheDocument();

      // Navigate to Analytics
      fireEvent.click(screen.getByRole('button', { name: /analytics navigation/i }));
      await waitFor(() => {
        expect(screen.getByText(/Entries by Content Type/i)).toBeInTheDocument();
      });

      // Navigate back to Dashboard
      fireEvent.click(screen.getByRole('button', { name: /dashboard navigation/i }));
      await waitFor(() => {
        expect(screen.getByText(/Recent Clipboard Captures/i)).toBeInTheDocument();
      });
    });
  });

  describe('2. Clipboard Page', () => {
    it('loads and displays clipboard entries with metadata and actions', async () => {
      render(<App />);
      fireEvent.click(screen.getByRole('button', { name: /clipboard navigation/i }));

      await waitFor(() => {
        expect(screen.getByText('docker compose up --build')).toBeInTheDocument();
      });

      expect(screen.getByText('git push -u origin main')).toBeInTheDocument();
      expect(screen.getByText('SELECT * FROM users WHERE active = true;')).toBeInTheDocument();
      expect(screen.getAllByText('Explain').length).toBe(3);
      expect(screen.getAllByText('Summarize').length).toBe(3);
    });

    it('handles clipboard refresh button', async () => {
      const getSpy = vi.spyOn(clipboardApi, 'getClipboardEntries');
      render(<App />);
      fireEvent.click(screen.getByRole('button', { name: /clipboard navigation/i }));

      await waitFor(() => {
        expect(screen.getByText('docker compose up --build')).toBeInTheDocument();
      });

      const initialCallCount = getSpy.mock.calls.length;
      const refreshBtn = screen.getByRole('button', { name: /refresh clipboard/i });
      fireEvent.click(refreshBtn);

      await waitFor(() => {
        expect(getSpy.mock.calls.length).toBe(initialCallCount + 1);
      });
    });

    it('handles empty clipboard state', async () => {
      vi.spyOn(clipboardApi, 'getClipboardEntries').mockResolvedValue([]);
      render(<App />);
      fireEvent.click(screen.getByRole('button', { name: /clipboard navigation/i }));

      await waitFor(() => {
        expect(screen.getByText(/No Clipboard Entries Found/i)).toBeInTheDocument();
      });
    });

    it('handles clipboard error state with retry', async () => {
      vi.spyOn(clipboardApi, 'getClipboardEntries').mockRejectedValue(
        new Error('Failed to communicate with PostgreSQL')
      );
      render(<App />);
      fireEvent.click(screen.getByRole('button', { name: /clipboard navigation/i }));

      await waitFor(() => {
        expect(screen.getByRole('alert')).toBeInTheDocument();
      });
      expect(screen.getByText(/Failed to communicate with PostgreSQL/i)).toBeInTheDocument();
    });
  });

  describe('3. Markdown Rendering in AI Panels (Requirements 1-6, 11-12)', () => {
    it('renders headings, bold, inline code, lists, and code blocks without raw markdown markers', async () => {
      const markdownSample = `### Command Overview
This command **builds images** and runs \`docker compose\`.

### Parameters
* \`up\`: Start containers
* \`--build\`: Build before starting

\`\`\`bash
docker compose up -d --build
\`\`\``;

      vi.spyOn(clipboardApi, 'explainClipboardEntry').mockResolvedValue({
        id: 11,
        explanation: markdownSample,
      });

      render(<App />);
      fireEvent.click(screen.getByRole('button', { name: /clipboard navigation/i }));

      await waitFor(() => {
        expect(screen.getByText('docker compose up --build')).toBeInTheDocument();
      });

      const explainBtn = screen.getByRole('button', { name: 'Explain entry 11' });
      fireEvent.click(explainBtn);

      // Verify Heading
      await waitFor(() => {
        const heading = screen.getByRole('heading', { level: 5, name: 'Command Overview' });
        expect(heading).toBeInTheDocument();
      });

      // Verify Bold Text
      const boldElem = screen.getByText('builds images');
      expect(boldElem.tagName).toBe('STRONG');

      // Verify Inline Code
      expect(screen.getByText('docker compose')).toBeInTheDocument();

      // Verify Bullet List Items
      expect(screen.getByText(/Start containers/i)).toBeInTheDocument();
      expect(screen.getByText(/Build before starting/i)).toBeInTheDocument();

      // Verify Code Block Content
      const codeBlockMatches = screen.getAllByText((content) =>
        content.includes('docker compose up -d --build')
      );
      expect(codeBlockMatches.length).toBeGreaterThanOrEqual(1);

      // Verify Raw Markdown markers are not shown literally
      expect(screen.queryByText('### Command Overview')).toBeNull();
      expect(screen.queryByText('**builds images**')).toBeNull();
    });

    it('renders AI Summary markdown cleanly', async () => {
      vi.spyOn(clipboardApi, 'summarizeClipboardEntry').mockResolvedValue({
        id: 9,
        summary: '**Pushes** commits from \`main\` to *origin*.\n\n- Updates remote tracking branch.',
      });

      render(<App />);
      fireEvent.click(screen.getByRole('button', { name: /clipboard navigation/i }));

      await waitFor(() => {
        expect(screen.getByText('git push -u origin main')).toBeInTheDocument();
      });

      const summarizeBtn = screen.getByRole('button', { name: 'Summarize entry 9' });
      fireEvent.click(summarizeBtn);

      await waitFor(() => {
        expect(screen.getByText('Pushes')).toBeInTheDocument();
      });
      expect(screen.getByText('Updates remote tracking branch.')).toBeInTheDocument();
    });
  });

  describe('4. AI Error Handling (Requirements 7-10)', () => {
    it('shows friendly service-unavailable message on network failure', async () => {
      vi.spyOn(clipboardApi, 'explainClipboardEntry').mockRejectedValue(
        new Error('AI service is unavailable. Make sure the AI service is running and try again.')
      );

      render(<App />);
      fireEvent.click(screen.getByRole('button', { name: /clipboard navigation/i }));

      await waitFor(() => {
        expect(screen.getByText('docker compose up --build')).toBeInTheDocument();
      });

      const explainBtn = screen.getByRole('button', { name: 'Explain entry 11' });
      fireEvent.click(explainBtn);

      await waitFor(() => {
        expect(
          screen.getByText('AI service is unavailable. Make sure the AI service is running and try again.')
        ).toBeInTheDocument();
      });
      expect(screen.queryByText(/null/i)).toBeNull();
    });

    it('shows friendly HTTP 502 failure message', async () => {
      vi.spyOn(clipboardApi, 'explainClipboardEntry').mockRejectedValue(
        new Error('AI service could not complete the request. Please try again.')
      );

      render(<App />);
      fireEvent.click(screen.getByRole('button', { name: /clipboard navigation/i }));

      await waitFor(() => {
        expect(screen.getByText('docker compose up --build')).toBeInTheDocument();
      });

      const explainBtn = screen.getByRole('button', { name: 'Explain entry 11' });
      fireEvent.click(explainBtn);

      await waitFor(() => {
        expect(
          screen.getByText('AI service could not complete the request. Please try again.')
        ).toBeInTheDocument();
      });
    });

    it('shows useful HTTP 429 rate-limit message when provided', async () => {
      vi.spyOn(clipboardApi, 'explainClipboardEntry').mockRejectedValue(
        new Error('AI service rate limit reached. Please try again later.')
      );

      render(<App />);
      fireEvent.click(screen.getByRole('button', { name: /clipboard navigation/i }));

      await waitFor(() => {
        expect(screen.getByText('docker compose up --build')).toBeInTheDocument();
      });

      const explainBtn = screen.getByRole('button', { name: 'Explain entry 11' });
      fireEvent.click(explainBtn);

      await waitFor(() => {
        expect(
          screen.getByText('AI service rate limit reached. Please try again later.')
        ).toBeInTheDocument();
      });
    });

    it('never displays "null" in AI error messages', async () => {
      vi.spyOn(clipboardApi, 'summarizeClipboardEntry').mockRejectedValue(
        new Error('AI service is unavailable. Make sure the AI service is running and try again.')
      );

      render(<App />);
      fireEvent.click(screen.getByRole('button', { name: /clipboard navigation/i }));

      await waitFor(() => {
        expect(screen.getByText('git push -u origin main')).toBeInTheDocument();
      });

      const summarizeBtn = screen.getByRole('button', { name: 'Summarize entry 9' });
      fireEvent.click(summarizeBtn);

      await waitFor(() => {
        expect(
          screen.getByText('AI service is unavailable. Make sure the AI service is running and try again.')
        ).toBeInTheDocument();
      });
      expect(screen.queryByText(/Failed to communicate with AI Service: null/i)).toBeNull();
    });
  });

  describe('5. Search Page & Actions (Requirements 13-14)', () => {
    it('executes keyword search', async () => {
      const searchSpy = vi.spyOn(clipboardApi, 'searchClipboard').mockResolvedValue([
        mockClipboardEntries[0],
      ]);

      render(<App />);
      fireEvent.click(screen.getByRole('button', { name: /search navigation/i }));

      const searchInput = screen.getByLabelText(/search clipboard input/i);
      fireEvent.change(searchInput, { target: { value: 'docker' } });
      fireEvent.submit(searchInput.closest('form')!);

      await waitFor(() => {
        expect(searchSpy).toHaveBeenCalledWith(
          expect.objectContaining({ q: 'docker' })
        );
      });
    });

    it('executes filters and combined search', async () => {
      const searchSpy = vi.spyOn(clipboardApi, 'searchClipboard');

      render(<App />);
      fireEvent.click(screen.getByRole('button', { name: /search navigation/i }));

      const searchInput = screen.getByLabelText(/search clipboard input/i);
      fireEvent.change(searchInput, { target: { value: 'docker' } });

      const techSelect = screen.getByLabelText(/filter by technology/i);
      fireEvent.change(techSelect, { target: { value: 'DOCKER' } });

      const catSelect = screen.getByLabelText(/filter by category/i);
      fireEvent.change(catSelect, { target: { value: 'DEVOPS' } });

      await waitFor(() => {
        expect(searchSpy).toHaveBeenCalledWith(
          expect.objectContaining({
            q: 'docker',
            technology: 'DOCKER',
            category: 'DEVOPS',
          })
        );
      });
    });

    it('allows Explain and Summarize with Markdown from search result items', async () => {
      vi.spyOn(clipboardApi, 'explainClipboardEntry').mockResolvedValue({
        id: 11,
        explanation: '### Search Explanation\n**Docker** compose command.',
      });
      vi.spyOn(clipboardApi, 'summarizeClipboardEntry').mockResolvedValue({
        id: 11,
        summary: '- Search summary item',
      });

      render(<App />);
      fireEvent.click(screen.getByRole('button', { name: /search navigation/i }));

      await waitFor(() => {
        expect(screen.getByText('docker compose up --build')).toBeInTheDocument();
      });

      // Explain
      const explainBtn = screen.getByRole('button', { name: 'Explain entry 11' });
      fireEvent.click(explainBtn);
      await waitFor(() => {
        expect(screen.getByText('Search Explanation')).toBeInTheDocument();
      });
      expect(screen.getByText('Docker')).toBeInTheDocument();

      // Summarize
      const summarizeBtn = screen.getByRole('button', { name: 'Summarize entry 11' });
      fireEvent.click(summarizeBtn);
      await waitFor(() => {
        expect(screen.getByText('Search summary item')).toBeInTheDocument();
      });
    });
  });
});
