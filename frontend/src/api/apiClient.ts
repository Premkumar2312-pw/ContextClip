import { authStorage } from '../auth/authStorage';

export async function authFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  const token = authStorage.getToken();
  const headers = new Headers(init?.headers);

  if (token && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(input, {
    ...init,
    headers,
  });

  if (response.status === 401) {
    authStorage.clearAuth();
    if (typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('auth:unauthorized'));
    }
    throw new Error('Your session has expired. Please log in again.');
  }

  return response;
}
