#!/usr/bin/env bash
# ============================================================
# 8G 机：停止全部后端容器（先停业务服务，再停基础设施）
# 用法：cd /opt/lingxi/deploy && ./stop-backend.sh
# 说明：容器都带 --restart=always，只有显式 docker stop 才会保持停止；
#       docker 服务本身重启（如服务器重启）会自动恢复，无需本脚本
# ============================================================
set -euo pipefail

# 业务服务：先停 gateway 切断入口，再停其余
SERVICES="lingxi-gateway lingxi-admin lingxi-hr lingxi-resume lingxi-job lingxi-chat lingxi-user"
# 基础设施：broker/namesrv 先于 nacos，mysql 最后
INFRA="rocketmq-broker rocketmq-namesrv nacos redis mysql"

echo "==> 停止业务服务"
for c in $SERVICES; do
  if docker inspect "$c" >/dev/null 2>&1; then
    docker stop --time 30 "$c" >/dev/null 2>&1 || true
    echo "   ✓ $c 已停止"
  fi
done

echo "==> 停止基础设施"
for c in $INFRA; do
  if docker inspect "$c" >/dev/null 2>&1; then
    docker stop --time 30 "$c" >/dev/null 2>&1 || true
    echo "   ✓ $c 已停止"
  fi
done

echo ""
echo "==> 全部已停止。恢复请执行 ./start-backend.sh"
