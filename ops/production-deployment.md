# QuantMore 生产部署手册（单机 Docker Compose）

适用：一台 Linux 服务器（Ubuntu 22.04/24.04 等），Docker + Compose 插件，全部服务容器化部署。

配套文件：

| 文件 | 用途 |
|---|---|
| `docker-compose.yml` | 生产编排（fail-fast 密钥校验、健康检查、日志轮转、仅暴露 80 端口） |
| `.env.example` | 密钥模板（末尾「生产部署」段与 compose 对齐） |
| `scripts/deploy.sh` | 升级/首装脚本（git pull + 重建镜像 + 重启） |
| `scripts/backup.sh` | 每日备份脚本（数据库 + 配置卷 + MinIO 数据卷，含保留策略） |

## 架构速览

五个长驻容器：`quantmore-postgres`（pgvector/pgvector:pg16）、`quantmore-redis`、`quantmore-minio`、`quantmore-app`（Spring Boot）、`quantmore-frontend`（Nginx）。

- 唯一公网入口是 frontend 的 **80 端口**，Nginx 把 `/api/` 反向代理到 app 容器 8080；postgres/redis/minio 均不发布端口。
- MinIO 控制台只绑定 `127.0.0.1:9003`，需 SSH 隧道访问（见第 8 节）。
- 数据卷（实际名称为 compose 固定项目名 `quantmore` + 定义键前缀）：`quantmore_postgres_data`、`quantmore_redis_data`、`quantmore_minio_data`、`quantmore_app_quantmore_data`（容器内 `/root/.quantmore`，存 Provider 运行配置）、`quantmore_app_logs`（容器内 `/app/logs`）。
- 种子知识库目录 `./docs` 只读挂载到 app 容器 `/seed-docs`（镜像构建时已排除 docs，必须走挂载）。

## 1. 服务器初始化

```bash
# 1) 安装 Docker（含 compose 插件；官方源或官方脚本二选一）
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER   # 重新登录后生效，避免处处 sudo
docker compose version          # 验证 compose 插件可用

# 2) 防火墙：只放行 SSH 与 Web，其余一律拒绝
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw enable

# 3) SSH 加固：禁止密码登录、禁止 root 登录
#    /etc/ssh/sshd_config 中确认：
#      PasswordAuthentication no
#      PermitRootLogin no
sudo systemctl restart ssh

# 4) 拉取代码到 /opt/quantmore（<仓库地址> 换成实际 Git 远程地址）
sudo mkdir -p /opt/quantmore
sudo chown $USER /opt/quantmore
git clone <仓库地址> /opt/quantmore
cd /opt/quantmore
git checkout main
```

## 2. 密钥生成

生成全部密钥并写入 `.env`（模板见 `.env.example`：「生产部署」段在文件末尾，`APP_JWT_SECRET` / `APP_AI_CONFIG_ENCRYPTION_KEY` / `AI_BAILIAN_API_KEY` 在文件上方「必需配置」段；开发段的 localhost/5433 等值不影响生产——compose 已在 app 容器 environment 中固定内部服务名 `POSTGRES_HOST: postgres` 等，宿主机 .env 里的开发值不会传入生产容器）。

```bash
cd /opt/quantmore
cp .env.example .env
chmod 600 .env        # 内含数据库密码与各类密钥，必须收紧权限
```

| 变量 | 生成/获取方式 | 是否可变 |
|---|---|---|
| `POSTGRES_PASSWORD` | `openssl rand -hex 24` | 不可变（仅数据库卷首次初始化生效；确需修改见第 6 节） |
| `MINIO_ROOT_USER` | `openssl rand -hex 24` | 不可变（仅 MinIO 数据卷首次初始化生效） |
| `MINIO_ROOT_PASSWORD` | `openssl rand -hex 24` | 不可变（同上；后续可在 MinIO 控制台另行修改） |
| `APP_JWT_SECRET` | `openssl rand -hex 32` | 可变；改动后所有已登录用户需重新登录 |
| `APP_AI_CONFIG_ENCRYPTION_KEY` | `openssl rand -base64 32` | **★绝不可更改**（见下方警示） |
| `AI_BAILIAN_API_KEY` | 阿里云百炼控制台创建 | 可变 |
| `APP_REGISTRATION_ENABLED` | 无需生成，取 `true`/`false` | 可变 |
| `CORS_ALLOWED_ORIGINS` | 无需生成，可选 | 可变 |

