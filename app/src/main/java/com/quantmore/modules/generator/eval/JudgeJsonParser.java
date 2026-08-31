package com.quantmore.modules.generator.eval;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 评委输出解析：优先提取 ```json 围栏内容，否则取首尾大括号子串，Jackson 反序列化。
 * 解析失败抛 IllegalStateException（原文由调用方留档）。
 */
public final class JudgeJsonParser {

  private static final Pattern JSON_FENCE = Pattern.compile("(?s)```(?:json)?\\s*(.*?)```");

  private static final ObjectMapper MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private JudgeJsonParser() {
  }

  public static EvalJudgeService.JudgeResult parse(String text) {
    if (text == null || text.isBlank()) {
      throw new IllegalStateException("评委输出为空");
    }
    String json = extractJson(text);
    JudgeDto dto;
    try {
      dto = MAPPER.readValue(json, JudgeDto.class);
    } catch (IOException e) {
      throw new IllegalStateException("评委输出 JSON 解析失败: " + text, e);
    }
    if (dto.score == null) {
      throw new IllegalStateException("评委输出缺少 score 字段: " + text);
    }
    double score = Math.max(0, Math.min(100, dto.score));
    boolean passed = Boolean.TRUE.equals(dto.passed);
    List<EvalJudgeService.JudgeIssue> issues = dto.issues == null ? List.of()
        : dto.issues.stream()
            .map(i -> new EvalJudgeService.JudgeIssue(i.dimension, i.comment))
            .toList();
    return new EvalJudgeService.JudgeResult(score, passed, issues);
  }

  private static String extractJson(String text) {
    Matcher matcher = JSON_FENCE.matcher(text);
    if (matcher.find()) {
      return matcher.group(1).trim();
    }
    int start = text.indexOf('{');
    int end = text.lastIndexOf('}');
    if (start < 0 || end < start) {
      return text;
    }
    return text.substring(start, end + 1);
  }

  private record JudgeDto(Double score, Boolean passed, List<IssueDto> issues) {
  }

  private record IssueDto(String dimension, String comment) {
  }
}
