package com.quantmore.modules.llmprovider.service;

import com.quantmore.common.config.LlmProviderProperties;
import com.quantmore.common.config.LlmProviderProperties.ProviderConfig;
import com.quantmore.modules.llmprovider.model.LlmGlobalSettingEntity;
import com.quantmore.modules.llmprovider.model.LlmProviderEntity;
import com.quantmore.modules.llmprovider.repository.LlmGlobalSettingRepository;
import com.quantmore.modules.llmprovider.repository.LlmProviderRepository;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmProviderBootstrapService {

  private final LlmProviderProperties properties;
  private final LlmProviderRepository providerRepository;
  private final LlmGlobalSettingRepository globalSettingRepository;
  private final ApiKeyEncryptionService encryptionService;

  @PostConstruct
  @Transactional
  public void seedProvidersIfNecessary() {
    if (providerRepository.count() == 0) {
      seedProviders();
    } else {
      refreshBuiltinKeysFromConfig();
    }
    ensureGlobalSetting();
  }

  /**
   * 内置 Provider 的 Key 若仍为空或占位符（首次启动时 env 未配置），
   * 用当前 env 配置刷新，避免用户在 .env 补 Key 后重启不生效。
   * 管理员在界面上手动设置的 Key 不会被覆盖。
   */
  private void refreshBuiltinKeysFromConfig() {
    Map<String, ProviderConfig> providers = properties.getProviders();
    if (providers == null || providers.isEmpty()) {
      return;
    }
    int refreshed = 0;
    for (Map.Entry<String, ProviderConfig> entry : providers.entrySet()) {
      ProviderConfig config = entry.getValue();
      if (config == null || isBlank(config.getApiKey())) {
        continue;
      }
      LlmProviderEntity existing = providerRepository.findById(entry.getKey()).orElse(null);
      if (existing == null || !existing.isBuiltin()) {
        continue;
      }
      String storedKey = encryptionService.decrypt(
          existing.getApiKeyNonce(), existing.getApiKeyCiphertext());
      if (!isBlank(storedKey) && !storedKey.startsWith("your_")) {
        continue; // 已是真实 Key，不动
      }
      ApiKeyEncryptionService.EncryptedValue encrypted = encryptionService.encrypt(config.getApiKey());
      existing.setApiKeyNonce(encrypted.nonce());
      existing.setApiKeyCiphertext(encrypted.ciphertext());
      providerRepository.save(existing);
      refreshed++;
      log.info("已从 env 刷新内置 Provider Key: id={}", entry.getKey());
    }
    if (refreshed > 0) {
      log.info("刷新了 {} 个内置 Provider 的 Key", refreshed);
    }
  }

  private void seedProviders() {
    Map<String, ProviderConfig> providers = properties.getProviders();
    if (providers == null || providers.isEmpty()) {
      log.warn("No app.ai.providers seed configuration found");
      return;
    }

    providers.forEach((id, config) -> {
      if (isBlank(id) || config == null || isBlank(config.getBaseUrl()) || isBlank(config.getModel())) {
        log.warn("Skip invalid provider seed: id={}", id);
        return;
      }
      ApiKeyEncryptionService.EncryptedValue encrypted =
          encryptionService.encrypt(config.getApiKey() != null ? config.getApiKey() : "");
      boolean supportsEmbedding = Boolean.TRUE.equals(config.getSupportsEmbedding())
          || !isBlank(config.getEmbeddingModel());

      LlmProviderEntity entity = LlmProviderEntity.builder()
          .id(id)
          .baseUrl(config.getBaseUrl())
          .apiKeyNonce(encrypted.nonce())
          .apiKeyCiphertext(encrypted.ciphertext())
          .model(config.getModel())
          .embeddingModel(trimOrNull(config.getEmbeddingModel()))
          .embeddingDimensions(resolveEmbeddingDimensions(config.getEmbeddingDimensions()))
          .supportsEmbedding(supportsEmbedding)
          .temperature(config.getTemperature())
          .enabled(true)
          .builtin(true)
          .build();
      providerRepository.save(entity);
    });
    log.info("Seeded {} LLM providers from application configuration", providerRepository.count());
  }

  private void ensureGlobalSetting() {
    if (globalSettingRepository.existsById(LlmGlobalSettingEntity.SINGLETON_ID)) {
      return;
    }
    String defaultChatProvider = resolveExistingProvider(
        properties.getDefaultProvider(),
        providerRepository.findAll().stream().findFirst().map(LlmProviderEntity::getId).orElse("qwen")
    );
    String configuredEmbeddingProvider = !isBlank(properties.getDefaultEmbeddingProvider())
        ? properties.getDefaultEmbeddingProvider()
        : defaultChatProvider;
    String defaultEmbeddingProvider = resolveExistingEmbeddingProvider(configuredEmbeddingProvider, defaultChatProvider);

    globalSettingRepository.save(LlmGlobalSettingEntity.builder()
        .id(LlmGlobalSettingEntity.SINGLETON_ID)
        .defaultChatProviderId(defaultChatProvider)
        .defaultEmbeddingProviderId(defaultEmbeddingProvider)
        .build());
    log.info("Initialized LLM global setting: chatProvider={}, embeddingProvider={}",
        defaultChatProvider, defaultEmbeddingProvider);
  }

  private String resolveExistingProvider(String preferredProvider, String fallbackProvider) {
    if (!isBlank(preferredProvider) && providerRepository.existsById(preferredProvider)) {
      return preferredProvider;
    }
    return fallbackProvider;
  }

  private String resolveExistingEmbeddingProvider(String preferredProvider, String fallbackProvider) {
    return providerRepository.findById(preferredProvider)
        .filter(this::canProvideEmbedding)
        .map(LlmProviderEntity::getId)
        .orElseGet(() -> providerRepository.findAll().stream()
            .filter(this::canProvideEmbedding)
            .findFirst()
            .map(LlmProviderEntity::getId)
            .orElse(fallbackProvider));
  }

  private boolean canProvideEmbedding(LlmProviderEntity provider) {
    return provider.isEnabled()
        && provider.isSupportsEmbedding()
        && !isBlank(provider.getEmbeddingModel());
  }

  private Integer resolveEmbeddingDimensions(Integer configuredDimensions) {
    if (configuredDimensions != null && configuredDimensions > 0) {
      return configuredDimensions;
    }
    return properties.getEmbeddingDimensions();
  }

  private String trimOrNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
