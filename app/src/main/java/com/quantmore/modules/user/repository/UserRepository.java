package com.quantmore.modules.user.repository;

import com.quantmore.modules.user.model.UserEntity;
import com.quantmore.modules.user.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户 Repository
 */
@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

  Optional<UserEntity> findByUsername(String username);

  boolean existsByUsername(String username);

  Optional<UserEntity> findFirstByRoleOrderByIdAsc(UserRole role);
}
