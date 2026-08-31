package com.quantmore.modules.generator.eval;

import com.quantmore.common.ai.LlmProviderRegistry;
import com.quantmore.common.ai.PromptSanitizer;
import com.quantmore.common.ai.PromptSecurityConstants;
import com.quantmore.common.exception.BusinessException;
import com.quantmore.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * LLM 评委：按 rubric 对生成代码评分，返回结构化 JSON 结果。
 * 使用 getPlainChatClient（无工具/记忆 advisor），保证输出为可解析的纯文本 JSON。
 */
@Slf4j
@Component
public class EvalJudgeService {

  private final LlmProviderRegistry registry;
  private final PromptSanitizer sanitizer;
  private final EvalProperties properties;
  private final PromptTemplate template;

  public EvalJudgeService(
      LlmProviderRegistry registry,
      PromptSanitizer sanitizer,
      EvalProperties properties) throws IOException {
    this.registry = registry;
    this.sanitizer = sanitizer;
    this.properties = properties;
    this.template = new PromptTemplate(
        new ClassPathResource("prompts/strategy-eval-judge.st")
            .getContentAsString(StandardCharsets.UTF_8));
  }

  /**
   * 评分失败抛 BusinessException(AI_SERVICE_ERROR)，由评测服务记 judgeFailed
   */
  public JudgeResult judge(EvalCase caseMeta, String code) {
    String systemPrompt = template.render(Map.of(
        "strategyName", sanitizer.sanitize(caseMeta.name()).trim(),
        "market", caseMeta.market(),
        "frequency", caseMeta.frequency(),
        "buyConditions", sanitizer.sanitize(caseMeta.buyConditions()).trim(),
        "sellConditions", sanitizeNullable(caseMeta.sellConditions()),
        "riskControls", sanitizeNullable(caseMeta.riskControls()),
        "generatedCode", sanitizer.wrapWithDelimiters("generated-code", code)
    )) + PromptSecurityConstants.ANTI_INJECTION_INSTRUCTION;

    String raw;
    try {
      raw = resolveClient().prompt()
          .system(systemPrompt)
          .call()
          .chatClientResponse()
          .chatResponse()
          .getResult()
          .getOutput()
          .getText();
    } catch (Exception e) {
      log.error("评委评分失败: case={}, error={}", caseMeta.id(), e.getMessage(), e);
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "评委评分失败: " + e.getMessage());
    }
    if (raw == null || raw.isBlank()) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "评委返回内容为空");
    }
    return JudgeJsonParser.parse(raw);
  }

  private String sanitizeNullable(String value) {
    return value == null ? "" : sanitizer.sanitize(value).trim();
  }

  private ChatClient resolveClient() {
    String providerId = properties.getJudgeProvider();
    return (providerId == null || providerId.isBlank())
        ? registry.getPlainChatClient()
        : registry.getPlainChatClient(providerId);
  }

  public record JudgeResult(double score, boolean passed, List<JudgeIssue> issues) {
  }

  public record JudgeIssue(String dimension, String comment) {
  }
}
