export interface RegisterRequest {
  username: string;
  password: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  username: string;
  role: string;
}

export interface AuthUser {
  username: string;
  role: string;
}

export interface MessageResponse {
  message: string;
}
