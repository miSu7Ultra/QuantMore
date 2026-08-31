package com.quantmore.modules.generator.eval;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 策略生成评测配置（均可用 APP_EVAL_* 环境变量覆盖）
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.eval")
public class EvalProperties {

  /** 是否启用评测：APP_EVAL_ENABLED=true 时 bootRun 跑完评测写报告后自动退出 */
  private boolean enabled = false;

  /** 用例文件路径，支持 classpath: / file: 前缀 */
  private String casesPath = "classpath:eval/strategy-eval-cases.json";

  /** 生成用 provider id（空 = 用户默认/全局默认） */
  private String generateProvider;

  /** 评委用 provider id（空 = 全局默认） */
  private String judgeProvider;

  /** 报告输出目录（相对 bootRun 工作目录） */
  private String outputDir = "eval-output";

  /** 评委通过分数线 */
  private double judgePassScore = 70.0;

  /** python 解释器路径 */
  private String pythonBin = "python3";

  /** 单次 python 语法检查超时 */
  private Duration pythonTimeout = Duration.ofSeconds(10);

  /** 等待知识库向量化就绪的超时 */
  private Duration vectorWaitTimeout = Duration.ofSeconds(120);

  /** 评测结束后是否清理本次评测写入的生成记录 */
  private boolean cleanupRecords = true;
}
