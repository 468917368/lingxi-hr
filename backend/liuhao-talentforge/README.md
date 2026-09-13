# 灵犀互聘 (TalentForge) 后端微服务系统

基于 Spring Cloud Alibaba 微服务架构的智能招聘平台后端，提供用户认证、岗位管理、简历解析、HR 管理、面试流程等核心功能。

## 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 1.8 |
| 基础框架 | Spring Boot | 2.7.18 |
| 微服务框架 | Spring Cloud | 2021.0.8 |
| 服务治理 | Spring Cloud Alibaba (Nacos, Sentinel) | 2021.0.6.0 |
| API 网关 | Spring Cloud Gateway | - |
| 远程调用 | OpenFeign | - |
| ORM | MyBatis + PageHelper | 3.5.13 / 1.4.7 |
| 数据库 | MySQL | 8.0.33 |
| 缓存 | Redis (Lettuce) | 3.9.0 |
| 消息队列 | RocketMQ | 2.2.3 |
| 对象存储 | MinIO | 8.3.0 |
| 鉴权 | JWT (jjwt) | 0.11.5 |
| 文档解析 | Apache Tika | 2.9.1 |
| 工具库 | Hutool, Lombok | 5.8.22 / 1.18.30 |

## 模块架构

```
liuhao-talentforge/
├── lingxi-common      # 公共模块（工具类、异常、枚举、拦截器、配置）
├── lingxi-gateway     # API 网关（路由转发、鉴权过滤、跨域、限流）
├── lingxi-user        # 用户服务（注册、登录、认证、通知、AI对话）
├── lingxi-job         # 岗位服务（职位管理、投递收藏、AI出题）
├── lingxi-resume      # 简历服务（简历解析、投递管理）
├── lingxi-hr          # HR 服务（候选人管理、面试、Offer、仪表盘）
├── lingxi-admin       # 管理后台服务
└── pom.xml            # 父 POM
```

## 服务端口与路由

| 服务 | 端口 | 路由前缀 |
|------|------|----------|
| `lingxi-gateway` | 8080 | / (统一入口) |
| `lingxi-user` | 8081 | /api/v1/auth/\*\*, /api/v1/user/\*\*, /api/v1/agent/\*\*, /api/v1/conversations/\*\*, /api/v1/notifications/\*\*, /api/v1/company/\*\* |
| `lingxi-job` | 8082 | /api/v1/jobs/\*\*, /api/v1/hr/jobs/\*\*, /api/v1/hr/questions/\*\*, /api/v1/favorites/\*\* |
| `lingxi-resume` | 8083 | /api/v1/resumes/\*\*, /api/v1/applications/\*\* |
| `lingxi-hr` | 8084 | /api/v1/hr/candidates/\*\*, /api/v1/hr/interviews/\*\*, /api/v1/hr/offers/\*\*, /api/v1/hr/dashboard/\*\* |
| `lingxi-admin` | 8085 | /api/v1/admin/\*\* |

各服务间通过 `/internal/**` 路由进行内部 Feign 调用。

## 角色体系

| 角色 | 标识 | 说明 |
|------|------|------|
| 求职者 | CANDIDATE | 浏览岗位、投递简历、查看进度 |
| HR | HR | 发布岗位、管理候选人、安排面试、发送 Offer |
| 面试官 | INTERVIEWER | 参与面试、提交评价 |
| 管理员 | ADMIN | 平台管理 |

## 环境依赖

- JDK 1.8+
- Maven 3.6+
- MySQL 8.0+
- Redis 5.0+
- RocketMQ 4.x+
- Nacos 2.x+
- MinIO (可选，文件存储)
- Sentinel (通过 Spring Cloud Alibaba 集成)

## 快速开始

### 1. 启动基础设施

确保以下服务已启动：

- MySQL（默认 `127.0.0.1:3306`，数据库名 `lingxi`）
- Redis（默认 `127.0.0.1:6379`）
- Nacos（默认 `127.0.0.1:8848`，命名空间 `lingxi`）
- RocketMQ NameServer + Broker

### 2. 配置数据库

创建数据库并导入初始化脚本：

```sql
CREATE DATABASE IF NOT EXISTS lingxi DEFAULT CHARSET utf8mb4;
```

### 3. 配置 Nacos

在 Nacos 控制台创建命名空间 `lingxi`，并上传各服务的配置文件（或使用本地 `application.yml` 和 `bootstrap.yml` 兜底）。

### 4. 编译运行

```bash
# 编译全部模块
mvn clean install -DskipTests

# 按顺序启动服务
# 1. Gateway
cd lingxi-gateway && mvn spring-boot:run

# 2. 各业务服务
cd lingxi-user && mvn spring-boot:run
cd lingxi-job && mvn spring-boot:run
cd lingxi-resume && mvn spring-boot:run
cd lingxi-hr && mvn spring-boot:run
cd lingxi-admin && mvn spring-boot:run
```

### 5. 验证

```bash
# 检查 Nacos 注册中心，确认所有服务已注册
curl http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=lingxi-gateway

# 通过网关访问
curl http://127.0.0.1:8080/api/v1/auth/login
```

## 公共模块 (lingxi-common) 功能清单

| 功能 | 类 | 说明 |
|------|-----|------|
| 统一响应 | `Result`, `PageResult` | API 标准返回格式 |
| 异常处理 | `GlobalExceptionHandler`, `BusinessException` | 全局异常拦截 + 业务异常 |
| JWT 鉴权 | `JwtUtil`, `AuthInterceptor` | Token 生成/校验/续期、拦截器 |
| 用户上下文 | `UserContext`, `UserDTO` | ThreadLocal 传递用户信息 |
| 幂等控制 | `IdempotentUtil` | 防重复提交 |
| 雪花 ID | `SnowflakeIdUtil` | 分布式 ID 生成 |
| 注解鉴权 | `@RequireLogin`, `@RequireRole`, `@RequireCertification` | 声明式权限控制 |
| 数据权限 | `DataPermissionInterceptor` | MyBatis 数据范围过滤 |
| 文件服务 | `MinioUtil`, `OssUtil` | 对象存储上传下载 |
| JSON | `JsonUtil` | Jackson 序列化封装 |
| 短信 | `SmsUtil` | 短信发送 |
| 事件模型 | `ApplicationEvent`, `DiagnosisEvent`, `InterviewEvent` | 领域事件定义 |

## 开发状态

| 模块 | 状态 | 说明 |
|------|------|------|
| `lingxi-common` | 已完成 | 基础设施完备 |
| `lingxi-gateway` | 已完成 | 路由、鉴权、异常处理已配置 |
| `lingxi-user` | 开发中 | 骨架已搭建 |
| `lingxi-job` | 开发中 | 骨架已搭建，含 Agent/Tools 预留 |
| `lingxi-resume` | 开发中 | 骨架已搭建 |
| `lingxi-hr` | 开发中 | 骨架已搭建 |
| `lingxi-admin` | 开发中 | 骨架已搭建 |

## 项目信息

- **Group ID**: `com.lingxi`
- **Artifact ID**: `lingxi-backend`
- **版本**: `1.0.0-SNAPSHOT`
- **作者**: lingxi-team
- **创建日期**: 2026-07-31
