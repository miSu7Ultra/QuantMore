package com.quantmore.modules.user.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 认证配置
 */
@Component
@ConfigurationProperties(prefix = "app.auth")
@Getter
@Setter
public class AuthProperties {

  /** 是否开放注册（生产环境创建首个 ADMIN 后可通过 APP_REGISTRATION_ENABLED=false 关闭） */
  private boolean registrationEnabled = true;
}
