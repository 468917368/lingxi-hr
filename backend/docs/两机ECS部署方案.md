# 两机 Docker 部署方案：灵犀互聘（后端 8G + 前端 4G）

## Context

用户有 2 台阿里云 ECS（**8G 后端机 + 4G 前端机**，已装 Docker）：**8G 机部署全部后端**（MySQL/Redis/Nacos/RocketMQ + 7 微服务），**4G 机部署前端 + nginx**。通读项目后确认：**项目原无任何 Docker 设施**，且存在几个部署硬阻塞点，需先改配置再交付部署文件。

已确认的关键事实：
- 7 个可执行服务：gateway(8080)、user(8086)、chat(8087)、job(8082)、resume(8083)、hr(8084)、admin(8085)；lingxi-common 是纯库依赖。
- **6 个服务 bootstrap.yml 的 Nacos `server-addr` 硬编码 `127.0.0.1:8848`**（仅 admin 支持 `NACOS_ADDR` 覆盖）。
- **gateway application.yml 的 Redis host/port 硬编码、password 注释掉**，无 env 覆盖；且 gateway bootstrap `active: dev`，dev profile 会把 Nacos/DB/Redis 全顶回 127.0.0.1。
- **lingxi-resume 是唯一 RocketMQ consumer，MQ 不可达时启动直接失败**，必须等 broker 就绪。
- `docs/schema.sql`（34 张表，自带 `CREATE DATABASE lingxi` + `USE lingxi`）可作 MySQL 首次启动初始化脚本。**不导入 init-admin.sql**（默认 admin 账号/配置不走，管理后台需手动建号）。
- 文件存储用 **阿里云 OSS**（不部署 MinIO），**key/bucket 用 dev 那套**（`OSS_ENDPOINT=oss-cn-guangzhou.aliyuncs.com`、bucket `lingxi-h`，已填进 `.env.example`/`.env`）。注意字段名坑：`MinioUtil` 代码读 `storage.aliyun-oss.access-key-id/access-key-secret`（`/lingxi-common/.../MinioUtil.java`），部分模块 dev yml 写的是 `access-key/secret-key`（字段对不上、dev 下 OSS 实际未生效）。部署脚本用 `STORAGE_ALIYUN_OSS_ACCESS_KEY_ID/SECRET` 按代码字段注入即可。
- 各服务 DB/Redis/MQ 均有 env 占位符（`DB_HOST`/`REDIS_HOST`/`REDIS_PASS`/`ROCKETMQ_NAMESRV` 等），admin 的 `REDIS_PASS` 默认空。
- Nacos config 中心 `fail-fast` 默认 false：配置中心不可达不阻塞启动，本地 application.yml 兜底完整；仅 **discovery 是运行时硬依赖**。
- 项目目标 **Java 8**（pom `java.version=1.8`），本机有 Zulu 8u492，服务器也是 Java 8 → **全链路统一 Java 8**。注意：本机默认 `mvn` 跑在 JDK 26 上，Lombok 1.18.30 不支持 Java 26 注解处理（会报"找不到符号"），编译前必须 `export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home`。

## 两机拓扑与资源分配

| 机器 | 内存 | 部署内容 | 端口 |
|------|:---:|------|------|
| **8G 后端机** | 8G | MySQL + Redis + Nacos 2.2.3 + RocketMQ + gateway + user/chat/job/resume/hr/admin | 3306/6379/8848,9848 / 9876,10909-10911 / 8080,8082-8087 |
| **4G 前端机** | 4G | nginx（前端静态 + `/api` 反代到 8G 机） | 80 |

8G 机内存核算（脚本按 `--memory` 限额）：MySQL 512m + Redis 256m + Nacos 512m + namesrv 256m + broker 512m + 7 服务(384~768m) ≈ **5.3G**，8G 机可行但偏紧，勿再起大内存容器。

**网络模式**：8G 机所有容器在同一个 `mynet`（`docker run --network mynet`），**容器名互通**（`mysql`/`redis`/`nacos`/`rocketmq-namesrv`/`gateway`…），符合用户单机 all-in-one 习惯；4G 机 nginx 用默认 bridge，通过内网 IP 反代 `BACKEND_IP:8080`。

