package com.quantmore.modules.generator.eval;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Python 语法检查：通过本机 python 子进程执行 ast.parse（代码经 stdin 传入）。
 * python 不可用时整体降级为 SKIPPED；另提供 Python 3.5 不兼容语法的启发式静态检查（仅警示）。
 */
@Slf4j
@Component
public class PythonSyntaxCheckService {

  private static final Pattern PY_FSTRING =
      Pattern.compile("(?<![A-Za-z0-9_])f(['\"]).*?\\{(?:[^{}]*)}.*?\\1", Pattern.DOTALL);
  private static final Pattern PY_WALRUS = Pattern.compile("[A-Za-z_]\\w*\\s*:=");
  private static final Pattern PY_VAR_ANNOTATION = Pattern.compile(
      "^\\s*[A-Za-z_][A-Za-z0-9_]*\\s*:\\s*[A-Za-z_][\\w\\[\\],\\s.]*\\s*=\\s*.+$",
      Pattern.MULTILINE);
  private static final Pattern PY_POSITIONAL_ONLY = Pattern.compile("def\\s+\\w+\\([^)\\n]*?/\\s*[,)]");

  private static final String PARSE_SCRIPT = "import sys,ast\nast.parse(sys.stdin.read())";
  private static final int STDERR_LIMIT = 500;
  private static final long VERSION_TIMEOUT_SECONDS = 3;

  private final EvalProperties properties;
  private volatile PythonInfo pythonInfo;

  public PythonSyntaxCheckService(EvalProperties properties) {
    this.properties = properties;
  }

  /** python 解释器探测（懒加载，仅探测一次） */
  public PythonInfo pythonInfo() {
    if (pythonInfo == null) {
      synchronized (this) {
        if (pythonInfo == null) {
          pythonInfo = detectPython();
        }
      }
    }
    return pythonInfo;
  }

  /**
   * 语法检查，status: PASS / FAIL / SKIPPED
   */
  public SyntaxCheckResult check(String code) {
    PythonInfo info = pythonInfo();
    if (!info.available()) {
      return new SyntaxCheckResult("SKIPPED", "python 不可用，跳过语法检查", "", List.of());
    }
    List<String> warnings = detectPy35Incompatibilities(code);
    try {
      Process process = new ProcessBuilder(properties.getPythonBin(), "-c", PARSE_SCRIPT)
          .start();
      try (OutputStream stdin = process.getOutputStream()) {
        stdin.write(code.getBytes(StandardCharsets.UTF_8));
      }
      if (!process.waitFor(properties.getPythonTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
        process.destroyForcibly();
        return new SyntaxCheckResult("FAIL", "语法检查超时", info.version(), warnings);
      }
      if (process.exitValue() == 0) {
        return new SyntaxCheckResult("PASS", "", info.version(), warnings);
      }
      return new SyntaxCheckResult("FAIL", readStderr(process), info.version(), warnings);
    } catch (IOException e) {
      log.warn("语法检查进程异常: error={}", e.getMessage(), e);
      return new SyntaxCheckResult("SKIPPED", "语法检查进程异常", info.version(), warnings);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new SyntaxCheckResult("SKIPPED", "语法检查被中断", info.version(), warnings);
    }
  }

  /**
   * Python 3.5 不兼容语法启发式检查（正则误报率高，仅作警示项，不计入语法成败）
   */
  public List<String> detectPy35Incompatibilities(String code) {
    List<String> warnings = new ArrayList<>();
    if (PY_FSTRING.matcher(code).find()) {
      warnings.add("f-string(Python 3.5 不支持)");
    }
    if (PY_WALRUS.matcher(code).find()) {
      warnings.add("海象运算符 :=(Python 3.8+)");
    }
    if (PY_VAR_ANNOTATION.matcher(code).find()) {
      warnings.add("变量注解(Python 3.6+)");
    }
    if (PY_POSITIONAL_ONLY.matcher(code).find()) {
      warnings.add("仅位置参数 /(Python 3.8+)");
    }
    return warnings;
  }

  private PythonInfo detectPython() {
    try {
      Process process = new ProcessBuilder(properties.getPythonBin(), "--version")
          .redirectErrorStream(true)
          .start();
      if (!process.waitFor(VERSION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        return new PythonInfo(false, "");
      }
      String version = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
          .trim();
      return new PythonInfo(process.exitValue() == 0, version);
    } catch (IOException e) {
      return new PythonInfo(false, "");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new PythonInfo(false, "");
    }
  }

  private String readStderr(Process process) throws IOException {
    String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8)
        .trim();
    return stderr.length() > STDERR_LIMIT ? stderr.substring(0, STDERR_LIMIT) : stderr;
  }

  public record PythonInfo(boolean available, String version) {
  }

  public record SyntaxCheckResult(
      String status,
      String message,
      String pythonVersion,
      List<String> py35Warnings
  ) {
  }
}
