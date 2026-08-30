package com.quantmore.modules.generator.service;

import com.quantmore.common.ai.LlmProviderRegistry;
import com.quantmore.common.ai.PromptSanitizer;
import com.quantmore.common.exception.BusinessException;
import com.quantmore.common.exception.ErrorCode;
import com.quantmore.modules.generator.config.GeneratorProperties;
import com.quantmore.modules.generator.dto.GenerateStrategyRequest;
import com.quantmore.modules.generator.dto.GenerateStrategyResponse;
import com.quantmore.modules.generator.model.StrategyGenerationEntity;
import com.quantmore.modules.generator.repository.StrategyGenerationRepository;
import com.quantmore.modules.knowledgebase.model.KbVisibility;
import com.quantmore.modules.knowledgebase.model.KnowledgeBaseEntity;
import com.quantmore.modules.knowledgebase.repository.KnowledgeBaseRepository;
import com.quantmore.modules.knowledgebase.service.KnowledgeBaseVectorService;
import com.quantmore.modules.user.model.UserPrincipal;
import com.quantmore.modules.user.model.UserRole;
import com.quantmore.modules.user.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ResourceLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StrategyGeneratorService 测试")
class StrategyGeneratorServiceTest {

  @Mock private StrategyGenerationRepository repository;
  @Mock private KnowledgeBaseVectorService vectorService;
  @Mock private KnowledgeBaseRepository knowledgeBaseRepository;
  @Mock private LlmProviderRegistry registry;
  @Mock private CurrentUserService currentUserService;
  @Mock private PromptSanitizer sanitizer;
  @Mock private ResourceLoader resourceLoader;

  private StrategyGeneratorService service;

  @TempDir Path tempDir;

  private GenerateStrategyRequest request() {
    return new GenerateStrategyRequest(
        "双均线策略", "STOCK", "DAILY",
        "五日均线上穿十日均线买入", "五日均线下穿十日均线卖出",
        "单只股票仓位不超过总资产50%", List.of(1L), null);
  }

  @BeforeEach
  void setUp() throws Exception {
    Path sysPrompt = tempDir.resolve("system.st");
    Path userPrompt = tempDir.resolve("user.st");
    Files.writeString(sysPrompt, "系统提示 {context}");
    Files.writeString(userPrompt, "生成 {strategyName} {market} {frequency} {buyConditions} {sellConditions} {riskControls}");
    when(resourceLoader.getResource("test-system")).thenReturn(new org.springframework.core.io.FileSystemResource(sysPrompt.toFile()));
    when(resourceLoader.getResource("test-user")).thenReturn(new org.springframework.core.io.FileSystemResource(userPrompt.toFile()));
    org.mockito.Mockito.lenient().when(sanitizer.sanitize(anyString()))
        .thenAnswer(inv -> inv.getArgument(0));

    GeneratorProperties properties = new GeneratorProperties();
    properties.setSystemPromptPath("test-system");
    properties.setUserPromptPath("test-user");

    service = new StrategyGeneratorService(
        repository, vectorService, knowledgeBaseRepository, registry,
        currentUserService, sanitizer, properties, resourceLoader);
  }

  @Nested
  @DisplayName("可见性校验")
  class Visibility {

