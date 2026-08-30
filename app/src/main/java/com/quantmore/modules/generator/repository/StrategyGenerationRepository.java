package com.quantmore.modules.generator.repository;

import com.quantmore.modules.generator.model.StrategyGenerationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 策略生成记录 Repository
 */
@Repository
public interface StrategyGenerationRepository extends JpaRepository<StrategyGenerationEntity, Long> {

  List<StrategyGenerationEntity> findByUserIdOrderByCreatedAtDesc(Long userId);
}
