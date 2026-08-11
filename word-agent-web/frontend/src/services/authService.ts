/**
 * JWT 认证服务。
 *
 * 工作流：
 * 1. 用户打开编辑器 → 前端请求 /api/v2/auth/token 获取短期 JWT
 * 2. 后续所有 AI API 请求带 Authorization: Bearer <token>
 * 3. token 过期后自动刷新
 */

const AUTH_BASE = '/api/v2/auth';

interface AuthState {
  token: string | null;
  userId: string;
  expiresAt: number;
}

let authState: AuthState = {
  token: typeof window !== 'undefined' ? localStorage.getItem('auth_token') : null,
  userId: typeof window !== 'undefined' ? localStorage.getItem('user_id') || 'anonymous' : 'anonymous',
  expiresAt: typeof window !== 'undefined' ? Number(localStorage.getItem('token_expires') || '0') : 0,
};

/** 获取/刷新 token */
export async function getToken(): Promise<string> {
  // 检查缓存 token 是否有效
  if (authState.token && Date.now() < authState.expiresAt - 60000) {
    return authState.token;
  }

  try {
    const resp = await fetch(`${AUTH_BASE}/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userId: authState.userId }),
    });

    const data = await resp.json();
    authState.token = data.token;
    authState.expiresAt = Date.now() + (data.validityMinutes || 60) * 60000;

    if (authState.token) {
      localStorage.setItem('auth_token', authState.token);
    }
    localStorage.setItem('token_expires', String(authState.expiresAt));

    return authState.token || '';
  } catch {
    // 如果后端没有 /api/v2/auth 端点，返回空 token（兼容模式）
    return '';
  }
}

/** 获取当前用户 ID */
export function getUserId(): string {
  return authState.userId;
}

/** 设置用户 ID */
export function setUserId(userId: string) {
  authState.userId = userId;
  localStorage.setItem('user_id', userId);
}

/** 检查是否需要鉴权 */
export function isAuthenticated(): boolean {
  return !!authState.token && Date.now() < authState.expiresAt;
}

/** 清除认证信息 */
export function clearAuth() {
  authState.token = null;
  authState.expiresAt = 0;
  localStorage.removeItem('auth_token');
  localStorage.removeItem('token_expires');
}
