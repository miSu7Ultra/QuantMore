package com.quantmore.modules.llmprovider.service;

import com.quantmore.common.config.LlmProviderProperties;
import com.quantmore.modules.llmprovider.model.LlmGlobalSettingEntity;
import com.quantmore.modules.llmprovider.model.LlmProviderEntity;
import com.quantmore.modules.llmprovider.repository.LlmGlobalSettingRepository;
import com.quantmore.modules.llmprovider.repository.LlmProviderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LlmProviderBootstrapService 测试")
class LlmProviderBootstrapServiceTest {

  @Mock private LlmProviderProperties properties;
  @Mock private LlmProviderRepository providerRepository;
  @Mock private LlmGlobalSettingRepository globalSettingRepository;
  @Mock private ApiKeyEncryptionService encryptionService;

  private LlmProviderBootstrapService service;

  private LlmProviderBootstrapService newService() {
    return new LlmProviderBootstrapService(
        properties, providerRepository, globalSettingRepository, encryptionService);
  }

  private LlmProviderProperties.ProviderConfig config(String key) {
    LlmProviderProperties.ProviderConfig c = new LlmProviderProperties.ProviderConfig();
    c.setBaseUrl("https://api.example.com");
    c.setModel("m1");
    c.setApiKey(key);
    return c;
  }

  @Nested
  @DisplayName("内置 Key 刷新")
  class RefreshBuiltinKeys {

    @Test
    @DisplayName("存量行 Key 为占位符时从 env 刷新")
    void refreshesPlaceholderKey() {
      service = newService();
      when(providerRepository.count()).thenReturn(1L);
      Map<String, LlmProviderProperties.ProviderConfig> providers = new HashMap<>();
      providers.put("dashscope", config("sk-real-key"));
      when(properties.getProviders()).thenReturn(providers);

      LlmProviderEntity entity = LlmProviderEntity.builder()
          .id("dashscope")
          .apiKeyNonce("n1")
          .apiKeyCiphertext("c1")
          .builtin(true)
          .build();
      when(providerRepository.findById("dashscope")).thenReturn(Optional.of(entity));
      when(encryptionService.decrypt("n1", "c1")).thenReturn("your_dashscope_api_key_here");
      when(encryptionService.encrypt("sk-real-key"))
          .thenReturn(new ApiKeyEncryptionService.EncryptedValue("n2", "c2"));
      when(globalSettingRepository.existsById(LlmGlobalSettingEntity.SINGLETON_ID)).thenReturn(true);

      service.seedProvidersIfNecessary();

      verify(providerRepository).save(entity);
      assertThat(entity.getApiKeyCiphertext()).isEqualTo("c2");
      assertThat(entity.getApiKeyNonce()).isEqualTo("n2");
    }

    @Test
    @DisplayName("存量行已是真实 Key 时不覆盖")
    void keepsRealKey() {
      service = newService();
      when(providerRepository.count()).thenReturn(1L);
      Map<String, LlmProviderProperties.ProviderConfig> providers = new HashMap<>();
      providers.put("dashscope", config("sk-new-key"));
      when(properties.getProviders()).thenReturn(providers);

      LlmProviderEntity entity = LlmProviderEntity.builder()
          .id("dashscope")
          .apiKeyNonce("n1")
          .apiKeyCiphertext("c1")
          .builtin(true)
          .build();
      when(providerRepository.findById("dashscope")).thenReturn(Optional.of(entity));
      when(encryptionService.decrypt("n1", "c1")).thenReturn("sk-admin-set-key");
      when(globalSettingRepository.existsById(LlmGlobalSettingEntity.SINGLETON_ID)).thenReturn(true);

      service.seedProvidersIfNecessary();

      verify(providerRepository, never()).save(any());
    }
  }
}
