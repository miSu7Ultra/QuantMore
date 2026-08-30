package com.quantmore.modules.knowledgebase.service;

import com.quantmore.infrastructure.file.FileStorageService;
import com.quantmore.infrastructure.mapper.KnowledgeBaseMapper;
import com.quantmore.modules.knowledgebase.model.KbVisibility;
import com.quantmore.modules.knowledgebase.model.KnowledgeBaseEntity;
import com.quantmore.modules.knowledgebase.model.KnowledgeBaseListItemDTO;
import com.quantmore.modules.knowledgebase.model.RagChatMessageEntity.MessageType;
import com.quantmore.modules.knowledgebase.model.VectorStatus;
import com.quantmore.modules.knowledgebase.repository.KnowledgeBaseRepository;
import com.quantmore.modules.knowledgebase.repository.RagChatMessageRepository;
import com.quantmore.modules.user.model.UserPrincipal;
import com.quantmore.modules.user.model.UserRole;
import com.quantmore.modules.user.repository.UserRepository;
import com.quantmore.modules.user.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("KnowledgeBaseListService 可见性测试")
class KnowledgeBaseListServiceTest {

  @Mock private KnowledgeBaseRepository knowledgeBaseRepository;
  @Mock private RagChatMessageRepository ragChatMessageRepository;
  @Mock private KnowledgeBaseMapper knowledgeBaseMapper;
  @Mock private FileStorageService fileStorageService;
  @Mock private CurrentUserService currentUserService;
  @Mock private UserRepository userRepository;

  private KnowledgeBaseListService service;

  @BeforeEach
  void setUp() {
    service = new KnowledgeBaseListService(
        knowledgeBaseRepository, ragChatMessageRepository, knowledgeBaseMapper,
        fileStorageService, currentUserService, userRepository);
  }

  private KnowledgeBaseEntity kb(Long id, Long ownerId, KbVisibility visibility) {
    KnowledgeBaseEntity e = new KnowledgeBaseEntity();
    e.setId(id);
    e.setName("kb-" + id);
    e.setOwnerId(ownerId);
    e.setVisibility(visibility);
    e.setFileHash("hash-" + id);
    return e;
  }

  private KnowledgeBaseListItemDTO dtoOf(KnowledgeBaseEntity e) {
    return new KnowledgeBaseListItemDTO(
        e.getId(), e.getName(), null, null, null, null, null, null, null, null,
        VectorStatus.PENDING, null, null, e.getOwnerId(), e.getVisibility(), null, null);
  }

  @Nested
  @DisplayName("列表范围")
  class ListScoping {

    @Test
    @DisplayName("普通用户列表走可见性范围查询")
    void userUsesScopedQuery() {
      when(currentUserService.get()).thenReturn(new UserPrincipal(2L, "bob", UserRole.USER));
      when(knowledgeBaseRepository.findVisibleOrderByUploadedAtDesc(2L)).thenReturn(List.of());
      when(knowledgeBaseMapper.toListItemDTOList(anyList())).thenReturn(List.of());

      service.listKnowledgeBases();

      verify(knowledgeBaseRepository).findVisibleOrderByUploadedAtDesc(2L);
      verify(knowledgeBaseRepository, never()).findAllByOrderByUploadedAtDesc();
    }

    @Test
    @DisplayName("管理员列表走全局查询")
    void adminUsesGlobalQuery() {
      when(currentUserService.get()).thenReturn(new UserPrincipal(1L, "admin", UserRole.ADMIN));
      when(knowledgeBaseRepository.findAllByOrderByUploadedAtDesc()).thenReturn(List.of());
      when(knowledgeBaseMapper.toListItemDTOList(anyList())).thenReturn(List.of());

      service.listKnowledgeBases();

      verify(knowledgeBaseRepository).findAllByOrderByUploadedAtDesc();
      verify(knowledgeBaseRepository, never()).findVisibleOrderByUploadedAtDesc(any());
    }
  }

  @Nested
  @DisplayName("详情可见性")
  class DetailVisibility {

    @Test
    @DisplayName("用户看不到他人的私有知识库")
    void userCannotSeeOthersPrivateKb() {
      when(currentUserService.get()).thenReturn(new UserPrincipal(2L, "bob", UserRole.USER));
      when(knowledgeBaseRepository.findById(9L))
          .thenReturn(Optional.of(kb(9L, 3L, KbVisibility.PRIVATE)));

      assertThat(service.getKnowledgeBase(9L)).isEmpty();
    }

    @Test
    @DisplayName("用户可以看到公共知识库和自己的私有知识库")
    void userSeesPublicAndOwnKb() {
      when(currentUserService.get()).thenReturn(new UserPrincipal(2L, "bob", UserRole.USER));
      KnowledgeBaseEntity publicKb = kb(1L, null, KbVisibility.PUBLIC);
      KnowledgeBaseEntity ownKb = kb(2L, 2L, KbVisibility.PRIVATE);
      when(knowledgeBaseRepository.findById(1L)).thenReturn(Optional.of(publicKb));
      when(knowledgeBaseRepository.findById(2L)).thenReturn(Optional.of(ownKb));
      when(knowledgeBaseMapper.toListItemDTO(publicKb)).thenReturn(dtoOf(publicKb));
      when(knowledgeBaseMapper.toListItemDTO(ownKb)).thenReturn(dtoOf(ownKb));

      assertThat(service.getKnowledgeBase(1L)).isPresent();
      assertThat(service.getKnowledgeBase(2L)).isPresent();
    }

    @Test
    @DisplayName("管理员可以看到任何知识库")
    void adminSeesEverything() {
      when(currentUserService.get()).thenReturn(new UserPrincipal(1L, "admin", UserRole.ADMIN));
      KnowledgeBaseEntity kb = kb(9L, 3L, KbVisibility.PRIVATE);
      when(knowledgeBaseRepository.findById(9L)).thenReturn(Optional.of(kb));
      when(knowledgeBaseMapper.toListItemDTO(kb)).thenReturn(dtoOf(kb));

      assertThat(service.getKnowledgeBase(9L)).isPresent();
    }
  }

  @Nested
  @DisplayName("DTO 装饰")
  class Decoration {

    @Test
    @DisplayName("canDelete: 本人私有库为 true，他人公共库为 false")
    void canDeleteComputedByOwnership() {
      when(currentUserService.get()).thenReturn(new UserPrincipal(2L, "bob", UserRole.USER));
      KnowledgeBaseEntity ownKb = kb(2L, 2L, KbVisibility.PRIVATE);
      KnowledgeBaseEntity publicKb = kb(1L, null, KbVisibility.PUBLIC);
      when(knowledgeBaseRepository.findVisibleOrderByUploadedAtDesc(2L))
          .thenReturn(List.of(ownKb, publicKb));
      when(knowledgeBaseMapper.toListItemDTOList(anyList()))
          .thenReturn(List.of(dtoOf(ownKb), dtoOf(publicKb)));

      List<KnowledgeBaseListItemDTO> result = service.listKnowledgeBases();

      assertThat(result).hasSize(2);
      assertThat(result.get(0).canDelete()).isTrue();
      assertThat(result.get(1).canDelete()).isFalse();
      assertThat(result.get(1).visibility()).isEqualTo(KbVisibility.PUBLIC);
    }
  }
}
