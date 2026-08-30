package com.quantmore.modules.generator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 策略生成器配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.generator")
public class GeneratorProperties {

  private String systemPromptPath = "classpath:prompts/strategy-generator-system.st";

  private String userPromptPath = "classpath:prompts/strategy-generator-user.st";

  /** 检索示例片段数 */
  private int topK = 8;

  /** 检索最低相似度 */
  private double minScore = 0.18;
}
