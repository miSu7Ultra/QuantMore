package com.quantmore.common.ai;

import com.openai.client.OpenAIClient;
import com.quantmore.common.config.LlmProviderProperties;
import com.quantmore.common.config.LlmProviderProperties.AdvisorConfig;
import com.quantmore.common.config.LlmProviderProperties.ProviderConfig;
import com.quantmore.common.exception.BusinessException;
import com.quantmore.common.exception.ErrorCode;
import com.quantmore.modules.llmprovider.model.LlmGlobalSettingEntity;
import com.quantmore.modules.llmprovider.model.LlmProviderEntity;
import com.quantmore.modules.llmprovider.model.UserProviderConfigEntity;
import com.quantmore.modules.llmprovider.repository.LlmGlobalSettingRepository;
import com.quantmore.modules.llmprovider.repository.LlmProviderRepository;
import com.quantmore.modules.llmprovider.repository.UserProviderRepository;
import com.quantmore.modules.llmprovider.service.ApiKeyEncryptionService;
import com.quantmore.modules.user.model.UserEntity;
import com.quantmore.modules.user.repository.UserRepository;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing and caching LLM providers.
 * Supports dynamic creation of ChatClient based on provider configurations.
 */
@Component
@Slf4j
public class LlmProviderRegistry {

    private final LlmProviderProperties properties;
    private final Map<String, ChatClient> clientCache = new ConcurrentHashMap<>();
    private final Map<String, OpenAiChatModel> chatModelCache = new ConcurrentHashMap<>();
    private final Map<String, EmbeddingModel> embeddingModelCache = new ConcurrentHashMap<>();
    private final LlmProviderRepository providerRepository;
    private final LlmGlobalSettingRepository globalSettingRepository;
    private final ApiKeyEncryptionService encryptionService;
    private final UserProviderRepository userProviderRepository;
    private final UserRepository userRepository;

    private final ToolCallingManager toolCallingManager;
    private final ObservationRegistry observationRegistry;
    private static final Map<String, String> RECOMMENDED_EMBEDDING_MODELS = Map.of(
        "qwen", "text-embedding-v4",
        "glm", "embedding-3",
        "zhipu", "embedding-3",
        "baidu", "Embedding-V1",
        "minimax", "embo-01"
    );

    @Autowired
    public LlmProviderRegistry(
            LlmProviderProperties properties,
            LlmProviderRepository providerRepository,
            LlmGlobalSettingRepository globalSettingRepository,
            ApiKeyEncryptionService encryptionService,
            @Autowired(required = false) ToolCallingManager toolCallingManager,
            @Autowired(required = false) ObservationRegistry observationRegistry,
            @Autowired(required = false) UserProviderRepository userProviderRepository,
            @Autowired(required = false) UserRepository userRepository) {
        this.properties = properties;
        this.providerRepository = providerRepository;
        this.globalSettingRepository = globalSettingRepository;
        this.encryptionService = encryptionService;
        this.toolCallingManager = toolCallingManager;
        this.observationRegistry = observationRegistry;
        this.userProviderRepository = userProviderRepository;
        this.userRepository = userRepository;
    }

    public LlmProviderRegistry(
            LlmProviderProperties properties,
            ToolCallingManager toolCallingManager,
            ObservationRegistry observationRegistry) {
        this(properties, null, null, null, toolCallingManager, observationRegistry, null, null);
    }

    /**
     * Get a ChatClient for the specified provider ID.
     * If the client is not in the cache, it will be created based on the provider's configuration.
     *
     * @param providerId The ID of the provider (e.g., "qwen", "deepseek")
     * @return A ChatClient instance
     * @throws IllegalArgumentException if the providerId is unknown
     */
    public ChatClient getChatClient(String providerId) {
        return clientCache.computeIfAbsent(providerId, id -> {
            log.info("[LlmProviderRegistry] Creating new client for provider: {}", id);
            return createChatClient(id);
        });
    }

