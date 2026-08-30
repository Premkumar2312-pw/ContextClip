import {
  AuthResponse,
  LoginRequest,
  MessageResponse,
  RegisterRequest,
} from '../types/auth';

const BASE_URL = '/api/auth';

export async function registerUser(request: RegisterRequest): Promise<MessageResponse> {
  const response = await fetch(`${BASE_URL}/register`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    let errorMsg = 'Failed to register. Please try again.';
    try {
      const data = await response.json();
      if (response.status === 409) {
        errorMsg = 'Username already exists. Please choose another.';
      } else if (response.status === 400) {
        errorMsg = data.message || 'Invalid registration details. Password must be at least 6 characters.';
      } else if (data.message) {
        errorMsg = data.message;
      }
    } catch {
      if (response.status === 409) {
        errorMsg = 'Username already exists. Please choose another.';
      }
    }
    throw new Error(errorMsg);
  }

  return response.json();
}

export async function loginUser(request: LoginRequest): Promise<AuthResponse> {
  const response = await fetch(`${BASE_URL}/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error('Invalid username or password.');
    }
    let errorMsg = 'Failed to log in. Please try again.';
    try {
      const data = await response.json();
      if (data.message) {
        errorMsg = data.message;
      }
    } catch {
      // fallback
    }
    throw new Error(errorMsg);
  }

  return response.json();
}
