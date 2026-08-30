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
} from 'lucide-react';
import { useAuth } from '../auth/AuthContext';

export type NavTab = 'dashboard' | 'clipboard' | 'ask' | 'search' | 'analytics';

interface SidebarProps {
  activeTab: NavTab;
  onSelectTab: (tab: NavTab) => void;
}

export const Sidebar: React.FC<SidebarProps> = ({ activeTab, onSelectTab }) => {
  const { user, logout } = useAuth();

  return (
    <aside className="sidebar" aria-label="Main Navigation">
      <div className="sidebar-header">
        <Layers className="logo-icon" />
        <span className="logo-text">ContextClip</span>
        <span className="logo-badge">v0.1</span>
      </div>

      <nav className="sidebar-nav">
        <button
          className={`nav-item ${activeTab === 'dashboard' ? 'active' : ''}`}
          onClick={() => onSelectTab('dashboard')}
          aria-current={activeTab === 'dashboard' ? 'page' : undefined}
          aria-label="Dashboard navigation"
        >
          <LayoutDashboard size={16} />
          <span>Dashboard</span>
        </button>

        <button
          className={`nav-item ${activeTab === 'clipboard' ? 'active' : ''}`}
          onClick={() => onSelectTab('clipboard')}
          aria-current={activeTab === 'clipboard' ? 'page' : undefined}
          aria-label="Clipboard navigation"
        >
          <Clipboard size={16} />
          <span>Clipboard</span>
        </button>

        <button
          className={`nav-item ${activeTab === 'ask' ? 'active' : ''}`}
          onClick={() => onSelectTab('ask')}
          aria-current={activeTab === 'ask' ? 'page' : undefined}
          aria-label="Ask navigation"
        >
          <MessageSquare size={16} />
          <span>Ask My Clipboard</span>
        </button>

        <button
          className={`nav-item ${activeTab === 'search' ? 'active' : ''}`}
          onClick={() => onSelectTab('search')}
          aria-current={activeTab === 'search' ? 'page' : undefined}
          aria-label="Search navigation"
        >
          <Search size={16} />
          <span>Search</span>
        </button>


        <button
          className={`nav-item ${activeTab === 'analytics' ? 'active' : ''}`}
          onClick={() => onSelectTab('analytics')}
          aria-current={activeTab === 'analytics' ? 'page' : undefined}
          aria-label="Analytics navigation"
        >
          <BarChart2 size={16} />
          <span>Analytics</span>
        </button>
      </nav>

      <div className="sidebar-footer">
        {user && (
          <div className="user-profile-section" data-testid="user-profile">
            <div className="user-info">
              <UserIcon size={14} className="user-avatar-icon" />
              <span className="user-name" title={user.username}>
                {user.username}
              </span>
            </div>
            <button
              className="btn-logout"
              onClick={logout}
              aria-label="Log out"
              data-testid="logout-button"
              title="Log out"
            >
              <LogOut size={14} />
              <span>Log out</span>
            </button>
          </div>
        )}
      </div>
    </aside>
  );
};