    /**
     * 获取指定用户视角的 ChatClient。
     * 解析优先级：用户配置行（enabled 且有 Key）覆盖全局内置行；未知 providerId 视为用户自定义。
     */
    public ChatClient getChatClientForUser(Long userId, String providerId) {
        String resolvedId = resolveProviderId(providerId);
        String cacheKey = "user:" + userId + ":" + resolvedId;
        return clientCache.computeIfAbsent(cacheKey, key -> {
            log.info("[LlmProviderRegistry] Creating new user client: userId={}, provider={}", userId, resolvedId);
            return createChatClient(resolveUserProviderOrThrow(userId, resolvedId), cacheKey);
        });
    }

    /**
     * 获取用户的默认 ChatClient：用户自设默认 provider，否则全局默认。
     */
    public ChatClient getDefaultChatClientForUser(Long userId) {
        return getChatClientForUser(userId, resolveDefaultProviderIdForUser(userId));
    }

    /**
     * Get the default ChatClient based on app.ai.default-provider.
     *
     * @return The default ChatClient instance
     */
    public ChatClient getDefaultChatClient() {
        return getChatClient(resolveDefaultChatProviderId());
    }

    /**
     * 获取默认 provider 的不带 SkillsTool 的 ChatClient，用于纯粹的摘要 / 结构化文本场景。
     * 与 {@link #getDefaultChatClient()} 的区别在于不挂 Skill 工具与记忆 Advisor，避免无关上下文干扰。
     */
    public ChatClient getPlainChatClient() {
        return getPlainChatClient(resolveDefaultChatProviderId());
    }

    /**
     * Get a ChatClient for the specified provider, falling back to the default if null, blank, or
     * the legacy "default" alias.
     */
    public ChatClient getChatClientOrDefault(String providerId) {
        return getChatClient(resolveProviderId(providerId));
    }

    /**
     * 获取不带 SkillsTool 的 ChatClient，用于结构化输出场景（出题、简历评分等）。
     * 这些场景要求模型一次性返回可解析 JSON，不应混入工具调用消息。
     */
    public ChatClient getPlainChatClient(String providerId) {
        String id = resolveProviderId(providerId);
        return clientCache.computeIfAbsent(id + ":plain", key -> createPlainChatClient(id));
    }

    /**
     * 清空缓存，重新加载所有 provider。
     */
    public void reload() {
        int size = clientCache.size() + chatModelCache.size() + embeddingModelCache.size();
        clientCache.clear();
        chatModelCache.clear();
        embeddingModelCache.clear();
        log.info("[LlmProviderRegistry] Cache cleared ({} entries). Next access will re-create clients.", size);
    }

    public EmbeddingModel getEmbeddingModel(String providerId) {
        return embeddingModelCache.computeIfAbsent(providerId, id -> {
            log.info("[LlmProviderRegistry] Creating new embedding model for provider: {}", id);
            return createEmbeddingModel(id);
        });
    }

    public EmbeddingModel getDefaultEmbeddingModel() {
        return getEmbeddingModel(resolveDefaultEmbeddingProviderId());
    }

    private ChatClient createChatClient(String providerId) {
        return createChatClient(providerId, providerId);
    }

    private ChatClient createChatClient(String providerId, String cacheKey) {
        ProviderSnapshot snapshot = loadProviderOrThrow(providerId);
        return createChatClient(snapshot, cacheKey);
    }

    private ChatClient createChatClient(ProviderSnapshot snapshot, String cacheKey) {
        OpenAiChatModel chatModel = getChatModel(snapshot, cacheKey);

        ChatClient.Builder builder = ChatClient.builder(chatModel);
        List<Advisor> advisors = buildDefaultAdvisors(snapshot.id());
        if (!advisors.isEmpty()) {
            builder.defaultAdvisors(advisors);
            log.info("[LlmProviderRegistry] Applied {} advisors for provider {}", advisors.size(), snapshot.id());
        }

        return builder.build();
    }

