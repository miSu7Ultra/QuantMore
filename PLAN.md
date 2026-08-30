# QuantMore:PTrade 策略代码生成平台实现计划

## Context(背景)

把 `/Users/pengpai/Desktop/QuantMore`(目前是空的 IntelliJ Java 脚手架、git 零提交)改造成「自然语言 → PTrade 策略代码」平台:用户用自然语言描述策略需求,系统结合用户上传的知识库,回答 PTrade 代码(对话式问答 + 策略生成器)。参考 `/Users/pengpai/Desktop/interview-guide`(JavaGuide 作者的 AI 面试官平台:Spring Boot 4.1 + Java 25 + Spring AI 2.0 + PostgreSQL/pgvector + Redis Streams + MinIO + React,已实测可在本机运行)。

**已确认的需求决策:**

| 决策点 | 结论 |
|---|---|
| 技术栈 | 完全复刻 interview-guide(Spring Boot 4.1.0 / Java 25 / Gradle 9.6.1 / Spring AI 2.0.0 / PG16+pgvector / Redis7+Redisson Streams / MinIO / React18+TS+Vite+Tailwind4) |
| 多用户 | 账号注册登录(**interview-guide 没有用户模块,需新建**);Spring Security + JWT(jjwt 0.12.x,HS256);开放注册,**首个注册用户自动成为 ADMIN**;无邮件/审核(仅本机) |
| 多模型 | 每用户独立 API Key(预置 DeepSeek/Qwen/Kimi/GLM + 自定义 OpenAI 兼容端点),AES-GCM 加密(复用 `ApiKeyEncryptionService`);全局内置 provider 模板仍由管理员管理 |
| Embedding | **全局唯一**(管理员配置,仅 1024 维模型;DeepSeek/Kimi 不支持 embedding 已天然排除);已验证:原项目 `EmbeddingModel` bean 就是全局的,无需新设计 |
| 知识库 | 公共(仅管理员上传,全员可见)+ 私有(用户自传,仅自己可见),查询时可多选 KB |
| 产品形态 | ① RAG 对话式问答(SSE 流式,代码块语法高亮);② 策略生成器页(表单 → 完整可运行 .py,复制/下载,历史记录) |
| 部署 | 仅本机 |
| 知识库来源 | 用户自行上传;新增「本地目录导入」种子运行器,可直接导入 `/Users/pengpai/Desktop/ptadeApiScrape/docs/`(60 个中文 MD,3.1MB,含 10 个完整策略示例;排除 docs_qmt) |
| 删除模块 | interview / resume / interviewschedule / voiceinterview 全部删除 |
| 包名/命名 | `interview.guide` → `com.quantmore`;rootProject `QuantMore`;DB/bucket/容器名 `quantmore*` |

## 总体方案

**复制策略**:QuantMore 保留自己的 `.git`(零提交、全新历史),用 rsync 从兄弟目录 interview-guide 拷贝骨架(排除 `.git/.gradle/build/node_modules/.idea/.env/docs` 等),整体搬运后裁剪,而不是从零搭建。保留 AGPL-3.0 LICENSE(衍生作品,个人使用无分发义务,README 注明)。

## 实施阶段

### 阶段 0:骨架搬运(~30 分钟)

1. 删除 QuantMore 脚手架:`src/ build.gradle settings.gradle gradle/ gradlew* .gitignore .idea/*`
2. rsync 拷贝 interview-guide(排除清单见上;注意排除 `.env` —— 含真实 API Key)
3. `cp .env.example .env`,编辑:`APP_AI_CONFIG_ENCRYPTION_KEY`、`APP_JWT_SECRET`、`POSTGRES_PASSWORD=123456`(对齐 dev compose)
4. `git add -A && git commit`(首个提交「import: copy interview-guide base」,作为后续 diff 基线)

### 阶段 1:裁剪 + 重命名 + 构建全绿(风险最高,先做;~1 天)

**1a. 包重命名** `interview.guide` → `com.quantmore`:sed 全局替换 + 目录 mv;`settings.gradle`(rootProject.name)、`app/build.gradle`(group)、`application.yml`(application.name、springdoc packages-to-scan)、`frontend/index.html` 标题「QuantMore - PTrade 策略助手」