## 源码改动（7 个文件）

0. **✅ 已完成（2026-08-04）父 pom.xml**：spring-boot-maven-plugin 补 `<executions>` 绑定 `repackage` goal。原项目只声明插件未绑定 goal，`mvn package` 只出普通 jar（无 Main-Class，无法 `java -jar`）；已修复并用 JDK8 验证 7 个 fat jar 正常（66M~126M）。
1. **6 个 bootstrap.yml**（gateway/user/chat/job/resume/hr）：discovery + config 两处 `server-addr: 127.0.0.1:8848` → `server-addr: ${NACOS_ADDR:127.0.0.1:8848}`。
2. **lingxi-gateway bootstrap.yml**：`active: dev` → `active: ${SPRING_PROFILES_ACTIVE:dev}`（保险丝，生产强制非 dev）。
3. **lingxi-gateway application.yml** Redis 块：`host: 127.0.0.1` → `${REDIS_HOST:127.0.0.1}`、`port: 6379` → `${REDIS_PORT:6379}`、注释掉的 `password` 行 → `password: ${REDIS_PASS:123456}`。

> 文件存储不改源码，靠部署脚本里 `STORAGE_*` env 覆盖为 aliyun-oss（Spring 环境变量优先级高于 application.yml）。OSS key/bucket 用 dev 那套（已在 `.env.example`/`.env` 填好）。

## 新增文件（`liuhao-talentforge/deploy/`）

```
deploy/
├── README.md                    # 部署手册：拓扑/前置(安全组、镜像加速)/构建/分发/启动/验证/FAQ
├── .env.example                 # BACKEND_IP/FRONT_IP + OSS 模板（两台机复制为 .env）
├── Dockerfile                   # 通用应用镜像：eclipse-temurin:8-jre + COPY app.jar
├── build-jars.sh                # 本地 JDK8(zulu-8) 打包 + 拷贝 7 个 fat jar + 拷贝 schema.sql
├── setup-backend.sh             # 8G 机：docker run 起全部后端 + 建命名空间
├── setup-front.sh               # 4G 机：docker run 起 nginx + 生成反代配置
├── broker.conf                  # RocketMQ：brokerIP1=<BACKEND_IP>（脚本替换）、autoCreateTopicEnable
├── nginx.conf                   # 前端：80 静态 + /api 反代到 <BACKEND_IP>:8080
└── (build-jars.sh 生成)
    ├── services/<模块>/app.jar  # 7 个 fat jar（gitignore）
    └── infra/schema.sql         # docs/schema.sql 副本（gitignore）
```

**Dockerfile 设计**：`FROM eclipse-temurin:8-jre`，`ARG JAR_PATH` + `COPY ${JAR_PATH} /app/app.jar`，`ENV JAVA_OPTS="-Xmx384m -XX:MaxMetaspaceSize=256m"`，`ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]`。`setup-backend.sh` 里 `docker build --build-arg JAR_PATH=services/<模块>/app.jar` 构建各服务镜像，运行时 `-e JAVA_OPTS` 覆盖内存。

**部署方式（docker run + mynet，参照用户既有流程）**：
- **8G 机（setup-backend.sh）**：建 `mynet` → MySQL 挂 `infra/schema.sql`→`/docker-entrypoint-initdb.d/01-schema.sql`（首次自动建 34 表，`--memory 512m`）→ Redis `--requirepass 123456` → Nacos `2.2.3` standalone（`-p 8848/9848`，`--memory 512m`，起来后自动创建命名空间 `lingxi`）→ build 7 镜像 → namesrv(`-p 9876`) → broker(`-p 10909/10911/10912`，brokerIP1=BACKEND_IP) → 7 业务服务 → **resume 在 broker ready 后才起**。
- **每业务服务 env**：`SPRING_PROFILES_ACTIVE=prod`、`NACOS_ADDR=nacos:8848`（容器名）、**`SPRING_CLOUD_NACOS_DISCOVERY_IP=${BACKEND_IP}`**、`DB_HOST=mysql`、`REDIS_HOST=redis`、`REDIS_PASS=123456`、`ROCKETMQ_NAMESRV=rocketmq-namesrv:9876`、`STORAGE_TYPE=aliyun-oss` + `STORAGE_ALIYUN_OSS_*`（dev key）；admin 另加 `NACOS_DISCOVERY_ENABLED=true`、`NACOS_CONFIG_ENABLED=true`。
- **4G 机（setup-front.sh）**：`sed` 把 nginx.conf 的 `<BACKEND_IP>` 替换为实际值 → 生成 `/data/nginx/nginx.conf` → `docker run nginx -p 80:80` 托管 `front-dist`，`/api` 反代 `BACKEND_IP:8080`。

