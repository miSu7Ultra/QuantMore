package com.quantmore.modules.generator.eval;

import com.quantmore.common.ai.LlmProviderRegistry;
import com.quantmore.common.ai.PromptSanitizer;
import com.quantmore.common.ai.PromptSecurityConstants;
import com.quantmore.common.exception.BusinessException;
import com.quantmore.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EvalJudgeService 测试")
class EvalJudgeServiceTest {

  @Mock private LlmProviderRegistry registry;
  @Mock private PromptSanitizer sanitizer;

  private EvalProperties properties;
  private EvalJudgeService service;

  private static final String RAW_JSON =
      "{\"score\": 82, \"passed\": true, "
          + "\"issues\": [{\"dimension\": \"结构\", \"comment\": \"完整\"}]}";

  @BeforeEach
  void setUp() throws Exception {
    properties = new EvalProperties();
    org.mockito.Mockito.lenient().when(sanitizer.sanitize(anyString()))
        .thenAnswer(inv -> inv.getArgument(0));
    org.mockito.Mockito.lenient().when(sanitizer.wrapWithDelimiters(anyString(), anyString()))
        .thenAnswer(inv -> "[" + inv.getArgument(0) + "]\n" + inv.getArgument(1));
    service = new EvalJudgeService(registry, sanitizer, properties);
  }

  private AtomicReference<String> stubPlainClient(String raw) {
    ChatClient chatClient = mock(ChatClient.class);
    ChatClient.ChatClientRequestSpec promptSpec = mock(ChatClient.ChatClientRequestSpec.class);
    when(registry.getPlainChatClient()).thenReturn(chatClient);
    when(chatClient.prompt()).thenReturn(promptSpec);
    AtomicReference<String> systemPrompt = new AtomicReference<>();
    when(promptSpec.system(anyString())).thenAnswer(inv -> {
      systemPrompt.set(inv.getArgument(0));
      return promptSpec;
    });
    when(promptSpec.call()).thenReturn(mock(ChatClient.CallResponseSpec.class));
    when(promptSpec.call().chatClientResponse()).thenReturn(
        new ChatClientResponse(
            new ChatResponse(List.of(new Generation(new AssistantMessage(raw)))), Map.of()));
    return systemPrompt;
  }

  private EvalCase caseMeta() {
    return new EvalCase("s01", "双均线策略", "STOCK", "DAILY", "金叉买入", "死叉卖出", "", "SIMPLE");
  }

  @Test
  @DisplayName("评分成功:提示词含用例字段/代码/反注入指令,结果解析正确")
  void judgesAndParsesResult() {
    AtomicReference<String> systemPrompt = stubPlainClient(RAW_JSON);

    EvalJudgeService.JudgeResult result = service.judge(caseMeta(), "def initialize(context):\n    pass\n");

    assertThat(result.score()).isEqualTo(82.0);
    assertThat(result.passed()).isTrue();
    assertThat(result.issues()).hasSize(1);
    assertThat(systemPrompt.get())
        .contains("双均线策略", "金叉买入", "def initialize", "generated-code")
        .endsWith(PromptSecurityConstants.ANTI_INJECTION_INSTRUCTION);
  }

  @Test
  @DisplayName("评委返回空内容抛 AI_SERVICE_ERROR")
  void emptyRawThrowsAiServiceError() {
    stubPlainClient("   ");

    assertThatThrownBy(() -> service.judge(caseMeta(), "pass\n"))
        .isInstanceOf(BusinessException.class)
        .satisfies(e -> assertThat(((BusinessException) e).getCode())
            .isEqualTo(ErrorCode.AI_SERVICE_ERROR.getCode()));
  }

  @Test
  @DisplayName("LLM 调用异常抛 AI_SERVICE_ERROR")
  void llmFailureThrowsAiServiceError() {
    ChatClient chatClient = mock(ChatClient.class);
    when(registry.getPlainChatClient()).thenReturn(chatClient);
    when(chatClient.prompt()).thenThrow(new RuntimeException("boom"));

    assertThatThrownBy(() -> service.judge(caseMeta(), "pass\n"))
        .isInstanceOf(BusinessException.class)
        .satisfies(e -> assertThat(((BusinessException) e).getCode())
            .isEqualTo(ErrorCode.AI_SERVICE_ERROR.getCode()));
  }
}
