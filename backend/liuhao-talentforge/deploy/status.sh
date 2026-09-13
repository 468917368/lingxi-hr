#!/usr/bin/env bash
# ============================================================
# 灵犀互聘 后端状态查看（8G 机）
# 用法：./status.sh
# ============================================================
echo "== 后端进程 =="
ps aux | grep "app.jar" | grep -v grep | awk '{printf "  PID=%-6s %s\n", $2, $13}'
echo ""
echo "== 端口监听 =="
ss -tlnp | grep -E ':(8080|8082|8083|8084|8085|8086|8087)' | awk '{print "  " $4}' | sort -u
echo ""
echo "== 基础设施容器（Docker）=="
docker ps --format '  {{.Names}}: {{.Status}}' 2>/dev/null | grep -E 'mysql|redis|nacos|rocketmq' || echo "  （无 Docker 或未运行）"
