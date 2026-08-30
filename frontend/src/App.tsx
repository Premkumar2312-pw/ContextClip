import React, { useState } from 'react';
import { AuthProvider, useAuth } from './auth/AuthContext';
import { Sidebar, NavTab } from './components/Sidebar';
import { DashboardOverviewPage } from './pages/DashboardOverviewPage';
import { ClipboardPage } from './pages/ClipboardPage';
import { AskClipboardPage } from './pages/AskClipboardPage';
import { SearchPage } from './pages/SearchPage';
import { AnalyticsPage } from './pages/AnalyticsPage';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';

type AuthRoute = 'login' | 'register';

const MainAppContent: React.FC = () => {
  const { isAuthenticated } = useAuth();
  const [activeTab, setActiveTab] = useState<NavTab>('dashboard');
  const [authRoute, setAuthRoute] = useState<AuthRoute>('login');
  const [regSuccessMessage, setRegSuccessMessage] = useState<string | null>(null);

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
        <DashboardOverviewPage onNavigateToClipboard={() => setActiveTab('clipboard')} />
      )}
      {activeTab === 'clipboard' && <ClipboardPage />}
      {activeTab === 'ask' && (
        <AskClipboardPage onNavigateToClipboard={() => setActiveTab('clipboard')} />
      )}
      {activeTab === 'search' && <SearchPage />}
      {activeTab === 'analytics' && <AnalyticsPage />}
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
