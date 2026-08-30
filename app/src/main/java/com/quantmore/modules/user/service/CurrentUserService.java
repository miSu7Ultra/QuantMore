package com.quantmore.modules.user.service;

import com.quantmore.common.exception.BusinessException;
import com.quantmore.common.exception.ErrorCode;
import com.quantmore.modules.user.model.UserPrincipal;
import com.quantmore.modules.user.model.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * 从 SecurityContext 解析当前登录用户
 */
@Service
public class CurrentUserService {

  /**
   * 获取当前用户，未登录抛 UNAUTHORIZED
   */
  public UserPrincipal get() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
    return principal;
  }

  /**
   * 获取当前用户并要求 ADMIN 角色，否则抛 FORBIDDEN
   */
  public UserPrincipal requireAdmin() {
    UserPrincipal principal = get();
    if (principal.role() != UserRole.ADMIN) {
      throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理员可执行该操作");
    }
    return principal;
  }
}
