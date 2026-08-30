import React, { useState } from 'react';
import { Sidebar, NavTab } from './components/Sidebar';
import { DashboardOverviewPage } from './pages/DashboardOverviewPage';
import { ClipboardPage } from './pages/ClipboardPage';
import { SearchPage } from './pages/SearchPage';
import { AnalyticsPage } from './pages/AnalyticsPage';

export const App: React.FC = () => {
  const [activeTab, setActiveTab] = useState<NavTab>('dashboard');

  return (
    <div className="app-layout">
      <Sidebar activeTab={activeTab} onSelectTab={setActiveTab} />
      {activeTab === 'dashboard' && (
        <DashboardOverviewPage onNavigateToClipboard={() => setActiveTab('clipboard')} />
      )}
      {activeTab === 'clipboard' && <ClipboardPage />}
      {activeTab === 'search' && <SearchPage />}
      {activeTab === 'analytics' && <AnalyticsPage />}
    </div>
  );
};

export default App;
