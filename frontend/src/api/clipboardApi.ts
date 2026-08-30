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

  if (status === 502 || status === 503) {
    return 'AI service could not complete the request. Please try again.';
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
          lower.includes('null')
        ) {
          return 'AI service is unavailable. Make sure the AI service is running and try again.';
        }
        // Don't show stack traces or internal exception names
        if (!lower.includes('exception') && !lower.includes('at ') && !lower.includes('null')) {
          return rawMsg.trim();
        }
      }
    } catch {
      // not json, check plain text
      const lower = bodyText.toLowerCase();
      if (lower.includes('rate limit') || lower.includes('quota') || lower.includes('429')) {
        return 'AI service rate limit reached. Please try again later.';
      }
      if (
        lower.includes('failed to communicate with ai service') ||
        lower.includes('connection refused') ||
        lower.includes('connectexception') ||
        lower.includes('null')
      ) {
        return 'AI service is unavailable. Make sure the AI service is running and try again.';
      }
    }
  }

  if (status === 500) {
    return 'Unable to generate the AI response. Please try again.';
  }

  return 'AI service is unavailable. Make sure the AI service is running and try again.';
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
    throw new Error('AI service is unavailable. Make sure the AI service is running and try again.');
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
    throw new Error('AI service is unavailable. Make sure the AI service is running and try again.');
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
    throw new Error('AI service is unavailable. Make sure the AI service is running and try again.');
  }
}
