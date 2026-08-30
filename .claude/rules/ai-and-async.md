---
paths:
  - "app/src/main/java/com/quantmore/common/ai/**/*.java"
  - "app/src/main/java/com/quantmore/common/annotation/**/*.java"
  - "app/src/main/java/com/quantmore/common/aspect/**/*.java"
  - "app/src/main/java/com/quantmore/common/async/**/*.java"
  - "app/src/main/java/com/quantmore/modules/**/listener/**/*.java"
  - "app/src/main/resources/prompts/**/*.st"
  - "app/src/main/resources/scripts/**/*.lua"
---

# AI, Rate Limit And Async Rules

## LLM Providers

- Provider 配置来自 `app.ai.providers.{providerId}` 和默认 `app.ai.default-provider`。
- ChatClient 获取统一使用 `LlmProviderRegistry`；用户场景使用 `getChatClientForUser` / `getDefaultChatClientForUser`。
- Provider 连通性测试要限制超时，并防止访问内网或特殊地址。
- Spring AI 2.0.0 代码优先使用非 deprecated API。

## Prompts

- Prompt 模板存放在 `resources/prompts/`，使用 `{placeholder}` 占位符（Spring AI PromptTemplate）。
- 提示词必须包含反注入指令（`PromptSecurityConstants`）。

## Rate Limit

- 接口限流使用可重复 `@RateLimit` 注解。
- 每个 `@RateLimit` 是独立维度，分别执行 Redis Lua 滑动窗口。
- Redis Key 使用 `ratelimit:{ClassName:MethodName}:dimension` 结构。
- 不要在 Controller 或 Service 中手写散落的限流逻辑。

## Redis Stream

- 生产者继承 `AbstractStreamProducer<T>`。
- 消费者继承 `AbstractStreamConsumer<T>` 并实现 `processMessage()`。
- Stream 名称、消费者组和任务常量放在 `AsyncTaskStreamConstants`。
- 消费失败最多重试 3 次，超过后标记 FAILED。
- 异步处理前校验实体是否存在；已删除实体直接 ACK 丢弃。
