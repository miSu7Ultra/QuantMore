import { request } from './request';
import type { UserDTO } from '../types/user';

// ========== 类型定义 ==========

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  user: UserDTO;
}

// ========== API 函数 ==========

export const authApi = {
  /**
   * 用户注册
   */
  register(data: RegisterRequest): Promise<UserDTO> {
    return request.post<UserDTO>('/api/auth/register', data);
  },

  /**
   * 用户登录，返回 JWT Token 与用户信息
   */
  login(data: LoginRequest): Promise<LoginResponse> {
    return request.post<LoginResponse>('/api/auth/login', data);
  },

  /**
   * 获取当前登录用户信息（Bearer Token 由请求拦截器自动附加）
   */
  me(): Promise<UserDTO> {
    return request.get<UserDTO>('/api/auth/me');
  },
};
