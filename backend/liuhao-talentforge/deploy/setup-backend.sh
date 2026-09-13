#!/usr/bin/env bash
# ============================================================
# 8G 机：部署全部后端（单机 all-in-one）
#   MySQL + Redis + Nacos + RocketMQ + gateway + 6 业务服务
# 用法：在 8G 机 deploy/ 目录下执行  ./setup-backend.sh
# 前置：本目录有 .env、Dockerfile、broker.conf、nginx.conf(占位)、
#       infra/schema.sql、front-dist/(可选)、services/<模块>/app.jar
# ============================================================
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
[ -f "$DEPLOY_DIR/.env" ] && source "$DEPLOY_DIR/.env"

# 必填校验
[ -n "${BACKEND_IP:-}" ] || { echo "✗ 缺少 BACKEND_IP（.env 里填本机内网 IP）"; exit 1; }

# 1. 创建网络（本机容器名互通）
docker network inspect mynet >/dev/null 2>&1 || docker network create mynet

# 2. MySQL（首次启动自动导入 schema.sql 建 34 张表）
if docker inspect mysql >/dev/null 2>&1; then
  echo "==> mysql 已存在，跳过"
else
  echo "==> 启动 MySQL"
  docker run -d --name mysql \
    --network mynet \
    -p 3306:3306 \
    --memory 512m \
    -e MYSQL_ROOT_PASSWORD=123456 \
    -v /data/mysql:/var/lib/mysql \
    -v "$DEPLOY_DIR/infra/schema.sql:/docker-entrypoint-initdb.d/01-schema.sql:ro" \
    --restart=always \
    mysql:8.0.33 \
    --character-set-server=utf8mb4 \
    --collation-server=utf8mb4_unicode_ci
  echo "   ✓ 首次启动会建表（约 30s-1min），可 docker logs -f mysql 查看"
fi

# 3. Redis（密码 123456，对齐各服务默认 REDIS_PASS）
if docker inspect redis >/dev/null 2>&1; then
  echo "==> redis 已存在，跳过"
else
  echo "==> 启动 Redis"
  docker run -d --name redis \
    --network mynet \
    -p 6379:6379 \
    --memory 256m \
    -v /data/redis:/data \
    --restart=always \
    redis:7-alpine \
    redis-server --appendonly yes --requirepass 123456
fi

# 4. Nacos（2.2.3 standalone，8848 HTTP + 9848 gRPC）
if docker inspect nacos >/dev/null 2>&1; then
  echo "==> nacos 已存在，跳过"
else
  echo "==> 启动 Nacos"
  docker run -d --name nacos \
    --network mynet \
    -p 8848:8848 -p 9848:9848 \
    --memory 512m \
    -e MODE=standalone \
    -e JVM_XMS=256m -e JVM_XMX=512m -e JVM_XMN=128m \
    -v /data/nacos:/home/nacos/data \
    --restart=always \
    nacos/nacos-server:v2.2.3
fi

# 5. 等 Nacos 就绪并创建命名空间 lingxi
echo "==> 等待 Nacos 就绪..."
READY=0
for i in $(seq 1 60); do
  if curl -sf "http://127.0.0.1:8848/nacos/v1/console/health/readiness" >/dev/null 2>&1; then
    READY=1; break
  fi
  sleep 2
done
if [ "$READY" = 1 ]; then
  echo "==> 创建 Nacos 命名空间 lingxi"
  curl -X POST "http://127.0.0.1:8848/nacos/v1/console/namespaces" \
    -d "customNamespaceId=lingxi&namespaceName=lingxi&namespaceDesc=lingxi" >/dev/null 2>&1 || true
else
  echo "   ⚠ Nacos 60s 未就绪，请 docker logs nacos 检查；命名空间需手动创建"
fi

# 6. 构建 7 个业务镜像
echo "==> 构建业务镜像（首次拉取 temurin:8-jre 较慢）..."
for m in lingxi-gateway lingxi-user lingxi-chat lingxi-job lingxi-resume lingxi-hr lingxi-admin; do
  docker build -q -t "$m:1.0.0" \
    --build-arg JAR_PATH="services/$m/app.jar" \
    -f "$DEPLOY_DIR/Dockerfile" "$DEPLOY_DIR"
  echo "   ✓ $m:1.0.0"
done

# 7. RocketMQ NameServer
if docker inspect rocketmq-namesrv >/dev/null 2>&1; then
  echo "==> rocketmq-namesrv 已存在，跳过"
