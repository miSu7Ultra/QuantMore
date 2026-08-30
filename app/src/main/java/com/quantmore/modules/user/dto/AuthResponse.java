package com.quantmore.modules.user.dto;

/**
 * 登录响应：JWT token + 用户信息
 */
public record AuthResponse(
    String token,
    UserDTO user
) {
}