**1b. 后端删除**(以 `./gradlew :app:compileJava` 为唯一验收标准,按缺符号逐处清理):
- 整个包:`modules/interview/`、`modules/resume/`、`modules/interviewschedule/`、`modules/voiceinterview/`(含 1484 行 WebSocketHandler)
- knowledgebase 内面试相关:`KnowledgeBaseInterview*`、`KnowledgeBaseQuestion*`、`QuestionGen*`、`QuestionGenerationRecoveryScheduler` 等
- `common/evaluation/`、`common/ai/StructuredOutputInvoker`/`StructuredOutputProperties`/`AgentUtils*`、`infrastructure/mapper/InterviewMapper`/`ResumeMapper`、`infrastructure/export/PdfExportService`、`infrastructure/redis/InterviewSessionCache`
- 资源:`resources/skills/`、`resources/fonts/`、`resources/voice-interview-opening.yml`;prompts 只留 `knowledgebase-query-{system,user,rewrite}.st`
- `application.yml`:删 `app.interview`/`app.resume`/`app.voice-interview`/`config.import`/`agent-utils` 配置段;`config-yaml-path` 默认值改 `~/.quantmore/`
- `build.gradle` 移除依赖:websocket、itext、font-asian、dashscope-sdk、spring-ai-agent-utils、pinyin4j(grep 确认无引用);保留 tika/s3/springdoc/redisson/mapstruct/lombok/testcontainers/h2

**1c. 前端删除**:pages 删 Interview*/Resume*/History/Voice*/Schedule/KnowledgeBaseInterview*;api 删 history/interview/interviewSchedule/resume/skill/voiceInterview;components 删对应目录(FileUploadCard 先 grep 确认 KB 页不依赖);types/utils/hooks 对应删除;`e2e/`+playwright 删除;package.json 移除 onnxruntime-web/react-big-calendar/playwright/wasm 插件(recharts/react-icons/react-virtuoso 先 grep 确认);重写 `App.tsx` 路由与 `Layout.tsx` 导航

**1d. Flyway 整合**:迁移目录清空重写为单一 `V1__init_schema.sql`(基于参考项目 V1 裁剪):保留 `knowledge_bases`(去掉 questionGen 列)、`llm_provider_config`、`llm_global_setting`、`rag_chat_sessions`、`rag_chat_messages`、`rag_session_knowledge_bases`、`vector_store`(HNSW、uuid-ossp+vector 扩展);删 interview/resume/voice/knowledge_base_questions 表及 hstore 扩展。后续新表(users 等)直接并入 V1(全新库,无已部署历史)

**1e. 测试清理**:删除已删模块的测试树,保留并适配 `AppTest`、`common/ai`(去 skills-tool 引用)、`common/aspect`、`infrastructure/file`、`KnowledgeBaseVectorServiceTest`、`modules/llmprovider` 测试;删 `.claude/tdd/*.tdd.md`(4 个面试 spec)

**1f. Docker**:dev compose 的 RustFS 换成 prod compose 的 MinIO + createbuckets 模式(统一 S3 实现);db `quantmore`、bucket `quantmore`、容器名 `quantmore-*`;prod compose 环境变量加 `APP_JWT_SECRET`/`APP_AI_CONFIG_ENCRYPTION_KEY`,删 `APP_INTERVIEW_*`

**验证门**:`./gradlew :app:compileJava` ✓ → `:app:test` ✓(先起 dev compose 的 Redis)→ `pnpm install && pnpm run build` ✓ → `bootRun` 启动 + Flyway V1 应用 ✓

### 阶段 2:用户模块 + Spring Security(TDD;~1 天)

- 先写 `.claude/tdd/user-auth.tdd.md`(注册/登录/me、首用户管理员、401、所有权规则)
- 新建 `modules/user/`:`UserEntity`/`UserRole`/`UserPrincipal`(record)、`RegisterRequest`/`LoginRequest`/`UserDTO`/`AuthResponse`、`UserRepository`、`UserService`(BCrypt;`count()==0 → ADMIN`)、`JwtService`(jjwt,HS256,密钥 env `APP_JWT_SECRET`,7 天过期)、`CurrentUserService`(get/requireAdmin)
- `config/SecurityConfig`(stateless;permitAll `/api/auth/**`、`/v3/api-docs/**`、`/swagger-ui/**`、OPTIONS;其余 authenticated)+ `JwtAuthenticationFilter` + `RestAuthenticationEntryPoint`(401 返回 `Result` 风格 JSON)
- `AuthController`:`POST /api/auth/register`、`POST /api/auth/login`、`GET /api/auth/me`
- `ErrorCode` 增 `USERNAME_TAKEN(12001)`/`INVALID_CREDENTIALS(12002)`,删已删模块的错误码
- 表(并入 V1):`users(id, username 唯一, password_hash, role USER/ADMIN, default_provider_id, created_at, updated_at)`
- 测试:UserServiceTest(注册/首用户管理员/重复名/错误密码)、JwtServiceTest、AuthControllerTest(MockMvc)、SecurityConfigTest(401/200)

