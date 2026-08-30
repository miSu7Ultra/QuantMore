package com.quantmore.modules.generator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 策略生成请求
 */
public record GenerateStrategyRequest(
    @NotBlank(message = "策略名称不能为空")
    @Size(max = 100, message = "策略名称过长")
    String strategyName,

    // STOCK / ETF / CONVERTIBLE_BOND / FUTURES / MARGIN
    @NotBlank(message = "交易市场不能为空")
    String market,

    // DAILY / MINUTE / TICK
    @NotBlank(message = "运行频率不能为空")
    String frequency,

    @NotBlank(message = "买入条件不能为空")
    String buyConditions,

    String sellConditions,

    String riskControls,

    // 可选：空 = 全部可见知识库
    List<Long> knowledgeBaseIds,

    // 可选：空 = 用户默认模型
    String providerId
) {
}
