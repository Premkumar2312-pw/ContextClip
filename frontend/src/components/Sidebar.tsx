import React from 'react';
import {
  BarChart2,
  Clipboard,
  Search,
  LayoutDashboard,
  Layers,
} from 'lucide-react';

export type NavTab = 'dashboard' | 'clipboard' | 'search' | 'analytics';

interface SidebarProps {
  activeTab: NavTab;
  onSelectTab: (tab: NavTab) => void;
}

export const Sidebar: React.FC<SidebarProps> = ({ activeTab, onSelectTab }) => {
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
        <p>PostgreSQL Connected</p>
      </div>
    </aside>
  );
};
