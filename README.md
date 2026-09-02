# QuantMore · 量化策略代码生成平台

支持多模态量化指导，让每个不懂代码的人都能参与量化；输入自然语言，产出傻瓜式可运行的量化策略代码，代码成功率远超豆包，元宝等大模型。

## 界面截图

| 登录 | 量化知识库管理 |
|---|---|
| ![登录](screenshots/1-login.png) | ![量化知识库管理](screenshots/2-knowledgebase.png) |

| 问答助手 | 策略生成器 |
|---|---|
| ![问答助手](screenshots/3-chat.png) | ![策略生成器](screenshots/4-generator.png) |

## 功能

- **量化知识库 + 问答**：严格根据量化知识库，对于量化相关问题，图文并茂回复，让用户便于理解与操作
- **策略生成器**：表单填写策略需求（名称/市场/频率/买卖条件/风控）→ 多平台策略代码生成 → 生成完整可运行的 `.py` 策略文件（可复制/下载，带历史记录）
- **多模型**：预置通义阿里百炼 / DeepSeek / Kimi(Moonshot) / 智谱 GLM，支持自定义 OpenAI 兼容端点；
- 
## TODO
- [ ] **数据回测**：接入行情数据，对生成的策略做历史数据回测，输出收益曲线与绩效指标
- [ ] **手机验证码注册登录**：支持短信验证码注册 / 登录（含发送频率限制与验证码有效期）

## 技术栈

Spring Boot 4.1 / Java 25 / Spring AI 2.0 / PostgreSQL 16 + pgvector / Redis 7 + Redisson Streams / MinIO / React 18 + TypeScript + Vite + Tailwind 4

## 本地运行

前置：JDK 25、Node 18+、pnpm 10+、Docker

```bash
# 1. 配置
cp .env.example .env
# 编辑 .env：设置 APP_AI_CONFIG_ENCRYPTION_KEY、APP_JWT_SECRET（已随机生成可直接用）、
#           AI_BAILIAN_API_KEY（通义千问/embedding）、各 PROVIDER_*_API_KEY（可选）

# 2. 启动依赖（PostgreSQL 5433 / Redis 6380 / MinIO 9002，避开常用端口）
docker compose -f docker-compose.dev.yml up -d

# 3. 启动后端
./gradlew :app:bootRun          # http://localhost:8080，Swagger: /swagger-ui.html

# 4. 启动前端
cd frontend && pnpm install && pnpm dev   # http://localhost:5173
```

### 量化知识库导入（自动）

`.env` 中 `APP_SEED_KB_DIR` 指向本地文档目录。每次启动后端自动执行种子导入：
- **按子目录切割**：每个顶层子目录 = 一个知识库单元（目录内 md 合并），根目录 md 文件各自成库（跳过 `assets/`）
- **幂等**：按内容哈希去重，重复启动不会重复导入
- **自愈**：已存在但向量化失败的单元会自动重新向量化（配好 API Key 后重启即可恢复）

## 生产部署

单机 Docker Compose 一键部署：生产编排 `docker-compose.yml` 已内置 fail-fast 密钥校验、健康检查与日志轮转，仅暴露 80 端口。完整手册（服务器初始化、密钥生成、首次部署两阶段、备份恢复、故障排查、schema 变更规范）见 [ops/production-deployment.md](ops/production-deployment.md)。

```bash
cp .env.example .env    # 按「必需配置」段与末尾「生产部署」段生成并填入全部密钥
docker compose up -d --build
```

## 使用流程

1. 启动后端自动导入量化知识库并向量化；若导入时 Key 未配好，配好后重启自动重新向量化
2. 在「我的模型」配置自己的 API Key（或用全局内置 Key），选择默认模型
3. 在「问答助手」创建会话，用自然语言提问
4. 在「策略生成器」填表生成完整策略文件

## 关键设计

- **全局 Embedding**：所有向量化统一使用管理员配置的 embedding 服务
- **模型解析优先级**：用户配置行（有 Key 且启用）> 全局内置行 > 用户默认模型 > 全局默认模型
- **API Key 安全**：AES-GCM 加密落库，密钥来自 `APP_AI_CONFIG_ENCRYPTION_KEY`；密码 BCrypt；JWT HS256 无状态鉴权
- **异步向量化**：Redis Stream 生产者/消费者，失败自动重试 3 次，状态可轮询

## 测试

```bash
./gradlew :app:test --no-daemon     # 后端
cd frontend && pnpm run build       # 前端
```

## 许可证

AGPL-3.0
