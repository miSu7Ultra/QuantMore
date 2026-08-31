package com.quantmore.modules.generator.eval;

/**
 * 评测用例（一条策略需求描述）
 */
public record EvalCase(
    String id,
    String name,
    String market,
    String frequency,
    String buyConditions,
    String sellConditions,
    String riskControls,
    String difficulty
) {
}
