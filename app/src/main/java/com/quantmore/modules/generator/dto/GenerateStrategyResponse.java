package com.quantmore.modules.generator.dto;

import java.time.LocalDateTime;

/**
 * 策略生成响应
 */
public record GenerateStrategyResponse(
    Long id,
    String strategyName,
    String fileName,
    String market,
    String frequency,
    String code,
    String explanation,
    String providerId,
    LocalDateTime createdAt
) {
}
