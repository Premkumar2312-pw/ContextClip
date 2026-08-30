import React from 'react';
import { Sidebar } from './components/Sidebar';
import { DashboardPage } from './pages/DashboardPage';

export const App: React.FC = () => {
  return (
    <div className="app-layout">
      <Sidebar />
      <DashboardPage />
    </div>
  );
};

export default App;
