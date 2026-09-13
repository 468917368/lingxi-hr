<div align="center">

# 🚀 灵犀互聘 · LingXi HR

**AI 全链路智能招聘平台**

一个融合 4 大 AI Agent 的企业级招聘系统，覆盖 **求职者、HR、管理员** 三端，贯穿从职位发布到 Offer 发放的完整招聘闭环。

[![Java](https://img.shields.io/badge/Java-8-orange)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.18-green)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud%20Alibaba-2021.0.6-blue)](https://github.com/alibaba/spring-cloud-alibaba)
[![React](https://img.shields.io/badge/React-18.3-61DAFB)](https://react.dev/)
[![Umi](https://img.shields.io/badge/Umi-4.3-purple)](https://umijs.org/)
[![Ant Design](https://img.shields.io/badge/Ant%20Design-5.22-blue)](https://ant.design/)

</div>

---

## ✨ 产品特性

### 🤖 四大 AI Agent

| Agent | 能力 | 服务端 |
|-------|------|--------|
| **Job Agent** | 自然语言对话求职，SSE 流式推荐，智能匹配 | 求职者端 |
| **Resume Agent** | 简历智能解析（Tika + LLM），10 维能力雷达图，AI 简历诊断 | 求职者端 |
| **Interview Agent** | 根据岗位 JD + 候选人简历自动生成面试题（基础/项目/边界/综合） | HR 端 |
| **Mock Interview Agent** | AI 模拟面试官，实时评分 + 面试报告生成 | 求职者端 |

### 👤 求职者端（C-端）

- 📱 手机号 + 验证码注册登录
- 📄 简历上传（PDF/Word）→ AI 自动解析为结构化数据
- 🔍 智能职位搜索（城市、薪资、技术栈、匹配度多维筛选）
- 💬 AI 对话式求职助手（自然语言 → 职位推荐）
- 📊 简历诊断报告 + 10 维能力雷达图
- 📋 投递状态全流程跟踪（已投递 → 已查看 → 筛选中 → 面试中 → 待发 Offer → 已发 Offer）
- 🎯 AI 模拟面试练习，实时评分
- 💼 收藏职位、站内消息、隐私设置（隐身模式）

### 🏢 HR / 面试官端（B-端）

- 📊 招聘数据看板（今日投递、待筛选、面试安排、月度入职）
- 📝 职位管理（AI 解析 JD，草稿/发布/暂停/关闭状态流转，HC 管控）
- 🎯 人才库 AI Top-5 推荐（技能 35% + 项目 25% + 行业 15% + 软技能 15% + 薪资 10%）
- 🗓️ 面试协调（排期、AI 出题、评估录入）
- 📬 Offer 管理（发起、跟踪、催办、撤回，HC 预冻结机制）
- 🏠 企业管理（成员管理、邀请面试官、企业信息）
- 💬 站内消息、通知中心

### 🛡️ 管理员端（A-端）

- 📈 平台数据总览（注册用户、认证企业、活跃职位、投递总量）
- ✅ 企业认证审核（通过/驳回 + 原因）
- 👥 企业管理、HR 管理、面试官管理、职位管理、候选人管理
- 📢 系统公告（发布/撤回，支持目标人群选择）
- 📋 审计日志（180+ 天留存）
- ⚙️ 系统配置（平台名称、AI 模型设置、通知渠道）

---

## 🏗️ 技术架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端（三端）                               │
│    React 18 + Umi 4 + Ant Design 5 + Zustand + AntV (G2/G6)   │
└────────────────────────────┬────────────────────────────────────┘
                             │ HTTP / SSE / WebSocket
┌────────────────────────────▼────────────────────────────────────┐
│                    Spring Cloud Gateway (:8080)                  │
│                   路由 · 认证过滤 · 限流                          │
└──┬──────────┬──────────┬──────────┬──────────┬──────────────────┘
   │          │          │          │          │
   ▼          ▼          ▼          ▼          ▼
┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐
│User  │ │ Job  │ │Resume│ │  HR  │ │Chat  │ │Admin │
│:8081 │ │:8082 │ │:8083 │ │:8084 │ │      │ │:8085 │
└──┬───┘ └──┬───┘ └──┬───┘ └──┬───┘ └──┬───┘ └──┬───┘
   │        │        │        │        │        │
   └────────┴────────┴────────┴────────┴────────┘
          │         │          │         │
        MySQL     Redis      RocketMQ   MinIO
```

### 后端技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 8 |
| 核心框架 | Spring Boot | 2.7.18 |
| 微服务 | Spring Cloud Alibaba | 2021.0.6.0 |
| API 网关 | Spring Cloud Gateway | - |
| 注册/配置中心 | Nacos | - |
| 限流/熔断 | Sentinel | - |
| ORM | MyBatis | 3.5.13 |
| 数据库 | MySQL | 8.0.33 |
| 缓存 | Redis (Lettuce) | 3.9.0 |
| 消息队列 | RocketMQ | 2.2.3 |
| 文件存储 | MinIO | 8.3.0 |
| 文档解析 | Apache Tika | 2.9.1 |
| JWT 认证 | jjwt | 0.11.5 |
| API 文档 | Knife4j (Swagger) | 4.3.0 |
| 工具库 | Hutool | 5.8.22 |

### 前端技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| UI 框架 | React | 18.3 |
| 构建框架 | Umi | 4.3 |
| 组件库 | Ant Design | 5.22 |
| 状态管理 | Zustand | 4.5 |
| 数据可视化 | AntV G2 / G6 | 5.x |
| HTTP 客户端 | Axios | 1.7 |
| Markdown 渲染 | react-markdown | 10.1 |
| 类型系统 | TypeScript | 5.4 |

---

## 📁 项目结构

```
hr/
├── front/                          # 前端项目（Umi + React）
│   ├── src/
│   │   ├── pages/
│   │   │   ├── candidate/          # 求职者端页面
│   │   │   │   ├── home/           #   首页
│   │   │   │   ├── ai-assistant/   #   AI 求职助手
│   │   │   │   ├── job/            #   职位列表/详情
│   │   │   │   ├── resume/         #   简历管理/诊断
│   │   │   │   ├── application/    #   投递跟踪
│   │   │   │   ├── mock-interview/ #   AI 模拟面试
│   │   │   │   ├── favorites/      #   收藏
│   │   │   │   ├── message/        #   站内消息
│   │   │   │   ├── notification/   #   通知中心
│   │   │   │   └── profile/        #   个人中心
│   │   │   ├── hr/                 # HR/面试官端页面
│   │   │   │   ├── dashboard/      #   数据看板
│   │   │   │   ├── job/            #   职位管理
│   │   │   │   ├── candidate/      #   人才库
│   │   │   │   ├── interview/      #   面试协调
│   │   │   │   ├── offer/          #   Offer 管理
│   │   │   │   └── company/        #   企业管理
│   │   │   ├── admin/              # 管理员端页面
│   │   │   │   ├── dashboard/      #   平台总览
│   │   │   │   ├── enterprise-audit/#  认证审核
│   │   │   │   ├── enterprise/     #   企业管理
│   │   │   │   ├── announcement/   #   系统公告
│   │   │   │   ├── audit-log/      #   审计日志
│   │   │   │   └── config/         #   系统配置
│   │   │   └── login/              # 登录/注册
│   │   ├── components/             # 公共组件
│   │   ├── services/               # API 请求
│   │   ├── stores/                 # Zustand 状态管理
│   │   ├── hooks/                  # 自定义 Hooks
│   │   ├── utils/                  # 工具函数
│   │   └── constants/              # 常量定义
│   ├── .umirc.ts                   # Umi 配置
│   └── package.json
│
├── backend/                        # 后端项目（Spring Cloud 微服务）
│   └── liuhao-talentforge/
│       ├── lingxi-gateway/         # API 网关服务
│       ├── lingxi-user/            # 用户服务 + Job Agent
│       ├── lingxi-job/             # 职位服务 + Interview Agent
│       ├── lingxi-resume/          # 简历服务 + Resume Agent
│       ├── lingxi-hr/              # HR 服务 + Mock Interview Agent
│       ├── lingxi-chat/            # 聊天消息服务
│       ├── lingxi-admin/           # 管理后台服务
│       └── lingxi-common/          # 公共模块
│
├── docs/                           # 项目文档
│   ├── prd.md                      # 产品需求文档
│   ├── 前端总体系分.md              # 前端系统分析
│   ├── 后端总体系分文档.md          # 后端系统分析
│   ├── 成员分工表.md                # 团队分工
│   └── 前端开发规范文档.md          # 开发规范
│
└── .gitignore
```

---

## 🚀 快速开始

### 环境要求

| 环境 | 版本要求 |
|------|----------|
| JDK | 8+ |
| Maven | 3.6+ |
| Node.js | 16+ |
| MySQL | 8.0+ |
| Redis | 6.0+ |
| Nacos | 2.x |
| RocketMQ | 4.x+ |
| MinIO | 最新版 |

### 后端启动

```bash
# 1. 启动基础中间件（MySQL、Redis、Nacos、RocketMQ、MinIO）

# 2. 导入数据库（各服务 sql 文件位于 backend/docs/schema.sql）

# 3. 启动微服务（按顺序）
cd backend/liuhao-talentforge
# 先启动 Gateway，再启动其他服务
mvn spring-boot:run -pl lingxi-gateway
mvn spring-boot:run -pl lingxi-user
mvn spring-boot:run -pl lingxi-job
mvn spring-boot:run -pl lingxi-resume
mvn spring-boot:run -pl lingxi-hr
mvn spring-boot:run -pl lingxi-admin
mvn spring-boot:run -pl lingxi-chat
```

### 前端启动

```bash
cd front
npm install
npm run dev          # 求职者端 http://localhost:8000
```

---

## 👥 团队成员

> 5 人团队，每位成员全栈负责（前端 + 后端 + 数据库 + AI Agent），按垂直业务模块划分。

| 成员 | 核心职责 | 负责后端服务 | AI Agent | 前端模块 |
|:----:|---------|:----------:|:--------:|---------|
| A | 微服务脚手架 + 用户服务 + 认证 | Gateway, User, Chat | Job Agent（AI 求职助手） | C端：登录/注册、首页、AI助手、消息、个人中心；B端：消息 |
| B | 职位 CRUD + 搜索 + 匹配引擎 + HC 管理 | Job | Interview Agent（AI 出题） | C端：职位列表/详情；B端：职位管理 |
| C | 简历解析 + 投递跟踪 + 能力模型 | Resume | Resume Agent（简历诊断） | C端：简历上传/预览/诊断、雷达图、投递跟踪、收藏 |
| D | 面试协调 + Offer 流转 + 模拟面试 + 通知 | HR | Mock Interview Agent（AI 模拟面试） | B端：看板、人才库、面试、Offer、企业管理、通知；C端：模拟面试、通知 |
| E | 管理后台 + 部署运维 + Docker | Admin | - | A端：全部页面；Docker Compose 编排 |

---

## 📄 License

本项目仅供学习交流使用。
