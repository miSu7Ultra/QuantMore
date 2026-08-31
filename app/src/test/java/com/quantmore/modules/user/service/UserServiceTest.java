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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService 测试")
class UserServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private JwtService jwtService;

  private AuthProperties authProperties;
  private UserService userService;

  @BeforeEach
  void setUp() {
    authProperties = new AuthProperties();
    userService = new UserService(userRepository, jwtService, new BCryptPasswordEncoder(),
        authProperties);
  }

  private UserEntity savedUser(Long id, String username, UserRole role) {
    UserEntity entity = new UserEntity();
    entity.setId(id);
    entity.setUsername(username);
    entity.setRole(role);
    return entity;
  }

  @Nested
  @DisplayName("注册")
  class Register {

    @Test
    @DisplayName("首个注册用户自动成为 ADMIN")
    void firstUserBecomesAdmin() {
      when(userRepository.count()).thenReturn(0L);
      when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> {
        UserEntity e = inv.getArgument(0);
        e.setId(1L);
        return e;
      });

      UserDTO dto = userService.register(new RegisterRequest("alice", "password123"));

      assertThat(dto.id()).isEqualTo(1L);
      assertThat(dto.username()).isEqualTo("alice");
      assertThat(dto.role()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    @DisplayName("非首个注册用户为 USER")
    void subsequentUserIsUser() {
      when(userRepository.count()).thenReturn(1L);
      when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> {
        UserEntity e = inv.getArgument(0);
        e.setId(2L);
        return e;
      });

      UserDTO dto = userService.register(new RegisterRequest("bob", "password123"));

      assertThat(dto.role()).isEqualTo(UserRole.USER);
    }

    @Test
    @DisplayName("密码以 BCrypt 哈希存储，不存明文")
    void passwordStoredHashed() {
      when(userRepository.count()).thenReturn(0L);
      when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> {
        UserEntity e = inv.getArgument(0);
        e.setId(1L);
        return e;
      });

      userService.register(new RegisterRequest("alice", "password123"));

      verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(
          e -> e.getPasswordHash() != null
              && !"password123".equals(e.getPasswordHash())
              && e.getPasswordHash().startsWith("$2")));
    }

    @Test
    @DisplayName("重复用户名抛 USERNAME_TAKEN")
    void duplicateUsernameFails() {
      when(userRepository.existsByUsername("alice")).thenReturn(true);

      assertThatThrownBy(() -> userService.register(new RegisterRequest("alice", "password123")))
          .isInstanceOf(BusinessException.class)
          .satisfies(e -> assertThat(((BusinessException) e).getCode())
              .isEqualTo(ErrorCode.USERNAME_TAKEN.getCode()));
      verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("注册关闭时抛 REGISTRATION_DISABLED 且不写库")
    void registrationDisabledThrows() {
      authProperties.setRegistrationEnabled(false);

      assertThatThrownBy(() -> userService.register(new RegisterRequest("alice", "password123")))
          .isInstanceOf(BusinessException.class)
          .satisfies(e -> assertThat(((BusinessException) e).getCode())
              .isEqualTo(ErrorCode.REGISTRATION_DISABLED.getCode()));
      verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("注册开启时正常注册")
    void registrationEnabledRegistersNormally() {
      authProperties.setRegistrationEnabled(true);
      when(userRepository.count()).thenReturn(0L);
      when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> {
        UserEntity e = inv.getArgument(0);
        e.setId(1L);
        return e;
      });

      UserDTO dto = userService.register(new RegisterRequest("alice", "password123"));

      assertThat(dto.id()).isEqualTo(1L);
      assertThat(dto.username()).isEqualTo("alice");
      assertThat(dto.role()).isEqualTo(UserRole.ADMIN);
    }
  }

  @Nested
  @DisplayName("登录")
  class Login {

    @Test
    @DisplayName("用户名密码正确返回 token 与用户信息")
    void loginSuccessReturnsToken() {
      UserEntity entity = savedUser(1L, "alice", UserRole.ADMIN);
      entity.setPasswordHash(new BCryptPasswordEncoder().encode("password123"));
      when(userRepository.findByUsername("alice")).thenReturn(Optional.of(entity));
      when(jwtService.generateToken(any(UserPrincipal.class))).thenReturn("jwt-token");

      AuthResponse response = userService.login(new LoginRequest("alice", "password123"));

      assertThat(response.token()).isEqualTo("jwt-token");
      assertThat(response.user().username()).isEqualTo("alice");
    }

    @Test
    @DisplayName("密码错误抛 INVALID_CREDENTIALS")
    void wrongPasswordFails() {
      UserEntity entity = savedUser(1L, "alice", UserRole.USER);
      entity.setPasswordHash(new BCryptPasswordEncoder().encode("password123"));
      when(userRepository.findByUsername("alice")).thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> userService.login(new LoginRequest("alice", "wrong")))
          .isInstanceOf(BusinessException.class)
          .satisfies(e -> assertThat(((BusinessException) e).getCode())
              .isEqualTo(ErrorCode.INVALID_CREDENTIALS.getCode()));
      verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("用户不存在抛 INVALID_CREDENTIALS（不泄露用户是否存在）")
    void unknownUserFails() {
      when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

      assertThatThrownBy(() -> userService.login(new LoginRequest("nobody", "whatever")))
          .isInstanceOf(BusinessException.class)
          .satisfies(e -> assertThat(((BusinessException) e).getCode())
              .isEqualTo(ErrorCode.INVALID_CREDENTIALS.getCode()));
      verify(jwtService, never()).generateToken(any());
    }
  }
}
