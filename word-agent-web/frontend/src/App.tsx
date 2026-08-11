import React from 'react';
import { Routes, Route, useLocation } from 'react-router-dom';
import { ToastProvider } from './components/Toast';
import Sidebar from './components/Sidebar';
import Topbar from './components/Topbar';
import HomePage from './pages/HomePage';
import DocumentList from './pages/DocumentList';
import DocumentEditor from './pages/DocumentEditor';
import DocumentEditorV2 from './pages/DocumentEditorV2';
import SearchPage from './pages/SearchPage';
import DesignSystem from './pages/DesignSystem';
import Writing from './pages/Writing';
import Templates from './pages/Templates';

/** 深度工作页（编辑器）自带页头，走整幅铺满布局，不再叠加顶栏 */
const isFocusRoute = (pathname: string) => /^\/editor(-v2)?\//.test(pathname);

const Shell: React.FC = () => {
  const { pathname } = useLocation();
  const focus = isFocusRoute(pathname);

  return (
    <div className="app-shell">
      <Sidebar />
      <div className="app-main">
        {!focus && <Topbar />}
        <main className={`app-content${focus ? ' flush' : ''}`}>
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/write" element={<Writing />} />
            <Route path="/templates" element={<Templates />} />
            <Route path="/documents" element={<DocumentList />} />
            <Route path="/editor/:id" element={<DocumentEditor />} />
            <Route path="/editor-v2/:id" element={<DocumentEditorV2 />} />
            <Route path="/search" element={<SearchPage />} />
            <Route path="/design" element={<DesignSystem />} />
            <Route path="*" element={<HomePage />} />
          </Routes>
        </main>
      </div>
    </div>
  );
};

const App: React.FC = () => (
  <ToastProvider>
    <Shell />
  </ToastProvider>
);

export default App;