**broker.conf**：`brokerClusterName=DefaultCluster`、`brokerIP1=<BACKEND_IP>`（setup-backend.sh 用 sed 替换为实际值）、`namesrvAddr=rocketmq-namesrv:9876`（容器名）、`autoCreateTopicEnable=true`、`brokerRole=ASYNC_MASTER`、`flushDiskType=ASYNC_FLUSH`。

## 部署步骤（README 摘要）

1. **前置**：8G 机安全组公网开 8080、内网开 3306/6379/8848,9848/9876/10909-10911/8080,8082-8087；4G 机公网开 80、内网能访问 8G 机 8080。配镜像加速（aliyun mirror）。
2. **本地构建**：`cd deploy && ./build-jars.sh`（脚本检查 JAVA_HOME 为 JDK 8，执行 `mvn clean package -DskipTests`，拷贝 7 个 jar 与 schema.sql）；前端 `cd front && umi build`，产物拷到 `deploy/front-dist/`。
3. **分发**：8G 机拷 `setup-backend.sh Dockerfile broker.conf .env.example` + `infra/ services/`；4G 机拷 `setup-front.sh nginx.conf .env.example` + `front-dist/`。两台机 `.env` 填 BACKEND_IP（OSS 已填）。
4. **启动顺序**：8G 机 `./setup-backend.sh`（全部后端）→ 确认 MySQL 建表 + 服务注册 → 4G 机 `./setup-front.sh`。
5. **验证**：见下。

## 验证

- `docker ps` 全部 Up；`docker logs -f <服务名>` 无致命错误。
- Nacos 控制台（`http://<8G机IP>:8848/nacos`）命名空间 `lingxi` 下 7 个服务已注册（含 gateway/admin）。
- 后端网关：`curl http://<8G机IP>:8080/api/v1/auth/login`（POST）应返回 JSON 而非 502/503。
- 前端：浏览器访问 `http://<4G机IP>`，登录、注册流程走通。
- 链路冒烟：HR 注册→企业认证（涉及 OSS 上传）→候选人列表→模拟面试（涉及百宝箱）。

## 风险与注意

- **Nacos 命名空间 `lingxi` 必须创建**，否则 7 服务注册/发现全部失效（服务仍能起但 gateway 路由 404/503）。setup-backend.sh 已自动创建。
- **resume 必须在 broker ready 后才起**（setup-backend.sh 已保证）；若反复重启，`docker logs -f lingxi-resume` 看是否为 MQ 连接错误。
- **bridge 发现 IP**：业务服务设 `SPRING_CLOUD_NACOS_DISCOVERY_IP=BACKEND_IP`（脚本已设），保证 gateway 通过宿主 IP 访问业务服务。
- **brokerIP1=BACKEND_IP** 由 setup-backend.sh 写入 broker.conf，否则生产者/消费者连不上 broker。
- **Redis 设了 requirepass 123456**，所有服务 env 传 `REDIS_PASS=123456`（含 admin，其默认空）。
- **8G 机内存偏紧**：所有容器 `--memory` 限额，勿同时起额外大内存容器。
- **4G 机 nginx 反代**：`/api` 走 `BACKEND_IP:8080`，8G 机安全组必须对 4G 机内网放行 8080，否则前端请求 502。
- 镜像拉取走公网，ECS 国内节点建议配镜像加速。
- **OSS 用 dev 那套 key/bucket**（已填进 `.env.example`/`.env`）；若上传失败，检查 dev key 是否仍有效、bucket `lingxi-h` 是否存在。
- 前端部署：需先 `cd front && umi build`，产物拷贝为 `deploy/front-dist/`。
- JWT secret 暂用默认值（user/chat/gateway 一致）；如需加固，三处要同步换。
