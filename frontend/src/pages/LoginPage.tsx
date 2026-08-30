import React, { useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { Layers, ArrowRight, AlertCircle, CheckCircle, Lock } from 'lucide-react';

interface LoginPageProps {
  onNavigateToRegister: () => void;
  successMessage?: string | null;
}

export const LoginPage: React.FC<LoginPageProps> = ({
  onNavigateToRegister,
  successMessage,
}) => {
  const { login, authError, setAuthError } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [localError, setLocalError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLocalError(null);
    setAuthError(null);

    if (!username.trim()) {
      setLocalError('Username is required.');
      return;
    }
    if (!password) {
      setLocalError('Password is required.');
      return;
    }

    setLoading(true);
    try {
      await login({ username: username.trim(), password });
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Invalid username or password.';
      setLocalError(msg);
    } finally {
      setLoading(false);
    }
  };

  const displayError = localError || authError;

  return (
    <div className="auth-page-container">
      <div className="auth-card" data-testid="login-card">
        <div className="auth-header">
          <div className="auth-logo-badge">
            <Layers className="auth-logo-icon" size={24} />
          </div>
          <h1 className="auth-title">ContextClip</h1>
          <p className="auth-subtitle">Sign in to your intelligent clipboard history</p>
        </div>

        {successMessage && (
          <div className="auth-banner success" data-testid="login-success" role="status">
            <CheckCircle size={16} />
            <span>{successMessage}</span>
          </div>
        )}

        {displayError && (
          <div className="auth-banner error" data-testid="login-error" role="alert">
            <AlertCircle size={16} />
            <span>{displayError}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="auth-form" noValidate>
          <div className="form-group">
            <label htmlFor="login-username" className="form-label">
              Username
            </label>
            <input
              id="login-username"
              data-testid="login-username"
              type="text"
              className="form-input"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="e.g. prem"
              autoComplete="username"
              disabled={loading}
              autoFocus
            />
          </div>

          <div className="form-group">
            <label htmlFor="login-password" className="form-label">
              Password
            </label>
            <input
              id="login-password"
              data-testid="login-password"
              type="password"
              className="form-input"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              autoComplete="current-password"
              disabled={loading}
            />
          </div>

          <button
            type="submit"
            className="btn-auth-submit"
            data-testid="login-submit"
            disabled={loading}
          >
            {loading ? (
              <span>Signing in...</span>
            ) : (
              <>
                <Lock size={15} />
                <span>Sign In</span>
                <ArrowRight size={15} />
              </>
            )}
          </button>
        </form>

        <div className="auth-footer">
          <span>Don't have an account?</span>{' '}
          <button
            type="button"
            className="auth-link-button"
            onClick={onNavigateToRegister}
            data-testid="link-to-register"
          >
            Create account
          </button>
        </div>
      </div>
    </div>
  );
};
