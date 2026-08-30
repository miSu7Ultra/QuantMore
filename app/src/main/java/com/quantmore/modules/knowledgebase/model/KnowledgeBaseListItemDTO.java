package com.quantmore.modules.knowledgebase.model;

import java.time.LocalDateTime;

/**
 * 知识库列表项DTO
 * 使用MapStruct进行转换，见KnowledgeBaseMapper
 * ownerUsername / canDelete 为服务层按当前用户计算后填充
 */
public record KnowledgeBaseListItemDTO(
    Long id,
    String name,
    String category,
    String originalFilename,
    Long fileSize,
    String contentType,
    LocalDateTime uploadedAt,
    LocalDateTime lastAccessedAt,
    Integer accessCount,
    Integer questionCount,
    VectorStatus vectorStatus,
    String vectorError,
    Integer chunkCount,
    Long ownerId,
    KbVisibility visibility,
    String ownerUsername,
    Boolean canDelete
) {
}
