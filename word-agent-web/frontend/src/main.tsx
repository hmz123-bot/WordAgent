import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { IconProvider, DEFAULT_ICON_CONFIGS } from '@icon-park/react';
import '@icon-park/react/styles/index.css';
import App from './App';
import './styles/global.css';
import './styles/v2.css';
import './styles/replica.css';

/**
 * IconPark 全局配置 —— 与米色/暖橙主题对齐。
 * theme: outline 线性风格，配色由 colors.outline 控制。
 */
const iconConfig = {
  ...DEFAULT_ICON_CONFIGS,
  theme: 'outline' as const,
  size: '1em',
  strokeWidth: 3,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
  colors: {
    ...DEFAULT_ICON_CONFIGS.colors,
    outline: {
      fill: 'currentColor',
      background: 'transparent',
    },
    filled: {
      fill: 'currentColor',
      background: 'transparent',
    },
  },
};

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <IconProvider value={iconConfig}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </IconProvider>
  </React.StrictMode>
);