### 阶段 3:按用户的模型配置(~1 天)

- 新表(并入 V1):`user_llm_provider_config(user_id, provider_id 联合唯一, base_url, api_key_ciphertext+nonce, model, temperature, enabled)`;`users.default_provider_id` 指向用户默认模型
- `LlmProviderRegistry`:删 skills-tool 接线;增 `getChatClientForUser(userId, providerId)`(缓存 key `user:{userId}:{providerId}`)与 `getDefaultChatClientForUser(userId)`;解析优先级:用户覆盖行(enabled 且有 key)> 全局内置行;未知 provider_id = 用户自定义(须有 base_url+model)
- 新 `modules/llmprovider/` 的 `UserProviderConfigService`(列表合并内置+用户行,maskedKey;CRUD 复用 `ApiKeyEncryptionService`)、`UserProviderController`(`/api/user/providers` + 连通性测试,抽取共享 `ProviderConnectivityTester`)
- **全局 embedding 硬校验**:`updateDefaultEmbeddingProvider` 强制 `embeddingDimensions == 1024`,否则 `BusinessException`(提示与现有向量索引不兼容);切换需全量重新向量化(文档注明)
- 测试:解析优先级单测 + LlmProviderConfigServiceTest 适配

### 阶段 4:知识库归属与可见性(~0.5 天)

- V1 加列:`knowledge_bases.owner_id BIGINT NULL(NULL=公共/管理员)` + `visibility VARCHAR(20) 'PUBLIC'|'PRIVATE'`(默认 PRIVATE)+ 索引;`rag_chat_sessions.user_id NOT NULL` + FK
- `KnowledgeBaseUploadService`:ADMIN 可传 PUBLIC,普通用户强制 PRIVATE;**去重修复**:原 `findByFileHash` 是全局的(私有 KB 哈希撞公共 KB 会泄露他人文档存在性),改为按 owner 范围去重
- `KnowledgeBaseListService` 所有列表/搜索/分类/统计按 `visibility='PUBLIC' OR owner_id=:userId` 过滤(管理员看全部);删除做 owner-or-admin 校验;查询/会话绑定 KB 时校验可见性;`RagChatSessionService` 所有会话操作按 `user_id` 隔离
- 测试:可见性过滤、上传去重范围

### 阶段 5:PTrade 提示词 + 用户级 RAG 对话(~0.5 天)

- 重写 `knowledgebase-query-system.st`(保留文件名减少改动):角色=「PTrade 策略开发专家」;只依据检索内容回答(保留反注入段落与 Markdown 规范);代码必须 python 代码块且严格使用文档中的 PTrade API(生命周期 `initialize/before_trading_start/handle_data`、`set_universe`、`get_history`、`order/order_value/order_target`、`get_position`、全局 `g.` 属性、`log.info`);不确定的 API 不臆造
- `KnowledgeBaseQueryService.getChatClient()` 接收 userId → `getDefaultChatClientForUser`;`RagChatSessionService` 流式回答传入 `session.getUserId()`

### 阶段 6:策略生成器(~1 天)

- TDD spec:`.claude/tdd/strategy-generator.tdd.md`
- 新模块 `modules/generator/`:
  - `GenerateStrategyRequest`(record):strategyName、market(STOCK|ETF|CONVERTIBLE_BOND|FUTURES|MARGIN)、frequency(DAILY|MINUTE|TICK)、buyConditions、sellConditions、riskControls(自由文本)、knowledgeBaseIds(可选,空=全部可见)、providerId(可选)
  - `StrategyGeneratorService`:输入过 `PromptSanitizer` → kbIds 可见性校验 → 检索查询(strategyName+market+frequency+买卖关键词,topK 8,minScore 0.18)经 `KnowledgeBaseVectorService.similaritySearch` → 渲染 `strategy-generator-{system,user}.st` → 生成 → 用 ` ```python ``` ` 围栏拆分说明与代码(无围栏则整体当代码)→ 持久化返回
  - `StrategyGeneratorController`:`POST /api/strategy/generate`(`@RateLimit`)、`GET /api/strategy/history`、`GET /api/strategy/{id}`(owner 校验)
  - 表(并入 V1):`strategy_generations(user_id, strategy_name, market, frequency, buy/sell/risk_controls, knowledge_base_ids, provider_id, generated_code, explanation, created_at)`
  - 提示词 system.st 约束:输出完整可运行 .py(含 import 与全部函数);生命周期函数按文档;`initialize` 中 `set_universe`、设 `g.` 参数;买卖逻辑在 `handle_data`(或按频率用定时函数);风控在交易逻辑前判断;中文注释;只使用检索示例与文档出现过的 API;禁止 `if __name__ == '__main__'`;结构=要点说明+完整代码块
