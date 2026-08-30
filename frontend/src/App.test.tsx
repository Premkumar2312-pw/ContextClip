import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import App from './App';
import * as analyticsApi from './api/analyticsApi';
import * as clipboardApi from './api/clipboardApi';
import * as authApi from './api/authApi';
import { authStorage } from './auth/authStorage';

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

describe('ContextClip Authentication and Application UI Tests', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    authStorage.clearAuth();
    vi.spyOn(analyticsApi, 'fetchAllAnalytics').mockResolvedValue(mockAnalyticsData);
    vi.spyOn(clipboardApi, 'getClipboardEntries').mockResolvedValue(mockClipboardEntries);
    vi.spyOn(clipboardApi, 'searchClipboard').mockResolvedValue(mockClipboardEntries);
  });

  describe('1. Authentication Flow (Login & Register)', () => {
    it('1. Login page renders branding, inputs, and links', () => {
      render(<App />);

      expect(screen.getByTestId('login-card')).toBeInTheDocument();
      expect(screen.getByText('ContextClip')).toBeInTheDocument();
      expect(screen.getByTestId('login-username')).toBeInTheDocument();
      expect(screen.getByTestId('login-password')).toBeInTheDocument();
      expect(screen.getByTestId('login-submit')).toBeInTheDocument();
      expect(screen.getByTestId('link-to-register')).toBeInTheDocument();
    });

    it('2. Register page renders branding, inputs, and links', () => {
      render(<App />);

      fireEvent.click(screen.getByTestId('link-to-register'));

      expect(screen.getByTestId('register-card')).toBeInTheDocument();
      expect(screen.getByRole('heading', { name: 'Create Account' })).toBeInTheDocument();
      expect(screen.getByTestId('register-username')).toBeInTheDocument();
      expect(screen.getByTestId('register-password')).toBeInTheDocument();
      expect(screen.getByTestId('register-confirm-password')).toBeInTheDocument();
      expect(screen.getByTestId('register-submit')).toBeInTheDocument();
      expect(screen.getByTestId('link-to-login')).toBeInTheDocument();
    });

    it('3. Successful login stores authentication state and shows app shell', async () => {
      vi.spyOn(authApi, 'loginUser').mockResolvedValue({
        token: 'test-jwt-token-123',
        username: 'premtest',
        role: 'USER',
      });

      render(<App />);

      fireEvent.change(screen.getByTestId('login-username'), { target: { value: 'premtest' } });
      fireEvent.change(screen.getByTestId('login-password'), { target: { value: 'StrongPassword123!' } });
      fireEvent.click(screen.getByTestId('login-submit'));

      await waitFor(() => {
        expect(screen.getByTestId('user-profile')).toBeInTheDocument();
      });

      expect(screen.getByText('premtest')).toBeInTheDocument();
      expect(authStorage.getToken()).toBe('test-jwt-token-123');
      expect(authStorage.getUser()?.username).toBe('premtest');
      expect(authStorage.getUser()?.role).toBe('USER');
    });

    it('4. Invalid login shows friendly error message', async () => {
      vi.spyOn(authApi, 'loginUser').mockRejectedValue(new Error('Invalid username or password.'));

      render(<App />);

      fireEvent.change(screen.getByTestId('login-username'), { target: { value: 'wronguser' } });
      fireEvent.change(screen.getByTestId('login-password'), { target: { value: 'wrongpass' } });
      fireEvent.click(screen.getByTestId('login-submit'));

      await waitFor(() => {
        expect(screen.getByTestId('login-error')).toBeInTheDocument();
      });
      expect(screen.getByText('Invalid username or password.')).toBeInTheDocument();
      expect(authStorage.getToken()).toBeNull();
    });

    it('5. Registration validation catches empty fields', async () => {
      render(<App />);
      fireEvent.click(screen.getByTestId('link-to-register'));

      fireEvent.click(screen.getByTestId('register-submit'));

      await waitFor(() => {
        expect(screen.getByTestId('register-error')).toBeInTheDocument();
      });
      expect(screen.getByText('Username is required.')).toBeInTheDocument();
    });

    it('6. Password confirmation mismatch shows friendly error', async () => {
      render(<App />);
      fireEvent.click(screen.getByTestId('link-to-register'));

      fireEvent.change(screen.getByTestId('register-username'), { target: { value: 'prem' } });
      fireEvent.change(screen.getByTestId('register-password'), { target: { value: 'Password123!' } });
      fireEvent.change(screen.getByTestId('register-confirm-password'), { target: { value: 'DifferentPassword!' } });
      fireEvent.click(screen.getByTestId('register-submit'));

      await waitFor(() => {
        expect(screen.getByTestId('register-error')).toBeInTheDocument();
      });
      expect(screen.getByText('Passwords do not match.')).toBeInTheDocument();
    });

    it('7. Successful registration redirects to login with success message', async () => {
      vi.spyOn(authApi, 'registerUser').mockResolvedValue({
        message: 'User registered successfully',
      });

      render(<App />);
      fireEvent.click(screen.getByTestId('link-to-register'));

      fireEvent.change(screen.getByTestId('register-username'), { target: { value: 'newuser' } });
      fireEvent.change(screen.getByTestId('register-password'), { target: { value: 'Password123!' } });
      fireEvent.change(screen.getByTestId('register-confirm-password'), { target: { value: 'Password123!' } });
      fireEvent.click(screen.getByTestId('register-submit'));

      await waitFor(() => {
        expect(screen.getByTestId('login-card')).toBeInTheDocument();
      });
      expect(screen.getByTestId('login-success')).toBeInTheDocument();
      expect(screen.getByText('User registered successfully')).toBeInTheDocument();
    });

    it('8. Protected route redirects to login when unauthenticated', () => {
      authStorage.clearAuth();
      render(<App />);

      expect(screen.getByTestId('login-card')).toBeInTheDocument();
      expect(screen.queryByTestId('user-profile')).toBeNull();
    });

    it('9. Authenticated route renders with valid auth state', () => {
      authStorage.setAuth('valid-token-xyz', { username: 'testuser', role: 'USER' });

      render(<App />);

      expect(screen.getByTestId('user-profile')).toBeInTheDocument();
      expect(screen.getByText('testuser')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /dashboard navigation/i })).toBeInTheDocument();
    });

    it('10. Logout clears authentication and redirects to login', async () => {
      authStorage.setAuth('valid-token-xyz', { username: 'testuser', role: 'USER' });

      render(<App />);

      expect(screen.getByTestId('user-profile')).toBeInTheDocument();

      const logoutBtn = screen.getByTestId('logout-button');
      fireEvent.click(logoutBtn);

      await waitFor(() => {
        expect(screen.getByTestId('login-card')).toBeInTheDocument();
      });
      expect(authStorage.getToken()).toBeNull();
      expect(authStorage.getUser()).toBeNull();
    });

    it('11. HTTP 401 unauthorized clears session and shows session expired message', async () => {
      authStorage.setAuth('expired-token', { username: 'expireduser', role: 'USER' });

      render(<App />);

      // Simulate 401 event
      window.dispatchEvent(new CustomEvent('auth:unauthorized'));

      await waitFor(() => {
        expect(screen.getByTestId('login-card')).toBeInTheDocument();
      });
      expect(screen.getByText('Your session has expired. Please log in again.')).toBeInTheDocument();
      expect(authStorage.getToken()).toBeNull();
    });

    it('12. No password is ever stored in localStorage', () => {
      authStorage.setAuth('some-token', { username: 'secureuser', role: 'USER' });

      const allKeys = Object.keys(localStorage);
      for (const key of allKeys) {
        const value = localStorage.getItem(key) || '';
        expect(value.toLowerCase()).not.toContain('password');
      }
    });
  });

  describe('2. Authenticated Application Pages', () => {
    beforeEach(() => {
      authStorage.setAuth('valid-token-abc', { username: 'devuser', role: 'USER' });
    });

    it('13. Dashboard works after login', async () => {
      render(<App />);

      await waitFor(() => {
        expect(screen.getByText(/Recent Clipboard Captures/i)).toBeInTheDocument();
      });
      expect(screen.getByText('docker compose up --build')).toBeInTheDocument();
    });

    it('14. Clipboard page works after login', async () => {
      render(<App />);

      fireEvent.click(screen.getByRole('button', { name: /clipboard navigation/i }));

      await waitFor(() => {
        expect(screen.getByText('Clipboard History')).toBeInTheDocument();
      });
      expect(screen.getByText('git push -u origin main')).toBeInTheDocument();
    });

    it('15. Search page works after login', async () => {
      render(<App />);

      fireEvent.click(screen.getByRole('button', { name: /search navigation/i }));

      await waitFor(() => {
        expect(screen.getByText('Search Clipboard')).toBeInTheDocument();
      });

      const searchInput = screen.getByLabelText(/search clipboard input/i);
      fireEvent.change(searchInput, { target: { value: 'docker' } });
      fireEvent.submit(searchInput.closest('form')!);

      await waitFor(() => {
        expect(clipboardApi.searchClipboard).toHaveBeenCalledWith(
          expect.objectContaining({ q: 'docker' })
        );
      });
    });

    it('16. AI Explain works after login', async () => {
      vi.spyOn(clipboardApi, 'explainClipboardEntry').mockResolvedValue({
        id: 11,
        explanation: '### Docker Explanation\nStarts Docker Compose services.',
      });

      render(<App />);
      fireEvent.click(screen.getByRole('button', { name: /clipboard navigation/i }));

      await waitFor(() => {
        expect(screen.getByText('docker compose up --build')).toBeInTheDocument();
      });

      const explainBtn = screen.getByRole('button', { name: 'Explain entry 11' });
      fireEvent.click(explainBtn);

      await waitFor(() => {
        expect(screen.getByText('Docker Explanation')).toBeInTheDocument();
      });
    });

    it('17. AI Summarize works after login', async () => {
      vi.spyOn(clipboardApi, 'summarizeClipboardEntry').mockResolvedValue({
        id: 9,
        summary: '**Git Summary:** Pushes branch to origin.',
      });

      render(<App />);
      fireEvent.click(screen.getByRole('button', { name: /clipboard navigation/i }));

      await waitFor(() => {
        expect(screen.getByText('git push -u origin main')).toBeInTheDocument();
      });

      const summarizeBtn = screen.getByRole('button', { name: 'Summarize entry 9' });
      fireEvent.click(summarizeBtn);

      await waitFor(() => {
        expect(screen.getByText('Git Summary:')).toBeInTheDocument();
      });
    });

    it('18. Analytics works after login', async () => {
      render(<App />);
      fireEvent.click(screen.getByRole('button', { name: /analytics navigation/i }));

      await waitFor(() => {
        expect(screen.getByText('Entries by Content Type')).toBeInTheDocument();
      });
      expect(screen.getByText('Entries by Technology')).toBeInTheDocument();
    });

    it('19. Ask My Clipboard API works after login', async () => {
      vi.spyOn(clipboardApi, 'askClipboard').mockResolvedValue({
        answer: 'You have 2 Docker commands.',
        sources: [11, 9],
      });

      const res = await clipboardApi.askClipboard({ question: 'What Docker commands do I have?' });
      expect(res.answer).toBe('You have 2 Docker commands.');
      expect(res.sources).toEqual([11, 9]);
    });

    it('20. Sidebar navigation allows switching across all 4 sections with no "Soon" badges', async () => {
      render(<App />);

      expect(screen.queryByText(/Soon/i)).toBeNull();

      fireEvent.click(screen.getByRole('button', { name: /search navigation/i }));
      expect(screen.getByText('Search Clipboard')).toBeInTheDocument();

      fireEvent.click(screen.getByRole('button', { name: /analytics navigation/i }));
      await waitFor(() => {
        expect(screen.getByText('Entries by Content Type')).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('button', { name: /clipboard navigation/i }));
      expect(screen.getByText('Clipboard History')).toBeInTheDocument();
    });
  });

  describe('3. Ask My Clipboard Feature', () => {
    beforeEach(() => {
      authStorage.setAuth('valid-token-ask', { username: 'askuser', role: 'USER' });
      vi.spyOn(clipboardApi, 'getClipboardEntries').mockResolvedValue(mockClipboardEntries);
    });

    it('21. Ask My Clipboard navigation item exists in sidebar', () => {
      render(<App />);
      expect(
        screen.getByRole('button', { name: /ask navigation/i })
      ).toBeInTheDocument();
    });

    it('22. Ask My Clipboard page renders title and question input', async () => {
      render(<App />);
      fireEvent.click(screen.getByRole('button', { name: /ask navigation/i }));

      await waitFor(() => {
        expect(screen.getByRole('heading', { name: 'Ask My Clipboard' })).toBeInTheDocument();
      });
      expect(screen.getByTestId('ask-question-input')).toBeInTheDocument();
      expect(screen.getByTestId('ask-submit-btn')).toBeInTheDocument();
    });

    it('23. Empty question shows validation error', async () => {
      render(<App />);
      fireEvent.click(screen.getByRole('button', { name: /ask navigation/i }));

      await waitFor(() => expect(screen.getByTestId('ask-submit-btn')).toBeInTheDocument());

      fireEvent.click(screen.getByTestId('ask-submit-btn'));

      expect(screen.getByTestId('ask-validation-error')).toBeInTheDocument();
      expect(screen.getByText('Please enter a question.')).toBeInTheDocument();
    });

    it('24. Whitespace-only question shows validation error', async () => {
      render(<App />);
      fireEvent.click(screen.getByRole('button', { name: /ask navigation/i }));

      await waitFor(() => expect(screen.getByTestId('ask-question-input')).toBeInTheDocument());

      fireEvent.change(screen.getByTestId('ask-question-input'), { target: { value: '   ' } });
      fireEvent.click(screen.getByTestId('ask-submit-btn'));

      expect(screen.getByTestId('ask-validation-error')).toBeInTheDocument();
      expect(screen.getByText('Please enter a question.')).toBeInTheDocument();
    });

    it('25. Question exceeding 2000 characters shows validation error', async () => {
      render(<App />);
      fireEvent.click(screen.getByRole('button', { name: /ask navigation/i }));

      await waitFor(() => expect(screen.getByTestId('ask-question-input')).toBeInTheDocument());

      const longQ = 'a'.repeat(2001);
      fireEvent.change(screen.getByTestId('ask-question-input'), { target: { value: longQ } });
      fireEvent.click(screen.getByTestId('ask-submit-btn'));

      expect(screen.getByTestId('ask-validation-error')).toBeInTheDocument();
      expect(screen.getByText(/2000 characters or fewer/i)).toBeInTheDocument();
    });

    it('26. Successful API request renders answer through MarkdownView', async () => {
      vi.spyOn(clipboardApi, 'askClipboard').mockResolvedValue({
        answer: '**You have 2 Docker commands.**',
        sources: [11, 9],
      });

      render(<App />);
      fireEvent.click(screen.getByRole('button', { name: /ask navigation/i }));

      await waitFor(() => expect(screen.getByTestId('ask-question-input')).toBeInTheDocument());

      fireEvent.change(screen.getByTestId('ask-question-input'), {
        target: { value: 'What Docker commands do I have?' },
      });
      fireEvent.click(screen.getByTestId('ask-submit-btn'));

      await waitFor(() => {
        expect(screen.getByTestId('ask-answer-panel')).toBeInTheDocument();
      });

      // MarkdownView renders **text** as <strong>
      expect(screen.getByText('You have 2 Docker commands.')).toBeInTheDocument();
    });

    it('27. askClipboard is called with JWT Authorization header (via authFetch)', async () => {
      const askSpy = vi.spyOn(clipboardApi, 'askClipboard').mockResolvedValue({
        answer: 'Docker commands: docker compose up --build',
        sources: [11],
      });

      render(<App />);
      fireEvent.click(screen.getByRole('button', { name: /ask navigation/i }));

      await waitFor(() => expect(screen.getByTestId('ask-question-input')).toBeInTheDocument());

      fireEvent.change(screen.getByTestId('ask-question-input'), {
        target: { value: 'What Docker commands do I have?' },
      });
      fireEvent.click(screen.getByTestId('ask-submit-btn'));

      await waitFor(() => {
        expect(askSpy).toHaveBeenCalledWith({ question: 'What Docker commands do I have?' });
      });
    });

    it('28. Source IDs render after successful response', async () => {
      vi.spyOn(clipboardApi, 'askClipboard').mockResolvedValue({
        answer: 'Here are your Docker commands.',
        sources: [11, 9],
      });

      render(<App />);
      fireEvent.click(screen.getByRole('button', { name: /ask navigation/i }));

      await waitFor(() => expect(screen.getByTestId('ask-question-input')).toBeInTheDocument());

      fireEvent.change(screen.getByTestId('ask-question-input'), {
        target: { value: 'What Docker commands?' },
      });
      fireEvent.click(screen.getByTestId('ask-submit-btn'));

      await waitFor(() => {
        expect(screen.getByTestId('ask-sources-panel')).toBeInTheDocument();
      });

      expect(screen.getByTestId('ask-source-11')).toBeInTheDocument();
      expect(screen.getByTestId('ask-source-9')).toBeInTheDocument();
    });

    it('29. Loading state disables the Ask button while request is in flight', async () => {
      let resolveAsk!: (val: { answer: string; sources: number[] }) => void;
      vi.spyOn(clipboardApi, 'askClipboard').mockReturnValue(
        new Promise((res) => { resolveAsk = res; })
      );

      render(<App />);
      fireEvent.click(screen.getByRole('button', { name: /ask navigation/i }));

      await waitFor(() => expect(screen.getByTestId('ask-question-input')).toBeInTheDocument());

      fireEvent.change(screen.getByTestId('ask-question-input'), {
        target: { value: 'What is in my clipboard?' },
      });
      fireEvent.click(screen.getByTestId('ask-submit-btn'));

      expect(screen.getByTestId('ask-submit-btn')).toBeDisabled();
      expect(screen.getByText(/thinking/i)).toBeInTheDocument();

      resolveAsk({ answer: 'Some answer', sources: [] });

      await waitFor(() => {
        expect(screen.getByTestId('ask-submit-btn')).not.toBeDisabled();
      });
    });

    it('30. Generic error state shows error panel', async () => {
      vi.spyOn(clipboardApi, 'askClipboard').mockRejectedValue(
        new Error('Something went wrong.')
      );

      render(<App />);
      fireEvent.click(screen.getByRole('button', { name: /ask navigation/i }));

      await waitFor(() => expect(screen.getByTestId('ask-question-input')).toBeInTheDocument());

      fireEvent.change(screen.getByTestId('ask-question-input'), {
        target: { value: 'Test question?' },
      });
      fireEvent.click(screen.getByTestId('ask-submit-btn'));

      await waitFor(() => {
        expect(screen.getByTestId('ask-error-panel')).toBeInTheDocument();
      });
      expect(screen.getByTestId('ask-error-message')).toBeInTheDocument();
    });

    it('31. AI unavailable message surfaces correctly', async () => {
      vi.spyOn(clipboardApi, 'askClipboard').mockRejectedValue(
        new Error('AI service is unavailable. Make sure the AI service is running and try again.')
      );

      render(<App />);
      fireEvent.click(screen.getByRole('button', { name: /ask navigation/i }));

      await waitFor(() => expect(screen.getByTestId('ask-question-input')).toBeInTheDocument());

      fireEvent.change(screen.getByTestId('ask-question-input'), {
        target: { value: 'What is in my clipboard?' },
      });
      fireEvent.click(screen.getByTestId('ask-submit-btn'));

      await waitFor(() => {
        expect(screen.getByText(/AI service is unavailable/i)).toBeInTheDocument();
      });
    });

    it('32. Rate-limit 429 message surfaces correctly', async () => {
      vi.spyOn(clipboardApi, 'askClipboard').mockRejectedValue(
        new Error('AI service rate limit reached. Please try again later.')
      );

      render(<App />);
      fireEvent.click(screen.getByRole('button', { name: /ask navigation/i }));

      await waitFor(() => expect(screen.getByTestId('ask-question-input')).toBeInTheDocument());

      fireEvent.change(screen.getByTestId('ask-question-input'), {
        target: { value: 'What is in my clipboard?' },
      });
      fireEvent.click(screen.getByTestId('ask-submit-btn'));

      await waitFor(() => {
        expect(screen.getByText(/rate limit/i)).toBeInTheDocument();
      });
    });

    it('33. 401 event on Ask page clears session and returns to login', async () => {
      render(<App />);
      fireEvent.click(screen.getByRole('button', { name: /ask navigation/i }));

      await waitFor(() =>
        expect(screen.getByRole('heading', { name: 'Ask My Clipboard' })).toBeInTheDocument()
      );

      window.dispatchEvent(new CustomEvent('auth:unauthorized'));

      await waitFor(() => {
        expect(screen.getByTestId('login-card')).toBeInTheDocument();
      });
      expect(authStorage.getToken()).toBeNull();
    });

    it('34. Empty clipboard history shows empty-state panel', async () => {
      vi.spyOn(clipboardApi, 'getClipboardEntries').mockResolvedValue([]);

      render(<App />);
      fireEvent.click(screen.getByRole('button', { name: /ask navigation/i }));

      await waitFor(() => {
        expect(screen.getByTestId('ask-empty-state')).toBeInTheDocument();
      });
      expect(screen.getByText(/No clipboard history available yet/i)).toBeInTheDocument();
    });

    it('35. Asking a second question replaces the previous result', async () => {
      vi.spyOn(clipboardApi, 'askClipboard')
        .mockResolvedValueOnce({ answer: 'First answer', sources: [11] })
        .mockResolvedValueOnce({ answer: 'Second answer', sources: [9] });

      render(<App />);
      fireEvent.click(screen.getByRole('button', { name: /ask navigation/i }));

      await waitFor(() => expect(screen.getByTestId('ask-question-input')).toBeInTheDocument());

      // First question
      fireEvent.change(screen.getByTestId('ask-question-input'), {
        target: { value: 'First question?' },
      });
      fireEvent.click(screen.getByTestId('ask-submit-btn'));

      await waitFor(() => {
        expect(screen.getByText('First answer')).toBeInTheDocument();
      });

      // Second question — overwrite previous result
      fireEvent.change(screen.getByTestId('ask-question-input'), {
        target: { value: 'Second question?' },
      });
      fireEvent.click(screen.getByTestId('ask-submit-btn'));

      await waitFor(() => {
        expect(screen.getByText('Second answer')).toBeInTheDocument();
      });
      expect(screen.queryByText('First answer')).not.toBeInTheDocument();
    });
  });
});
