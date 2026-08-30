package com.quantmore.modules.user.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantmore.modules.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("AuthController 测试")
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;

  private String register(String username, String password) throws Exception {
    String body = """
        {"username": "%s", "password": "%s"}
        """.formatted(username, password);
    return mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andReturn().getResponse().getContentAsString();
  }

  private String login(String username, String password) throws Exception {
    String body = """
        {"username": "%s", "password": "%s"}
        """.formatted(username, password);
    String response = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andReturn().getResponse().getContentAsString();
    return objectMapper.readTree(response).path("data").path("token").asText();
  }

  @Nested
  @DisplayName("注册登录全流程")
  @Transactional
  class RegisterLoginFlow {

    @Test
    @DisplayName("注册成功返回用户信息（不含密码字段）")
    void registerReturnsUserInfo() throws Exception {
      String username = "alice_" + System.nanoTime();
      String registered = register(username, "password123");
      JsonNode data = objectMapper.readTree(registered).path("data");
      assertThat(data.path("username").asText()).isEqualTo(username);
      assertThat(data.has("passwordHash")).isFalse();
      assertThat(data.path("role").asText()).isNotBlank();
    }

    @Test
    @DisplayName("登录成功后带 token 访问 /me 返回当前用户")
    void meReturnsCurrentUser() throws Exception {
      String username = "carol_" + System.nanoTime();
      register(username, "password123");
      String token = login(username, "password123");

      mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(200))
          .andExpect(jsonPath("$.data.username").value(username));
    }

    @Test
    @DisplayName("无 token 访问 /me 返回 401")
    void meWithoutTokenIs401() throws Exception {
      mockMvc.perform(get("/api/auth/me"))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("伪造 token 访问 /me 返回 401")
    void meWithInvalidTokenIs401() throws Exception {
      mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-real-token"))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("错误密码登录返回 200 + INVALID_CREDENTIALS 业务码")
    void wrongPasswordReturnsBusinessCode() throws Exception {
      String username = "dave_" + System.nanoTime();
      register(username, "password123");
      String body = """
          {"username": "%s", "password": "wrong"}
          """.formatted(username);

      mockMvc.perform(post("/api/auth/login")
              .contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(12002));
    }
  }

  @Nested
  @DisplayName("安全配置")
  @Transactional
  class Security {

    @Test
    @DisplayName("未认证访问受保护接口返回 401 且响应体为 Result 风格 JSON")
    void protectedEndpointWithoutTokenReturns401WithResultBody() throws Exception {
      mockMvc.perform(get("/api/llm-provider/list"))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/api/auth/** 无需认证即可访问")
    void authEndpointsArePublic() throws Exception {
      mockMvc.perform(post("/api/auth/register")
              .contentType(MediaType.APPLICATION_JSON)
              .content("""
                  {"username": "eve_%s", "password": "password123"}
                  """.formatted(System.nanoTime())))
          .andExpect(status().isOk());
    }
  }
}
