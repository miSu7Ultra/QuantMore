package com.quantmore.modules.knowledgebase.repository;

import com.quantmore.modules.knowledgebase.model.KnowledgeBaseEntity;
import com.quantmore.modules.knowledgebase.model.VectorStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 知识库Repository
 * 可见性规则：PUBLIC（owner 为 NULL）全员可见，PRIVATE 仅 owner 可见；
 * 「可见」查询以 findVisible* / isVisibleToUser 方法表达，管理员场景由 Service 选择全局方法。
 */
@Repository
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBaseEntity, Long> {

    // ==================== 按 owner 范围去重 ====================

    /**
     * 私有知识库去重：同一用户下相同哈希视为重复
     */
    Optional<KnowledgeBaseEntity> findByOwnerIdAndFileHash(Long ownerId, String fileHash);

    /**
     * 公共知识库去重：公共范围内相同哈希视为重复
     */
    Optional<KnowledgeBaseEntity> findByFileHashAndOwnerIdIsNull(String fileHash);

    // ==================== 全局查询（管理员/系统使用） ====================

    /**
     * 按上传时间倒序查找所有知识库
     */
    List<KnowledgeBaseEntity> findAllByOrderByUploadedAtDesc();

    /**
     * 根据分类查找知识库
     */
    List<KnowledgeBaseEntity> findByCategoryOrderByUploadedAtDesc(String category);

    /**
     * 查找未分类的知识库
     */
    List<KnowledgeBaseEntity> findByCategoryIsNullOrderByUploadedAtDesc();

    /**
     * 按向量化状态查找知识库（按上传时间倒序）
     */
    List<KnowledgeBaseEntity> findByVectorStatusOrderByUploadedAtDesc(VectorStatus vectorStatus);

    /**
     * 按名称或文件名模糊搜索（不区分大小写）
     */
    @Query("SELECT k FROM KnowledgeBaseEntity k WHERE LOWER(k.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(k.originalFilename) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY k.uploadedAt DESC")
    List<KnowledgeBaseEntity> searchByKeyword(@Param("keyword") String keyword);

    /**
     * 获取所有不同的分类
     */
    @Query("SELECT DISTINCT k.category FROM KnowledgeBaseEntity k WHERE k.category IS NOT NULL ORDER BY k.category")
    List<String> findAllCategories();

    // ==================== 可见性范围查询 ====================

    @Query("SELECT k FROM KnowledgeBaseEntity k WHERE k.visibility = 'PUBLIC' OR k.ownerId = :userId ORDER BY k.uploadedAt DESC")
    List<KnowledgeBaseEntity> findVisibleOrderByUploadedAtDesc(@Param("userId") Long userId);

    @Query("SELECT k FROM KnowledgeBaseEntity k WHERE (k.visibility = 'PUBLIC' OR k.ownerId = :userId) AND k.vectorStatus = :status ORDER BY k.uploadedAt DESC")
    List<KnowledgeBaseEntity> findVisibleByVectorStatusOrderByUploadedAtDesc(
        @Param("userId") Long userId, @Param("status") VectorStatus status);

    @Query("SELECT k FROM KnowledgeBaseEntity k WHERE (k.visibility = 'PUBLIC' OR k.ownerId = :userId) AND k.category = :category ORDER BY k.uploadedAt DESC")
    List<KnowledgeBaseEntity> findVisibleByCategoryOrderByUploadedAtDesc(
        @Param("userId") Long userId, @Param("category") String category);

    @Query("SELECT k FROM KnowledgeBaseEntity k WHERE (k.visibility = 'PUBLIC' OR k.ownerId = :userId) AND k.category IS NULL ORDER BY k.uploadedAt DESC")
    List<KnowledgeBaseEntity> findVisibleUncategorizedOrderByUploadedAtDesc(@Param("userId") Long userId);

    @Query("SELECT k FROM KnowledgeBaseEntity k WHERE (k.visibility = 'PUBLIC' OR k.ownerId = :userId) AND (LOWER(k.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(k.originalFilename) LIKE LOWER(CONCAT('%', :keyword, '%'))) ORDER BY k.uploadedAt DESC")
    List<KnowledgeBaseEntity> searchVisibleByKeyword(
        @Param("userId") Long userId, @Param("keyword") String keyword);

    @Query("SELECT DISTINCT k.category FROM KnowledgeBaseEntity k WHERE k.category IS NOT NULL AND (k.visibility = 'PUBLIC' OR k.ownerId = :userId) ORDER BY k.category")
    List<String> findVisibleCategories(@Param("userId") Long userId);

    @Query("SELECT COUNT(k) > 0 FROM KnowledgeBaseEntity k WHERE k.id = :id AND (k.visibility = 'PUBLIC' OR k.ownerId = :userId)")
    boolean isVisibleToUser(@Param("id") Long id, @Param("userId") Long userId);

    // ==================== 可见性范围统计 ====================

    @Query("SELECT COUNT(k) FROM KnowledgeBaseEntity k WHERE k.visibility = 'PUBLIC' OR k.ownerId = :userId")
    long countVisible(@Param("userId") Long userId);

    @Query("SELECT COUNT(k) FROM KnowledgeBaseEntity k WHERE (k.visibility = 'PUBLIC' OR k.ownerId = :userId) AND k.vectorStatus = :status")
    long countVisibleByVectorStatus(@Param("userId") Long userId, @Param("status") VectorStatus status);

    @Query("SELECT COALESCE(SUM(k.accessCount), 0) FROM KnowledgeBaseEntity k WHERE k.visibility = 'PUBLIC' OR k.ownerId = :userId")
    long sumVisibleAccessCount(@Param("userId") Long userId);

    // ==================== 全局统计 ====================

    /**
     * 统计总提问次数
     */
    @Query("SELECT COALESCE(SUM(k.questionCount), 0) FROM KnowledgeBaseEntity k")
    long sumQuestionCount();

    /**
     * 统计总访问次数
     */
    @Query("SELECT COALESCE(SUM(k.accessCount), 0) FROM KnowledgeBaseEntity k")
    long sumAccessCount();

    /**
     * 按向量化状态统计数量
     */
    long countByVectorStatus(VectorStatus vectorStatus);

    // ==================== 批量更新 ====================

    /**
     * 批量增加知识库提问计数
     * @param ids 知识库ID列表
     * @return 更新的行数
     */
    @Modifying
    @Query("UPDATE KnowledgeBaseEntity k SET k.questionCount = k.questionCount + 1 WHERE k.id IN :ids")
    int incrementQuestionCountBatch(@Param("ids") List<Long> ids);
}
