package com.quantmore.modules.llmprovider.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户级 LLM Provider 配置：覆盖全局内置 Provider 的 Key/模型，或完全自定义的 OpenAI 兼容端点。
 * 未配置的字段回退到全局内置 Provider 的同名字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_llm_provider_config", uniqueConstraints = {
    @UniqueConstraint(name = "uk_user_provider", columnNames = {"user_id", "provider_id"})
})
public class UserProviderConfigEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "provider_id", nullable = false, length = 64)
  private String providerId;

  @Column(name = "base_url", length = 512)
  private String baseUrl;

  @Column(name = "api_key_ciphertext", nullable = false, length = 4096)
  private String apiKeyCiphertext;

  @Column(name = "api_key_nonce", nullable = false, length = 64)
  private String apiKeyNonce;

  @Column(length = 128)
  private String model;

  private Double temperature;

  @Column(nullable = false)
  private boolean enabled = true;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  void prePersist() {
    LocalDateTime now = LocalDateTime.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
