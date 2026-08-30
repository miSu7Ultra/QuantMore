package com.quantmore.modules.llmprovider.controller;

import com.quantmore.common.annotation.RateLimit;
import com.quantmore.common.result.Result;
import com.quantmore.modules.llmprovider.dto.DefaultProviderDTO;
import com.quantmore.modules.llmprovider.dto.ProviderTestResult;
import com.quantmore.modules.llmprovider.dto.UserProviderDTO;
import com.quantmore.modules.llmprovider.dto.UserProviderUpsertRequest;
import com.quantmore.modules.llmprovider.service.UserProviderConfigService;
import com.quantmore.modules.user.service.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户级模型配置接口（每个用户管理自己的 API Key 与默认模型）
 */
@RestController
@RequestMapping("/api/user/providers")
@RequiredArgsConstructor
public class UserProviderController {

  private final UserProviderConfigService userProviderConfigService;
  private final CurrentUserService currentUserService;

  @GetMapping
  @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 30)
  public Result<List<UserProviderDTO>> list() {
    return Result.success(userProviderConfigService.list(currentUserService.get().id()));
  }

  @PutMapping("/{providerId}")
  @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 10)
  public Result<Void> upsert(
      @PathVariable String providerId,
      @RequestBody @Valid UserProviderUpsertRequest request) {
    userProviderConfigService.upsert(currentUserService.get().id(), providerId, request);
    return Result.success();
  }

  @DeleteMapping("/{providerId}")
  @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 10)
  public Result<Void> delete(@PathVariable String providerId) {
    userProviderConfigService.delete(currentUserService.get().id(), providerId);
    return Result.success();
  }

  @PostMapping("/{providerId}/test")
  @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 10)
  public Result<ProviderTestResult> test(@PathVariable String providerId) {
    return Result.success(userProviderConfigService.test(currentUserService.get().id(), providerId));
  }

  @GetMapping("/default")
  @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 30)
  public Result<DefaultProviderDTO> getDefault() {
    return Result.success(new DefaultProviderDTO(
        userProviderConfigService.getDefaultProviderId(currentUserService.get().id())));
  }

  @PutMapping("/default")
  @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 10)
  public Result<Void> setDefault(@RequestBody DefaultProviderDTO request) {
    userProviderConfigService.setDefaultProvider(
        currentUserService.get().id(), request.defaultProvider());
    return Result.success();
  }
}
