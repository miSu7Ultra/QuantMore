package com.quantmore.modules.generator.eval;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PythonSyntaxCheckService 测试")
class PythonSyntaxCheckServiceTest {

  @TempDir Path tempDir;

  private EvalProperties properties;

  @BeforeEach
  void setUp() {
    properties = new EvalProperties();
    properties.setPythonTimeout(Duration.ofSeconds(5));
  }

  private PythonSyntaxCheckService service() {
    return new PythonSyntaxCheckService(properties);
  }

  @Test
  @DisplayName("合法 Python 代码语法检查 PASS 并携带 python 版本")
  void validCodePasses() {
    PythonSyntaxCheckService service = service();
    Assumptions.assumeTrue(service.pythonInfo().available(), "本机无 python3,跳过");

    PythonSyntaxCheckService.SyntaxCheckResult result =
        service.check("def initialize(context):\n    pass\n");

    assertThat(result.status()).isEqualTo("PASS");
    assertThat(result.pythonVersion()).isNotBlank();
  }

  @Test
  @DisplayName("非法 Python 代码语法检查 FAIL 并携带错误信息")
  void invalidCodeFails() {
    PythonSyntaxCheckService service = service();
    Assumptions.assumeTrue(service.pythonInfo().available(), "本机无 python3,跳过");

    PythonSyntaxCheckService.SyntaxCheckResult result = service.check("def initialize(:\n");

    assertThat(result.status()).isEqualTo("FAIL");
    assertThat(result.message()).isNotBlank();
  }

  @Test
  @DisplayName("python 解释器不存在时降级 SKIPPED")
  void missingPythonSkips() {
    properties.setPythonBin(tempDir.resolve("no-such-python").toString());

    PythonSyntaxCheckService.SyntaxCheckResult result = service().check("anything");

    assertThat(result.status()).isEqualTo("SKIPPED");
  }

  @Test
  @DisplayName("检测 Python 3.5 不兼容语法启发式")
  void detectsPy35Incompatibilities() {
    List<String> warnings = service().detectPy35Incompatibilities(
        "s = f'当前价格 {price}'\n"
            + "if (y := 1):\n"
            + "    pass\n");

    assertThat(warnings).anyMatch(w -> w.contains("f-string"));
    assertThat(warnings).anyMatch(w -> w.contains("海象"));
  }

  @Test
  @DisplayName("普通代码无 3.5 不兼容警示")
  void normalCodeHasNoWarnings() {
    List<String> warnings = service().detectPy35Incompatibilities(
        "def handle_data(context, data):\n    return\n");

    assertThat(warnings).isEmpty();
  }
}
