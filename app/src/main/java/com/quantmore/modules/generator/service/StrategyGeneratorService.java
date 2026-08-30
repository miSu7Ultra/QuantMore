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
import com.quantmore.modules.knowledgebase.model.KnowledgeBaseEntity;
import com.quantmore.modules.knowledgebase.repository.KnowledgeBaseRepository;
import com.quantmore.modules.knowledgebase.service.KnowledgeBaseVectorService;
import com.quantmore.modules.user.model.UserPrincipal;
import com.quantmore.modules.user.model.UserRole;
import com.quantmore.modules.user.service.CurrentUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 策略生成器：表单需求 → 知识库检索示例 → LLM 生成完整 PTrade 策略文件
 */
@Slf4j
@Service
public class StrategyGeneratorService {

  static final Pattern PYTHON_FENCE = Pattern.compile("```python\\s*(.*?)```", Pattern.DOTALL);

  private final StrategyGenerationRepository repository;
  private final KnowledgeBaseVectorService vectorService;
  private final KnowledgeBaseRepository knowledgeBaseRepository;
  private final LlmProviderRegistry registry;
  private final CurrentUserService currentUserService;
  private final PromptSanitizer sanitizer;
  private final PromptTemplate systemPromptTemplate;
  private final PromptTemplate userPromptTemplate;
  private final int topK;
  private final double minScore;

  public StrategyGeneratorService(
      StrategyGenerationRepository repository,
      KnowledgeBaseVectorService vectorService,
      KnowledgeBaseRepository knowledgeBaseRepository,
      LlmProviderRegistry registry,
      CurrentUserService currentUserService,
      PromptSanitizer sanitizer,
      GeneratorProperties properties,
      ResourceLoader resourceLoader) throws IOException {
    this.repository = repository;
    this.vectorService = vectorService;
    this.knowledgeBaseRepository = knowledgeBaseRepository;
    this.registry = registry;
    this.currentUserService = currentUserService;
    this.sanitizer = sanitizer;
    this.systemPromptTemplate = new PromptTemplate(
        resourceLoader.getResource(properties.getSystemPromptPath())
            .getContentAsString(StandardCharsets.UTF_8)
    );
    this.userPromptTemplate = new PromptTemplate(
        resourceLoader.getResource(properties.getUserPromptPath())
            .getContentAsString(StandardCharsets.UTF_8)
    );
    this.topK = properties.getTopK();
    this.minScore = properties.getMinScore();
  }

  /**
   * 生成策略
   */
  @Transactional
  public GenerateStrategyResponse generate(GenerateStrategyRequest request) {
    UserPrincipal user = currentUserService.get();

    // 1. 输入清洗（反注入）
    String strategyName = sanitizer.sanitize(request.strategyName()).trim();
    String buyConditions = sanitizer.sanitize(request.buyConditions()).trim();
    String sellConditions = request.sellConditions() == null ? ""
        : sanitizer.sanitize(request.sellConditions()).trim();
    String riskControls = request.riskControls() == null ? ""
        : sanitizer.sanitize(request.riskControls()).trim();

    // 2. 解析知识库范围并校验可见性
    List<Long> kbIds = resolveKnowledgeBaseIds(user, request.knowledgeBaseIds());
    if (user.role() != UserRole.ADMIN) {
      for (Long kbId : kbIds) {
        if (!knowledgeBaseRepository.isVisibleToUser(kbId, user.id())) {
          throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_FORBIDDEN,
              "知识库不可见或不存在: " + kbId);
        }
      }
    }

    // 3. 检索参考示例（检索失败不阻断生成）
    String context = retrieveContext(strategyName, request, buyConditions, sellConditions, kbIds);

    // 4. 渲染提示词并生成
    String systemPrompt = systemPromptTemplate.render(Map.of("context", context));
    String userPrompt = userPromptTemplate.render(Map.of(
        "strategyName", strategyName,
        "market", request.market(),
        "frequency", request.frequency(),
        "buyConditions", buyConditions,
        "sellConditions", sellConditions.isBlank() ? "无（默认长期持有至风控触发）" : sellConditions,
        "riskControls", riskControls.isBlank() ? "无" : riskControls
    ));

