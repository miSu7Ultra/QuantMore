package com.quantmore.modules.user.controller;

import com.quantmore.common.annotation.RateLimit;
import com.quantmore.common.result.Result;
import com.quantmore.modules.user.dto.AuthResponse;
import com.quantmore.modules.user.dto.LoginRequest;
import com.quantmore.modules.user.dto.RegisterRequest;
import com.quantmore.modules.user.dto.UserDTO;
import com.quantmore.modules.user.model.UserPrincipal;
import com.quantmore.modules.user.service.CurrentUserService;
import com.quantmore.modules.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：注册 / 登录 / 当前用户
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final UserService userService;
  private final CurrentUserService currentUserService;

  @PostMapping("/register")
  @RateLimit(dimension = RateLimit.Dimension.IP, count = 10)
  public Result<UserDTO> register(@RequestBody @Valid RegisterRequest request) {
    return Result.success(userService.register(request));
  }

  @PostMapping("/login")
  @RateLimit(dimension = RateLimit.Dimension.IP, count = 10)
  public Result<AuthResponse> login(@RequestBody @Valid LoginRequest request) {
    return Result.success(userService.login(request));
  }

  @GetMapping("/me")
  public Result<UserDTO> me() {
    UserPrincipal principal = currentUserService.get();
    return Result.success(new UserDTO(
        principal.id(),
        principal.username(),
        principal.role(),
        null,
        null
    ));
  }
}