else
  echo "==> 启动 NameServer"
  docker run -d --name rocketmq-namesrv \
    --network mynet \
    -p 9876:9876 \
    --memory 768m \
    -e "JAVA_OPT_EXT=-Xms256m -Xmx512m" \
    -v /data/rocketmq/namesrv/logs:/home/rocketmq/logs \
    --restart=always \
    apache/rocketmq:5.1.0 \
    sh mqnamesrv
fi
echo "==> 等待 NameServer..."
until docker exec rocketmq-namesrv sh -c 'echo > /dev/tcp/127.0.0.1/9876' >/dev/null 2>&1; do sleep 2; done

# 8. RocketMQ Broker（brokerIP1 = 本机内网 IP，供客户端连）
if docker inspect rocketmq-broker >/dev/null 2>&1; then
  echo "==> rocketmq-broker 已存在，跳过"
else
  mkdir -p /data/rocketmq/broker/logs /data/rocketmq/broker/store
  sed "s/<BACKEND_IP>/${BACKEND_IP}/g" "$DEPLOY_DIR/broker.conf" > /data/rocketmq/broker/broker.conf
  echo "==> 启动 Broker (brokerIP1=${BACKEND_IP})"
  docker run -d --name rocketmq-broker \
    --network mynet \
    -p 10909:10909 -p 10911:10911 -p 10912:10912 \
    --memory 512m \
    -e "JAVA_OPT_EXT=-Xms256m -Xmx512m" \
    -v /data/rocketmq/broker/broker.conf:/home/rocketmq/conf/broker.conf:ro \
    -v /data/rocketmq/broker/logs:/home/rocketmq/logs \
    -v /data/rocketmq/broker/store:/home/rocketmq/store \
    --restart=always \
    apache/rocketmq:5.1.0 \
    sh mqbroker -c /home/rocketmq/conf/broker.conf
fi
echo "==> 等待 Broker..."
until docker exec rocketmq-broker sh -c 'echo > /dev/tcp/127.0.0.1/10911' >/dev/null 2>&1; do sleep 2; done

# 9. 业务服务（同机 mynet 用容器名互通；注册 discovery IP = BACKEND_IP）
run_service() {
  local name=$1 port=$2 mem=$3
  shift 3
  local extra_env=("${@}")
  if docker inspect "$name" >/dev/null 2>&1; then
    echo "   $name 已存在，跳过"
    return
  fi
  echo "==> 启动 $name (:$port)"
  docker run -d --name "$name" \
    --network mynet \
    -p "$port:$port" \
    --memory "$mem" \
    -e SPRING_PROFILES_ACTIVE=prod \
    -e NACOS_ADDR=nacos:8848 \
    -e "SPRING_CLOUD_NACOS_DISCOVERY_IP=${BACKEND_IP}" \
    -e DB_HOST=mysql -e DB_PORT=3306 -e DB_NAME=lingxi -e DB_USER=root -e DB_PASS=123456 \
    -e REDIS_HOST=redis -e REDIS_PORT=6379 -e REDIS_PASS=123456 \
    -e ROCKETMQ_NAMESRV=rocketmq-namesrv:9876 \
    -e STORAGE_TYPE=aliyun-oss \
    -e "STORAGE_ALIYUN_OSS_ENDPOINT=${OSS_ENDPOINT}" \
    -e "STORAGE_ALIYUN_OSS_ACCESS_KEY_ID=${OSS_ACCESS_KEY_ID}" \
    -e "STORAGE_ALIYUN_OSS_ACCESS_KEY_SECRET=${OSS_ACCESS_KEY_SECRET}" \
    -e "STORAGE_ALIYUN_OSS_BUCKET_NAME=${OSS_BUCKET_NAME}" \
    -e "JAVA_OPTS=-Xmx${mem%m}m -XX:MaxMetaspaceSize=256m" \
    "${extra_env[@]}" \
    --restart=always \
    "$name:1.0.0"
}

run_service lingxi-gateway 8080 384m
run_service lingxi-user     8086 512m
run_service lingxi-chat     8087 384m
run_service lingxi-job      8082 512m
run_service lingxi-resume   8083 768m
run_service lingxi-hr       8084 512m
run_service lingxi-admin    8085 384m \
  -e NACOS_DISCOVERY_ENABLED=true \
  -e NACOS_CONFIG_ENABLED=true

echo ""
echo "==> 8G 后端机部署完成。"
echo "    检查：docker ps"
echo "    MySQL 建表：docker exec -it mysql mysql -uroot -p123456 -e 'USE lingxi; SHOW TABLES;' | wc -l"
echo "    Nacos：http://${BACKEND_IP}:8848/nacos 命名空间 lingxi 下应有 7 个服务"
echo "    ⚠ 确认后端全部起来后，再到 4G 机执行 ./setup-front.sh"
