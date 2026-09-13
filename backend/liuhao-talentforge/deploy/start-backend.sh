#!/usr/bin/env bash
# ============================================================
# 8G 机：启动全部后端容器（日常启动 / 停机后恢复用）
#   MySQL + Redis + Nacos + RocketMQ + gateway + 6 业务服务
# 用法：cd /opt/lingxi/deploy && ./start-backend.sh
# 前置：容器已由 setup-backend.sh 创建过（首次部署请先跑 setup-backend.sh）
# ============================================================
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
[ -f "$DEPLOY_DIR/.env" ] && source "$DEPLOY_DIR/.env"

INFRA="mysql redis nacos rocketmq-namesrv rocketmq-broker"
# 业务服务：gateway 最后起（等其余服务注册完成，路由可用）
SERVICES="lingxi-user lingxi-chat lingxi-job lingxi-resume lingxi-hr lingxi-admin lingxi-gateway"

start_one() {
  local c=$1
  if docker inspect "$c" >/dev/null 2>&1; then
    if docker start "$c" >/dev/null 2>&1; then
      echo "   ✓ $c 已启动"
    else
      echo "   ✗ $c 启动失败，用 docker logs $c 排查"
    fi
  else
    echo "   ⚠ $c 不存在（首次部署请先执行 ./setup-backend.sh）"
  fi
}

echo "==> 启动基础设施（MySQL/Redis/Nacos/RocketMQ）"
for c in $INFRA; do start_one "$c"; done

echo "==> 等待 Nacos 就绪..."
for i in $(seq 1 30); do
  if curl -sf "http://127.0.0.1:8848/nacos/v1/console/health/readiness" >/dev/null 2>&1; then
    echo "   ✓ Nacos 就绪"
    break
  fi
  [ "$i" = 30 ] && echo "   ⚠ Nacos 30 次未就绪，继续尝试启动服务（容器 --restart=always 会自动重试）"
  sleep 2
done

echo "==> 等待 RocketMQ Broker 就绪..."
for i in $(seq 1 30); do
  if docker exec rocketmq-broker sh -c 'echo > /dev/tcp/127.0.0.1/10911' >/dev/null 2>&1; then
    echo "   ✓ Broker 就绪"
    break
  fi
  [ "$i" = 30 ] && echo "   ⚠ Broker 未就绪，resume 是 MQ 消费者，需关注 docker logs lingxi-resume"
  sleep 2
done

echo "==> 启动 7 个业务服务"
for c in $SERVICES; do start_one "$c"; done

echo ""
echo "==> 完成。验证：docker ps"
echo "    Nacos 控制台：http://${BACKEND_IP:-$(hostname -I | awk '{print $1}')}:8848/nacos 命名空间 lingxi 下应有 7 个服务"
