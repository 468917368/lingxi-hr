# 灵犀互聘 Docker 部署手册（后端 8G + 前端 4G）

> 部署 `liuhao-talentforge` 后端 7 微服务到 **8G 机**（单机 all-in-one），前端 + nginx 到 **4G 机**。
> 全部 `docker run` + 自定义网络 `mynet`（仅 8G 机需要，容器名互通）。

## 拓扑

| 机器 | 内存 | 部署内容 | 端口 |
|------|:---:|------|------|
| **8G 机** | 8G | MySQL + Redis + Nacos 2.2.3 + RocketMQ + gateway + user/chat/job/resume/hr/admin | 3306/6379/8848,9848 / 9876,10909-10911 / 8080,8082-8087 |
| **4G 机** | 4G | nginx（前端静态页 + `/api` 反代到 8G 机） | 80 |

- 8G 机所有容器在同一个 `mynet` 网络，**用容器名互通**（`mysql`/`redis`/`nacos`/`rocketmq-namesrv`/`gateway`…）。
- 4G 机 nginx 通过内网 IP 访问 8G 机 `BACKEND_IP:8080` 反代 API。
- 文件存储用**阿里云 OSS**（dev 那套 key，已填进 `.env`）。

## 前置

1. **8G 机安全组**：公网开 8080；内网开 3306/6379/8848,9848/9876/10909-10911/8080,8082-8087（只限 VPC 内网）。
2. **4G 机安全组**：公网开 80；内网能访问 8G 机 8080。
3. **Docker 镜像加速**（国内 ECS 建议）：`/etc/docker/daemon.json` 配 aliyun mirror 后 `systemctl restart docker`。
4. **本地环境**：JDK 8（zulu-8）、Maven、Node。

## 构建（本地）

```bash
# 后端：打包 7 个 fat jar + schema.sql
export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home
cd liuhao-talentforge/deploy
./build-jars.sh

# 前端：
cd ../front
umi build
mkdir -p ../deploy/front-dist
cp -r dist/* ../deploy/front-dist/
```

## 分发

**8G 后端机**（全部后端相关）：
```bash
scp setup-backend.sh Dockerfile broker.conf .env.example root@<8G_IP>:/opt/lingxi/deploy/
scp -r infra services root@<8G_IP>:/opt/lingxi/deploy/
```

**4G 前端机**：
```bash
scp setup-front.sh nginx.conf .env.example root@<4G_IP>:/opt/lingxi/deploy/
scp -r front-dist root@<4G_IP>:/opt/lingxi/deploy/
```

**两台机都执行**：
```bash
cd /opt/lingxi/deploy
cp .env.example .env
vi .env        # 只需填 BACKEND_IP（8G 机内网 IP）；FRONT_IP 仅标识，OSS 已填好
```

## 部署（按顺序）

### 1. 8G 机起全部后端
```bash
cd /opt/lingxi/deploy
./setup-backend.sh
```
脚本会：建 `mynet` → MySQL(自动建 34 表)/Redis/Nacos(自动建命名空间 lingxi) → build 7 镜像 → RocketMQ → 7 个业务服务（**resume 在 broker ready 后启动**）。

**确认后端全起来再部署前端**：
```bash
docker ps                                          # 全部 Up
docker exec -it mysql mysql -uroot -p123456 -e "USE lingxi; SHOW TABLES;" | wc -l   # ≈34
```

### 2. 4G 机起前端
```bash
cd /opt/lingxi/deploy
./setup-front.sh
```
脚本会生成 `/data/nginx/nginx.conf`（`/api → BACKEND_IP:8080`）并启动 nginx。

## 验证

```bash
# 后端网关（在 8G 机或本地）
curl -X POST http://<8G_IP>:8080/api/v1/auth/login -H "Content-Type: application/json" \
  -d '{"phone":"13800138000","password":"x"}'
# Nacos 注册（命名空间 lingxi 下 7 个服务）
http://<8G_IP>:8848/nacos
# 前端
浏览器访问 http://<4G_IP>
# 排障
docker logs -f <服务名>
```

## 关键坑（务必看）

- **Nacos 命名空间必须叫 `lingxi`**（setup-backend.sh 已自动创建）。
- **brokerIP1=BACKEND_IP**（setup-backend.sh 写入 broker.conf），否则 MQ 客户端连不上。
- **resume 是唯一 RocketMQ consumer**，脚本等 broker ready 后才起；反复重启看 `docker logs -f lingxi-resume`。
- **8G 机内存紧张**：MySQL/Nacos/broker 等已按 `--memory` 限额，不要同时起额外大内存容器。
- **4G 机 nginx 反代**：`/api` 走 `BACKEND_IP:8080`，8G 机安全组必须对 4G 机内网放行 8080，否则前端请求 502。
- **MySQL 首次建表**：`infra/schema.sql` 已挂 `/docker-entrypoint-initdb.d/`，不要删。
- **Redis 密码 123456**，与各服务 `REDIS_PASS` 一致。
- **OSS 用 dev key**（`.env` 已填），上传失败先查 key 是否有效。

## 常见问题

| 现象 | 处理 |
|------|------|
| 业务服务启动即退，日志报 MySQL 连接失败 | MySQL 没起来或没建完表；`docker logs -f mysql` |
| 前端页面能开，接口 502 | 8G 机 8080 没对 4G 机内网放行；或 gateway 没起 |
| gateway 路由 503 | Nacos 命名空间 lingxi 没建，或服务没注册（Nacos 控制台看） |
| resume 反复重启 | broker 未就绪或 brokerIP1 不对；`docker logs -f lingxi-resume` |
| 上传文件失败 | `.env` OSS_* 是否正确、bucket `lingxi-h` 是否存在 |
| 服务器重启后容器没起来 | 容器都 `--restart=always`；若 Docker 未自启：`systemctl enable docker` |