- 测试:kb 可见性拒绝、代码围栏解析、提示词渲染、空检索回退

### 阶段 7:前端(登录/我的模型/生成器页;~1.5 天)

- **Auth**:`api/auth.ts`、`AuthContext`(localStorage `qm_token`/`qm_user`,useAuth)、`request.ts` 拦截器加 `Authorization: Bearer`(401 → 清存储跳 `/login`)、ragChat 流式请求带头、`LoginPage`/`RegisterPage`(居中卡片、中文)、`App.tsx` 加 `<RequireAuth>` 包裹 Layout 子树、Layout 底部用户名+退出
- **我的模型页** `UserProviderPage`:内置 provider 列表(掩码 key)+ 覆盖/添加 key/model、自定义 OpenAI 兼容 provider、连通性测试;`SettingsPage` 去掉 ASR/TTS/语音段,管理员可见全局内置管理 + **默认 Embedding 选择器**(1024 维约束提示 + 切换需重新向量化警告)
- **生成器页** `StrategyGeneratorPage`:左侧表单(名称/市场/频率/买入/卖出/风控/KB 多选/模型选择),右侧结果(说明 markdown + `react-syntax-highlighter` 代码高亮 + 复制/下载 .py 按钮)+ 历史抽屉
- KB 上传/管理页:加 visibility 单选(仅管理员可见)与 owner/visibility 徽标
- 导航:「知识库」(管理/上传/问答)、「策略」(生成器/历史)、「系统」(我的模型/管理员设置)

### 阶段 8:种子导入 + 文档 + 终验(~0.5 天)

- **种子运行器** `LocalKbSeedRunner`(CommandLineRunner,env `APP_SEED_KB_DIR` 激活):递归收集 `*.md`(跳过 assets/),以 PUBLIC 调用上传服务(要求管理员存在,否则快速失败),靠文件哈希幂等;向量化走既有 Redis Stream 消费者自动完成。用法:
  `APP_SEED_KB_DIR=/Users/pengpai/Desktop/ptadeApiScrape/docs ./gradlew :app:bootRun`
- 文档:`AGENTS.md` 改写为 QuantMore 规则(加所有权校验/JWT/admin-only 规则)、`CLAUDE.md`/`.claude/rules/*` 微调、README 与 SETUP_API_KEYS 重写(四个国内模型 + 自定义)、`.githooks` 保留

## 端到端验证清单

1. `docker compose -f docker-compose.dev.yml up -d` → postgres/redis/minio 健康,`quantmore` bucket 自动创建
2. bootRun 启动,Flyway V1 应用(flyway_schema_history = 1 行)
3. curl:注册第一个用户 → role=ADMIN;第二个 → USER;无 token 请求 401
4. 管理员配全局 provider key + 默认 Embedding(试 1536 维 → 被拒)
5. `APP_SEED_KB_DIR` 种子导入 → ~60 个 PUBLIC KB,轮询至 vector_status=COMPLETED
6. 前端:注册/登录/错密码/刷新保持/退出/未登录跳转
7. 用户 B:知识库管理只见 PUBLIC;传私有 KB 仅自己可见(管理员可见全部)
8. 问答:「PTrade 中如何用 handle_data 写双均线策略」→ 流式回答含 python 代码块、有据可查、无臆造 API
9. 生成器:双均线示例表单 → 完整 .py(initialize/handle_data/set_universe/get_history/order_value)+ 复制/下载 + 历史记录
10. B 在「我的模型」换自己的 DeepSeek key → 对话走 B 的 key;B 改不了全局 embedding(admin-only)
11. `./gradlew :app:test` 与 `pnpm run build` 全绿

## 主要风险

- **版本激进**:Spring Boot 4.1 / Spring AI 2.0 为 milestone,依赖解析靠 settings.gradle 已配的 Aliyun 镜像 + milestone 仓库(勿删);JDK 25 工具链来自参考项目(本机已验证)
- **裁剪编译**:模块间引用多,删完以 compileJava 为准逐处清理;`ddl-auto: validate` 要求 V1 与实体严格一致
- **pgvector 维度**:固定 1024,全局 embedding 只能选 1024 维模型(DashScope text-embedding-v3 / GLM embedding-3)
- **AGPL-3.0**:个人本机使用无义务;若日后公开分发需开源(README 注明)
