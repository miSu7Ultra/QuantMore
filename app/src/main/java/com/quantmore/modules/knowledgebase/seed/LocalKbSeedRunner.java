package com.quantmore.modules.knowledgebase.seed;

import com.quantmore.modules.knowledgebase.service.KnowledgeBaseUploadService;
import com.quantmore.modules.user.model.UserEntity;
import com.quantmore.modules.user.model.UserRole;
import com.quantmore.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 本地知识库种子导入：启动时若设置了 APP_SEED_KB_DIR，递归导入目录下的 Markdown 文件
 * 为公共知识库（仅管理员执行，靠文件哈希幂等，向量化走既有 Redis Stream 异步完成）。
 *
 * 用法：APP_SEED_KB_DIR=/path/to/docs ./gradlew :app:bootRun
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LocalKbSeedRunner implements CommandLineRunner {

  private final UserRepository userRepository;
  private final KnowledgeBaseUploadService uploadService;

  @Value("${APP_SEED_KB_DIR:}")
  private String seedDir;

  @Override
  public void run(String... args) throws Exception {
    if (seedDir == null || seedDir.isBlank()) {
      return;
    }
    Path root = Path.of(seedDir);
    if (!Files.isDirectory(root)) {
      log.warn("APP_SEED_KB_DIR 不是有效目录，跳过种子导入: {}", seedDir);
      return;
    }
    UserEntity admin = userRepository.findFirstByRoleOrderByIdAsc(UserRole.ADMIN)
        .orElseThrow(() -> new IllegalStateException(
            "APP_SEED_KB_DIR 已设置但系统中没有管理员用户，请先注册首个用户(自动成为 ADMIN)"));

    List<Path> mdFiles = collectMarkdownFiles(root);
    log.info("开始种子导入: dir={}, 文件数={}, 管理员={}", seedDir, mdFiles.size(), admin.getUsername());

    int imported = 0;
    int duplicated = 0;
    int failed = 0;
    for (Path file : mdFiles) {
      try {
        String name = stripExtension(file.getFileName().toString());
        String category = file.getParent() != null && !file.getParent().equals(root)
            ? file.getParent().getFileName().toString()
            : null;
        MultipartFile multipart = toMultipartFile(file);
        Map<String, Object> result = uploadService.uploadKnowledgeBase(
            multipart, name, category, "PUBLIC", admin.getId(), true);
        if (Boolean.TRUE.equals(result.get("duplicate"))) {
          duplicated++;
          log.info("种子导入跳过(重复): {}", file);
        } else {
          imported++;
          log.info("种子导入完成: {}", file);
        }
      } catch (Exception e) {
        failed++;
        log.error("种子导入失败: {}", file, e);
      }
    }
    log.info("种子导入汇总: 新增={}, 重复={}, 失败={}", imported, duplicated, failed);
  }

  private List<Path> collectMarkdownFiles(Path root) throws IOException {
    try (var stream = Files.walk(root)) {
      return stream
          .filter(Files::isRegularFile)
          .filter(p -> p.getFileName().toString().endsWith(".md"))
          .filter(p -> !p.toString().contains("/assets/"))
          .sorted(Comparator.comparing(Path::toString))
          .toList();
    }
  }

  private MultipartFile toMultipartFile(Path file) throws IOException {
    byte[] bytes = Files.readAllBytes(file);
    String fileName = file.getFileName().toString();
    String contentType = Files.probeContentType(file);
    return new PathMultipartFile(fileName, contentType != null ? contentType : "text/markdown", bytes);
  }

  /**
   * 最小化的 MultipartFile 实现（避免在 main 依赖 spring-test）
   */
  private record PathMultipartFile(String name, String contentType, byte[] bytes)
      implements MultipartFile {

    @Override
    public String getName() {
      return "file";
    }

    @Override
    public String getOriginalFilename() {
      return name;
    }

    @Override
    public String getContentType() {
      return contentType;
    }

    @Override
    public boolean isEmpty() {
      return bytes.length == 0;
    }

    @Override
    public long getSize() {
      return bytes.length;
    }

    @Override
    public byte[] getBytes() {
      return bytes;
    }

    @Override
    public InputStream getInputStream() {
      return new java.io.ByteArrayInputStream(bytes);
    }

    @Override
    public void transferTo(File dest) throws IOException, IllegalStateException {
      Files.write(dest.toPath(), bytes);
    }
  }

  private String stripExtension(String fileName) {
    int dot = fileName.lastIndexOf('.');
    return dot > 0 ? fileName.substring(0, dot) : fileName;
  }
}