    private ChatClient createPlainChatClient(String providerId) {
        // 与默认客户端共享 provider 级 ChatModel 缓存：缓存命中时不重复加载 Provider 配置
        OpenAiChatModel chatModel = getChatModel(providerId, providerId);
        ChatClient.Builder builder = ChatClient.builder(chatModel);
        buildSafeGuardAdvisor().ifPresent(advisor -> builder.defaultAdvisors(List.of(advisor)));
        log.info("[LlmProviderRegistry] Created plain ChatClient (no tools) for {}", providerId);
        return builder.build();
    }

    private OpenAiChatModel getChatModel(String providerId, String cacheKey) {
        return chatModelCache.computeIfAbsent(cacheKey, key -> {
            log.info("[LlmProviderRegistry] Creating new ChatModel for key: {}", key);
            return buildChatModel(loadProviderOrThrow(providerId));
        });
    }

    private OpenAiChatModel getChatModel(ProviderSnapshot snapshot, String cacheKey) {
        return chatModelCache.computeIfAbsent(cacheKey, key -> {
            log.info("[LlmProviderRegistry] Creating new ChatModel for key: {}", key);
            return buildChatModel(snapshot);
        });
    }

    private OpenAiChatModel buildChatModel(ProviderSnapshot config) {
        log.info("[LlmProviderRegistry] Building ChatModel - Provider: {}, BaseUrl: {}, Model: {}",
                 config.id(), config.baseUrl(), config.model());

        OpenAIClient openAiClient = ApiPathResolver.buildOpenAiClient(config.baseUrl(), config.apiKey());

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(config.model())
                .temperature(config.temperature() != null ? config.temperature() : 0.2)
                .build();

        return OpenAiChatModel.builder()
            .openAiClient(openAiClient)
            .openAiClientAsync(openAiClient.async())
            .options(options)
            .observationRegistry(observationRegistry != null ? observationRegistry : ObservationRegistry.NOOP)
            .build();
    }

    private EmbeddingModel createEmbeddingModel(String providerId) {
        ProviderSnapshot config = loadProviderOrThrow(providerId);
        if (!config.supportsEmbedding() || isBlank(config.embeddingModel())) {
            throw new BusinessException(ErrorCode.PROVIDER_CONFIG_READ_FAILED,
                "Provider '" + providerId + "' 未配置可用的 Embedding 模型，无法执行知识库向量化");
        }
        if (looksLikeChatModel(config.embeddingModel())) {
            String recommendation = RECOMMENDED_EMBEDDING_MODELS.get(providerId.toLowerCase());
            String suffix = recommendation != null
                ? "，推荐填写 " + recommendation
                : "，请填写该厂商真实的 Embedding 模型名";
            throw new BusinessException(ErrorCode.PROVIDER_CONFIG_READ_FAILED,
                "Provider '" + providerId + "' 的 Embedding Model 配成了聊天模型 '"
                    + config.embeddingModel() + "'" + suffix);
        }
        log.info("[LlmProviderRegistry] Building EmbeddingModel - Provider: {}, BaseUrl: {}, Model: {}",
            providerId, config.baseUrl(), config.embeddingModel());

        OpenAIClient openAiClient = ApiPathResolver.buildOpenAiClient(config.baseUrl(), config.apiKey());
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
            .model(config.embeddingModel())
            .dimensions(resolveEmbeddingDimensions(config.embeddingDimensions()))
            .build();

        return OpenAiEmbeddingModel.builder()
            .openAiClient(openAiClient)
            .metadataMode(MetadataMode.EMBED)
            .options(options)
            .observationRegistry(observationRegistry != null ? observationRegistry : ObservationRegistry.NOOP)
            .build();
    }

