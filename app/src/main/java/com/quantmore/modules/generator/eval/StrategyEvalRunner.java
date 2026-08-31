package com.quantmore.modules.generator.eval;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 评测入口：APP_EVAL_ENABLED=true 时，启动后执行评测、写报告并退出进程。
 * 退出码：0=全部通过且知识库就绪；1=存在失败分支或知识库未就绪；2=致命错误（无 ADMIN/无 COMPLETED KB/用例加载失败）。
 * 顺序在 LocalKbSeedRunner(@Order(10)) 之后，且评测服务会等待向量化完成，双保险。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(20)
public class StrategyEvalRunner implements CommandLineRunner {

  private final EvalProperties properties;
  private final EvalCaseLoader caseLoader;
  private final StrategyEvalService evalService;
  private final EvalReportWriter reportWriter;
  private final ConfigurableApplicationContext context;

  @Override
  public void run(String... args) {
    if (!properties.isEnabled()) {
      return;
    }
    int result;
    try {
      log.info("策略生成评测开始: casesPath={}", properties.getCasesPath());
      List<EvalCase> cases = caseLoader.load();
      EvalReport.FullReport report = evalService.run(cases);
      reportWriter.write(report);
      result = exitCodeOf(report);
      log.info("策略生成评测结束: exitCode={}", result);
    } catch (Exception e) {
      log.error("策略生成评测致命失败", e);
      result = 2;
    }
    final int exitCode = result;
    int code = SpringApplication.exit(context, () -> exitCode);
    System.exit(code);
  }

  static int exitCodeOf(EvalReport.FullReport report) {
    EvalReport.EvalSummary summary = report.summary();
    if (!report.kbReady() || summary.generationFailures() > 0 || summary.judgeFailures() > 0) {
      return 1;
    }
    return 0;
  }
}
