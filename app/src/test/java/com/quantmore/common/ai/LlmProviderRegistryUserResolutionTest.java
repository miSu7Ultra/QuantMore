package com.quantmore.common.ai;

import com.quantmore.common.config.LlmProviderProperties;
import com.quantmore.modules.llmprovider.model.LlmProviderEntity;
import com.quantmore.modules.llmprovider.model.UserProviderConfigEntity;
import com.quantmore.modules.llmprovider.repository.LlmProviderRepository;
import com.quantmore.modules.llmprovider.repository.UserProviderRepository;
import com.quantmore.modules.llmprovider.service.ApiKeyEncryptionService;
import com.quantmore.modules.user.model.UserEntity;
import com.quantmore.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LlmProviderRegistry 用户解析测试")
class LlmProviderRegistryUserResolutionTest {

  @Mock private LlmProviderProperties properties;
  @Mock private LlmProviderRepository llmProviderRepository;
  @Mock private ApiKeyEncryptionService encryptionService;
  @Mock private UserProviderRepository userProviderRepository;
  @Mock private UserRepository userRepository;

  private LlmProviderRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new LlmProviderRegistry(
        properties, llmProviderRepository, null, encryptionService,
        null, null, userProviderRepository, userRepository);
  }

  private LlmProviderEntity globalDeepseek() {
    return LlmProviderEntity.builder()
        .id("deepseek")
        .baseUrl("https://api.deepseek.com")
        .apiKeyNonce("g-nonce")
        .apiKeyCiphertext("g-cipher")
        .model("deepseek-chat")
        .enabled(true)
        .builtin(true)
        .supportsEmbedding(false)
        .build();
  }

  private UserProviderConfigEntity userRow(String providerId, boolean enabled) {
    return UserProviderConfigEntity.builder()
        .id(1L)
        .userId(1L)
        .providerId(providerId)
        .apiKeyNonce("u-nonce")
        .apiKeyCiphertext("u-cipher")
        .enabled(enabled)
        .build();
  }

  @Nested
  @DisplayName("解析优先级")
  class Precedence {

    @Test
    @DisplayName("用户配置行覆盖全局 Key，其余字段回退全局")
    void userConfigOverridesGlobalKey() {
      when(userProviderRepository.findByUserIdAndProviderId(1L, "deepseek"))
          .thenReturn(Optional.of(userRow("deepseek", true)));
      when(encryptionService.decrypt("u-nonce", "u-cipher")).thenReturn("sk-user-key");
      when(llmProviderRepository.findById("deepseek")).thenReturn(Optional.of(globalDeepseek()));

      LlmProviderRegistry.ProviderSnapshot snapshot =
          registry.resolveUserProviderOrThrow(1L, "deepseek");

      assertThat(snapshot.apiKey()).isEqualTo("sk-user-key");
      assertThat(snapshot.baseUrl()).isEqualTo("https://api.deepseek.com");
      assertThat(snapshot.model()).isEqualTo("deepseek-chat");
    }

    @Test
    @DisplayName("用户行 disabled 时回退全局配置")
    void disabledUserConfigFallsBackToGlobal() {
      when(userProviderRepository.findByUserIdAndProviderId(1L, "deepseek"))
          .thenReturn(Optional.of(userRow("deepseek", false)));
      when(encryptionService.decrypt("g-nonce", "g-cipher")).thenReturn("sk-global-key");
      when(llmProviderRepository.findById("deepseek")).thenReturn(Optional.of(globalDeepseek()));

      LlmProviderRegistry.ProviderSnapshot snapshot =
          registry.resolveUserProviderOrThrow(1L, "deepseek");

      assertThat(snapshot.apiKey()).isEqualTo("sk-global-key");
    }

    @Test
    @DisplayName("未知 Provider 且用户行缺 baseUrl 抛异常")
    void unknownProviderWithoutBaseUrlFails() {
      UserProviderConfigEntity row = userRow("my-gpt", true);
      row.setModel("gpt-4o");
      when(userProviderRepository.findByUserIdAndProviderId(1L, "my-gpt"))
          .thenReturn(Optional.of(row));
      when(encryptionService.decrypt("u-nonce", "u-cipher")).thenReturn("sk-user-key");
      when(llmProviderRepository.findById("my-gpt")).thenReturn(Optional.empty());

      assertThatThrownBy(() -> registry.resolveUserProviderOrThrow(1L, "my-gpt"))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("默认模型解析")
  class DefaultProvider {

    @Test
    @DisplayName("用户设了默认 provider 时优先使用")
    void userDefaultProviderWins() {
      UserEntity user = new UserEntity();
      user.setId(1L);
      user.setDefaultProviderId("kimi");
      when(userRepository.findById(1L)).thenReturn(Optional.of(user));

      String defaultId = registry.resolveDefaultProviderIdForUser(1L);

      assertThat(defaultId).isEqualTo("kimi");
    }

    @Test
    @DisplayName("用户未设默认时回退全局默认")
    void fallsBackToGlobalDefault() {
      when(userRepository.findById(1L)).thenReturn(Optional.empty());
      when(properties.getDefaultProvider()).thenReturn("dashscope");

      String defaultId = registry.resolveDefaultProviderIdForUser(1L);

      assertThat(defaultId).isEqualTo("dashscope");
    }
  }
}
