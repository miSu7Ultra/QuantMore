#!/usr/bin/env bash
# 生产升级/首装脚本：git pull + 重建镜像 + 重启服务
set -euo pipefail
cd "$(dirname "$0")/.."

git fetch origin main
git pull --ff-only origin main

docker compose build
docker compose up -d
docker compose ps

curl -fsS http://localhost/ > /dev/null && echo "前端健康检查通过"
