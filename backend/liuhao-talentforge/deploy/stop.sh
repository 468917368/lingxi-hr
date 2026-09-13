#!/usr/bin/env bash
# ============================================================
# 灵犀互聘 后端停止脚本（8G 机）
# 用法：./stop.sh
# ============================================================
for name in lingxi-gateway lingxi-user lingxi-chat lingxi-job lingxi-resume lingxi-hr lingxi-admin; do
  if pkill -f "$name/app.jar" 2>/dev/null; then
    echo "  $name 已停止"
  else
    echo "  $name 未在运行"
  fi
done
echo "==> 完成。可用 ps aux | grep app.jar | grep -v grep 确认"
