package com.quantmore.modules.llmprovider.service;

import com.quantmore.common.ai.LlmProviderRegistry;
import com.quantmore.common.exception.BusinessException;
import com.quantmore.common.exception.ErrorCode;
import com.quantmore.modules.llmprovider.dto.UserProviderDTO;
import com.quantmore.modules.llmprovider.dto.UserProviderUpsertRequest;
import com.quantmore.modules.llmprovider.model.LlmProviderEntity;
import com.quantmore.modules.llmprovider.model.UserProviderConfigEntity;
import com.quantmore.modules.llmprovider.repository.LlmProviderRepository;
import com.quantmore.modules.llmprovider.repository.UserProviderRepository;
import com.quantmore.modules.user.model.UserEntity;
import com.quantmore.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserProviderConfigService 测试")
class UserProviderConfigServiceTest {

  @Mock private UserProviderRepository userProviderRepository;
  @Mock private LlmProviderRepository llmProviderRepository;
  @Mock private ApiKeyEncryptionService encryptionService;
  @Mock private LlmProviderRegistry registry;
  @Mock private UserRepository userRepository;
  @Mock private ProviderConnectivityTester connectivityTester;

  private UserProviderConfigService service;

  private static final ApiKeyEncryptionService.EncryptedValue ENCRYPTED =
      new ApiKeyEncryptionService.EncryptedValue("nonce-1", "cipher-1");

  @BeforeEach
  void setUp() {
    service = new UserProviderConfigService(
        userProviderRepository, llmProviderRepository, encryptionService,
        registry, userRepository, connectivityTester);
  }

  private UserProviderConfigEntity userRow(Long userId, String providerId, boolean enabled) {
    return UserProviderConfigEntity.builder()
        .id(1L)
        .userId(userId)
        .providerId(providerId)
        .apiKeyNonce("nonce-1")
        .apiKeyCiphertext("cipher-1")
        .enabled(enabled)
        .build();
  }

  @Nested
  @DisplayName("upsert")
  class Upsert {

    @Test
    @DisplayName("内置 Provider 仅需 apiKey，Key 加密存储")
    void builtinProviderOnlyNeedsApiKey() {
      when(llmProviderRepository.existsById("deepseek")).thenReturn(true);
      when(encryptionService.encrypt("sk-user-1")).thenReturn(ENCRYPTED);
      when(userProviderRepository.findByUserIdAndProviderId(1L, "deepseek"))
          .thenReturn(Optional.empty());

      service.upsert(1L, "deepseek",
          new UserProviderUpsertRequest(null, "sk-user-1", null, null, null));

      verify(userProviderRepository).save(org.mockito.ArgumentMatchers.argThat(
          e -> "cipher-1".equals(e.getApiKeyCiphertext()) && "nonce-1".equals(e.getApiKeyNonce())));
    }

    @Test
    @DisplayName("自定义 Provider 缺 baseUrl 抛 BAD_REQUEST")
    void customProviderRequiresBaseUrl() {
      when(llmProviderRepository.existsById("my-custom")).thenReturn(false);

      assertThatThrownBy(() -> service.upsert(1L, "my-custom",
          new UserProviderUpsertRequest(null, "sk-x", "gpt-x", null, null)))
          .isInstanceOf(BusinessException.class)
          .satisfies(e -> assertThat(((BusinessException) e).getCode())
              .isEqualTo(ErrorCode.BAD_REQUEST.getCode()));
    }

    @Test
    @DisplayName("apiKey 为空抛 BAD_REQUEST")
    void emptyApiKeyRejected() {
      assertThatThrownBy(() -> service.upsert(1L, "deepseek",
          new UserProviderUpsertRequest(null, "  ", null, null, null)))
          .isInstanceOf(BusinessException.class)
          .satisfies(e -> assertThat(((BusinessException) e).getCode())
              .isEqualTo(ErrorCode.BAD_REQUEST.getCode()));
    }
  }

  @Nested
  @DisplayName("默认模型")
  class DefaultProvider {

    @Test
    @DisplayName("删除的 Provider 若是用户默认模型则清空引用")
    void deleteClearsDefaultReference() {
      UserEntity user = new UserEntity();
      user.setId(1L);
      user.setDefaultProviderId("deepseek");
      when(userRepository.findById(1L)).thenReturn(Optional.of(user));

      service.delete(1L, "deepseek");

      assertThat(user.getDefaultProviderId()).isNull();
      verify(userRepository).save(user);
    }

    @Test
    @DisplayName("设置默认模型写入 users 表")
    void setDefaultPersists() {
      UserEntity user = new UserEntity();
      user.setId(1L);
      when(userRepository.findById(1L)).thenReturn(Optional.of(user));

      service.setDefaultProvider(1L, "kimi");

      assertThat(user.getDefaultProviderId()).isEqualTo("kimi");
      verify(userRepository).save(user);
    }
  }

  @Nested
  @DisplayName("list")
  class ListView {

    @Test
    @DisplayName("合并全局内置与用户配置，标记 hasOwnConfig")
    void mergesGlobalWithUserConfigs() {
      LlmProviderEntity global = LlmProviderEntity.builder()
          .id("deepseek")
          .baseUrl("https://api.deepseek.com")
          .model("deepseek-chat")
          .enabled(true)
          .builtin(true)
          .supportsEmbedding(false)
          .build();
      when(llmProviderRepository.findAll()).thenReturn(List.of(global));
      when(userProviderRepository.findByUserId(1L))
          .thenReturn(List.of(userRow(1L, "deepseek", true)));
      when(encryptionService.decrypt("nonce-1", "cipher-1")).thenReturn("sk-secret");
      when(userRepository.findById(1L)).thenReturn(Optional.empty());

      List<UserProviderDTO> result = service.list(1L);

      assertThat(result).hasSize(1);
      UserProviderDTO dto = result.get(0);
      assertThat(dto.id()).isEqualTo("deepseek");
      assertThat(dto.hasOwnConfig()).isTrue();
      assertThat(dto.custom()).isFalse();
      assertThat(dto.maskedApiKey()).isEqualTo("sk-***ret");
    }

    @Test
    @DisplayName("用户自定义 Provider 以 custom=true 出现在列表")
    void listsCustomProviders() {
      when(llmProviderRepository.findAll()).thenReturn(List.of());
      UserProviderConfigEntity custom = userRow(1L, "my-gpt", true);
      custom.setBaseUrl("https://my-gpt.example.com/v1");
      custom.setModel("gpt-4o");
      when(userProviderRepository.findByUserId(1L)).thenReturn(List.of(custom));
      when(encryptionService.decrypt("nonce-1", "cipher-1")).thenReturn("sk-secret");
      when(userRepository.findById(1L)).thenReturn(Optional.empty());

      List<UserProviderDTO> result = service.list(1L);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).custom()).isTrue();
      assertThat(result.get(0).model()).isEqualTo("gpt-4o");
    }
  }
}
