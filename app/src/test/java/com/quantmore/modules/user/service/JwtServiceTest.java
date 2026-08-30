package com.quantmore.modules.user.service;

import com.quantmore.modules.user.model.UserPrincipal;
import com.quantmore.modules.user.model.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtService 测试")
class JwtServiceTest {

  private final String secret = "test-secret-for-quantmore-jwt-service-0123456789";

  private JwtService newService(long ttlMillis) {
    return new JwtService(secret, ttlMillis);
  }

  @Nested
  @DisplayName("签发与解析")
  class Roundtrip {

    @Test
    @DisplayName("签发后解析返回相同的主体信息")
    void generateThenParseReturnsSamePrincipal() {
      JwtService service = newService(7L * 24 * 60 * 60 * 1000);
      UserPrincipal principal = new UserPrincipal(42L, "alice", UserRole.ADMIN);

      String token = service.generateToken(principal);
      UserPrincipal parsed = service.parseToken(token);

      assertThat(parsed.id()).isEqualTo(42L);
      assertThat(parsed.username()).isEqualTo("alice");
      assertThat(parsed.role()).isEqualTo(UserRole.ADMIN);
    }
  }

  @Nested
  @DisplayName("无效 token")
  class InvalidToken {

    @Test
    @DisplayName("篡改的 token 解析抛异常")
    void tamperedTokenFails() {
      JwtService service = newService(60_000);
      String token = service.generateToken(new UserPrincipal(1L, "alice", UserRole.USER));

      String tampered = token.substring(0, token.length() - 4) + "abcd";

      assertThatThrownBy(() -> service.parseToken(tampered))
          .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("乱码字符串解析抛异常")
    void garbageTokenFails() {
      JwtService service = newService(60_000);

      assertThatThrownBy(() -> service.parseToken("not-a-jwt"))
          .isInstanceOf(RuntimeException.class);
    }
  }

  @Nested
  @DisplayName("过期 token")
  class ExpiredToken {

    @Test
    @DisplayName("超过有效期的 token 解析抛异常")
    void expiredTokenFails() throws InterruptedException {
      JwtService service = newService(800);
      String token = service.generateToken(new UserPrincipal(1L, "alice", UserRole.USER));

      Thread.sleep(1_200);

      assertThatThrownBy(() -> service.parseToken(token))
          .isInstanceOf(RuntimeException.class);
    }
  }
}
