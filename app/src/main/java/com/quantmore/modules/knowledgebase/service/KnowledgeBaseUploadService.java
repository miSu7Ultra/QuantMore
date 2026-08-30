package com.quantmore.modules.knowledgebase.service;

import com.quantmore.common.exception.BusinessException;
import com.quantmore.common.exception.ErrorCode;
import com.quantmore.infrastructure.file.FileHashService;
import com.quantmore.infrastructure.file.FileStorageService;
import com.quantmore.infrastructure.file.FileValidationService;
import com.quantmore.modules.knowledgebase.listener.VectorizeStreamProducer;
import com.quantmore.modules.knowledgebase.model.KbVisibility;
import com.quantmore.modules.knowledgebase.model.KnowledgeBaseEntity;
import com.quantmore.modules.knowledgebase.model.VectorStatus;
import com.quantmore.modules.knowledgebase.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Optional;

/**
 * 知识库上传服务
 * 处理知识库上传、解析的业务逻辑
 * 向量化改为异步处理，通过 Redis Stream 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseUploadService {

    private final KnowledgeBaseParseService parseService;
    private final KnowledgeBasePersistenceService persistenceService;
    private final FileStorageService storageService;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final FileValidationService fileValidationService;
    private final FileHashService fileHashService;
    private final VectorizeStreamProducer vectorizeStreamProducer;

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB
    
    /**
     * 上传知识库文件
     *
     * @param file 知识库文件
     * @param name 知识库名称（可选，如果为空则从文件名提取）
     * @param category 分类（可选）
     * @param visibility 可见性（仅管理员可传 PUBLIC，普通用户强制 PRIVATE）
     * @param userId 当前用户（管理员传 PUBLIC 时为 NULL owner）
     * @param isAdmin 当前用户是否为管理员
     * @return 上传结果和存储信息（包含duplicate字段，表示是否为重复上传）
     */
    public Map<String, Object> uploadKnowledgeBase(MultipartFile file, String name, String category,
                                                   String visibility, Long userId, boolean isAdmin) {
        // 0. 可见性解析：普通用户强制 PRIVATE
        boolean isPublic = isAdmin && "PUBLIC".equalsIgnoreCase(visibility);
        KbVisibility kbVisibility = isPublic ? KbVisibility.PUBLIC : KbVisibility.PRIVATE;
        Long ownerId = isPublic ? null : userId;

        // 1. 验证文件
        fileValidationService.validateFile(file, MAX_FILE_SIZE, "知识库");

        String fileName = file.getOriginalFilename();
        log.info("收到知识库上传请求: {}, 大小: {} bytes, category: {}, visibility: {}",
            fileName, file.getSize(), category, kbVisibility);

        // 2. 验证文件类型
        String contentType = parseService.detectContentType(file);
        validateContentType(contentType, fileName);

        // 3. 按 owner 范围检查知识库是否已存在（去重，避免跨用户信息泄露）
        String fileHash = fileHashService.calculateHash(file);
        Optional<KnowledgeBaseEntity> existingKb = isPublic
            ? knowledgeBaseRepository.findByFileHashAndOwnerIdIsNull(fileHash)
            : knowledgeBaseRepository.findByOwnerIdAndFileHash(ownerId, fileHash);
        if (existingKb.isPresent()) {
            log.info("检测到重复知识库: hash={}, scope={}", fileHash, kbVisibility);
            return persistenceService.handleDuplicateKnowledgeBase(existingKb.get(), fileHash);
        }

        // 4. 解析知识库文本（用于向量化）
        String content = parseService.parseContent(file);
        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "无法从文件中提取文本内容，请确保文件格式正确");
        }

        // 5. 保存文件到MinIO
        String fileKey = storageService.uploadKnowledgeBase(file);
        String fileUrl = storageService.getFileUrl(fileKey);
        log.info("知识库已存储到MinIO: {}", fileKey);

        // 6. 保存知识库元数据到数据库（状态为 PENDING）
        KnowledgeBaseEntity savedKb = persistenceService.saveKnowledgeBase(
            file, name, category, fileKey, fileUrl, fileHash, ownerId, kbVisibility);

        // 7. 发送向量化任务到 Redis Stream（异步处理）
        vectorizeStreamProducer.sendVectorizeTask(savedKb.getId(), content);

        log.info("知识库上传完成，向量化任务已入队: {}, kbId={}", fileName, savedKb.getId());

        // 8. 返回结果（状态为 PENDING，前端可轮询获取最新状态）
        return Map.of(
            "knowledgeBase", Map.of(
                "id", savedKb.getId(),
                "name", savedKb.getName(),
                "category", savedKb.getCategory() != null ? savedKb.getCategory() : "",
                "fileSize", savedKb.getFileSize(),
                "contentLength", content.length(),
                "vectorStatus", VectorStatus.PENDING.name(),
                "visibility", kbVisibility.name()
            ),
            "storage", Map.of(
                "fileKey", fileKey,
                "fileUrl", fileUrl
            ),
            "duplicate", false
        );
    }

    /**
     * 验证文件类型
     */
    private void validateContentType(String contentType, String fileName) {
        fileValidationService.validateContentType(
            contentType,
            fileName,
            fileValidationService::isKnowledgeBaseMimeType,
            fileValidationService::isMarkdownExtension,
            "不支持的文件类型: " + contentType + "，支持的类型：PDF、DOCX、DOC、TXT、MD等"
        );
    }
    
    /**
     * 重新向量化知识库（手动重试）
     * 从 MinIO 重新下载文件并发送向量化任务
     *
     * @param kbId 知识库ID
     * @param userId 当前用户
     * @param isAdmin 当前用户是否为管理员
     */
    public void revectorize(Long kbId, Long userId, boolean isAdmin) {
        KnowledgeBaseEntity kb = knowledgeBaseRepository.findById(kbId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "知识库不存在"));
        if (!isAdmin && !userId.equals(kb.getOwnerId())) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_FORBIDDEN, "无权操作该知识库");
        }

        log.info("开始重新向量化知识库: kbId={}, name={}", kbId, kb.getName());

        // 1. 下载文件并解析内容
        String content = parseService.downloadAndParseContent(kb.getStorageKey(), kb.getOriginalFilename());
        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "无法从文件中提取文本内容");
        }

        // 2. 更新状态为 PENDING（通过单独的 Service 保证事务生效）
        persistenceService.updateVectorStatusToPending(kbId);

        // 3. 发送向量化任务到 Stream
        vectorizeStreamProducer.sendVectorizeTask(kbId, content);

        log.info("重新向量化任务已发送: kbId={}", kbId);
    }
}

