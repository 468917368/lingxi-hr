#!/usr/bin/env bash
# ============================================================
# 4G 机：部署前端 + nginx（托管静态页，/api 反代到 8G 后端机 gateway）
# 用法：在 4G 机 deploy/ 目录下执行  ./setup-front.sh
# 前置：本目录有 .env、nginx.conf(占位 <BACKEND_IP>)、front-dist/
# ============================================================
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
[ -f "$DEPLOY_DIR/.env" ] && source "$DEPLOY_DIR/.env"

# 必填校验
[ -n "${BACKEND_IP:-}" ] || { echo "✗ 缺少 BACKEND_IP（.env 里填 8G 后端机内网 IP）"; exit 1; }
[ -d "$DEPLOY_DIR/front-dist" ] || { echo "✗ 未找到 front-dist/（先构建前端并拷贝）"; exit 1; }

# 1. 生成 nginx.conf（替换后端机 IP）
mkdir -p /data/nginx
sed "s/<BACKEND_IP>/${BACKEND_IP}/g" "$DEPLOY_DIR/nginx.conf" > /data/nginx/nginx.conf
echo "==> 已生成 /data/nginx/nginx.conf（/api -> ${BACKEND_IP}:8080）"

# 2. 启动 nginx（默认 bridge 网络，-p 80:80）
if docker inspect nginx >/dev/null 2>&1; then
  echo "==> nginx 已存在，跳过"
else
  echo "==> 启动 nginx"
  docker run -d --name nginx \
    -p 80:80 \
    --memory 128m \
    -v "$DEPLOY_DIR/front-dist:/usr/share/nginx/html:ro" \
    -v /data/nginx/nginx.conf:/etc/nginx/conf.d/default.conf:ro \
    --restart=always \
    nginx:1.27-alpine
fi

echo ""
echo "==> 4G 前端机部署完成。"
echo "    浏览器访问 http://$(hostname -I | awk '{print $1}')  （或本机公网 IP）"
echo "    后端网关：http://${BACKEND_IP}:8080"
echo ""
echo "    ⚠ 确认 8G 机安全组对 4G 机内网放行 8080，否则 /api 请求会 502"