    String raw;
    try {
      raw = registry.getChatClientForUser(user.id(), request.providerId())
          .prompt()
          .system(systemPrompt)
          .user(userPrompt)
          .call()
          .content();
    } catch (Exception e) {
      log.error("策略生成失败: strategy={}, provider={}, error={}",
          strategyName, request.providerId(), e.getMessage(), e);
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "策略生成失败: " + e.getMessage());
    }
    if (raw == null || raw.isBlank()) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "模型返回内容为空");
    }

    SplitResult split = splitExplanationAndCode(raw);

    // 5. 持久化
    StrategyGenerationEntity entity = StrategyGenerationEntity.builder()
        .userId(user.id())
        .strategyName(strategyName)
        .market(request.market())
        .frequency(request.frequency())
        .buyConditions(buyConditions)
        .sellConditions(sellConditions)
        .riskControls(riskControls)
        .knowledgeBaseIds(kbIds.stream().map(String::valueOf).collect(Collectors.joining(",")))
        .providerId(request.providerId())
        .generatedCode(split.code())
        .explanation(split.explanation())
        .build();
    entity = repository.save(entity);

    log.info("策略生成完成: id={}, strategy={}, userId={}", entity.getId(), strategyName, user.id());
    return toResponse(entity);
  }

  /**
   * 当前用户的生成历史（倒序）
   */
  @Transactional(readOnly = true)
  public List<GenerateStrategyResponse> history() {
    Long userId = currentUserService.get().id();
    return repository.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .map(this::toResponse)
        .toList();
  }

  /**
   * 生成详情（owner 校验）
   */
  @Transactional(readOnly = true)
  public GenerateStrategyResponse getById(Long id) {
    Long userId = currentUserService.get().id();
    StrategyGenerationEntity entity = repository.findById(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "生成记录不存在"));
    if (!userId.equals(entity.getUserId())) {
      throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该生成记录");
    }
    return toResponse(entity);
  }

  /**
   * 拆分说明与代码：```python 围栏内为代码，其余为说明；无围栏则整体视为代码
   */
  SplitResult splitExplanationAndCode(String raw) {
    Matcher matcher = PYTHON_FENCE.matcher(raw);
    if (matcher.find()) {
      String code = matcher.group(1).trim();
      String explanation = (raw.substring(0, matcher.start()) + "\n" + raw.substring(matcher.end())).trim();
      return new SplitResult(code, explanation);
    }
    return new SplitResult(raw.trim(), "");
  }

  record SplitResult(String code, String explanation) {
  }

  private List<Long> resolveKnowledgeBaseIds(UserPrincipal user, List<Long> requestedIds) {
    if (requestedIds != null && !requestedIds.isEmpty()) {
      return requestedIds;
    }
    // 空 = 全部可见知识库
    List<KnowledgeBaseEntity> visible = user.role() == UserRole.ADMIN
        ? knowledgeBaseRepository.findAllByOrderByUploadedAtDesc()
        : knowledgeBaseRepository.findVisibleOrderByUploadedAtDesc(user.id());
    return visible.stream().map(KnowledgeBaseEntity::getId).toList();
  }

  private String retrieveContext(
      String strategyName,
      GenerateStrategyRequest request,
      String buyConditions,
      String sellConditions,
      List<Long> kbIds) {
    if (kbIds.isEmpty()) {
      return "（无可用知识库，请严格按 PTrade 官方规范编写，不确定的 API 用注释标注）";
    }
    String query = String.join(" ",
        strategyName, request.market(), request.frequency(), buyConditions, sellConditions);
    try {
      List<Document> docs = vectorService.similaritySearch(query, kbIds, topK, minScore);
      if (docs.isEmpty()) {
        return "（知识库中未检索到高度相关的示例，请严格按 PTrade 官方规范编写，不确定的 API 用注释标注）";
      }
      return docs.stream()
          .map(Document::getText)
          .collect(Collectors.joining("\n\n---\n\n"));
    } catch (Exception e) {
      log.warn("策略生成检索失败，继续生成: error={}", e.getMessage(), e);
      return "（检索暂不可用，请严格按 PTrade 官方规范编写，不确定的 API 用注释标注）";
    }
  }

  private GenerateStrategyResponse toResponse(StrategyGenerationEntity entity) {
    return new GenerateStrategyResponse(
        entity.getId(),
        entity.getStrategyName(),
        sanitizeFileName(entity.getStrategyName()),
        entity.getMarket(),
        entity.getFrequency(),
        entity.getGeneratedCode(),
        entity.getExplanation(),
        entity.getProviderId(),
        entity.getCreatedAt()
    );
  }

  private String sanitizeFileName(String strategyName) {
    return strategyName.replaceAll("[\\\\/:*?\"<>|\\s]+", "_") + ".py";
  }
}
