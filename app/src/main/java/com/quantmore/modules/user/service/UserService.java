package com.quantmore.modules.user.service;

import com.quantmore.common.exception.BusinessException;
import com.quantmore.common.exception.ErrorCode;
import com.quantmore.modules.user.config.AuthProperties;
import com.quantmore.modules.user.dto.AuthResponse;
import com.quantmore.modules.user.dto.LoginRequest;
import com.quantmore.modules.user.dto.RegisterRequest;
import com.quantmore.modules.user.dto.UserDTO;
import com.quantmore.modules.user.model.UserEntity;
import com.quantmore.modules.user.model.UserPrincipal;
import com.quantmore.modules.user.model.UserRole;
import com.quantmore.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户注册与登录
 */
@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;
  private final JwtService jwtService;
  private final PasswordEncoder passwordEncoder;
  private final AuthProperties authProperties;

  /**
   * 开放注册；首个注册用户自动成为 ADMIN
   */
  @Transactional
  public UserDTO register(RegisterRequest request) {
    if (!authProperties.isRegistrationEnabled()) {
      throw new BusinessException(ErrorCode.REGISTRATION_DISABLED);
    }
    String username = request.username().trim();
    if (userRepository.existsByUsername(username)) {
      throw new BusinessException(ErrorCode.USERNAME_TAKEN);
    }
    UserEntity entity = new UserEntity();
    entity.setUsername(username);
    entity.setPasswordHash(passwordEncoder.encode(request.password()));
    entity.setRole(userRepository.count() == 0 ? UserRole.ADMIN : UserRole.USER);
    return toDto(userRepository.save(entity));
  }

  /**
   * 登录成功返回 JWT；用户名或密码错误统一返回 INVALID_CREDENTIALS（不泄露用户是否存在）
   */
  @Transactional(readOnly = true)
  public AuthResponse login(LoginRequest request) {
    UserEntity entity = userRepository.findByUsername(request.username().trim())
        .filter(e -> passwordEncoder.matches(request.password(), e.getPasswordHash()))
        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
    String token = jwtService.generateToken(
        new UserPrincipal(entity.getId(), entity.getUsername(), entity.getRole()));
    return new AuthResponse(token, toDto(entity));
  }

  private UserDTO toDto(UserEntity entity) {
    return new UserDTO(
        entity.getId(),
        entity.getUsername(),
        entity.getRole(),
        entity.getDefaultProviderId(),
        entity.getCreatedAt()
    );
  }
}
