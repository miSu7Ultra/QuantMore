package com.quantmore.modules.generator.eval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JudgeJsonParser 测试")
class JudgeJsonParserTest {

  @Test
  @DisplayName("解析 ```json 围栏内容")
  void parsesFencedJson() {
    EvalJudgeService.JudgeResult result = JudgeJsonParser.parse(
        "以下是评分\n```json\n{\"score\": 85, \"passed\": true, "
            + "\"issues\": [{\"dimension\": \"结构\", \"comment\": \"ok\"}]}\n```\n完毕");

    assertThat(result.score()).isEqualTo(85.0);
    assertThat(result.passed()).isTrue();
    assertThat(result.issues()).hasSize(1);
    assertThat(result.issues().get(0).dimension()).isEqualTo("结构");
  }

  @Test
  @DisplayName("解析裸 JSON(前后有杂文)")
  void parsesBareJson() {
    EvalJudgeService.JudgeResult result = JudgeJsonParser.parse(
        "评分如下 {\"score\": 60, \"passed\": false, \"issues\": []} 谢谢");

    assertThat(result.score()).isEqualTo(60.0);
    assertThat(result.passed()).isFalse();
  }

  @Test
  @DisplayName("issues 缺失时为空列表")
  void missingIssuesDefaultsToEmpty() {
    EvalJudgeService.JudgeResult result = JudgeJsonParser.parse("{\"score\": 70, \"passed\": true}");

    assertThat(result.issues()).isEmpty();
  }

  @Test
  @DisplayName("score 越界夹取到 [0,100]")
  void clampsScore() {
    assertThat(JudgeJsonParser.parse("{\"score\": 150, \"passed\": true}").score())
        .isEqualTo(100.0);
    assertThat(JudgeJsonParser.parse("{\"score\": -5, \"passed\": false}").score())
        .isEqualTo(0.0);
  }

  @Test
  @DisplayName("非法 JSON 抛 IllegalStateException")
  void invalidJsonThrows() {
    assertThatThrownBy(() -> JudgeJsonParser.parse("不是 JSON"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("空输出抛 IllegalStateException")
  void blankTextThrows() {
    assertThatThrownBy(() -> JudgeJsonParser.parse("  "))
        .isInstanceOf(IllegalStateException.class);
  }
}
