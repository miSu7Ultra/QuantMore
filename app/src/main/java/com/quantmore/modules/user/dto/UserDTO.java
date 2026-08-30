package com.quantmore.modules.user.dto;

import com.quantmore.modules.user.model.UserRole;

import java.time.LocalDateTime;

/**
 * 用户信息（对外，永不包含密码）
 */
public record UserDTO(
    Long id,
    String username,
    UserRole role,
    String defaultProviderId,
    LocalDateTime createdAt
) {
}
