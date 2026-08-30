package com.quantmore.infrastructure.mapper;

import com.quantmore.modules.knowledgebase.model.KnowledgeBaseEntity;
import com.quantmore.modules.knowledgebase.model.KnowledgeBaseListItemDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * 知识库实体到DTO的映射器
 * 使用MapStruct自动生成转换代码
 * ownerUsername / canDelete 由服务层按当前用户上下文填充，这里忽略
 */
@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface KnowledgeBaseMapper {

    /**
     * 将知识库实体转换为列表项DTO
     */
    @Mapping(target = "ownerUsername", ignore = true)
    @Mapping(target = "canDelete", ignore = true)
    KnowledgeBaseListItemDTO toListItemDTO(KnowledgeBaseEntity entity);

    /**
     * 将知识库实体列表转换为列表项DTO列表
     */
    List<KnowledgeBaseListItemDTO> toListItemDTOList(List<KnowledgeBaseEntity> entities);
}

