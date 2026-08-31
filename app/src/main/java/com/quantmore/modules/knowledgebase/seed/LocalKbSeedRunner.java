package com.quantmore.modules.knowledgebase.seed;

import com.quantmore.infrastructure.file.FileHashService;
import com.quantmore.modules.knowledgebase.model.KnowledgeBaseEntity;
import com.quantmore.modules.knowledgebase.model.VectorStatus;
import com.quantmore.modules.knowledgebase.repository.KnowledgeBaseRepository;
import com.quantmore.modules.knowledgebase.service.KnowledgeBaseDeleteService;
import com.quantmore.modules.knowledgebase.service.KnowledgeBaseUploadService;
import com.quantmore.modules.user.model.UserEntity;
import com.quantmore.modules.user.model.UserRole;
import com.quantmore.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 本地知识库种子导入：启动时若设置了 APP_SEED_KB_DIR，按子目录切割导入 Markdown 文档
 * 为公共知识库（仅管理员执行，靠内容哈希幂等，向量化走既有 Redis Stream 异步完成）。
 *
 * 切割规则：
 * - 每个顶层子目录 = 一个知识库单元（目录内所有 md 按文件名排序合并，名称 = 目录名）
 * - 根目录下的每个 md 文件 = 一个独立知识库单元
 * - 跳过 assets/ 等资源目录
 *
 * 用法：APP_SEED_KB_DIR=/path/to/docs ./gradlew :app:bootRun
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(10)
public class LocalKbSeedRunner implements CommandLineRunner {

  private final UserRepository userRepository;
  private final KnowledgeBaseUploadService uploadService;
  private final KnowledgeBaseRepository knowledgeBaseRepository;
  private final KnowledgeBaseDeleteService deleteService;
  private final FileHashService fileHashService;

  @Value("${APP_SEED_KB_DIR:}")
  String seedDir;

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

    Map<String, List<Path>> units = groupBySubdirectory(root);
    log.info("开始种子导入: dir={}, 知识库单元数={}, 管理员={}",
        seedDir, units.size(), admin.getUsername());

    int imported = 0;
    int duplicated = 0;
    int replaced = 0;
    int revectorized = 0;
    int failed = 0;
    for (Map.Entry<String, List<Path>> entry : units.entrySet()) {
      String unitName = entry.getKey();
      try {
        MultipartFile multipart = toCombinedMarkdown(unitName, entry.getValue());
        if (replaceIfContentChanged(unitName, multipart, admin)) {
          replaced++;
        }
        Map<String, Object> result = uploadService.uploadKnowledgeBase(
            multipart, unitName, unitName, "PUBLIC", admin.getId(), true);
        if (Boolean.TRUE.equals(result.get("duplicate"))) {
          duplicated++;
          // 自愈：内容未变但之前向量化失败（如 API Key 未配置）时自动重新向量化
          Long kbId = extractKbId(result);
          if (kbId != null && shouldRevectorize(kbId)) {
            uploadService.revectorize(kbId, admin.getId(), true);
            revectorized++;
            log.info("种子导入: {} 已存在但向量化失败，已自动重新向量化", unitName);
          } else {
            log.info("种子导入跳过(重复): {}", unitName);
          }
        } else {
          imported++;
          log.info("种子导入完成: {} ({} 个文件)", unitName, entry.getValue().size());
        }
      } catch (Exception e) {
        failed++;
        log.error("种子导入失败: {}", unitName, e);
      }
    }
    log.info("种子导入汇总: 新增={}, 重复={}, 替换={}, 自动重向量化={}, 失败={}",
        imported, duplicated, replaced, revectorized, failed);
  }

  /**
   * 内容同步：同名公共知识库存在但内容哈希不同时，删除旧单元以便重新导入
   */
  private boolean replaceIfContentChanged(String unitName, MultipartFile multipart, UserEntity admin) {
    KnowledgeBaseEntity existing = knowledgeBaseRepository.findByOwnerIdIsNullAndName(unitName)
        .orElse(null);
    if (existing == null) {
      return false;
    }
    String newHash = fileHashService.calculateHash(multipart);
    if (newHash.equals(existing.getFileHash())) {
      return false;
    }
    deleteService.deleteKnowledgeBase(existing.getId(), admin.getId(), true);
    log.info("种子同步: {} 内容已变更，已删除旧单元(kbId={})，将重新导入", unitName, existing.getId());
    return true;
  }

  @SuppressWarnings("unchecked")
  private Long extractKbId(Map<String, Object> result) {
    Object kb = result.get("knowledgeBase");
    if (kb instanceof Map<?, ?> kbMap) {
      Object id = kbMap.get("id");
      return id instanceof Number number ? number.longValue() : null;
    }
    return null;
  }

  private boolean shouldRevectorize(Long kbId) {
    return knowledgeBaseRepository.findById(kbId)
        .map(kb -> kb.getVectorStatus() == VectorStatus.FAILED)
        .orElse(false);
  }

  /**
   * 按顶层子目录分组：目录 → 目录内所有 md；根目录文件 → 各自成组
   */
  Map<String, List<Path>> groupBySubdirectory(Path root) throws IOException {
    Map<String, List<Path>> units = new LinkedHashMap<>();
    try (Stream<Path> stream = Files.walk(root, 1)) {
      List<Path> entries = stream
          .filter(p -> !p.equals(root))
          .filter(p -> !p.getFileName().toString().equals("assets"))
          .sorted(Comparator.comparing(Path::toString))
          .toList();
      for (Path entry : entries) {
        if (Files.isDirectory(entry)) {
          List<Path> mdFiles = collectMarkdownFiles(entry);
          if (!mdFiles.isEmpty()) {
            units.put(entry.getFileName().toString(), mdFiles);
          }
        } else if (entry.getFileName().toString().endsWith(".md")) {
          units.put(stripExtension(entry.getFileName().toString()), List.of(entry));
        }
      }
    }
    return units;
  }

  private List<Path> collectMarkdownFiles(Path dir) throws IOException {
    try (Stream<Path> stream = Files.walk(dir)) {
      return stream
          .filter(Files::isRegularFile)
          .filter(p -> p.getFileName().toString().endsWith(".md"))
          .sorted(Comparator.comparing(Path::toString))
          .toList();
    }
  }

  /**
   * 将一个单元内的多个 md 合并为单个 MultipartFile（文件名 = 单元名.md），
   * 各文件之间以 Markdown 分隔线 + 原文件名标题分隔，保证内容哈希稳定可幂等
   */
  private MultipartFile toCombinedMarkdown(String unitName, List<Path> files) throws IOException {
    StringBuilder sb = new StringBuilder();
    for (Path file : files) {
      sb.append("\n\n---\n\n# ").append(stripExtension(file.getFileName().toString())).append("\n\n");
      sb.append(Files.readString(file, StandardCharsets.UTF_8));
    }
    return new BytesMultipartFile(unitName + ".md", "text/markdown",
        sb.toString().getBytes(StandardCharsets.UTF_8));
  }

  private String stripExtension(String fileName) {
    int dot = fileName.lastIndexOf('.');
    return dot > 0 ? fileName.substring(0, dot) : fileName;
  }

  /**
   * 最小化的 MultipartFile 实现（避免在 main 依赖 spring-test）
   */
  private record BytesMultipartFile(String name, String contentType, byte[] bytes)
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
}
