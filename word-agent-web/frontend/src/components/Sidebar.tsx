import React from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import {
  AllApplication,
  Write,
  FileText,
  Compass,
  Config,
  Peoples,
  Fire,
  Share,
} from '@icon-park/react';
import type { IconComp } from '../types/icon';

interface MainNavItem {
  key: string;
  label: string;
  path: string;
  Icon: IconComp;
}

const mainNav: MainNavItem[] = [
  { key: 'templates', label: '模板', path: '/templates', Icon: AllApplication },
  { key: 'writing', label: '写作', path: '/write', Icon: Write },
  { key: 'documents', label: '文档', path: '/documents', Icon: FileText },
];

interface PlazaNavItem {
  label: string;
  path: string;
  Icon: IconComp;
  badge?: string;
}

const plazaNav: PlazaNavItem[] = [
  { label: '发现', path: '/', Icon: Compass },
  { label: '技能', path: '/design', Icon: Config, badge: '新' },
  { label: '协作', path: '/documents', Icon: Peoples },
  { label: '活动', path: '#', Icon: Fire },
  { label: '我的分享', path: '#', Icon: Share },
];

const Sidebar: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const path = location.pathname;

  const activeMainKey =
    path.startsWith('/templates') ? 'templates' :
    path.startsWith('/write') ? 'writing' :
    path.startsWith('/documents') ? 'documents' : '';

  const activePlaza = (item: PlazaNavItem): boolean => {
    if (item.path === '#') return false;
    if (item.label === '发现') return path === '/';
    if (item.label === '技能') return path.startsWith('/design');
    if (item.label === '协作') return path.startsWith('/documents');
    return false;
  };

  return (
    <aside className="wa-sidebar">
      <div className="wa-brand" onClick={() => navigate('/')} style={{ cursor: 'pointer' }}>
        <div className="logo">W</div>
        <div className="name">Word Agent<small>WORD AGENT</small></div>
      </div>

      <div className="wa-nav-group">
        {mainNav.map(({ key, label, path: to, Icon }) => (
          <div
            key={key}
            className={`wa-nav-item ${activeMainKey === key ? 'active' : ''}`}
            onClick={() => navigate(to)}
          >
            <span className="ico"><Icon theme="outline" size="17" /></span>{label}
          </div>
        ))}
      </div>

      <div className="wa-agent-card">
        <div className="spark" />
        <h4>开启你的 Agent 模式</h4>
        <p>智能写作！Agent 模式自动调用技能，陪你完成文档全流程。</p>
        <button className="btn" onClick={() => navigate('/write')}>立即开启</button>
      </div>

      <div className="wa-nav-group">
        <div className="wa-nav-label">文档广场</div>
        {plazaNav.map((item) => {
          const { label, path: to, Icon, badge } = item;
          return (
            <div
              key={label}
              className={`wa-nav-item ${activePlaza(item) ? 'active' : ''}`}
              onClick={() => to !== '#' && navigate(to)}
              style={to === '#' ? { cursor: 'default', opacity: 0.7 } : undefined}
            >
              <span className="ico"><Icon theme="outline" size="16" /></span>{label}
              {badge && <span className="badge">{badge}</span>}
            </div>
          );
        })}
      </div>

      <div className="wa-side-spacer" />
      <div className="wa-user">
        <div className="avatar">U</div>
        <div className="meta"><b>我的工作台</b><span>已登录 · 标准会员</span></div>
      </div>
    </aside>
  );
};

export default Sidebar;
