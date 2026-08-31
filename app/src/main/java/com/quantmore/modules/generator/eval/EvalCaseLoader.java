package com.quantmore.modules.generator.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * 评测用例加载与校验：从 casesPath 读取 JSON 数组并校验字段合法性
 */
@Component
@RequiredArgsConstructor
public class EvalCaseLoader {

  private static final Set<String> MARKETS = Set.of("STOCK", "ETF", "CONVERTIBLE_BOND", "FUTURES");
  private static final Set<String> FREQUENCIES = Set.of("DAILY", "MINUTE");
  private static final Set<String> DIFFICULTIES = Set.of("SIMPLE", "MEDIUM", "COMPLEX");

  private final ResourceLoader resourceLoader;
  private final ObjectMapper objectMapper;
  private final EvalProperties properties;

  public List<EvalCase> load() {
    Resource resource = resourceLoader.getResource(properties.getCasesPath());
    if (!resource.exists()) {
      throw new IllegalStateException("评测用例文件不存在: " + properties.getCasesPath());
    }
    List<EvalCase> cases;
    try (InputStream in = resource.getInputStream()) {
      cases = objectMapper.readValue(in, new TypeReference<List<EvalCase>>() {
      });
    } catch (IOException e) {
      throw new IllegalStateException("评测用例文件解析失败: " + properties.getCasesPath(), e);
    }
    validate(cases);
    return cases;
  }

  private void validate(List<EvalCase> cases) {
    if (cases == null || cases.isEmpty()) {
      throw new IllegalStateException("评测用例为空");
    }
    for (EvalCase c : cases) {
      requireNotBlank(c.id(), "id");
      requireNotBlank(c.name(), "name");
      requireNotBlank(c.buyConditions(), "buyConditions");
      if (!MARKETS.contains(c.market())) {
        throw new IllegalStateException("用例 " + c.id() + " market 非法: " + c.market());
      }
      if (!FREQUENCIES.contains(c.frequency())) {
        throw new IllegalStateException("用例 " + c.id() + " frequency 非法: " + c.frequency());
      }
      if (!DIFFICULTIES.contains(c.difficulty())) {
        throw new IllegalStateException("用例 " + c.id() + " difficulty 非法: " + c.difficulty());
      }
    }
  }

  private void requireNotBlank(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("用例字段为空: " + field);
    }
  }
}
