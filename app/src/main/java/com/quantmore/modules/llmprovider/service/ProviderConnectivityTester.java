package com.quantmore.modules.llmprovider.service;

import com.quantmore.common.ai.ApiPathResolver;
import com.quantmore.modules.llmprovider.dto.ProviderTestResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.InetAddressFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * LLM Provider 连通性测试（全局与用户级配置共用）。
 * SSRF 防护：仅允许外部地址 / 回环地址 / 198.18.0.0/15 测试网段。
 */
@Component
@Slf4j
public class ProviderConnectivityTester {

  /**
   * 向 {baseUrl}/chat/completions 发送最小请求验证连通性
   */
  public ProviderTestResult test(String id, String baseUrl, String apiKey, String model) {
    try {
      HttpClientSettings settings = HttpClientSettings.defaults()
          .withConnectTimeout(Duration.ofSeconds(5))
          .withReadTimeout(Duration.ofSeconds(10))
          .withInetAddressFilter(
              InetAddressFilter.externalAddresses()
                  .or(InetAddressFilter.adapt(InetAddress::isLoopbackAddress))
                  .or("198.18.0.0/15"));

      RestClient restClient = RestClient.builder()
          .defaultHeader("Authorization", "Bearer " + apiKey)
          .requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(settings))
          .build();

      Map<String, Object> requestBody = buildConnectivityTestRequestBody(model);
      List<String> candidateUrls = buildConnectivityTestUrls(baseUrl);
      String lastFailureMessage = "Unknown error";

      for (String targetUrl : candidateUrls) {
        try {
          restClient.post()
              .uri(URI.create(targetUrl))
              .body(requestBody)
              .retrieve()
              .toEntity(String.class);
          log.info("Provider connectivity test succeeded: providerId={}, baseUrl={}, targetUrl={}, model={}",
              id, baseUrl, targetUrl, model);
          return ProviderTestResult.builder()
              .success(true)
              .message("连接成功")
              .model(model)
              .build();
        } catch (RestClientResponseException e) {
          String responseBody = abbreviate(e.getResponseBodyAsString());
          lastFailureMessage = String.format(
              "HTTP %s on %s, body=%s",
              e.getStatusCode().value(),
              targetUrl,
              responseBody
          );
          log.warn(
              "Provider connectivity test failed with response: providerId={}, baseUrl={}, targetUrl={}, model={}, status={}, body={}",
              id,
              baseUrl,
              targetUrl,
              model,
              e.getStatusCode().value(),
              responseBody,
              e
          );
        } catch (Exception e) {
          lastFailureMessage = String.format(
              "%s on %s: %s",
              e.getClass().getSimpleName(),
              targetUrl,
              e.getMessage()
          );
          log.warn(
              "Provider connectivity test failed: providerId={}, baseUrl={}, targetUrl={}, model={}, error={}",
              id,
              baseUrl,
              targetUrl,
              model,
              e.getMessage(),
              e
          );
        }
      }
      return ProviderTestResult.builder()
          .success(false)
          .message("连接失败: " + lastFailureMessage)
          .model(model)
          .build();
    } catch (Exception e) {
      log.warn("Provider connectivity test setup failed: providerId={}, baseUrl={}, model={}, error={}",
          id, baseUrl, model, e.getMessage(), e);
      return ProviderTestResult.builder()
          .success(false)
          .message("连接失败: " + e.getMessage())
          .model(model)
          .build();
    }
  }

  List<String> buildConnectivityTestUrls(String baseUrl) {
    String normalizedBaseUrl = ApiPathResolver.stripTrailingSlashes(baseUrl);
    LinkedHashSet<String> candidateUrls = new LinkedHashSet<>();

    candidateUrls.add(normalizedBaseUrl + "/chat/completions");
    if (!ApiPathResolver.baseUrlContainsVersion(normalizedBaseUrl)) {
      candidateUrls.add(normalizedBaseUrl + "/v1/chat/completions");
    }

    return List.copyOf(candidateUrls);
  }

  Map<String, Object> buildConnectivityTestRequestBody(String model) {
    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("model", model);
    requestBody.put("messages", List.of(Map.of(
        "role", "user",
        "content", "Reply with OK only."
    )));
    requestBody.put("max_tokens", 1);
    return requestBody;
  }

  private String abbreviate(String text) {
    if (text == null || text.isBlank()) {
      return "[no body]";
    }
    String normalized = text.replaceAll("\\s+", " ").trim();
    if (normalized.length() <= 200) {
      return normalized;
    }
    return normalized.substring(0, 200) + "...";
  }
}
