package com.quantmore.modules.generator.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 评测报告输出：eval-output/ 下写 eval-report.json 与 eval-report.md，并打控制台汇总。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvalReportWriter {

  private final EvalProperties properties;
  private final ObjectMapper objectMapper;

  public void write(EvalReport.FullReport report) throws IOException {
    Path dir = Path.of(properties.getOutputDir());
    Files.createDirectories(dir);
    objectMapper.writerWithDefaultPrettyPrinter()
        .writeValue(dir.resolve("eval-report.json").toFile(), report);
    Files.writeString(dir.resolve("eval-report.md"), toMarkdown(report), StandardCharsets.UTF_8);
    log.info("评测报告已写出: dir={}", dir.toAbsolutePath());
    printConsole(report);
  }

  private String toMarkdown(EvalReport.FullReport report) {
    EvalReport.EvalSummary s = report.summary();
    StringBuilder sb = new StringBuilder();
    sb.append("# 策略生成评测报告\n\n");
    sb.append("- 运行时间: ").append(report.runAt()).append('\n');
    sb.append("- 评测用户: ").append(report.evalUser()).append('\n');
    sb.append("- 生成模型: ").append(display(report.generateProvider())).append('\n');
    sb.append("- 评委模型: ").append(display(report.judgeProvider())).append('\n');
    sb.append("- Python: ").append(display(report.pythonVersion())).append('\n');
    sb.append("- 知识库就绪: ").append(report.kbReady() ? "是" : "否")
        .append("(COMPLETED ").append(report.kbCompleted())
        .append(" / FAILED ").append(report.kbFailed()).append(")\n\n");

    sb.append("## 汇总(通过 = 语法 PASS 且评委分 >= ")
        .append(properties.getJudgePassScore()).append(")\n\n");
    sb.append("| 指标 | RAG | no-RAG |\n|---|---|---|\n");
    sb.append("| 用例通过数 | ").append(s.ragPassed()).append(" | ").append(s.noRagPassed())
        .append(" |\n");
    sb.append("| 语法通过数 | ").append(s.ragSyntaxPassed()).append(" | ")
        .append(s.noRagSyntaxPassed()).append(" |\n");
    sb.append("| 评委平均分 | ").append(String.format("%.1f", s.ragAvgScore())).append(" | ")
        .append(String.format("%.1f", s.noRagAvgScore())).append(" |\n");
    sb.append("\n- 总用例数: ").append(s.totalCases()).append('\n');
    sb.append("- 生成失败分支: ").append(s.generationFailures()).append('\n');
    sb.append("- 评委失败分支: ").append(s.judgeFailures()).append('\n');
    sb.append("- Python 3.5 兼容警示数: ").append(s.py35WarningCount()).append('\n');
    sb.append("- 平均分差(RAG - no-RAG): ").append(String.format("%.1f", s.scoreDelta()))
        .append("\n\n");

    sb.append("## 逐用例\n\n");
    sb.append("| 用例 | 难度 | RAG 语法 | RAG 评分 | RAG 通过 | no-RAG 语法 | no-RAG 评分 | no-RAG 通过 |\n");
    sb.append("|---|---|---|---|---|---|---|---|\n");
    for (EvalReport.CaseResult r : report.results()) {
      sb.append("| ").append(r.caseMeta().id()).append(" ").append(r.caseMeta().name())
          .append(" | ").append(r.caseMeta().difficulty())
          .append(" | ").append(branchCell(r.rag()))
          .append(" |\n");
    }

    sb.append("\n## 问题明细\n\n");
    boolean hasIssues = false;
    for (EvalReport.CaseResult r : report.results()) {
      appendBranchIssues(sb, r.caseMeta(), r.rag());
      appendBranchIssues(sb, r.caseMeta(), r.noRag());
      hasIssues = hasIssues || !r.rag().judgeOk() || !r.noRag().judgeOk()
          || !"PASS".equals(syntax(r.rag())) || !"PASS".equals(syntax(r.noRag()));
    }
    if (!hasIssues) {
      sb.append("无\n");
    }
    return sb.toString();
  }

  private void appendBranchIssues(StringBuilder sb, EvalCase caseMeta,
                                  EvalReport.BranchResult branch) {
    String label = branch.ragEnabled() ? "RAG" : "no-RAG";
    if (!branch.generationOk()) {
      sb.append("- ").append(caseMeta.id()).append(" ").append(label)
          .append(" 生成失败: ").append(branch.generationError()).append('\n');
      return;
    }
    if (!"PASS".equals(syntax(branch))) {
      sb.append("- ").append(caseMeta.id()).append(" ").append(label)
          .append(" 语法 ").append(syntax(branch)).append(": ")
          .append(branch.syntax() == null ? "" : branch.syntax().message()).append('\n');
    }
    if (!branch.judgeOk()) {
      sb.append("- ").append(caseMeta.id()).append(" ").append(label)
          .append(" 评委失败: ").append(branch.judgeRaw()).append('\n');
    } else if (branch.judge().issues() != null && !branch.judge().issues().isEmpty()) {
      for (EvalJudgeService.JudgeIssue issue : branch.judge().issues()) {
        sb.append("- ").append(caseMeta.id()).append(" ").append(label)
            .append(" [").append(issue.dimension()).append("] ")
            .append(issue.comment()).append('\n');
      }
    }
    List<String> warnings = branch.syntax() == null ? List.of() : branch.syntax().py35Warnings();
    for (String warning : warnings) {
      sb.append("- ").append(caseMeta.id()).append(" ").append(label)
          .append(" 3.5 兼容警示: ").append(warning).append('\n');
    }
  }

  private String branchCell(EvalReport.BranchResult branch) {
    String syntax = branch.generationOk() ? syntax(branch) : "生成失败";
    String score = !branch.generationOk() ? "-"
        : branch.judgeOk() ? String.format("%.0f", branch.judge().score()) : "评委失败";
    boolean passed = branch.generationOk() && "PASS".equals(syntax(branch))
        && branch.judgeOk() && branch.judge().score() >= properties.getJudgePassScore();
    return syntax + " | " + score + " | " + (passed ? "是" : "否");
  }

  private String syntax(EvalReport.BranchResult branch) {
    return branch.syntax() == null ? "-" : branch.syntax().status();
  }

  private String display(String value) {
    return (value == null || value.isBlank()) ? "(默认)" : value;
  }

  private void printConsole(EvalReport.FullReport report) {
    EvalReport.EvalSummary s = report.summary();
    log.info("================ 评测汇总 ================");
    log.info("用例数={} 通过: RAG={}/{} no-RAG={}/{}", s.totalCases(), s.ragPassed(),
        s.totalCases(), s.noRagPassed(), s.totalCases());
    log.info("语法通过: RAG={}/{} no-RAG={}/{}", s.ragSyntaxPassed(), s.totalCases(),
        s.noRagSyntaxPassed(), s.totalCases());
    log.info("平均分: RAG={} no-RAG={} 差值={}", String.format("%.1f", s.ragAvgScore()),
        String.format("%.1f", s.noRagAvgScore()), String.format("%+.1f", s.scoreDelta()));
    log.info("生成失败={} 评委失败={} 3.5警示={}", s.generationFailures(), s.judgeFailures(),
        s.py35WarningCount());
    log.info("=========================================");
  }
}
