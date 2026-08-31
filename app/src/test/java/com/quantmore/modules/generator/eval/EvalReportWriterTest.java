package com.quantmore.modules.generator.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EvalReportWriter 测试")
class EvalReportWriterTest {

  @TempDir Path tempDir;

  private Path outputDir;
  private EvalReportWriter writer;

  @BeforeEach
  void setUp() {
    outputDir = tempDir.resolve("out");
    EvalProperties properties = new EvalProperties();
    properties.setOutputDir(outputDir.toString());
    writer = new EvalReportWriter(properties, new ObjectMapper());
  }

  private EvalReport.BranchResult branch(
      boolean ragEnabled, String syntaxStatus, boolean judgeOk, double score) {
    PythonSyntaxCheckService.SyntaxCheckResult syntax =
        new PythonSyntaxCheckService.SyntaxCheckResult(syntaxStatus, "", "3.9", List.of());
    EvalJudgeService.JudgeResult judge = new EvalJudgeService.JudgeResult(score, score >= 70, List.of());
    return new EvalReport.BranchResult(
        ragEnabled, true, null, 1L, 100L,
        syntax, judgeOk, judgeOk ? judge : null, judgeOk ? null : "评委失败");
  }

  private EvalReport.FullReport sampleReport() {
    List<EvalReport.CaseResult> results = List.of(
        new EvalReport.CaseResult(
            new EvalCase("s01", "双均线", "STOCK", "DAILY", "买入", "", "", "SIMPLE"),
            branch(true, "PASS", true, 85),
            branch(false, "PASS", true, 60)),
        new EvalReport.CaseResult(
            new EvalCase("s02", "轮动", "STOCK", "DAILY", "买入", "", "", "COMPLEX"),
            branch(true, "FAIL", false, 0),
            branch(false, "SKIPPED", true, 55)));
    return new EvalReport.FullReport(
        "2026-08-31T10:00:00", "admin", "qwen", "qwen", "Python 3.9.6",
        true, 1, 0, results, EvalReport.EvalSummary.of(results, 70.0));
  }

  @Test
  @DisplayName("写出 eval-report.json 与 eval-report.md,JSON 可反序列化回完整报告")
  void writesJsonAndMarkdown() throws Exception {
    writer.write(sampleReport());

    Path json = outputDir.resolve("eval-report.json");
    Path md = outputDir.resolve("eval-report.md");
    assertThat(json).exists();
    assertThat(md).exists();

    EvalReport.FullReport parsed = new ObjectMapper()
        .readValue(Files.readString(json), EvalReport.FullReport.class);
    assertThat(parsed.summary().totalCases()).isEqualTo(2);
    assertThat(parsed.results()).hasSize(2);
    assertThat(Files.readString(md)).contains("RAG", "no-RAG", "s01", "s02");
  }
}
