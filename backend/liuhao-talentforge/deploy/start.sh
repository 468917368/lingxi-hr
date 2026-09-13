#!/usr/bin/env bash
# ============================================================
# 灵犀互聘 后端启动脚本（8G 机，java -jar 直接运行）
# 用法：./start.sh
# 前置：基础设施（MySQL/Redis/Nacos/RocketMQ）已在 Docker 运行
# ============================================================
cd "$(dirname "$0")"
mkdir -p logs

# ---- 环境变量（与部署时保持一致；IP 为 8G 机内网 IP）----
export SPRING_PROFILES_ACTIVE=prod
export NACOS_ADDR=127.0.0.1:8848
export SPRING_CLOUD_NACOS_DISCOVERY_IP=172.17.24.42
export NACOS_DISCOVERY_ENABLED=true NACOS_CONFIG_ENABLED=true
export DB_HOST=127.0.0.1 DB_PORT=3306 DB_NAME=lingxi DB_USER=root DB_PASS=123456
export REDIS_HOST=127.0.0.1 REDIS_PORT=6379 REDIS_PASS=123456
export ROCKETMQ_NAMESRV=127.0.0.1:9876
export STORAGE_TYPE=aliyun-oss STORAGE_ALIYUN_OSS_ENDPOINT=oss-cn-guangzhou.aliyuncs.com
export STORAGE_ALIYUN_OSS_ACCESS_KEY_ID=${OSS_ACCESS_KEY_ID:your-oss-access-key-id}
export STORAGE_ALIYUN_OSS_ACCESS_KEY_SECRET=${OSS_ACCESS_KEY_SECRET:your-oss-access-key-secret}
export STORAGE_ALIYUN_OSS_BUCKET_NAME=lingxi-h

start_one() {
  local name=$1 mem=$2 port=$3
  if pgrep -f "$name/app.jar" >/dev/null 2>&1; then
    echo "  $name 已在运行，跳过"
  else
    nohup java -Xmx"$mem" -jar "services/$name/app.jar" > "logs/$name.log" 2>&1 &
    echo "  $name 启动中 (PID $!, 端口 $port)"
  fi
}

echo "==> 启动 7 个后端服务..."
start_one lingxi-gateway 384m 8080
start_one lingxi-user     512m 8086
start_one lingxi-chat     384m 8087
start_one lingxi-job      512m 8082
start_one lingxi-resume   768m 8083
start_one lingxi-hr       512m 8084
start_one lingxi-admin    384m 8085

echo ""
echo "==> 已发出启动命令。约 1-2 分钟后就绪，检查："
echo "    ps aux | grep app.jar | grep -v grep | wc -l        # 应为 7"
echo "    tail -f logs/gateway.log                            # 看启动日志"
echo "    curl -s http://127.0.0.1:8080/actuator/health       # 网关健康"
