package com.quantmore.modules.llmprovider.repository;

import com.quantmore.modules.llmprovider.model.UserProviderConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 用户级 Provider 配置 Repository
 */
@Repository
public interface UserProviderRepository extends JpaRepository<UserProviderConfigEntity, Long> {

  List<UserProviderConfigEntity> findByUserId(Long userId);

  Optional<UserProviderConfigEntity> findByUserIdAndProviderId(Long userId, String providerId);

  void deleteByUserIdAndProviderId(Long userId, String providerId);
}
