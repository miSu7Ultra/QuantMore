package com.quantmore.modules.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

  /** 签名密钥（HMAC-SHA256），生产环境必须通过 APP_JWT_SECRET 覆盖 */
  private String secret = "quantmore-dev-jwt-secret-change-me";

  /** token 有效期（天） */
  private long expirationDays = 7;
}