    private List<Advisor> buildDefaultAdvisors(String providerId) {
        AdvisorConfig config = properties.getAdvisors();
        if (config == null || !config.isEnabled()) {
            return List.of();
        }

        List<Advisor> advisors = new ArrayList<>();

        if (config.isToolCallEnabled()) {
            if (toolCallingManager != null) {
                advisors.add(buildToolCallAdvisor(config.isToolCallConversationHistoryEnabled()));
            } else {
                log.warn("[LlmProviderRegistry] ToolCallAdvisor skipped: ToolCallingManager unavailable, provider={}", providerId);
            }
        }

        if (config.isMessageChatMemoryEnabled()) {
            int maxMessages = Math.max(20, config.getMessageChatMemoryMaxMessages());
            MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(
                MessageWindowChatMemory.builder()
                    .maxMessages(maxMessages)
                    .build()
            ).build();
            advisors.add(memoryAdvisor);
        }

        if (config.isSimpleLoggerEnabled()) {
            advisors.add(new SimpleLoggerAdvisor());
        }

        buildSafeGuardAdvisor().ifPresent(advisors::add);

        return advisors;
    }

    private ToolCallingAdvisor buildToolCallAdvisor(boolean conversationHistoryEnabled) {
        return ToolCallingAdvisor.builder()
            .toolCallingManager(toolCallingManager)
            .conversationHistoryEnabled(conversationHistoryEnabled)
            .build();
    }

    private Optional<SafeGuardAdvisor> buildSafeGuardAdvisor() {
        AdvisorConfig config = properties.getAdvisors();
        if (config == null || !config.isSafeguardEnabled()) {
            return Optional.empty();
        }
        SafeGuardAdvisor advisor = SafeGuardAdvisor.builder()
            .sensitiveWords(config.getSafeguardWords())
            .failureResponse("抱歉，我只能协助 PTrade 策略开发相关的任务。")
            .order(100)
            .build();
        return Optional.of(advisor);
    }

    private String resolveProviderId(String providerId) {
        if (providerId == null || providerId.isBlank() || "default".equalsIgnoreCase(providerId.trim())) {
            return resolveDefaultChatProviderId();
        }
        return providerId;
    }

    private String resolveDefaultChatProviderId() {
        if (globalSettingRepository == null) {
            return properties.getDefaultProvider();
        }
        return globalSettingRepository.findById(LlmGlobalSettingEntity.SINGLETON_ID)
            .map(LlmGlobalSettingEntity::getDefaultChatProviderId)
            .filter(id -> !isBlank(id))
            .orElse(properties.getDefaultProvider());
    }

    private String resolveDefaultEmbeddingProviderId() {
        if (globalSettingRepository == null) {
            return !isBlank(properties.getDefaultEmbeddingProvider())
                ? properties.getDefaultEmbeddingProvider()
                : properties.getDefaultProvider();
        }
        return globalSettingRepository.findById(LlmGlobalSettingEntity.SINGLETON_ID)
            .map(LlmGlobalSettingEntity::getDefaultEmbeddingProviderId)
            .filter(id -> !isBlank(id))
            .orElseGet(() -> !isBlank(properties.getDefaultEmbeddingProvider())
                ? properties.getDefaultEmbeddingProvider()
                : properties.getDefaultProvider());
    }

    /**
     * 用户默认聊天 provider：优先 users.default_provider_id，否则全局默认。
     */
    String resolveDefaultProviderIdForUser(Long userId) {
        if (userRepository != null) {
            return userRepository.findById(userId)
                .map(UserEntity::getDefaultProviderId)
                .filter(id -> !isBlank(id))
                .orElseGet(this::resolveDefaultChatProviderId);
        }
        return resolveDefaultChatProviderId();
    }

