import React from 'react';
import {
  BarChart2,
  Clipboard,
  Search,
  LayoutDashboard,
  Layers,
  LogOut,
  MessageSquare,
  User as UserIcon,
  MonitorSmartphone,
} from 'lucide-react';
import { useAuth } from '../auth/AuthContext';

export type NavTab = 'dashboard' | 'clipboard' | 'ask' | 'search' | 'analytics' | 'agent';

interface SidebarProps {
  activeTab: NavTab;
  onSelectTab: (tab: NavTab) => void;
}

export const Sidebar: React.FC<SidebarProps> = ({ activeTab, onSelectTab }) => {
  const { user, logout } = useAuth();

  const userInitial = user?.username ? user.username.charAt(0).toUpperCase() : 'U';

  return (
    <aside className="sidebar" aria-label="Main Navigation">
      <div className="sidebar-header">
        <div className="logo-badge-icon">
          <Layers className="logo-icon" size={20} />
        </div>
        <div className="logo-text-group">
          <span className="logo-text">ContextClip</span>
          <span className="logo-tagline">AI Clipboard Memory</span>
        </div>
        <span className="logo-badge">v1.0</span>
      </div>

      <nav className="sidebar-nav">
        <div className="nav-section-label">Main Menu</div>
        <button
          className={`nav-item ${activeTab === 'dashboard' ? 'active' : ''}`}
          onClick={() => onSelectTab('dashboard')}
          aria-current={activeTab === 'dashboard' ? 'page' : undefined}
          aria-label="Dashboard navigation"
        >
          <LayoutDashboard size={18} className="nav-icon" />
          <span>Dashboard</span>
          {activeTab === 'dashboard' && <span className="nav-active-pill" />}
        </button>

        <button
          className={`nav-item ${activeTab === 'clipboard' ? 'active' : ''}`}
          onClick={() => onSelectTab('clipboard')}
          aria-current={activeTab === 'clipboard' ? 'page' : undefined}
          aria-label="Clipboard navigation"
        >
          <Clipboard size={18} className="nav-icon" />
          <span>Clipboard</span>
          {activeTab === 'clipboard' && <span className="nav-active-pill" />}
        </button>

        <button
          className={`nav-item ${activeTab === 'ask' ? 'active' : ''}`}
          onClick={() => onSelectTab('ask')}
          aria-current={activeTab === 'ask' ? 'page' : undefined}
          aria-label="Ask navigation"
        >
          <MessageSquare size={18} className="nav-icon" />
          <span>Ask My Clipboard</span>
          <span className="nav-ai-badge">AI</span>
          {activeTab === 'ask' && <span className="nav-active-pill" />}
        </button>

        <div className="nav-section-label" style={{ marginTop: 12 }}>Insights &amp; Tools</div>
        <button
          className={`nav-item ${activeTab === 'search' ? 'active' : ''}`}
          onClick={() => onSelectTab('search')}
          aria-current={activeTab === 'search' ? 'page' : undefined}
          aria-label="Search navigation"
        >
          <Search size={18} className="nav-icon" />
          <span>Search</span>
          {activeTab === 'search' && <span className="nav-active-pill" />}
        </button>

        <button
          className={`nav-item ${activeTab === 'analytics' ? 'active' : ''}`}
          onClick={() => onSelectTab('analytics')}
          aria-current={activeTab === 'analytics' ? 'page' : undefined}
          aria-label="Analytics navigation"
        >
          <BarChart2 size={18} className="nav-icon" />
          <span>Analytics</span>
          {activeTab === 'analytics' && <span className="nav-active-pill" />}
        </button>

        <div className="nav-section-label" style={{ marginTop: 12 }}>Setup</div>
        <button
          className={`nav-item ${activeTab === 'agent' ? 'active' : ''}`}
          onClick={() => onSelectTab('agent')}
          aria-current={activeTab === 'agent' ? 'page' : undefined}
          aria-label="Agent navigation"
        >
          <MonitorSmartphone size={18} className="nav-icon" />
          <span>Connect Agent</span>
          {activeTab === 'agent' && <span className="nav-active-pill" />}
        </button>
      </nav>

      <div className="sidebar-footer">
        {user && (
          <div className="user-profile-section" data-testid="user-profile">
            <div className="user-profile-main">
              <div className="user-avatar-circle" aria-hidden="true">
                {userInitial}
              </div>
              <div className="user-info">
                <div className="user-name-row">
                  <UserIcon size={12} className="user-avatar-icon" />
                  <span className="user-name" title={user.username}>
                    {user.username}
                  </span>
                </div>
                <div className="user-meta-row">
                  <span className="user-status-dot" />
                  <span className="user-role-badge">{user.role || 'USER'}</span>
                </div>
              </div>
            </div>
            <button
              className="btn-logout"
              onClick={logout}
              aria-label="Log out"
              data-testid="logout-button"
              title="Log out"
            >
              <LogOut size={15} />
              <span>Log out</span>
            </button>
          </div>
        )}
      </div>
    </aside>
  );
};
