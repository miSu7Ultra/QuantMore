package com.quantmore.modules.generator.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 策略生成记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "strategy_generations")
public class StrategyGenerationEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "strategy_name", nullable = false, length = 255)
  private String strategyName;

  @Column(length = 32)
  private String market;

  @Column(length = 32)
  private String frequency;

  @Column(name = "buy_conditions", columnDefinition = "TEXT")
  private String buyConditions;

  @Column(name = "sell_conditions", columnDefinition = "TEXT")
  private String sellConditions;

  @Column(name = "risk_controls", columnDefinition = "TEXT")
  private String riskControls;

  @Column(name = "knowledge_base_ids", length = 500)
  private String knowledgeBaseIds;

  @Column(name = "provider_id", length = 64)
  private String providerId;

  @Column(name = "generated_code", nullable = false, columnDefinition = "TEXT")
  private String generatedCode;

  @Column(columnDefinition = "TEXT")
  private String explanation;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now();
  }
}
