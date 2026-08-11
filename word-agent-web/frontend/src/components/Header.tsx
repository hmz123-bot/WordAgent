import React from 'react';
import { useNavigate, useLocation } from 'react-router-dom';

interface NavItem {
  key: string;
  label: string;
  path: string;
  icon?: React.ReactNode;
}

const navItems: NavItem[] = [
  { key: 'home', label: '首页', path: '/' },
  { key: 'documents', label: '文档', path: '/documents' },
  { key: 'search', label: '搜索', path: '/search' },
  { key: 'design', label: '设计', path: '/design' },
];

const Header: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();

  const getActiveKey = () => {
    const path = location.pathname;
    if (path === '/' || path === '') return 'home';
    if (path.startsWith('/documents')) return 'documents';
    if (path.startsWith('/search')) return 'search';
    if (path.startsWith('/design')) return 'design';
    return '';
  };

  const activeKey = getActiveKey();

  return (
    <header className="app-header">
      <div className="header-inner">
        <div className="header-left">
          <div className="header-logo" onClick={() => navigate('/')}>
            <div className="header-logo-icon">W</div>
            <span className="header-logo-text">Word Agent</span>
          </div>
          <nav className="header-nav">
            {navItems.map((item) => (
              <button
                key={item.key}
                className={`header-nav-item ${activeKey === item.key ? 'active' : ''}`}
                onClick={() => navigate(item.path)}
              >
                {item.icon}
                <span>{item.label}</span>
              </button>
            ))}
          </nav>
        </div>
        <div className="header-right">
          <button className="btn btn-primary btn-sm" onClick={() => navigate('/documents?action=import')}>
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M8 3v10M3 8h10" />
            </svg>
            导入文档
          </button>
          <div className="header-avatar" title="用户">A</div>
        </div>
      </div>
    </header>
  );
};

export default Header;