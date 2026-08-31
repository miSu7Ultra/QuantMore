package com.quantmore.modules.generator.eval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StrategyEvalRunner 退出码测试")
class StrategyEvalRunnerTest {

  private EvalReport.FullReport report(boolean kbReady, int generationFailures, int judgeFailures) {
    EvalReport.EvalSummary summary = new EvalReport.EvalSummary(
        1, 1, 1, 80, 60, 1, 1, generationFailures, judgeFailures, 0, 20);
    return new EvalReport.FullReport(
        "now", "admin", null, null, "3.9", kbReady, 1, 0, List.of(), summary);
  }

  @Test
  @DisplayName("知识库就绪且无失败时退出码 0")
  void allPassedExitsZero() {
    assertThat(StrategyEvalRunner.exitCodeOf(report(true, 0, 0))).isEqualTo(0);
  }

  @Test
  @DisplayName("存在生成失败时退出码 1")
  void generationFailureExitsOne() {
    assertThat(StrategyEvalRunner.exitCodeOf(report(true, 1, 0))).isEqualTo(1);
  }

  @Test
  @DisplayName("存在评委失败时退出码 1")
  void judgeFailureExitsOne() {
    assertThat(StrategyEvalRunner.exitCodeOf(report(true, 0, 1))).isEqualTo(1);
  }

  @Test
  @DisplayName("知识库未就绪时退出码 1")
  void kbNotReadyExitsOne() {
    assertThat(StrategyEvalRunner.exitCodeOf(report(false, 0, 0))).isEqualTo(1);
  }
}
