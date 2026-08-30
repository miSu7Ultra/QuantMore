package com.quantmore.modules.knowledgebase.seed;

import com.quantmore.modules.knowledgebase.repository.KnowledgeBaseRepository;
import com.quantmore.modules.knowledgebase.service.KnowledgeBaseUploadService;
import com.quantmore.modules.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("LocalKbSeedRunner 分组测试")
class LocalKbSeedRunnerTest {

  @Mock private UserRepository userRepository;
  @Mock private KnowledgeBaseUploadService uploadService;
  @Mock private KnowledgeBaseRepository knowledgeBaseRepository;

  @TempDir Path tempDir;

  private LocalKbSeedRunner newRunner(String seedDir) {
    LocalKbSeedRunner runner = new LocalKbSeedRunner(
        userRepository, uploadService, knowledgeBaseRepository);
    runner.seedDir = seedDir;
    return runner;
  }

  @Test
  @DisplayName("子目录各成一个单元，根目录 md 文件各自成组，assets 被跳过")
  void groupsBySubdirectory() throws Exception {
    Path docs = tempDir.resolve("docs");
    Files.createDirectories(docs.resolve("08-交易相关函数"));
    Files.createDirectories(docs.resolve("12-策略示例"));
    Files.createDirectories(docs.resolve("assets"));
    Files.writeString(docs.resolve("01-API文档.md"), "# API");
    Files.writeString(docs.resolve("README.md"), "# README");
    Files.writeString(docs.resolve("08-交易相关函数/a.md"), "# a");
    Files.writeString(docs.resolve("08-交易相关函数/b.md"), "# b");
    Files.writeString(docs.resolve("12-策略示例/双均线.md"), "# 双均线");
    Files.writeString(docs.resolve("assets/img.png"), "png");

    Map<String, List<Path>> units = newRunner(docs.toString()).groupBySubdirectory(docs);

    assertThat(units).containsOnlyKeys(
        "01-API文档", "README", "08-交易相关函数", "12-策略示例");
    assertThat(units.get("08-交易相关函数")).hasSize(2);
    assertThat(units.get("12-策略示例")).hasSize(1);
    assertThat(units.get("01-API文档")).hasSize(1);
  }

  @Test
  @DisplayName("子目录内无 md 文件时不成组")
  void skipsEmptySubdirectories() throws Exception {
    Path docs = tempDir.resolve("docs");
    Files.createDirectories(docs.resolve("empty-dir"));
    Files.writeString(docs.resolve("01-API文档.md"), "# API");

    Map<String, List<Path>> units = newRunner(docs.toString()).groupBySubdirectory(docs);

    assertThat(units).containsOnlyKeys("01-API文档");
  }
}
