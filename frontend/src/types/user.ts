/**
 * 用户相关类型定义
 */

export type UserRole = 'USER' | 'ADMIN';

/**
 * 用户信息（对应后端 UserDTO）
 */
export interface UserDTO {
  id: number;
  username: string;
  role: UserRole;
  /** 用户默认模型 Provider ID，为空表示回退全局默认 */
  defaultProviderId: string | null;
  createdAt: string | null;
}
