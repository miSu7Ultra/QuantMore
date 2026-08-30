package com.quantmore.modules.llmprovider.service;

import com.quantmore.common.ai.LlmProviderRegistry;
import com.quantmore.common.exception.BusinessException;
import com.quantmore.common.exception.ErrorCode;
import com.quantmore.modules.llmprovider.dto.ProviderTestResult;
import com.quantmore.modules.llmprovider.dto.UserProviderDTO;
import com.quantmore.modules.llmprovider.dto.UserProviderUpsertRequest;
import com.quantmore.modules.llmprovider.model.LlmProviderEntity;
import com.quantmore.modules.llmprovider.model.UserProviderConfigEntity;
import com.quantmore.modules.llmprovider.repository.LlmProviderRepository;
import com.quantmore.modules.llmprovider.repository.UserProviderRepository;
import com.quantmore.modules.user.model.UserEntity;
import com.quantmore.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户级 LLM Provider 配置：用户覆盖内置 Provider 的 Key/模型，或添加自定义 OpenAI 兼容端点。
 * 全局内置 Provider 模板仍由 LlmProviderConfigService 管理（管理员）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserProviderConfigService {

  private final UserProviderRepository userProviderRepository;
  private final LlmProviderRepository llmProviderRepository;
  private final ApiKeyEncryptionService encryptionService;
  private final LlmProviderRegistry registry;
  private final UserRepository userRepository;
  private final ProviderConnectivityTester connectivityTester;

  /**
   * 用户视角的 Provider 列表：全局内置 + 用户自定义，合并用户自己的配置状态
   */
  @Transactional(readOnly = true)
  public List<UserProviderDTO> list(Long userId) {
    Map<String, UserProviderConfigEntity> ownConfigs = userProviderRepository.findByUserId(userId).stream()
        .collect(Collectors.toMap(UserProviderConfigEntity::getProviderId, Function.identity()));
    String defaultProviderId = resolveDefaultProviderId(userId);

    List<UserProviderDTO> result = new ArrayList<>();
    for (var global : llmProviderRepository.findAll()) {
      UserProviderConfigEntity own = ownConfigs.remove(global.getId());
      boolean ownUsable = own != null && own.isEnabled() && !isBlank(decrypt(own));
      boolean globalUsable = global.isEnabled() && !isBlank(decrypt(global));
      result.add(toDto(global.getId(), global.getBaseUrl(),
          own != null ? own.getModel() : global.getModel(),
          own != null ? mask(decrypt(own)) : null,
          own != null, false,
          own != null ? own.isEnabled() : global.isEnabled(),
          global.isSupportsEmbedding(),
          ownUsable || globalUsable,
          global.getId().equals(defaultProviderId)));
    }
    // 剩余 = 用户自定义 Provider（不在全局表中）
    ownConfigs.forEach((id, own) -> result.add(toDto(id, own.getBaseUrl(), own.getModel(),
        mask(decrypt(own)), true, true, own.isEnabled(), false,
        own.isEnabled() && !isBlank(decrypt(own)),
        id.equals(defaultProviderId))));
    return result;
  }

  /**
   * 配置/覆盖一个 Provider。
   * 全局内置 Provider：baseUrl/model 可省略；自定义 Provider：三者必填。
   */
  @Transactional
  public void upsert(Long userId, String providerId, UserProviderUpsertRequest request) {
    String apiKey = trimOrNull(request.apiKey());
    if (apiKey == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "apiKey 不能为空");
    }
    boolean isBuiltin = llmProviderRepository.existsById(providerId);
    UserProviderConfigEntity entity = userProviderRepository
        .findByUserIdAndProviderId(userId, providerId)
        .orElseGet(() -> UserProviderConfigEntity.builder()
            .userId(userId)
            .providerId(providerId)
            .build());

    if (!isBuiltin) {
      String baseUrl = trimOrNull(request.baseUrl());
      String model = trimOrNull(request.model());
      if (baseUrl == null || model == null) {
        throw new BusinessException(ErrorCode.BAD_REQUEST,
            "自定义 Provider '" + providerId + "' 必须填写 baseUrl 与 model");
      }
      entity.setBaseUrl(baseUrl);
      entity.setModel(model);
    } else {
      String baseUrl = trimOrNull(request.baseUrl());
      if (baseUrl != null) entity.setBaseUrl(baseUrl);
      String model = trimOrNull(request.model());
      if (model != null) entity.setModel(model);
    }
    ApiKeyEncryptionService.EncryptedValue encrypted = encryptionService.encrypt(apiKey);
    entity.setApiKeyNonce(encrypted.nonce());
    entity.setApiKeyCiphertext(encrypted.ciphertext());
    if (request.temperature() != null) entity.setTemperature(request.temperature());
    if (request.enabled() != null) entity.setEnabled(request.enabled());

    userProviderRepository.save(entity);
    registry.reload();
    log.info("User provider upserted: userId={}, providerId={}, custom={}", userId, providerId, !isBuiltin);
  }

  /**
   * 删除用户的 Provider 配置（同时清除其默认模型引用）
   */
  @Transactional
  public void delete(Long userId, String providerId) {
    userProviderRepository.deleteByUserIdAndProviderId(userId, providerId);
    userRepository.findById(userId).ifPresent(user -> {
      if (providerId.equals(user.getDefaultProviderId())) {
        user.setDefaultProviderId(null);
        userRepository.save(user);
      }
    });
    registry.reload();
    log.info("User provider deleted: userId={}, providerId={}", userId, providerId);
  }

  @Transactional(readOnly = true)
  public String getDefaultProviderId(Long userId) {
    return userRepository.findById(userId)
        .map(UserEntity::getDefaultProviderId)
        .orElse(null);
  }

  /**
   * 设置用户默认聊天模型；null 表示回退全局默认
   */
  @Transactional
  public void setDefaultProvider(Long userId, String providerId) {
    String normalized = trimOrNull(providerId);
    userRepository.findById(userId).ifPresent(user -> {
      user.setDefaultProviderId(normalized);
      userRepository.save(user);
    });
    registry.reload();
    log.info("User default provider updated: userId={}, providerId={}", userId, normalized);
  }

  /**
   * 用户视角的连通性测试（含用户覆盖后的 Key/baseUrl/model）
   */
  @Transactional(readOnly = true)
  public ProviderTestResult test(Long userId, String providerId) {
    LlmProviderRegistry.ProviderSnapshot snapshot = registry.resolveUserProviderOrThrow(userId, providerId);
    return connectivityTester.test(providerId, snapshot.baseUrl(), snapshot.apiKey(), snapshot.model());
  }

  private String resolveDefaultProviderId(Long userId) {
    return userRepository.findById(userId)
        .map(UserEntity::getDefaultProviderId)
        .orElse(null);
  }

  private UserProviderDTO toDto(
      String id,
      String baseUrl,
      String model,
      String maskedApiKey,
      boolean hasOwnConfig,
      boolean custom,
      boolean enabled,
      boolean supportsEmbedding,
      boolean available,
      boolean defaultChatProvider) {
    return UserProviderDTO.builder()
        .id(id)
        .baseUrl(baseUrl)
        .model(model)
        .maskedApiKey(maskedApiKey)
        .hasOwnConfig(hasOwnConfig)
        .custom(custom)
        .enabled(enabled)
        .supportsEmbedding(supportsEmbedding)
        .available(available)
        .defaultChatProvider(defaultChatProvider)
        .build();
  }

  private String decrypt(UserProviderConfigEntity entity) {
    return encryptionService.decrypt(entity.getApiKeyNonce(), entity.getApiKeyCiphertext());
  }

  private String decrypt(LlmProviderEntity entity) {
    return encryptionService.decrypt(entity.getApiKeyNonce(), entity.getApiKeyCiphertext());
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private String mask(String apiKey) {
    if (apiKey == null || apiKey.length() <= 6) {
      return "***";
    }
    return apiKey.substring(0, 3) + "***" + apiKey.substring(apiKey.length() - 3);
  }

  private String trimOrNull(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