> ⚠️ <span style="color:red">**APP_AI_CONFIG_ENCRYPTION_KEY 生成后绝不可更改。**</span>
> 数据库中所有 Provider API Key 均以 AES-GCM 用该密钥加密存储。更换密钥后，全部已加密的密文
> **永久无法解密**，所有用户必须重新录入各自的 API Key 才能恢复功能。请把该值连同生成命令一起
> 离线备份到密码管理器等安全位置。

说明：`POSTGRES_PASSWORD`、`MINIO_ROOT_USER`、`MINIO_ROOT_PASSWORD` 在 compose 中使用 `:?` 必填校验，留空时 `docker compose` 直接报错退出，避免弱口令上线。`APP_AI_CONFIG_ENCRYPTION_KEY`、`APP_JWT_SECRET` 虽也有同样的校验，但 `.env` 模板自带 `change_me` 占位值、不会被 compose 拦截，**必须手工替换为随机生成值后才能对外使用**（这两项定义在上方「必需配置」段，生成命令见该段注释）。

## 3. 首次部署（两阶段）

### 阶段一：拉起服务 + 注册管理员

```bash
cd /opt/quantmore
# .env 中：第 2 节全部密钥已就位；生产段的 APP_SEED_KB_DIR 保持注释（开发段的 Mac 路径在容器内不存在，会自动告警跳过）；
#          APP_REGISTRATION_ENABLED=true
docker compose up -d --build
```

首次启动会：初始化 postgres 卷（`docker/postgres/init.sql` 创建 vector 扩展）→ Flyway 执行 `V1__init_schema.sql` 建表 → createbuckets 任务创建 MinIO 桶 → app 健康后 frontend 启动。

> 阶段一日志中若出现「系统中没有管理员用户，跳过种子导入」，是**正常现象**（种子导入要求先有管理员），
> 不是故障。另外开发段默认的 `APP_SEED_KB_DIR` 指向开发者本机路径，容器内不存在，同样只是告警跳过，无碍。

### 阶段二：注册首个管理员 → 开启种子导入

1. 浏览器打开 `http://<服务器IP>`，注册第一个账号——**首个注册用户自动成为 ADMIN**。
2. 编辑 `.env`：取消注释 `APP_SEED_KB_DIR=/seed-docs`，并把 `APP_REGISTRATION_ENABLED` 改为 `false`。
3. `docker compose up -d` 重启（app 容器以新环境变量重建）。
4. `docker logs -f quantmore-app` 观察「开始种子导入」，等待出现「种子导入汇总」（向量化为异步任务，稍候片刻）。

验证清单（逐项确认后再对外使用）：

| 检查项 | 命令/操作 | 预期 |
|---|---|---|
| 知识库出现 | 登录后打开「知识库管理」 | 出现种子知识库单元且向量化完成 |
| 注册已关闭 | `curl -s -X POST http://localhost/api/auth/register -H 'Content-Type: application/json' -d '{"username":"testuser","password":"test123456"}'` | 返回 `code: 12003`「注册已关闭」 |
| Swagger 已关闭 | `docker exec quantmore-app curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/swagger-ui.html` | `404`（prod profile 关闭了接口文档） |
| 服务健康 | `docker compose ps` | postgres/redis/minio/app 为 healthy；frontend 未配健康检查，显示 running 属正常 |
| 端口最小暴露 | `ss -tlnp` | 公网只有 80/22；`127.0.0.1:9003`（MinIO 控制台回环绑定）也在列表中，属正常 |

> 若知识库单元「向量化失败」：用管理员账号在设置页配置全局 Embedding（DashScope），然后
> `docker compose restart app`，失败单元会自动重新向量化（种子导入有自愈机制）。

