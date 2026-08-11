import React, { createContext, useContext, useState, useCallback, useEffect, useRef } from 'react';
import { Check, Close, Attention, Info } from '@icon-park/react';
import type { IconComp } from '../types/icon';

// ============================================================
// Toast — 轻量通知组件
// 替代原生 alert()，不阻塞交互，自动消失
// 设计语言：克制即力量
// ============================================================

type ToastType = 'success' | 'error' | 'warning' | 'info';

interface ToastItem {
  id: number;
  type: ToastType;
  message: string;
  exiting: boolean;
}

interface ToastContextValue {
  toast: (message: string, type?: ToastType, duration?: number) => void;
}

const ToastContext = createContext<ToastContextValue>({
  toast: () => {},
});

export const useToast = () => useContext(ToastContext);

// ============================================================
// 内联样式 — 匹配 "克制即力量" 设计语言
// ============================================================

const typeStyles: Record<
  ToastType,
  { bg: string; border: string; Icon: IconComp; iconBg: string }
> = {
  success: {
    bg: '#EDF7F1',
    border: '#B7DFC8',
    Icon: Check,
    iconBg: '#4A9E6E',
  },
  error: {
    bg: '#FDF0F0',
    border: '#F0C0C0',
    Icon: Close,
    iconBg: '#D95252',
  },
  warning: {
    bg: '#FDF8ED',
    border: '#F0DCA0',
    Icon: Attention,
    iconBg: '#D9A840',
  },
  info: {
    bg: '#E8F3F1',
    border: '#B7D5CF',
    Icon: Info,
    iconBg: '#2D8C7F',
  },
};

// ============================================================
// Provider
// ============================================================

let nextId = 0;

export const ToastProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const timersRef = useRef<Map<number, ReturnType<typeof setTimeout>>>(new Map());

  const dismiss = useCallback((id: number) => {
    // 先触发退出动画
    setToasts(prev => prev.map(t => (t.id === id ? { ...t, exiting: true } : t)));
    // 动画结束后移除
    setTimeout(() => {
      setToasts(prev => prev.filter(t => t.id !== id));
    }, 300);
    // 清理定时器
    const timer = timersRef.current.get(id);
    if (timer) {
      clearTimeout(timer);
      timersRef.current.delete(id);
    }
  }, []);

  const toast = useCallback(
    (message: string, type: ToastType = 'info', duration = 3500) => {
      const id = ++nextId;
      setToasts(prev => [...prev, { id, type, message, exiting: false }]);

      // 自动消失
      const timer = setTimeout(() => dismiss(id), duration);
      timersRef.current.set(id, timer);
    },
    [dismiss],
  );

  // 组件卸载时清理所有定时器
  useEffect(() => {
    return () => {
      timersRef.current.forEach(t => clearTimeout(t));
    };
  }, []);

  return (
    <ToastContext.Provider value={{ toast }}>
      {children}
      {/* Toast 容器 — 固定定位右上角 */}
      <div style={containerStyle}>
        {toasts.map((t, i) => {
          const colors = typeStyles[t.type];
          const isLeaving = t.exiting;
          return (
            <div
              key={t.id}
              onClick={() => dismiss(t.id)}
              style={{
                ...toastItemStyle,
                background: colors.bg,
                borderColor: colors.border,
                // 堆叠偏移
                transform: isLeaving
                  ? 'translateX(120%) scale(0.95)'
                  : `translateX(0) scale(1)`,
                opacity: isLeaving ? 0 : 1,
                cursor: 'pointer',
                zIndex: 10000 - i,
              }}
            >
              <span
                style={{
                  ...iconStyle,
                  background: colors.iconBg,
                }}
              >
                <colors.Icon theme="outline" size="12" fill="#fff" strokeWidth={4} />
              </span>
              <span style={messageStyle}>{t.message}</span>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
};

// ============================================================
// 样式常量
// ============================================================

const containerStyle: React.CSSProperties = {
  position: 'fixed',
  top: 24,
  right: 24,
  zIndex: 10000,
  display: 'flex',
  flexDirection: 'column',
  gap: 10,
  maxWidth: 400,
  pointerEvents: 'none',
};

const toastItemStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'flex-start',
  gap: 12,
  padding: '14px 18px',
  borderRadius: 10,
  border: '1px solid',
  boxShadow: '0 4px 16px rgba(26,29,36,0.10), 0 1px 4px rgba(26,29,36,0.06)',
  transition: 'all 280ms cubic-bezier(0.4, 0, 0.2, 1)',
  pointerEvents: 'auto',
  fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif",
};

const iconStyle: React.CSSProperties = {
  flexShrink: 0,
  width: 22,
  height: 22,
  borderRadius: 6,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  color: '#fff',
  fontSize: 12,
  fontWeight: 600,
  lineHeight: 1,
  marginTop: 1,
};

const messageStyle: React.CSSProperties = {
  flex: 1,
  fontSize: 14,
  lineHeight: 1.55,
  color: '#1A1D24',
  wordBreak: 'break-word',
};
