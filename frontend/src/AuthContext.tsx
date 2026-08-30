import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { authApi } from './api/auth';
import {
  clearStoredAuth,
  getStoredToken,
  getStoredUser,
  storeAuth,
  storeUser,
} from './utils/authStorage';
import type { UserDTO } from './types/user';

interface AuthContextValue {
  /** 当前登录用户 */
  user: UserDTO | null;
  /** 是否正在校验登录态（应用启动时刷新用户信息） */
  loading: boolean;
  /** 是否为管理员 */
  isAdmin: boolean;
  /** 保存登录凭证并更新用户状态 */
  login: (token: string, user: UserDTO) => void;
  /** 清除登录凭证并登出 */
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  // 初始化时从 localStorage 恢复用户信息，避免刷新闪烁
  const [user, setUser] = useState<UserDTO | null>(() => getStoredUser<UserDTO>());
  const [loading, setLoading] = useState<boolean>(() => !!getStoredToken());

  // 应用启动时若本地有 Token，调用 /api/auth/me 刷新用户信息；失败则登出
  useEffect(() => {
    if (!getStoredToken()) {
      setLoading(false);
      return;
    }

    authApi.me()
      .then((me) => {
        setUser(me);
        storeUser(me);
      })
      .catch(() => {
        // Token 失效：请求层已清除存储并跳转登录页，这里同步状态
        clearStoredAuth();
        setUser(null);
      })
      .finally(() => setLoading(false));
  }, []);

  const login = useCallback((token: string, nextUser: UserDTO) => {
    storeAuth(token, nextUser);
    setUser(nextUser);
  }, []);

  const logout = useCallback(() => {
    clearStoredAuth();
    setUser(null);
  }, []);

  const value: AuthContextValue = {
    user,
    loading,
    isAdmin: user?.role === 'ADMIN',
    login,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

/** 获取认证上下文，必须在 AuthProvider 内使用 */
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth 必须在 AuthProvider 内使用');
  }
  return ctx;
}

/**
 * 登录守卫：未登录时重定向到登录页
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  const location = useLocation();

  // 正在校验登录态（应用启动 /api/auth/me 刷新中）
  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="w-10 h-10 border-3 border-slate-200 border-t-primary-500 rounded-full animate-spin" />
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return <>{children}</>;
}