## 4. 升级与回滚

```bash
cd /opt/quantmore
./scripts/deploy.sh    # git pull --ff-only + 重建镜像 + up -d + 前端健康检查
```

- 脚本仅允许在 main 分支执行（其他分支直接拒绝）；**不做** `down -v`，数据卷不受影响。
- 回滚：直接执行 `git checkout <旧tag> && docker compose build && docker compose up -d`（镜像会按旧代码重建）。**不能重跑 `deploy.sh`——其内置 `git pull` 会把 checkout 拉回 main。**
- 回滚前先看第 7 节：如果旧版本依赖的 schema 与新版本不一致（validate 校验），旧代码可能无法启动。

## 5. 备份与恢复

### 配置定时备份

```bash
cd /opt/quantmore
mkdir -p backups
# 用 root 的 crontab（日志写到 /var/log 需要权限）：
sudo crontab -e
# 添加一行（每天 03:10 执行）：
10 3 * * * /opt/quantmore/scripts/backup.sh >> /var/log/quantmore-backup.log 2>&1
```

脚本产出三个文件（均在 `backups/`，已加入 .gitignore）：

- `quantmore_<日期>.dump.gz`：PostgreSQL 逻辑备份（pg_dump 自定义格式，含全部业务数据）；
- `quantmore_config_<日期>.tgz`：`quantmore_app_quantmore_data` 卷归档（Provider 运行配置）；
- `quantmore_minio_<日期>.tgz`：`quantmore_minio_data` 卷归档（上传的知识库原始文档）。

保留策略：日备保留 14 天；每月 1 号的文件视为月备，长期保留。

### 部署后必须做一次备份演练

```bash
cd /opt/quantmore
./scripts/backup.sh        # 手动执行一次
ls -lh backups/
```

把备份灌入一次性空库，验证备份可用（容器用完即删，不影响生产）：

```bash
# 1) 起一个一次性空库
docker run -d --name quantmore-restore-test \
  -e POSTGRES_PASSWORD=testpass -e POSTGRES_DB=quantmore \
  pgvector/pgvector:pg16
# 2) 兜底创建 vector 扩展（dump 自带扩展定义，此步保险）
docker exec quantmore-restore-test psql -U postgres -d quantmore -c 'CREATE EXTENSION IF NOT EXISTS vector;'
# 3) 灌入备份
gunzip -c backups/quantmore_$(date +%F).dump.gz | docker exec -i quantmore-restore-test pg_restore --clean --if-exists -U postgres -d quantmore
# 4) 抽查表与数据
docker exec quantmore-restore-test psql -U postgres -d quantmore -c '\dt' -c 'SELECT count(*) FROM users;'
# 5) 清理演练容器
docker rm -f quantmore-restore-test
```

### 真实恢复流程

```bash
cd /opt/quantmore
docker compose stop app    # 仅停 app 防止写入，postgres 保持运行
gunzip -c backups/quantmore_<日期>.dump.gz | docker exec -i quantmore-postgres pg_restore --clean --if-exists -U postgres -d quantmore
docker compose start app
```

注意：`--clean --if-exists` 会先删除同名对象，恢复是**覆盖式**的，执行前先跑一次 `backup.sh` 留存现状；备份中的 `flyway_schema_history` 会一并恢复，重启 app 时 Flyway 自动补齐备份之后新增的迁移，前提是备份与当前代码在同一演进线上。

若还需还原 `quantmore_minio_data`（上传文档误删/丢失场景）：同样先 `docker compose stop app`，再解包归档到卷——
`docker run --rm -v quantmore_minio_data:/data -v "$PWD/backups":/backup alpine tar xzf /backup/quantmore_minio_<日期>.tgz -C /data`，
最后 `docker compose start app`，登录后抽查上传文档是否可下载。

## 6. 常见故障

### app 容器崩溃循环 / 种子导入问题

