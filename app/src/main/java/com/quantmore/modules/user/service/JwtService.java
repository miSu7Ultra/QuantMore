package com.quantmore.modules.user.service;

import com.quantmore.modules.user.config.JwtProperties;
import com.quantmore.modules.user.model.UserPrincipal;
import com.quantmore.modules.user.model.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与解析（HS256）
 */
@Service
@Slf4j
public class JwtService {

  public static final String CLAIM_USERNAME = "username";
  public static final String CLAIM_ROLE = "role";

  private final SecretKey key;
  private final long ttlMillis;

  @org.springframework.beans.factory.annotation.Autowired
  public JwtService(JwtProperties properties) {
    this(properties.getSecret(), properties.getExpirationDays() * 24 * 60 * 60 * 1000);
    if (properties.getSecret().startsWith("quantmore-dev-jwt-secret")) {
      log.warn("JWT 密钥使用开发默认值，生产环境请通过 APP_JWT_SECRET 设置随机密钥");
    }
  }

  public JwtService(String secret, long ttlMillis) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.ttlMillis = ttlMillis;
  }

  /**
   * 为用户签发 token
   */
  public String generateToken(UserPrincipal principal) {
    Date now = new Date();
    return Jwts.builder()
        .subject(String.valueOf(principal.id()))
        .claim(CLAIM_USERNAME, principal.username())
        .claim(CLAIM_ROLE, principal.role().name())
        .issuedAt(now)
        .expiration(new Date(now.getTime() + ttlMillis))
        .signWith(key)
        .compact();
  }

  /**
   * 解析并校验 token，非法/过期抛 {@link JwtException}
   */
  public UserPrincipal parseToken(String token) {
    Claims claims = Jwts.parser()
        .verifyWith(key)
        .build()
        .parseSignedClaims(token)
        .getPayload();
    return new UserPrincipal(
        Long.parseLong(claims.getSubject()),
        claims.get(CLAIM_USERNAME, String.class),
        UserRole.valueOf(claims.get(CLAIM_ROLE, String.class))
    );
  }
}
