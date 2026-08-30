# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

@AGENTS.md

# Claude Code Instructions

- `AGENTS.md` 是本仓库的共享规则源；更新长期规则时优先改 `AGENTS.md`，不要在这里复制一份。
- 本文件只保留 Claude Code 专属入口、常用命令和「读多个文件才能拼出来的」架构要点，避免根目录上下文膨胀。
- 目录细则在 `.claude/rules/`，处理匹配文件前先读取对应规则。
- 个人偏好和临时调试结论放 `CLAUDE.local.md` 或 Claude Memory，不要提交到仓库。

## Path Rules

- Backend: `.claude/rules/backend.md`
- AI / async / rate limit: `.claude/rules/ai-and-async.md`
- Frontend: `.claude/rules/frontend.md`

## 常用命令

```bash
# 后端(在仓库根目录执行;bootRun 自动加载根目录 .env)
./gradlew :app:compileJava
./gradlew :app:test --no-daemon        # 全量测试;限流集成测试需要本机 6379 有真实 Redis
./gradlew :app:test --tests "com.quantmore.modules.user.*"   # 单个包
./gradlew :app:test --rerun            # 强制重跑(Gradle 有时误判 UP-TO-DATE)
./gradlew :app:bootRun                 # 启动时自动执行知识库种子导入(.env 的 APP_SEED_KB_DIR)

# 前端(在 frontend/ 目录执行;无 lint/test 脚本,build = tsc + vite build)
cd frontend && pnpm run dev
cd frontend && pnpm run build

# 依赖容器
docker compose -f docker-compose.dev.yml up -d
```

端口:后端 8080 · 前端 5173 · PostgreSQL 5433 · Redis 6380 · MinIO 9002(控制台 9003)。

## 架构要点(非显而易见,踩过坑)

- **知识库 = `docs/PTrade量化知识库.md` 单一文件**:`LocalKbSeedRunner` 启动时按 APP_SEED_KB_DIR 导入,按内容哈希幂等;同名文件内容变更会自动删除旧单元重导,向量化 FAILED 会自动重向量化。**更新知识库 = 编辑 docs 文件 + 重启后端**。
- **Provider 配置是 DB 后盾的**:`llm_provider_config` 仅在空库时从 application.yml 种子;启动时 bootstrap 会把「空/占位符」的内置 Key 用当前 env 刷新,但管理员在界面手动设置的 Key 不会被覆盖。改 `.env` 的 Key 后需重启生效。
- **全局 Embedding 唯一**:所有向量化(含用户上传)固定走管理员配置的 embedding(qwen text-embedding-v4,1024 维);pgvector 列固定 1024 维,换模型必须全量重新向量化。`knowledge_bases.chunk_count` 不写回是已知问题,向量行数看 `vector_store` 表。
- **首个注册用户自动成为 ADMIN**,所有用户数据(会话/私有 KB/生成记录)按 owner 过滤,从 `CurrentUserService` 取用户,禁止从请求参数取 userId。
- **数据库 schema 由单一 `V1__init_schema.sql` 管理**(ddl-auto=validate),全新库、无已部署历史;改表直接改 V1 并清库重建,不要新增 V2。
- **Java 25 工具链 + Spring Boot 4.1/Spring AI 2.0 milestone 依赖**,settings.gradle 里的 Aliyun 镜像与 milestone 仓库是构建前提,勿删。
- **前端默认浅色**(深色需手动切换),主题色在 `index.css` 的 `@theme`(cyan 主色);API 统一走 `api/request.ts`(自动带 JWT,401 跳登录),SSE 用 `api/stream.ts` 的 fetch 封装。
- 之前 `git mv`/批量改动后容易出现 zsh 分词、sed 跨目录等问题:cwd 会漂移到 frontend/,执行 Gradle/相对路径前先确认所在目录。

## Maintenance

- 新增规则前先判断：删掉这条后，Claude 是否更容易犯同类错误。
- 能被测试、格式化、Hook 或 CI 强制的规则，不要只写成自然语言。
- 如果同一条规则反复被忽略，优先精简规则文件，而不是继续加粗或加感叹号。
