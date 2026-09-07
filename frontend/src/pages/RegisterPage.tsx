import React, { useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import {
  Layers,
  ArrowRight,
  AlertCircle,
  UserPlus,
  User,
  Lock,
  Eye,
  EyeOff,
  Zap,
  Search,
  Sparkles,
} from 'lucide-react';

interface RegisterPageProps {
  onNavigateToLogin: () => void;
  onRegisterSuccess: (message: string) => void;
}

export const RegisterPage: React.FC<RegisterPageProps> = ({
  onNavigateToLogin,
  onRegisterSuccess,
}) => {
  const { register } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    const trimmedUser = username.trim();
    if (!trimmedUser) {
      setError('Username is required.');
      return;
    }

    if (!password) {
      setError('Password is required.');
      return;
    }

    if (password.length < 6) {
      setError('Password must be at least 6 characters long.');
      return;
    }

    if (password !== confirmPassword) {
      setError('Passwords do not match.');
      return;
    }

    setLoading(true);
    try {
      const msg = await register({ username: trimmedUser, password });
      onRegisterSuccess(msg || 'Registration successful. Please log in.');
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Registration failed. Please try again.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  const getStrength = () => {
    if (!password) return 0;
    let s = 0;
    if (password.length >= 6) s += 1;
    if (password.length >= 10) s += 1;
    if (/[A-Z]/.test(password)) s += 1;
    if (/[0-9]/.test(password) || /[^A-Za-z0-9]/.test(password)) s += 1;
    return s;
  };

  const strength = getStrength();

  return (
    <div className="auth-page-container">
      <div className="auth-shell">
        {/* Left Side: Compact Brand Panel */}
        <div className="auth-showcase">
          <div className="auth-showcase-inner">
            <div className="auth-brand-lockup">
              <div className="auth-showcase-badge">
                <Layers size={22} />
              </div>
              <div className="auth-brand-wordmark">
                <span className="auth-brand-name">Context</span>
                <span className="auth-brand-accent">Clip</span>
              </div>
            </div>

            <p className="auth-brand-tagline">
              Your clipboard memory for developers.
            </p>

            <div className="auth-feature-pills">
              <div className="auth-feature-pill">
                <div className="auth-pill-icon pill-capture">
                  <Zap size={14} />
                </div>
                <span>Auto Capture</span>
              </div>
              <div className="auth-feature-pill">
                <div className="auth-pill-icon pill-search">
                  <Search size={14} />
                </div>
                <span>Deep Search</span>
              </div>
              <div className="auth-feature-pill">
                <div className="auth-pill-icon pill-ai">
                  <Sparkles size={14} />
                </div>
                <span>Ask AI</span>
              </div>
            </div>
          </div>
        </div>

        {/* Right Side: Authentication Card */}
        <div className="auth-card-side">
          <div className="auth-card" data-testid="register-card">
            <div className="auth-header">
              <div className="auth-logo-badge">
                <Layers className="auth-logo-icon" size={24} />
              </div>
              <h1 className="auth-title">Create Account</h1>
              <p className="auth-subtitle">Register to secure and sync your clipboard</p>
            </div>

            {error && (
              <div className="auth-banner error" data-testid="register-error" role="alert">
                <AlertCircle size={16} />
                <span>{error}</span>
              </div>
            )}

            <form onSubmit={handleSubmit} className="auth-form" noValidate>
              <div className="form-group">
                <label htmlFor="register-username" className="form-label">
                  Username
                </label>
                <div className="input-icon-wrapper">
                  <User size={16} className="input-leading-icon" />
                  <input
                    id="register-username"
                    data-testid="register-username"
                    type="text"
                    className="form-input has-leading-icon"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    placeholder="Choose a username"
                    autoComplete="username"
                    disabled={loading}
                    autoFocus
                  />
                </div>
              </div>

              <div className="form-group">
                <label htmlFor="register-password" className="form-label">
                  Password (min 6 chars)
                </label>
                <div className="input-icon-wrapper">
                  <Lock size={16} className="input-leading-icon" />
                  <input
                    id="register-password"
                    data-testid="register-password"
                    type={showPassword ? 'text' : 'password'}
                    className="form-input has-leading-icon has-trailing-icon"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="••••••••"
                    autoComplete="new-password"
                    disabled={loading}
                  />
                  <button
                    type="button"
                    className="input-trailing-btn"
                    onClick={() => setShowPassword(!showPassword)}
                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                    tabIndex={-1}
                  >
                    {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>

                {password.length > 0 && (
                  <div className="password-strength-meter" aria-label="Password strength">
                    <div className="strength-bars">
                      <div className={`strength-segment ${strength >= 1 ? 'active level-' + strength : ''}`} />
                      <div className={`strength-segment ${strength >= 2 ? 'active level-' + strength : ''}`} />
                      <div className={`strength-segment ${strength >= 3 ? 'active level-' + strength : ''}`} />
                      <div className={`strength-segment ${strength >= 4 ? 'active level-' + strength : ''}`} />
                    </div>
                    <span className="strength-label">
                      {strength <= 1 && 'Weak'}
                      {strength === 2 && 'Fair'}
                      {strength === 3 && 'Good'}
                      {strength >= 4 && 'Strong'}
                    </span>
                  </div>
                )}
              </div>

              <div className="form-group">
                <label htmlFor="register-confirm-password" className="form-label">
                  Confirm Password
                </label>
                <div className="input-icon-wrapper">
                  <Lock size={16} className="input-leading-icon" />
                  <input
                    id="register-confirm-password"
                    data-testid="register-confirm-password"
                    type={showConfirmPassword ? 'text' : 'password'}
                    className="form-input has-leading-icon has-trailing-icon"
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    placeholder="••••••••"
                    autoComplete="new-password"
                    disabled={loading}
                  />
                  <button
                    type="button"
                    className="input-trailing-btn"
                    onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                    aria-label={showConfirmPassword ? 'Hide password' : 'Show password'}
                    tabIndex={-1}
                  >
                    {showConfirmPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>
              </div>

              <button
                type="submit"
                className="btn-auth-submit"
                data-testid="register-submit"
                disabled={loading}
              >
                {loading ? (
                  <span className="btn-loading-flex">
                    <span className="btn-spinner" aria-hidden="true" />
                    Creating Account...
                  </span>
                ) : (
                  <>
                    <UserPlus size={15} />
                    <span>Create Account</span>
                    <ArrowRight size={15} />
                  </>
                )}
              </button>
            </form>

            <div className="auth-footer">
              <span>Already have an account?</span>{' '}
              <button
                type="button"
                className="auth-link-button"
                onClick={onNavigateToLogin}
                data-testid="link-to-login"
              >
                Sign in
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
