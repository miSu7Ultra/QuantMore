package com.quantmore.modules.generator.eval;

import java.util.List;

/**
 * 评测报告数据结构（嵌套 record，聚合逻辑见 EvalSummary.of）
 */
public final class EvalReport {

  private EvalReport() {
  }

  /**
   * 单个分支（RAG 或无 RAG）的执行结果
   */
  public record BranchResult(
      boolean ragEnabled,
      boolean generationOk,
      String generationError,
      Long generationId,
      long generationMs,
      PythonSyntaxCheckService.SyntaxCheckResult syntax,
      boolean judgeOk,
      EvalJudgeService.JudgeResult judge,
      String judgeRaw
  ) {
  }

  /**
   * 单个用例的 RAG / no-RAG 对照结果
   */
  public record CaseResult(EvalCase caseMeta, BranchResult rag, BranchResult noRag) {
  }

  /**
   * 汇总统计（纯函数聚合，便于单测）
   */
  public record EvalSummary(
      int totalCases,
      int ragPassed,
      int noRagPassed,
      double ragAvgScore,
      double noRagAvgScore,
      int ragSyntaxPassed,
      int noRagSyntaxPassed,
      int generationFailures,
      int judgeFailures,
      int py35WarningCount,
      double scoreDelta
  ) {

    public static EvalSummary of(List<CaseResult> results, double passScore) {
      int ragPassed = 0;
      int noRagPassed = 0;
      int ragSyntaxPassed = 0;
      int noRagSyntaxPassed = 0;
      int generationFailures = 0;
      int judgeFailures = 0;
      int py35WarningCount = 0;
      double ragScoreSum = 0;
      int ragScoreCount = 0;
      double noRagScoreSum = 0;
      int noRagScoreCount = 0;

      for (CaseResult result : results) {
        ragPassed += passed(result.rag(), passScore) ? 1 : 0;
        noRagPassed += passed(result.noRag(), passScore) ? 1 : 0;
        ragSyntaxPassed += syntaxPassed(result.rag()) ? 1 : 0;
        noRagSyntaxPassed += syntaxPassed(result.noRag()) ? 1 : 0;
        generationFailures += result.rag().generationOk() ? 0 : 1;
        generationFailures += result.noRag().generationOk() ? 0 : 1;
        judgeFailures += judgeFailed(result.rag()) ? 1 : 0;
        judgeFailures += judgeFailed(result.noRag()) ? 1 : 0;
        py35WarningCount += warnings(result.rag()).size();
        py35WarningCount += warnings(result.noRag()).size();
        if (result.rag().judgeOk()) {
          ragScoreSum += result.rag().judge().score();
          ragScoreCount++;
        }
        if (result.noRag().judgeOk()) {
          noRagScoreSum += result.noRag().judge().score();
          noRagScoreCount++;
        }
      }

      double ragAvgScore = ragScoreCount == 0 ? 0 : ragScoreSum / ragScoreCount;
      double noRagAvgScore = noRagScoreCount == 0 ? 0 : noRagScoreSum / noRagScoreCount;
      return new EvalSummary(
          results.size(),
          ragPassed,
          noRagPassed,
          ragAvgScore,
          noRagAvgScore,
          ragSyntaxPassed,
          noRagSyntaxPassed,
          generationFailures,
          judgeFailures,
          py35WarningCount,
          ragAvgScore - noRagAvgScore
      );
    }

    private static boolean passed(BranchResult branch, double passScore) {
      return branch.generationOk()
          && syntaxPassed(branch)
          && branch.judgeOk()
          && branch.judge().score() >= passScore;
    }

    private static boolean syntaxPassed(BranchResult branch) {
      return branch.generationOk() && "PASS".equals(branch.syntax().status());
    }

    private static boolean judgeFailed(BranchResult branch) {
      return branch.generationOk() && !branch.judgeOk();
    }

    private static List<String> warnings(BranchResult branch) {
      return branch.syntax() == null ? List.of() : branch.syntax().py35Warnings();
    }
  }

  /**
   * 完整评测报告
   */
  public record FullReport(
      String runAt,
      String evalUser,
      String generateProvider,
      String judgeProvider,
      String pythonVersion,
      boolean kbReady,
      int kbCompleted,
      int kbFailed,
      List<CaseResult> results,
      EvalSummary summary
  ) {
  }
}
