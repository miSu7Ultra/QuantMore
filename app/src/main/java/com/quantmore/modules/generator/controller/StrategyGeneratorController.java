package com.quantmore.modules.generator.controller;

import com.quantmore.common.annotation.RateLimit;
import com.quantmore.common.result.Result;
import com.quantmore.modules.generator.dto.GenerateStrategyRequest;
import com.quantmore.modules.generator.dto.GenerateStrategyResponse;
import com.quantmore.modules.generator.service.StrategyGeneratorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 策略生成器接口
 */
@RestController
@RequestMapping("/api/strategy")
@RequiredArgsConstructor
public class StrategyGeneratorController {

  private final StrategyGeneratorService generatorService;

  @PostMapping("/generate")
  @RateLimit(dimension = RateLimit.Dimension.IP, count = 5)
  public Result<GenerateStrategyResponse> generate(@RequestBody @Valid GenerateStrategyRequest request) {
    return Result.success(generatorService.generate(request));
  }

  @GetMapping("/history")
  @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 30)
  public Result<List<GenerateStrategyResponse>> history() {
    return Result.success(generatorService.history());
  }

  @GetMapping("/{id}")
  @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 30)
  public Result<GenerateStrategyResponse> getById(@PathVariable Long id) {
    return Result.success(generatorService.getById(id));
  }
}