    @Test
    @DisplayName("普通用户引用他人私有 KB 抛 KNOWLEDGE_BASE_FORBIDDEN")
    void rejectsOthersPrivateKb() {
      when(currentUserService.get()).thenReturn(new UserPrincipal(2L, "bob", UserRole.USER));
      when(knowledgeBaseRepository.isVisibleToUser(1L, 2L)).thenReturn(false);

      assertThatThrownBy(() -> service.generate(request()))
          .isInstanceOf(BusinessException.class)
          .satisfies(e -> assertThat(((BusinessException) e).getCode())
              .isEqualTo(ErrorCode.KNOWLEDGE_BASE_FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("管理员生成不校验可见性")
    void adminSkipsVisibilityCheck() {
      when(currentUserService.get()).thenReturn(new UserPrincipal(1L, "admin", UserRole.ADMIN));
      when(vectorService.similaritySearch(anyString(), anyList(), anyInt(), anyDouble()))
          .thenReturn(List.of(new Document("示例内容", java.util.Map.of())));
      ChatClient chatClient = mock(ChatClient.class);
      when(registry.getChatClientForUser(1L, null)).thenReturn(chatClient);
      when(chatClient.prompt()).thenReturn(mock(ChatClient.ChatClientRequestSpec.class));
      when(chatClient.prompt().system(anyString())).thenReturn(mock(ChatClient.ChatClientRequestSpec.class));
      when(chatClient.prompt().system(anyString()).user(anyString()))
          .thenReturn(mock(ChatClient.ChatClientRequestSpec.class));
      when(chatClient.prompt().system(anyString()).user(anyString()).call())
          .thenReturn(mock(ChatClient.CallResponseSpec.class));
      when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
          .thenReturn("```python\nprint('hi')\n```");
      StrategyGenerationEntity saved = new StrategyGenerationEntity();
      saved.setId(100L);
      saved.setUserId(1L);
      saved.setStrategyName("双均线策略");
      saved.setGeneratedCode("print('hi')");
      when(repository.save(any())).thenReturn(saved);

      GenerateStrategyResponse response = service.generate(request());

      assertThat(response.code()).isEqualTo("print('hi')");
    }
  }

  @Nested
  @DisplayName("检索")
  class Retrieval {

    @Test
    @DisplayName("检索查询包含策略名/市场/频率/买卖条件关键词")
    void queryContainsFormKeywords() {
      when(currentUserService.get()).thenReturn(new UserPrincipal(1L, "admin", UserRole.ADMIN));
      when(vectorService.similaritySearch(anyString(), anyList(), anyInt(), anyDouble()))
          .thenReturn(List.of());
      ChatClient chatClient = mock(ChatClient.class);
      when(registry.getChatClientForUser(1L, null)).thenReturn(chatClient);
      when(chatClient.prompt()).thenReturn(mock(ChatClient.ChatClientRequestSpec.class));
      when(chatClient.prompt().system(anyString())).thenReturn(mock(ChatClient.ChatClientRequestSpec.class));
      when(chatClient.prompt().system(anyString()).user(anyString()))
          .thenReturn(mock(ChatClient.ChatClientRequestSpec.class));
      when(chatClient.prompt().system(anyString()).user(anyString()).call())
          .thenReturn(mock(ChatClient.CallResponseSpec.class));
      when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
          .thenReturn("```python\npass\n```");
      when(repository.save(any())).thenAnswer(inv -> {
        StrategyGenerationEntity e = inv.getArgument(0);
        e.setId(1L);
        e.setCreatedAt(java.time.LocalDateTime.now());
        return e;
      });

      service.generate(request());

      ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
      verify(vectorService).similaritySearch(
          queryCaptor.capture(), anyList(), anyInt(), anyDouble());
      assertThat(queryCaptor.getValue())
          .contains("双均线策略", "STOCK", "DAILY", "五日均线上穿十日均线买入");
    }

    @Test
    @DisplayName("空知识库列表时仍可生成（提示词携带降级说明）")
    void generatesWithoutAnyKb() {
      when(currentUserService.get()).thenReturn(new UserPrincipal(1L, "admin", UserRole.ADMIN));
      when(knowledgeBaseRepository.findAllByOrderByUploadedAtDesc()).thenReturn(List.of());
      ChatClient chatClient = mock(ChatClient.class);
      when(registry.getChatClientForUser(1L, null)).thenReturn(chatClient);
      when(chatClient.prompt()).thenReturn(mock(ChatClient.ChatClientRequestSpec.class));
      when(chatClient.prompt().system(anyString())).thenReturn(mock(ChatClient.ChatClientRequestSpec.class));
      when(chatClient.prompt().system(anyString()).user(anyString()))
          .thenReturn(mock(ChatClient.ChatClientRequestSpec.class));
      when(chatClient.prompt().system(anyString()).user(anyString()).call())
          .thenReturn(mock(ChatClient.CallResponseSpec.class));
      when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
          .thenReturn("```python\npass\n```");
      when(repository.save(any())).thenAnswer(inv -> {
        StrategyGenerationEntity e = inv.getArgument(0);
        e.setId(1L);
        e.setCreatedAt(java.time.LocalDateTime.now());
        return e;
      });

      GenerateStrategyResponse response = service.generate(new GenerateStrategyRequest(
          "测试策略", "STOCK", "DAILY", "买入", "", "", null, null));

      assertThat(response).isNotNull();
      verify(vectorService, org.mockito.Mockito.never())
          .similaritySearch(anyString(), anyList(), anyInt(), anyDouble());
    }
  }

  @Nested
  @DisplayName("代码拆分")
  class CodeSplitting {

    @Test
    @DisplayName("有 python 围栏：围栏内为代码，其余为说明")
    void splitsFencedCode() {
      String raw = "这是说明\n```python\ndef initialize():\n    pass\n```\n更多说明";
      var result = service.splitExplanationAndCode(raw);
      assertThat(result.code()).contains("def initialize()");
      assertThat(result.explanation()).contains("这是说明", "更多说明");
    }

    @Test
    @DisplayName("无围栏：整体视为代码")
    void noFenceMeansAllCode() {
      String raw = "def handle_data(context, data):\n    pass";
      var result = service.splitExplanationAndCode(raw);
      assertThat(result.code()).isEqualTo(raw);
      assertThat(result.explanation()).isEmpty();
    }
  }

  @Nested
  @DisplayName("历史归属")
  class HistoryOwnership {

    @Test
    @DisplayName("他人记录抛 FORBIDDEN")
    void rejectsOthersRecord() {
      when(currentUserService.get()).thenReturn(new UserPrincipal(2L, "bob", UserRole.USER));
      StrategyGenerationEntity entity = new StrategyGenerationEntity();
      entity.setId(1L);
      entity.setUserId(3L);
      entity.setGeneratedCode("pass");
      when(repository.findById(1L)).thenReturn(java.util.Optional.of(entity));

      assertThatThrownBy(() -> service.getById(1L))
          .isInstanceOf(BusinessException.class)
          .satisfies(e -> assertThat(((BusinessException) e).getCode())
              .isEqualTo(ErrorCode.FORBIDDEN.getCode()));
    }
  }
}
