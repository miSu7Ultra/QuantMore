#!/usr/bin/env bash
# 生产升级/首装脚本：git pull + 重建镜像 + 重启服务
set -euo pipefail
cd "$(dirname "$0")/.."

# 仅允许在 main 分支上执行升级；回滚请用 ops/production-deployment.md 中「回滚」节的直接命令（本脚本会 pull 回 main）
if [ "$(git rev-parse --abbrev-ref HEAD)" != "main" ]; then
  echo "错误：当前不在 main 分支（$(git rev-parse --abbrev-ref HEAD)），拒绝执行升级。回滚请直接执行: git checkout <旧tag> && docker compose build && docker compose up -d" >&2
  exit 1
fi

# 预检：.env 中关键密钥不得为模板占位值（占位加密密钥会静默派生出公开可算的密钥）
if [ -f .env ] && grep -qE '^(AI_BAILIAN_API_KEY|APP_AI_CONFIG_ENCRYPTION_KEY|APP_JWT_SECRET)=(change_me|your_dashscope)' .env; then
  echo "错误：.env 中存在模板占位密钥（change_me/your_dashscope），请先按 ops/production-deployment.md 生成真实密钥" >&2
  exit 1
fi

git fetch origin main
git pull --ff-only origin main

docker compose build
docker compose up -d
docker compose ps

curl -fsS http://localhost/ > /dev/null && echo "前端健康检查通过"
