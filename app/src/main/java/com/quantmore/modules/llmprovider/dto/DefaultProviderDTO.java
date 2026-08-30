package com.quantmore.modules.llmprovider.dto;

public record DefaultProviderDTO(
    String defaultProvider,
    String defaultEmbeddingProvider
) {
    public DefaultProviderDTO(String defaultProvider) {
        this(defaultProvider, null);
    }
}
