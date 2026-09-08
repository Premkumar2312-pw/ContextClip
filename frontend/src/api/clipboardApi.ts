import {
  ClipboardAskRequest,
  ClipboardAskResponse,
  ClipboardEntry,
  ClipboardExplanationResponse,
  ClipboardSummaryResponse,
  SearchParams,
} from '../types/clipboard';
import { authFetch } from './apiClient';

const BASE_URL = '/api/clipboard';

async function parseAiError(response: Response): Promise<string> {
  const status = response.status;

  if (status === 429) {
    return 'AI service rate limit reached. Please try again later.';
  }

  if (status === 401 || status === 403) {
    return 'AI service authentication failed. Check the AI service configuration.';
  }

  if (status === 400) {
    return 'AI service rejected the request. Please try again.';
  }

  if (status === 502 || status === 503) {
    return 'AI service is unavailable. Please try again.';
  }

  let bodyText = '';
  try {
    bodyText = await response.text();
  } catch {
    // ignore
  }

  // Try JSON parsing
  if (bodyText) {
    try {
      const json = JSON.parse(bodyText);
      const rawMsg = json.message || json.detail || json.error;
      if (typeof rawMsg === 'string' && rawMsg.trim()) {
        const lower = rawMsg.toLowerCase();
        if (lower.includes('rate limit') || lower.includes('quota') || lower.includes('429')) {
          return 'AI service rate limit reached. Please try again later.';
        }
        if (
          lower.includes('failed to communicate with ai service') ||
          lower.includes('connection refused') ||
          lower.includes('connectexception') ||
          lower.includes('unavailable') ||
          lower.includes('null')
        ) {
          return 'AI service is unavailable. Please try again.';
        }
        if (lower.includes('authentication') || lower.includes('api key') || lower.includes('unauthorized')) {
          return 'AI service authentication failed. Check the AI service configuration.';
        }
        // Don't show stack traces or internal exception names
        if (!lower.includes('exception') && !lower.includes('at ') && !lower.includes('null')) {
          return rawMsg.trim();
        }
      }
    } catch {
      const lower = bodyText.toLowerCase();
      if (lower.includes('rate limit') || lower.includes('quota') || lower.includes('429')) {
        return 'AI service rate limit reached. Please try again later.';
      }
      if (
        lower.includes('failed to communicate with ai service') ||
        lower.includes('connection refused') ||
        lower.includes('connectexception') ||
        lower.includes('unavailable') ||
        lower.includes('null')
      ) {
        return 'AI service is unavailable. Please try again.';
      }
    }
  }

  if (status === 500) {
    return 'Unable to generate the AI response. Please try again.';
  }

  return 'AI service is unavailable. Please try again.';
}

export async function getClipboardEntries(): Promise<ClipboardEntry[]> {
  const response = await authFetch(BASE_URL);
  if (!response.ok) {
    throw new Error(`Failed to fetch clipboard entries: ${response.status} ${response.statusText}`);
  }
  const entries: ClipboardEntry[] = await response.json();
  return entries.sort((a, b) => b.id - a.id);
}

export async function searchClipboard(params: SearchParams): Promise<ClipboardEntry[]> {
  const query = new URLSearchParams();
  if (params.q && params.q.trim()) {
    query.set('q', params.q.trim());
  }
  if (params.type && params.type.trim()) {
    query.set('type', params.type.trim());
  }
  if (params.technology && params.technology.trim()) {
    query.set('technology', params.technology.trim());
  }
  if (params.category && params.category.trim()) {
    query.set('category', params.category.trim());
  }

  const url = `${BASE_URL}/search?${query.toString()}`;
  const response = await authFetch(url);
  if (!response.ok) {
    throw new Error(`Unable to search clipboard history: ${response.status} ${response.statusText}`);
  }
  return response.json();
}

export async function explainClipboardEntry(id: number): Promise<ClipboardExplanationResponse> {
  try {
    const response = await authFetch(`${BASE_URL}/${id}/explain`);
    if (!response.ok) {
      const userMessage = await parseAiError(response);
      throw new Error(userMessage);
    }
    return await response.json();
  } catch (err: unknown) {
    if (err instanceof Error && err.message) {
      if (err.message.includes('session has expired')) {
        throw err;
      }
      if (!err.message.toLowerCase().includes('failed to fetch')) {
        throw err;
      }
    }
    throw new Error('AI service is unavailable. Please try again.');
  }
}

export async function summarizeClipboardEntry(id: number): Promise<ClipboardSummaryResponse> {
  try {
    const response = await authFetch(`${BASE_URL}/${id}/summarize`);
    if (!response.ok) {
      const userMessage = await parseAiError(response);
      throw new Error(userMessage);
    }
    return await response.json();
  } catch (err: unknown) {
    if (err instanceof Error && err.message) {
      if (err.message.includes('session has expired')) {
        throw err;
      }
      if (!err.message.toLowerCase().includes('failed to fetch')) {
        throw err;
      }
    }
    throw new Error('AI service is unavailable. Please try again.');
  }
}

export async function askClipboard(request: ClipboardAskRequest): Promise<ClipboardAskResponse> {
  try {
    const response = await authFetch(`${BASE_URL}/ask`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      const userMessage = await parseAiError(response);
      throw new Error(userMessage);
    }
    return await response.json();
  } catch (err: unknown) {
    if (err instanceof Error && err.message) {
      if (err.message.includes('session has expired')) {
        throw err;
      }
      if (!err.message.toLowerCase().includes('failed to fetch')) {
        throw err;
      }
    }
    throw new Error('AI service is unavailable. Please try again.');
  }
}

export async function deleteClipboardEntry(id: number): Promise<void> {
  const response = await authFetch(`${BASE_URL}/${id}`, {
    method: 'DELETE',
  });
  if (!response.ok) {
    throw new Error(`Failed to delete clipboard entry: ${response.status} ${response.statusText}`);
  }
}

export async function clearClipboardHistory(): Promise<void> {
  const response = await authFetch(BASE_URL, {
    method: 'DELETE',
  });
  if (!response.ok) {
    throw new Error(`Failed to clear clipboard history: ${response.status} ${response.statusText}`);
  }
}

export interface AgentTokenResponse {
  token: string;
  username: string;
  role: string;
}

export async function getAgentToken(): Promise<AgentTokenResponse> {
  const response = await authFetch('/api/auth/agent-token', {
    method: 'POST',
  });
  if (!response.ok) {
    throw new Error(`Failed to obtain agent token: ${response.status} ${response.statusText}`);
  }
  return await response.json();
}

export interface PairingCodeResponse {
  code: string;
  expiresInSeconds: number;
  pairUrl: string;
}

export async function createPairingCode(): Promise<PairingCodeResponse> {
  const response = await authFetch('/api/agent/pairing', {
    method: 'POST',
  });
  if (!response.ok) {
    throw new Error(`Failed to generate pairing code: ${response.status} ${response.statusText}`);
  }
  return await response.json();
}

