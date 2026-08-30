import React from 'react';
import {
  BarChart2,
  Clipboard,
  Search,
  LayoutDashboard,
  Layers,
} from 'lucide-react';

export const Sidebar: React.FC = () => {
  return (
    <aside className="sidebar" aria-label="Main Navigation">
      <div className="sidebar-header">
        <Layers className="logo-icon" />
        <span className="logo-text">ContextClip</span>
        <span className="logo-badge">v0.1</span>
      </div>

      <nav className="sidebar-nav">
        <button
          className="nav-item disabled"
          title="Dashboard view coming soon"
          disabled
        >
          <LayoutDashboard size={16} />
          <span>Dashboard</span>
          <span className="nav-item-badge">Soon</span>
        </button>

        <button
          className="nav-item disabled"
          title="Clipboard history view coming soon"
          disabled
        >
          <Clipboard size={16} />
          <span>Clipboard</span>
          <span className="nav-item-badge">Soon</span>
        </button>

        <button
          className="nav-item disabled"
          title="Search view coming soon"
          disabled
        >
          <Search size={16} />
          <span>Search</span>
          <span className="nav-item-badge">Soon</span>
        </button>

        <button
          className="nav-item active"
          aria-current="page"
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
