#!/usr/bin/env bash
# 生产备份脚本：PostgreSQL 逻辑备份 + app 配置卷/MinIO 数据卷归档 + 按保留策略清理旧备份
#
# crontab 示例（每天 03:10 执行，日志追加到 /var/log/quantmore-backup.log）：
#   10 3 * * * /opt/quantmore/scripts/backup.sh >> /var/log/quantmore-backup.log 2>&1
#
# 保留策略：日备保留 14 天；每月 1 号生成的备份视为月备，长期保留。
# BACKUP_DIR 可外部覆盖（须为绝对路径），默认 <仓库根>/backups（已在 .gitignore 中排除）。
set -euo pipefail
cd "$(dirname "$0")/.."

BACKUP_DIR="${BACKUP_DIR:-$(pwd)/backups}"
mkdir -p "$BACKUP_DIR"

# 1) PostgreSQL 逻辑备份（自定义格式，可用 pg_restore 恢复）
docker exec quantmore-postgres pg_dump -U postgres -Fc quantmore | gzip > "$BACKUP_DIR/quantmore_$(date +%F).dump.gz"

# 2) app 配置卷归档（实际卷名 quantmore_app_quantmore_data，容器内 /root/.quantmore，存 Provider 运行配置）
#    前缀 quantmore 来自 docker-compose.yml 顶层 name: 固定项目名，此处直接引用最终卷名
docker run --rm -v quantmore_app_quantmore_data:/data -v "$BACKUP_DIR":/backup alpine tar czf /backup/quantmore_config_$(date +%F).tgz -C /data .

# 3) MinIO 数据卷归档（实际卷名 quantmore_minio_data，上传的知识库原始文档；前缀来源同上）
docker run --rm -v quantmore_minio_data:/data -v "$BACKUP_DIR":/backup alpine tar czf /backup/quantmore_minio_$(date +%F).tgz -C /data .

# 4) 清理过期日备：文件名形如 quantmore_2026-08-31.dump.gz；每月 1 号的文件跳过（月备）
# 注：依赖 GNU date 的 -d 参数（脚本在 Linux 服务器上运行）
cutoff_epoch="$(date -d '14 days ago' +%s)"
find "$BACKUP_DIR" -maxdepth 1 -type f \
  \( -name 'quantmore_????-??-??.dump.gz' -o -name 'quantmore_config_????-??-??.tgz' -o -name 'quantmore_minio_????-??-??.tgz' \) -print0 |
while IFS= read -r -d '' f; do
  base="$(basename "$f")"
  date_part="$(echo "$base" | sed -E 's/.*_([0-9]{4}-[0-9]{2}-[0-9]{2})\..*/\1/')"
  day="$(echo "$date_part" | cut -d- -f3)"
  [ "$day" = "01" ] && continue
  if [ "$(date -d "$date_part" +%s)" -lt "$cutoff_epoch" ]; then
    rm -f "$f"
    echo "已清理过期备份: $base"
  fi
done
