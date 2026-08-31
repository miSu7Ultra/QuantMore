package com.quantmore.modules.generator.eval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EvalSummary 聚合测试")
class EvalSummaryTest {

  private EvalCase caseMeta(String id) {
    return new EvalCase(id, id, "STOCK", "DAILY", "买入", "", "", "SIMPLE");
  }

  private EvalReport.BranchResult branch(
      boolean ragEnabled, boolean generationOk, String syntaxStatus,
      boolean judgeOk, double score, List<String> warnings) {
    PythonSyntaxCheckService.SyntaxCheckResult syntax =
        new PythonSyntaxCheckService.SyntaxCheckResult(syntaxStatus, "", "3.9", warnings);
    EvalJudgeService.JudgeResult judge = new EvalJudgeService.JudgeResult(score, score >= 70, List.of());
    return new EvalReport.BranchResult(
        ragEnabled, generationOk, generationOk ? null : "生成失败", 1L, 100L,
        syntax, judgeOk, judgeOk ? judge : null, judgeOk ? "{}" : null);
  }

  @Test
  @DisplayName("聚合通过数/语法通过数/平均分/差值/失败计数")
  void aggregatesPassedAndScores() {
    EvalReport.CaseResult case1 = new EvalReport.CaseResult(caseMeta("s01"),
        branch(true, true, "PASS", true, 85, List.of()),
        branch(false, true, "PASS", true, 60, List.of()));
    EvalReport.CaseResult case2 = new EvalReport.CaseResult(caseMeta("s02"),
        branch(true, true, "FAIL", false, 0, List.of("f-string")),
        branch(false, false, "SKIPPED", false, 0, List.of()));

    EvalReport.EvalSummary summary = EvalReport.EvalSummary.of(List.of(case1, case2), 70.0);

    assertThat(summary.totalCases()).isEqualTo(2);
    assertThat(summary.ragPassed()).isEqualTo(1);
    assertThat(summary.noRagPassed()).isEqualTo(0);
    assertThat(summary.ragSyntaxPassed()).isEqualTo(1);
    assertThat(summary.noRagSyntaxPassed()).isEqualTo(1);
    assertThat(summary.generationFailures()).isEqualTo(1);
    assertThat(summary.judgeFailures()).isEqualTo(1);
    assertThat(summary.py35WarningCount()).isEqualTo(1);
    assertThat(summary.ragAvgScore()).isEqualTo(85.0);
    assertThat(summary.noRagAvgScore()).isEqualTo(60.0);
    assertThat(summary.scoreDelta()).isEqualTo(25.0);
  }

  @Test
  @DisplayName("无评委结果时平均分为 0")
  void noJudgeResultsMeansZeroAvg() {
    EvalReport.CaseResult onlyFailure = new EvalReport.CaseResult(caseMeta("s03"),
        branch(true, false, "SKIPPED", false, 0, List.of()),
        branch(false, true, "FAIL", false, 0, List.of()));

    EvalReport.EvalSummary summary = EvalReport.EvalSummary.of(List.of(onlyFailure), 70.0);

    assertThat(summary.ragAvgScore()).isEqualTo(0.0);
    assertThat(summary.noRagAvgScore()).isEqualTo(0.0);
  }
}
