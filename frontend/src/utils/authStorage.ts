/**
 * 认证信息本地存储工具
 * 独立模块，避免 request.ts 与 AuthContext 相互引用造成循环依赖
 */

export const TOKEN_KEY = 'qm_token';
export const USER_KEY = 'qm_user';

/** 读取本地 Token */
export function getStoredToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

/** 读取本地缓存的用户信息 */
export function getStoredUser<T>(): T | null {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}

/** 保存登录凭证（Token + 用户信息） */
export function storeAuth(token: string, user: unknown): void {
  localStorage.setItem(TOKEN_KEY, token);
  storeUser(user);
}

/** 仅更新用户信息（用于 /api/auth/me 刷新后同步） */
export function storeUser(user: unknown): void {
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

/** 清除本地登录凭证 */
export function clearStoredAuth(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}
