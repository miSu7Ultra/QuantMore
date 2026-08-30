package com.quantmore.modules.user.model;

/**
 * SecurityContext 中的当前用户主体（JWT 解析结果）
 */
public record UserPrincipal(
    Long id,
    String username,
    UserRole role
) {
}
