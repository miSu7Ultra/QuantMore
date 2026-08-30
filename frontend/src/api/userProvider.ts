import request from './request';
import type {
  UserProviderItem,
  UserProviderUpsertRequest,
  UserDefaultProvider,
} from '../types/userProvider';
import type { ProviderTestResult } from '../types/llmProvider';

export const userProviderApi = {
  /**
   * 获取用户视角的 Provider 列表
   */
  list: () => request.get<UserProviderItem[]>('/api/user/providers'),

  /**
   * 配置/覆盖一个 Provider（内置 Provider baseUrl/model 可省略，自定义三者必填）
   */
  upsert: (providerId: string, data: UserProviderUpsertRequest) =>
    request.put<void>(`/api/user/providers/${encodeURIComponent(providerId)}`, data),

  /**
   * 删除用户自己的 Provider 配置
   */
  remove: (providerId: string) =>
    request.delete<void>(`/api/user/providers/${encodeURIComponent(providerId)}`),

  /**
   * 连通性测试（使用用户覆盖后的 Key/baseUrl/model）
   */
  test: (providerId: string) =>
    request.post<ProviderTestResult>(`/api/user/providers/${encodeURIComponent(providerId)}/test`),

  /**
   * 获取用户默认模型
   */
  getDefault: () => request.get<UserDefaultProvider>('/api/user/providers/default'),

  /**
   * 设置用户默认模型；null 表示跟随全局默认
   */
  setDefault: (defaultProvider: string | null) =>
    request.put<void>('/api/user/providers/default', { defaultProvider }),
};
