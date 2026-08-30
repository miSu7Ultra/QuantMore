package com.quantmore.modules.llmprovider.dto;

import lombok.Builder;

/**
 * 用户视角的 Provider 列表项：全局内置/自定义 Provider 与用户自身配置合并后的视图
 */
@Builder
public record UserProviderDTO(
    String id,
    String baseUrl,
    String model,
    String maskedApiKey,
    // 用户是否已配置自己的 Key
    boolean hasOwnConfig,
    // 是否为用户自定义（非全局内置）Provider
    boolean custom,
    boolean enabled,
    boolean supportsEmbedding,
    // 是否可直接用于生成（用户已配 Key 或全局内置已有 Key）
    boolean available,
    // 是否为该用户的默认聊天模型
    boolean defaultChatProvider
) {
}
