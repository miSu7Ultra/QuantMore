package com.quantmore.modules.knowledgebase.service;

import com.quantmore.common.exception.BusinessException;
import com.quantmore.common.exception.ErrorCode;
import com.quantmore.infrastructure.file.FileStorageService;
import com.quantmore.infrastructure.mapper.KnowledgeBaseMapper;
import com.quantmore.modules.knowledgebase.model.KbVisibility;
import com.quantmore.modules.knowledgebase.model.KnowledgeBaseEntity;
import com.quantmore.modules.knowledgebase.model.KnowledgeBaseListItemDTO;
import com.quantmore.modules.knowledgebase.model.KnowledgeBaseStatsDTO;
import com.quantmore.modules.knowledgebase.model.RagChatMessageEntity.MessageType;
import com.quantmore.modules.knowledgebase.model.VectorStatus;
import com.quantmore.modules.knowledgebase.repository.KnowledgeBaseRepository;
import com.quantmore.modules.knowledgebase.repository.RagChatMessageRepository;
import com.quantmore.modules.user.model.UserPrincipal;
import com.quantmore.modules.user.model.UserRole;
import com.quantmore.modules.user.repository.UserRepository;
import com.quantmore.modules.user.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 知识库查询服务
 * 负责知识库列表和详情的查询；所有查询按当前用户可见性过滤（管理员看全部）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseListService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final RagChatMessageRepository ragChatMessageRepository;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final FileStorageService fileStorageService;
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;

    /**
     * 获取知识库列表（支持状态过滤和排序）
     *
     * @param vectorStatus 向量化状态，null 表示不过滤
     * @param sortBy 排序字段，null 或 "time" 表示按时间排序
     * @return 知识库列表
     */
    public List<KnowledgeBaseListItemDTO> listKnowledgeBases(VectorStatus vectorStatus, String sortBy) {
        UserPrincipal user = currentUserService.get();
        boolean isAdmin = user.role() == UserRole.ADMIN;

        List<KnowledgeBaseEntity> entities;
        if (vectorStatus != null) {
            entities = isAdmin
                ? knowledgeBaseRepository.findByVectorStatusOrderByUploadedAtDesc(vectorStatus)
                : knowledgeBaseRepository.findVisibleByVectorStatusOrderByUploadedAtDesc(user.id(), vectorStatus);
        } else {
            entities = isAdmin
                ? knowledgeBaseRepository.findAllByOrderByUploadedAtDesc()
                : knowledgeBaseRepository.findVisibleOrderByUploadedAtDesc(user.id());
        }

        // 如果指定了排序字段，在内存中排序
        if (sortBy != null && !sortBy.isBlank() && !sortBy.equalsIgnoreCase("time")) {
            entities = sortEntities(entities, sortBy);
        }

        return decorate(knowledgeBaseMapper.toListItemDTOList(entities), user, isAdmin);
    }

    /**
     * 获取所有知识库列表（保持向后兼容）
     */
    public List<KnowledgeBaseListItemDTO> listKnowledgeBases() {
        return listKnowledgeBases(null, null);
    }

    /**
     * 按向量化状态获取知识库列表（保持向后兼容）
     */
    public List<KnowledgeBaseListItemDTO> listKnowledgeBasesByStatus(VectorStatus vectorStatus) {
        return listKnowledgeBases(vectorStatus, null);
    }

    /**
     * 根据ID获取知识库详情（校验可见性）
     */
    public Optional<KnowledgeBaseListItemDTO> getKnowledgeBase(Long id) {
        UserPrincipal user = currentUserService.get();
        boolean isAdmin = user.role() == UserRole.ADMIN;
        return knowledgeBaseRepository.findById(id)
            .filter(kb -> isAdmin || kb.getVisibility() == KbVisibility.PUBLIC
                || user.id().equals(kb.getOwnerId()))
            .map(knowledgeBaseMapper::toListItemDTO)
            .map(dto -> decorate(List.of(dto), user, isAdmin).getFirst());
    }

    /**
     * 根据ID获取知识库实体（用于删除等操作，校验可见性）
     */
    public Optional<KnowledgeBaseEntity> getKnowledgeBaseEntity(Long id) {
        UserPrincipal user = currentUserService.get();
        boolean isAdmin = user.role() == UserRole.ADMIN;
        return knowledgeBaseRepository.findById(id)
            .filter(kb -> isAdmin || kb.getVisibility() == KbVisibility.PUBLIC
                || user.id().equals(kb.getOwnerId()));
    }

    /**
     * 根据ID列表获取知识库名称列表
     */
    public List<String> getKnowledgeBaseNames(List<Long> ids) {
        return ids.stream()
            .map(id -> knowledgeBaseRepository.findById(id)
                .map(KnowledgeBaseEntity::getName)
                .orElse("未知知识库"))
            .toList();
    }

    // ========== 分类管理 ==========

    /**
     * 获取所有分类（按可见性过滤）
     */
    public List<String> getAllCategories() {
        UserPrincipal user = currentUserService.get();
        return user.role() == UserRole.ADMIN
            ? knowledgeBaseRepository.findAllCategories()
            : knowledgeBaseRepository.findVisibleCategories(user.id());
    }

    /**
     * 根据分类获取知识库列表
     */
    public List<KnowledgeBaseListItemDTO> listByCategory(String category) {
        UserPrincipal user = currentUserService.get();
        boolean isAdmin = user.role() == UserRole.ADMIN;
        List<KnowledgeBaseEntity> entities;
        if (category == null || category.isBlank()) {
            entities = isAdmin
                ? knowledgeBaseRepository.findByCategoryIsNullOrderByUploadedAtDesc()
                : knowledgeBaseRepository.findVisibleUncategorizedOrderByUploadedAtDesc(user.id());
        } else {
            entities = isAdmin
                ? knowledgeBaseRepository.findByCategoryOrderByUploadedAtDesc(category)
                : knowledgeBaseRepository.findVisibleByCategoryOrderByUploadedAtDesc(user.id(), category);
        }
        return decorate(knowledgeBaseMapper.toListItemDTOList(entities), user, isAdmin);
    }

    /**
     * 更新知识库分类（owner 或管理员）
     */
    @Transactional
    public void updateCategory(Long id, String category) {
        UserPrincipal user = currentUserService.get();
        KnowledgeBaseEntity entity = getKnowledgeBaseEntity(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库不存在"));
        if (user.role() != UserRole.ADMIN && !user.id().equals(entity.getOwnerId())) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_FORBIDDEN, "无权操作该知识库");
        }
        entity.setCategory(category != null && !category.isBlank() ? category : null);
        knowledgeBaseRepository.save(entity);
        log.info("更新知识库分类: id={}, category={}", id, category);
    }

    // ========== 搜索功能 ==========

    /**
     * 按关键词搜索知识库（按可见性过滤）
     */
    public List<KnowledgeBaseListItemDTO> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return listKnowledgeBases();
        }
        UserPrincipal user = currentUserService.get();
        boolean isAdmin = user.role() == UserRole.ADMIN;
        List<KnowledgeBaseEntity> entities = isAdmin
            ? knowledgeBaseRepository.searchByKeyword(keyword.trim())
            : knowledgeBaseRepository.searchVisibleByKeyword(user.id(), keyword.trim());
        return decorate(knowledgeBaseMapper.toListItemDTOList(entities), user, isAdmin);
    }

    // ========== 排序功能 ==========

    /**
     * 按指定字段排序获取知识库列表（保持向后兼容）
     */
    public List<KnowledgeBaseListItemDTO> listSorted(String sortBy) {
        return listKnowledgeBases(null, sortBy);
    }

    /**
     * 在内存中对实体列表排序
     */
    private List<KnowledgeBaseEntity> sortEntities(List<KnowledgeBaseEntity> entities, String sortBy) {
        return switch (sortBy.toLowerCase()) {
            case "size" -> entities.stream()
                .sorted((a, b) -> Long.compare(b.getFileSize(), a.getFileSize()))
                .toList();
            case "access" -> entities.stream()
                .sorted((a, b) -> Integer.compare(b.getAccessCount(), a.getAccessCount()))
                .toList();
            case "question" -> entities.stream()
                .sorted((a, b) -> Integer.compare(b.getQuestionCount(), a.getQuestionCount()))
                .toList();
            default -> entities; // time 已经在数据库层面排序了
        };
    }

    // ========== 统计功能 ==========

    /**
     * 获取知识库统计信息（按可见性范围统计）
     * 总提问次数从用户消息数统计，确保多知识库提问只算一次
     */
    public KnowledgeBaseStatsDTO getStatistics() {
        UserPrincipal user = currentUserService.get();
        boolean isAdmin = user.role() == UserRole.ADMIN;
        if (isAdmin) {
            return new KnowledgeBaseStatsDTO(
                knowledgeBaseRepository.count(),
                ragChatMessageRepository.countByType(MessageType.USER),
                knowledgeBaseRepository.sumAccessCount(),
                knowledgeBaseRepository.countByVectorStatus(VectorStatus.COMPLETED),
                knowledgeBaseRepository.countByVectorStatus(VectorStatus.PROCESSING)
            );
        }
        return new KnowledgeBaseStatsDTO(
            knowledgeBaseRepository.countVisible(user.id()),
            ragChatMessageRepository.countByTypeAndSessionUserId(MessageType.USER, user.id()),
            knowledgeBaseRepository.sumVisibleAccessCount(user.id()),
            knowledgeBaseRepository.countVisibleByVectorStatus(user.id(), VectorStatus.COMPLETED),
            knowledgeBaseRepository.countVisibleByVectorStatus(user.id(), VectorStatus.PROCESSING)
        );
    }

    // ========== 下载功能 ==========

    /**
     * 下载知识库文件（校验可见性）
     */
    public byte[] downloadFile(Long id) {
        KnowledgeBaseEntity entity = getEntityForDownload(id);

        String storageKey = entity.getStorageKey();
        if (storageKey == null || storageKey.isBlank()) {
            throw new BusinessException(ErrorCode.STORAGE_DOWNLOAD_FAILED, "文件存储信息不存在");
        }

        log.info("下载知识库文件: id={}, filename={}", id, entity.getOriginalFilename());
        return fileStorageService.downloadFile(storageKey);
    }

    /**
     * 获取知识库文件信息（用于下载，校验可见性）
     */
    public KnowledgeBaseEntity getEntityForDownload(Long id) {
        return getKnowledgeBaseEntity(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库不存在"));
    }

    // ========== DTO 装饰 ==========

    /**
     * 批量填充 ownerUsername 与 canDelete
     */
    private List<KnowledgeBaseListItemDTO> decorate(
        List<KnowledgeBaseListItemDTO> dtos, UserPrincipal user, boolean isAdmin) {
        List<Long> ownerIds = dtos.stream()
            .map(KnowledgeBaseListItemDTO::ownerId)
            .filter(id -> id != null)
            .distinct()
            .toList();
        Map<Long, String> usernames = ownerIds.isEmpty()
            ? Map.of()
            : userRepository.findAllById(ownerIds).stream()
                .collect(Collectors.toMap(u -> u.getId(), u -> u.getUsername()));
        return dtos.stream()
            .map(dto -> decorateOne(dto, isAdmin, usernames, user.id()))
            .toList();
    }

    private KnowledgeBaseListItemDTO decorateOne(
        KnowledgeBaseListItemDTO dto, boolean isAdmin, Map<Long, String> usernames, Long userId) {
        String ownerUsername = dto.ownerId() != null ? usernames.get(dto.ownerId()) : null;
        boolean canDelete = isAdmin || (dto.ownerId() != null && userId.equals(dto.ownerId()));
        return new KnowledgeBaseListItemDTO(
            dto.id(), dto.name(), dto.category(), dto.originalFilename(), dto.fileSize(),
            dto.contentType(), dto.uploadedAt(), dto.lastAccessedAt(), dto.accessCount(),
            dto.questionCount(), dto.vectorStatus(), dto.vectorError(), dto.chunkCount(),
            dto.ownerId(), dto.visibility(), ownerUsername, canDelete);
    }
}
