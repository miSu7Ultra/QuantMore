package com.quantmore.modules.generator.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EvalCaseLoader 测试")
class EvalCaseLoaderTest {

  @TempDir Path tempDir;

  private EvalProperties properties;
  private EvalCaseLoader loader;

  @BeforeEach
  void setUp() {
    properties = new EvalProperties();
    loader = new EvalCaseLoader(new DefaultResourceLoader(), new ObjectMapper(), properties);
  }

  private Path writeCases(String json) throws Exception {
    Path file = tempDir.resolve("cases.json");
    Files.writeString(file, json);
    properties.setCasesPath("file:" + file.toAbsolutePath());
    return file;
  }

  @Test
  @DisplayName("合法用例文件加载成功")
  void loadsValidCases() throws Exception {
    writeCases("""
        [
          {"id":"s01","name":"双均线","market":"STOCK","frequency":"DAILY",
           "buyConditions":"五日均线上穿十日均线买入","sellConditions":"死叉卖出",
           "riskControls":"","difficulty":"SIMPLE"}
        ]
        """);

    List<EvalCase> cases = loader.load();

    assertThat(cases).hasSize(1);
    assertThat(cases.get(0).id()).isEqualTo("s01");
    assertThat(cases.get(0).market()).isEqualTo("STOCK");
    assertThat(cases.get(0).difficulty()).isEqualTo("SIMPLE");
  }

  @Test
  @DisplayName("文件不存在抛 IllegalStateException")
  void missingFileThrows() {
    properties.setCasesPath("file:" + tempDir.resolve("nope.json").toAbsolutePath());

    assertThatThrownBy(loader::load).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("非法 market 枚举值抛 IllegalStateException 并携带用例 id")
  void invalidEnumThrows() throws Exception {
    writeCases("""
        [
          {"id":"b1","name":"x","market":"CRYPTO","frequency":"DAILY",
           "buyConditions":"买入","sellConditions":"","riskControls":"","difficulty":"SIMPLE"}
        ]
        """);

    assertThatThrownBy(loader::load)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("b1");
  }

  @Test
  @DisplayName("空数组抛 IllegalStateException")
  void emptyArrayThrows() throws Exception {
    writeCases("[]");

    assertThatThrownBy(loader::load).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("必填字段为空抛 IllegalStateException")
  void blankRequiredFieldThrows() throws Exception {
    writeCases("""
        [
          {"id":"m1","name":"x","market":"STOCK","frequency":"DAILY",
           "buyConditions":"","sellConditions":"","riskControls":"","difficulty":"SIMPLE"}
        ]
        """);

    assertThatThrownBy(loader::load).isInstanceOf(IllegalStateException.class);
  }
}