- 早期版本在「无管理员」时种子导入会抛异常导致 app crash-loop；现已修复为**告警跳过**：日志出现「系统中没有管理员用户，跳过种子导入；注册首个用户(自动成为 ADMIN)后重启生效」，容器保持正常运行。处理方式：完成首注册后 `docker compose restart app` 即可触发导入。
- 若日志出现「APP_AI_CONFIG_ENCRYPTION_KEY 未配置」，或 `docker compose up` 报 `required variable ... is missing`：`.env` 的必填项没填，按第 2 节补齐后重试。
- 排查命令：`docker logs quantmore-app --tail 100`。

### 模板占位密钥未替换

两种占位值漏改的后果不对称，务必注意：

- `APP_JWT_SECRET` 忘记替换：占位值只有 20 字节，不满足 HS256 要求的 32 字节，app 启动时直接抛 `WeakKeyException` **大声失败**，容易发现。
- `APP_AI_CONFIG_ENCRYPTION_KEY` 忘记替换：**不会报错**，而是静默派生出公开可算的密钥，数据库中所有 Provider API Key 都能被解密——最危险的一种，务必按第 2 节生成随机值替换。
- `scripts/deploy.sh` 内置预检会在部署前扫描 `.env` 中的 `change_me`/`your_dashscope` 占位值，两种情况都会被拦截，不会带病上线。

### 密钥变更的后果

- **APP_JWT_SECRET 改动**：已签发的 JWT 全部失效，所有用户需重新登录（数据不丢，代价最小）。
- **APP_AI_CONFIG_ENCRYPTION_KEY 改动**：数据库中所有已加密的 Provider API Key **永久无法解密**，必须逐用户重新录入 API Key。没有补救手段，请严守第 2 节警示。
- **POSTGRES_PASSWORD / MINIO_ROOT_* 改 .env 不会生效**（这两个服务的环境变量只在数据卷首次初始化时读取）。数据库确需改密码：
  `docker exec quantmore-postgres psql -U postgres -c "ALTER USER postgres WITH PASSWORD '新密码';"`，再同步 `.env` 并 `docker compose up -d`；MinIO 凭据在控制台（经 SSH 隧道）修改。

## 7. 生产 schema 变更须知

- 生产 `ddl-auto=validate`，schema 完全由 Flyway 管理，迁移文件在 `app/src/main/resources/db/migration/`。
- 数据落库后，任何 schema 变更都必须**新增 `V2__*.sql` 迁移文件**随代码发布；Flyway 按版本号只执行一次，`deploy.sh` 重启后自动生效。
- **禁止修改已发布的 `V1__init_schema.sql`**：历史迁移一旦被改动，Flyway 的 checksum 校验会让启动直接失败。
- 开发环境「改 V1 清库重建」的惯例只适用于无数据环境，生产一律禁用。
- 迁移写法尽量向后兼容（加列给默认值或可空、重命名分步做等），避免新旧代码并存期出问题；回滚前先确认旧版本代码能通过新 schema 的 validate 校验。

## 8. HTTPS/域名与 MinIO 控制台

### HTTPS/域名后续路径

1. 域名 DNS A 记录指向服务器 IP。
2. 推荐宿主机 Caddy 自动签发证书：`caddy reverse-proxy --from https://域名 --to localhost:80`；或 nginx + certbot。此时把 compose 中 frontend 的端口映射改为 `"127.0.0.1:80:80"`，80 只回环绑定，由宿主机反代终止 TLS，然后 `docker compose up -d` 生效。
3. `.env` 中把 `CORS_ALLOWED_ORIGINS` 改为 `https://域名`（同源反代下通常不触发 CORS，此项为多域名/直连场景预留）。后端已启用 `forward-headers-strategy: native`，反代下客户端 IP 与协议透传正常。

### MinIO 控制台访问

控制台仅绑定服务器 `127.0.0.1:9003`，不直接对公网开放。本地机器建立 SSH 隧道：

```bash
ssh -L 9003:127.0.0.1:9003 user@<服务器IP>
# 浏览器打开 http://localhost:9003，用 MINIO_ROOT_USER / MINIO_ROOT_PASSWORD 登录
```
