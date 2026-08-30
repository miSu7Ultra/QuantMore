package com.quantmore.modules.knowledgebase.service;

import com.quantmore.infrastructure.file.FileHashService;
import com.quantmore.infrastructure.file.FileStorageService;
import com.quantmore.infrastructure.file.FileValidationService;
import com.quantmore.modules.knowledgebase.listener.VectorizeStreamProducer;
import com.quantmore.modules.knowledgebase.model.KbVisibility;
import com.quantmore.modules.knowledgebase.model.KnowledgeBaseEntity;
import com.quantmore.modules.knowledgebase.repository.KnowledgeBaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("KnowledgeBaseUploadService 归属与去重测试")
class KnowledgeBaseUploadServiceTest {

  @Mock private KnowledgeBaseParseService parseService;
  @Mock private KnowledgeBasePersistenceService persistenceService;
  @Mock private FileStorageService storageService;
  @Mock private KnowledgeBaseRepository knowledgeBaseRepository;
  @Mock private FileValidationService fileValidationService;
  @Mock private FileHashService fileHashService;
  @Mock private VectorizeStreamProducer vectorizeStreamProducer;

  private KnowledgeBaseUploadService service;

  @BeforeEach
  void setUp() {
    service = new KnowledgeBaseUploadService(
        parseService, persistenceService, storageService, knowledgeBaseRepository,
        fileValidationService, fileHashService, vectorizeStreamProducer);
  }

  private MultipartFile file() {
    return new MockMultipartFile("file", "双均线策略.md", "text/markdown",
        "策略内容".getBytes());
  }

  private KnowledgeBaseEntity savedKb(Long ownerId, KbVisibility visibility) {
    KnowledgeBaseEntity e = new KnowledgeBaseEntity();
    e.setId(1L);
    e.setName("双均线策略");
    e.setOwnerId(ownerId);
    e.setVisibility(visibility);
    e.setFileSize(12L);
    return e;
  }

  @Nested
  @DisplayName("可见性解析")
  class Visibility {

    @Test
    @DisplayName("管理员上传 PUBLIC 时 owner 为 NULL、visibility=PUBLIC")
    void adminPublicUpload() {
      when(fileHashService.calculateHash(any(MultipartFile.class))).thenReturn("hash-1");
      when(knowledgeBaseRepository.findByFileHashAndOwnerIdIsNull("hash-1"))
          .thenReturn(Optional.empty());
      when(parseService.detectContentType(any())).thenReturn("text/markdown");
      when(parseService.parseContent(any())).thenReturn("策略内容");
      when(storageService.uploadKnowledgeBase(any())).thenReturn("key-1");
      when(storageService.getFileUrl("key-1")).thenReturn("http://url");
      when(persistenceService.saveKnowledgeBase(
          any(), any(), any(), any(), any(), any(), isNull(), eq(KbVisibility.PUBLIC)))
          .thenReturn(savedKb(null, KbVisibility.PUBLIC));

      Map<String, Object> result = service.uploadKnowledgeBase(
          file(), "双均线策略", "策略", "PUBLIC", 1L, true);

      assertThat(result.get("duplicate")).isEqualTo(false);
      verify(knowledgeBaseRepository).findByFileHashAndOwnerIdIsNull("hash-1");
      verify(knowledgeBaseRepository, never()).findByOwnerIdAndFileHash(any(), anyString());
    }

    @Test
    @DisplayName("普通用户传 PUBLIC 被强制降为 PRIVATE")
    void nonAdminForcedPrivate() {
      when(fileHashService.calculateHash(any(MultipartFile.class))).thenReturn("hash-2");
      when(knowledgeBaseRepository.findByOwnerIdAndFileHash(2L, "hash-2"))
          .thenReturn(Optional.empty());
      when(parseService.detectContentType(any())).thenReturn("text/markdown");
      when(parseService.parseContent(any())).thenReturn("策略内容");
      when(storageService.uploadKnowledgeBase(any())).thenReturn("key-2");
      when(storageService.getFileUrl("key-2")).thenReturn("http://url");
      when(persistenceService.saveKnowledgeBase(
          any(), any(), any(), any(), any(), any(), eq(2L), eq(KbVisibility.PRIVATE)))
          .thenReturn(savedKb(2L, KbVisibility.PRIVATE));

      service.uploadKnowledgeBase(file(), "双均线策略", "策略", "PUBLIC", 2L, false);

      ArgumentCaptor<Long> ownerCaptor = ArgumentCaptor.forClass(Long.class);
      verify(persistenceService).saveKnowledgeBase(
          any(), any(), any(), any(), any(), any(),
          ownerCaptor.capture(), eq(KbVisibility.PRIVATE));
      assertThat(ownerCaptor.getValue()).isEqualTo(2L);
      verify(knowledgeBaseRepository).findByOwnerIdAndFileHash(2L, "hash-2");
    }
  }

  @Nested
  @DisplayName("去重范围")
  class DedupScoping {

    @Test
    @DisplayName("私有上传只与自己的哈希去重，不跨用户")
    void privateDedupScopedToOwner() {
      when(fileHashService.calculateHash(any(MultipartFile.class))).thenReturn("hash-3");
      KnowledgeBaseEntity existing = savedKb(2L, KbVisibility.PRIVATE);
      when(knowledgeBaseRepository.findByOwnerIdAndFileHash(2L, "hash-3"))
          .thenReturn(Optional.of(existing));

      // 解析文件类型先于去重；此时无 S3 交互
      when(parseService.detectContentType(any())).thenReturn("text/markdown");
      when(persistenceService.handleDuplicateKnowledgeBase(existing, "hash-3"))
          .thenReturn(Map.of("duplicate", true));

      Map<String, Object> result = service.uploadKnowledgeBase(
          file(), "双均线策略", "策略", null, 2L, false);

      assertThat(result.get("duplicate")).isEqualTo(true);
      verify(storageService, never()).uploadKnowledgeBase(any());
      verify(knowledgeBaseRepository, never()).findByFileHashAndOwnerIdIsNull(anyString());
    }
  }
}
