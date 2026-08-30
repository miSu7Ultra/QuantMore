/**
 * 用户级模型 Provider 相关类型（对应后端 /api/user/providers）
 */

/** 用户视角的 Provider 列表项：全局内置/自定义 Provider 与用户自身配置合并后的视图 */
export interface UserProviderItem {
  id: string;
  baseUrl: string;
  model: string;
  /** 掩码后的 API Key；未配置自己的 Key 时为 null */
  maskedApiKey: string | null;
  /** 用户是否已配置自己的 Key */
  hasOwnConfig: boolean;
  /** 是否为用户自定义（非全局内置）Provider */
  custom: boolean;
  enabled: boolean;
  supportsEmbedding: boolean;
  /** 是否可直接用于生成（用户已配 Key 或全局内置已有 Key） */
  available: boolean;
  /** 是否为该用户的默认聊天模型 */
  defaultChatProvider: boolean;
}

/**
 * 用户配置/覆盖一个 Provider 的请求体：
 * - 全局内置 Provider：baseUrl / model 可省略（回退到全局配置），apiKey 必填
 * - 自定义 Provider：baseUrl / model / apiKey 均必填
 */
export interface UserProviderUpsertRequest {
  baseUrl?: string;
  apiKey: string;
  model?: string;
  temperature?: number;
  enabled?: boolean;
}

/** 用户默认模型信息 */
export interface UserDefaultProvider {
  defaultProvider: string | null;
  defaultEmbeddingProvider: string | null;
}
