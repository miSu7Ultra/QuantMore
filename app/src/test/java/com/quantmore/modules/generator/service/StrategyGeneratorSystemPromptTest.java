package com.quantmore.modules.generator.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 系统提示词必须长期保留 PTrade 编写规范中的硬性规则（防止后续改动误删）。
 */
@DisplayName("策略生成器系统提示词规范硬规则")
class StrategyGeneratorSystemPromptTest {

  private static String prompt;

  @BeforeAll
  static void renderPrompt() throws IOException {
    String template = new ClassPathResource("prompts/strategy-generator-system.st")
        .getContentAsString(StandardCharsets.UTF_8);
    prompt = new PromptTemplate(template).render(Map.of("context", "（测试上下文）"));
  }

  @Test
  @DisplayName("包含 Python 3.5 兼容约束（禁 f-string、import os 等）")
  void containsPython35Constraints() {
    assertThat(prompt)
        .contains("Python 3.5", "f-string", "import os");
  }

  @Test
  @DisplayName("包含 API 硬约束：禁 import ptrade、get_index_stocks 事件位置、get_snapshot 回测禁用、参数互斥")
  void containsApiHardConstraints() {
    assertThat(prompt)
        .contains("import ptrade", "get_index_stocks", "before_trading_start",
            "get_snapshot", "start_date", "count");
  }

  @Test
  @DisplayName("要求处理停牌/涨跌停/行情为空/资金不足等边界情况")
  void requiresEdgeCaseHandling() {
    assertThat(prompt)
        .contains("停牌", "涨跌停", "行情为空", "资金不足");
  }

  @Test
  @DisplayName("包含实盘防护与免责声明：get_open_orders、order_target、不承诺收益")
  void containsLiveTradingSafetyAndDisclaimer() {
    assertThat(prompt)
        .contains("get_open_orders", "order_target", "不承诺收益");
  }
}
