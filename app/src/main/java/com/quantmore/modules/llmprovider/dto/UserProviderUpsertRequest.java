package com.quantmore.modules.llmprovider.dto;

/**
 * 用户配置/覆盖一个 Provider：
 * - 全局内置 Provider：baseUrl / model 可省略（回退到全局配置），apiKey 必填
 * - 自定义 Provider：baseUrl / model / apiKey 均必填
 */
public record UserProviderUpsertRequest(
    String baseUrl,
    String apiKey,
    String model,
    Double temperature,
    Boolean enabled
) {
}
