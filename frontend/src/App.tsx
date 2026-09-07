import React, { useState } from 'react';
import { AuthProvider, useAuth } from './auth/AuthContext';
import { Sidebar, NavTab } from './components/Sidebar';
import { DashboardOverviewPage } from './pages/DashboardOverviewPage';
import { ClipboardPage } from './pages/ClipboardPage';
import { AskClipboardPage } from './pages/AskClipboardPage';
import { SearchPage } from './pages/SearchPage';
import { AnalyticsPage } from './pages/AnalyticsPage';
import { AgentPairingPage } from './pages/AgentPairingPage';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';

type AuthRoute = 'login' | 'register';

const MainAppContent: React.FC = () => {
  const { isAuthenticated } = useAuth();
  const [activeTab, setActiveTab] = useState<NavTab>('dashboard');
  const [authRoute, setAuthRoute] = useState<AuthRoute>('login');
  const [regSuccessMessage, setRegSuccessMessage] = useState<string | null>(null);
  const [targetEntryId, setTargetEntryId] = useState<number | null>(null);

  const handleNavigateToClipboard = (entryId?: number) => {
    if (entryId) {
      setTargetEntryId(entryId);
    }
    setActiveTab('clipboard');
  };

  if (!isAuthenticated) {
    if (authRoute === 'register') {
      return (
        <RegisterPage
          onNavigateToLogin={() => {
            setRegSuccessMessage(null);
            setAuthRoute('login');
          }}
          onRegisterSuccess={(msg) => {
            setRegSuccessMessage(msg);
            setAuthRoute('login');
          }}
        />
      );
    }

    return (
      <LoginPage
        onNavigateToRegister={() => {
          setRegSuccessMessage(null);
          setAuthRoute('register');
        }}
        successMessage={regSuccessMessage}
      />
    );
  }

  return (
    <div className="app-layout">
      <Sidebar activeTab={activeTab} onSelectTab={setActiveTab} />
      {activeTab === 'dashboard' && (
        <DashboardOverviewPage onNavigateToClipboard={() => handleNavigateToClipboard()} />
      )}
      {activeTab === 'clipboard' && <ClipboardPage targetEntryId={targetEntryId} />}
      {activeTab === 'ask' && (
        <AskClipboardPage onNavigateToClipboard={handleNavigateToClipboard} />
      )}
      {activeTab === 'search' && <SearchPage />}
      {activeTab === 'analytics' && (
        <AnalyticsPage onNavigateToClipboard={() => handleNavigateToClipboard()} />
      )}
      {activeTab === 'agent' && <AgentPairingPage />}
    </div>
  );
};

export const App: React.FC = () => {
  return (
    <AuthProvider>
      <MainAppContent />
    </AuthProvider>
  );
};

export default App;