    /**
     * 解析用户视角的 provider 运行配置。
     * 用户配置行（enabled 且 Key 非空）覆盖全局内置行；未知 providerId 视为用户自定义（须有 baseUrl+model）。
     */
    public ProviderSnapshot resolveUserProviderOrThrow(Long userId, String providerId) {
        UserProviderConfigEntity userConfig = userProviderRepository != null
            ? userProviderRepository.findByUserIdAndProviderId(userId, providerId).orElse(null)
            : null;
        boolean userActive = userConfig != null && userConfig.isEnabled()
            && !isBlank(decryptUserApiKey(userConfig));
        if (!userActive) {
            return loadProviderOrThrow(providerId);
        }
        LlmProviderEntity global = providerRepository != null
            ? providerRepository.findById(providerId).orElse(null)
            : null;
        String baseUrl = !isBlank(userConfig.getBaseUrl()) ? userConfig.getBaseUrl()
            : (global != null ? global.getBaseUrl() : null);
        String model = !isBlank(userConfig.getModel()) ? userConfig.getModel()
            : (global != null ? global.getModel() : null);
        if (isBlank(baseUrl) || isBlank(model)) {
            throw new IllegalArgumentException(
                "用户自定义 Provider '" + providerId + "' 必须配置 baseUrl 与 model");
        }
        Double temperature = userConfig.getTemperature() != null ? userConfig.getTemperature()
            : (global != null ? global.getTemperature() : null);
        return new ProviderSnapshot(
            providerId,
            baseUrl,
            decryptUserApiKey(userConfig),
            model,
            global != null ? global.getEmbeddingModel() : null,
            global != null ? global.getEmbeddingDimensions() : null,
            global != null && global.isSupportsEmbedding(),
            temperature
        );
    }

    private String decryptUserApiKey(UserProviderConfigEntity entity) {
        if (encryptionService == null) {
            throw new IllegalStateException("ApiKeyEncryptionService 未初始化，无法解密用户 Provider 配置");
        }
        return encryptionService.decrypt(entity.getApiKeyNonce(), entity.getApiKeyCiphertext());
    }

    private ProviderSnapshot loadProviderOrThrow(String providerId) {
        if (providerRepository == null) {
            return loadProviderFromPropertiesOrThrow(providerId);
        }
        LlmProviderEntity entity = providerRepository.findById(providerId)
            .filter(LlmProviderEntity::isEnabled)
            .orElseThrow(() -> new IllegalArgumentException("Unknown LLM provider: " + providerId));
        return new ProviderSnapshot(
            entity.getId(),
            entity.getBaseUrl(),
            encryptionService.decrypt(entity.getApiKeyNonce(), entity.getApiKeyCiphertext()),
            entity.getModel(),
            entity.getEmbeddingModel(),
            entity.getEmbeddingDimensions(),
            entity.isSupportsEmbedding(),
            entity.getTemperature()
        );
    }

    private ProviderSnapshot loadProviderFromPropertiesOrThrow(String providerId) {
        ProviderConfig config = properties.getProviders().get(providerId);
        if (config == null) {
            log.error("[LlmProviderRegistry] Provider config not found: {}", providerId);
            throw new IllegalArgumentException("Unknown LLM provider: " + providerId);
        }
        boolean supportsEmbedding = Boolean.TRUE.equals(config.getSupportsEmbedding())
            || !isBlank(config.getEmbeddingModel());
        return new ProviderSnapshot(
            providerId,
            config.getBaseUrl(),
            config.getApiKey(),
            config.getModel(),
            config.getEmbeddingModel(),
            config.getEmbeddingDimensions(),
            supportsEmbedding,
            config.getTemperature()
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Integer resolveEmbeddingDimensions(Integer configuredDimensions) {
        if (configuredDimensions != null && configuredDimensions > 0) {
            return configuredDimensions;
        }
        return properties.getEmbeddingDimensions();
    }

    private boolean looksLikeChatModel(String model) {
        String lower = model.toLowerCase();
        return lower.startsWith("glm-")
            || lower.startsWith("deepseek")
            || lower.startsWith("kimi")
            || lower.startsWith("moonshot")
            || lower.startsWith("qwen")
            || lower.startsWith("ernie");
    }

    public record ProviderSnapshot(
        String id,
        String baseUrl,
        String apiKey,
        String model,
        String embeddingModel,
        Integer embeddingDimensions,
        boolean supportsEmbedding,
        Double temperature
    ) {
    }
}
