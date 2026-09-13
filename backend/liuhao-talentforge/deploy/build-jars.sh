#!/usr/bin/env bash
# ============================================================
# 灵犀互聘 本地打包脚本
# 前置：JDK 8（zulu-8），否则编译会因 Lombok 注解处理失败
#   export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home
# 用法：cd deploy && ./build-jars.sh
# ============================================================
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJ_DIR="$(cd "$DEPLOY_DIR/.." && pwd)"   # liuhao-talentforge

echo "==> 项目目录: $PROJ_DIR"
echo "==> 检查 JDK..."
JAVA_VER="$(java -version 2>&1 | head -1)"
echo "    $JAVA_VER"
if ! java -version 2>&1 | grep -qiE '1\.8|version "8|zulu.*8'; then
  echo "   ⚠ 当前 java 不是 JDK 8，编译可能失败。"
  echo "     请先执行: export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home"
fi

echo "==> mvn clean package -DskipTests"
cd "$PROJ_DIR"
mvn clean package -DskipTests -q

echo "==> 拷贝 7 个 fat jar 到 deploy/services/"
for m in lingxi-gateway lingxi-user lingxi-chat lingxi-job lingxi-resume lingxi-hr lingxi-admin; do
  src="$PROJ_DIR/$m/target/$m-1.0.0-SNAPSHOT.jar"
  mkdir -p "$DEPLOY_DIR/services/$m"
  if [ -f "$src" ]; then
    cp "$src" "$DEPLOY_DIR/services/$m/app.jar"
    echo "   ✓ $m/app.jar  $(du -h "$DEPLOY_DIR/services/$m/app.jar" | cut -f1)"
  else
    echo "   ✗ 找不到 $src，请检查是否编译成功"
    exit 1
  fi
done

echo "==> 拷贝数据库初始化脚本到 deploy/infra/"
mkdir -p "$DEPLOY_DIR/infra"
SCHEMA="$PROJ_DIR/../docs/schema.sql"
if [ -f "$SCHEMA" ]; then
  cp "$SCHEMA" "$DEPLOY_DIR/infra/schema.sql"
  echo "   ✓ infra/schema.sql"
else
  echo "   ✗ 找不到 $SCHEMA"
  exit 1
fi

echo ""
echo "==> 完成。前端产物需手动拷贝为 deploy/front-dist/（cd front && umi build）"
echo "==> 然后把 deploy/ 分发到两台服务器，并按 README.md 部署。"
