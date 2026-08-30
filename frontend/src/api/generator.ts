import { request } from './request';
import type {
  GenerateStrategyRequest,
  GenerateStrategyResponse,
} from '../types/generator';

export const strategyApi = {
  /**
   * 生成策略（LLM 调用，超时时间放宽到 3 分钟）
   */
  generate: (data: GenerateStrategyRequest): Promise<GenerateStrategyResponse> =>
    request.post<GenerateStrategyResponse>('/api/strategy/generate', data, { timeout: 180000 }),

  /**
   * 获取历史生成记录
   */
  history: () => request.get<GenerateStrategyResponse[]>('/api/strategy/history'),

  /**
   * 获取单条生成记录详情
   */
  getById: (id: number) => request.get<GenerateStrategyResponse>(`/api/strategy/${id}`),
};
