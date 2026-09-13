# 灵犀互聘 — 前端项目

AI 全链路智能招聘平台前端，基于 React 18 + TypeScript 5 + Umi 4 + Ant Design 5 + Zustand 4。

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 框架 | React | 18.x |
| 类型系统 | TypeScript | 5.x |
| 路由/构建 | Umi | 4.x |
| UI 组件库 | Ant Design | 5.x |
| 状态管理 | Zustand | 4.x |
| 图表 | AntV G2 / G6 | 5.x |
| HTTP 请求 | Axios | 1.x |
| 大数据精度 | json-bigint | 1.x |
| Markdown 渲染 | react-markdown | 10.x |
| 样式 | Less + CSS Modules | - |

## 快速开始

```bash
cd front

# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 构建生产包
npm run build

# 本地预览构建产物
npm run preview
```

开发服务器默认运行在 `http://localhost:8000`。

## 代理与接口约定

开发环境代理配置在 `.umirc.ts`，后端网关默认 `http://localhost:8080`：

| 路径 | 用途 |
|------|------|
| `/api/*` | REST 接口（含 SSE 流式接口，已关闭缓冲） |
| `/files/*` | 文件访问（简历附件等） |
| `/ws/*` | WebSocket 长连接（实时消息/通知） |

接口统一响应格式为 `{ code: number, message: string, data: T }`，**统一成功码为 200**，由请求拦截器 `src/utils/request.ts` 集中判断，页面与 service 层无需自行判断。拦截器已内置：

- Token 自动附带、过期自动刷新（`/v1/auth/refresh-token`）与并发请求排队
- 401 / 403 / 用户禁用（1004）自动清理登录态并跳转登录页
- 雪花 ID（19 位）经 json-bigint 解析为字符串，避免 JS Number 精度丢失

## 项目结构

```
src/
├── app.tsx                    # 应用入口 + 路由守卫（ErrorBoundary、角色鉴权、开发绕过）
├── access.ts                  # 权限定义（CANDIDATE / HR / INTERVIEWER / ADMIN）
├── global.less                # 全局样式 + 设计 Token（CSS 变量）
│
├── pages/                     # 页面（C/B/A 三端 + 通用）
│   ├── login/                 #   登录 / 管理员登录 / 注册
│   ├── certification/         #   企业认证等待页
│   ├── candidate/             #   C端 求职者（岗位/简历/投递/收藏/AI/消息/个人中心 等）
│   ├── hr/                    #   B端 HR（看板/岗位/题库/候选人/面试/Offer/公司/个人中心 等）
│   ├── interviewer/           #   B端 面试官（面试/候选人/消息/个人中心）
│   └── admin/                 #   A端 管理后台（企业审核/企业/HR/面试官/岗位/公告/审计/配置 等）
│
├── components/                # 共享组件
│   ├── Layout/                #   3 套布局（CandidateLayout / HRLayout / AdminLayout）
│   ├── Message/               #   聊天组件集（ChatArea / ConversationList / MessageBubble / MessageInput）
│   ├── JobCard/               #   岗位卡片
│   ├── StatCard/              #   统计卡片
│   ├── StatusTag/             #   状态标签
│   ├── StatusTimeline/        #   状态时间线
│   ├── RadarChart/            #   雷达图（能力评估）
│   ├── PageHeader/            #   页面标题
│   ├── CPageHero/ PageHero/   #   页面首屏
│   ├── MarkdownRenderer/      #   Markdown 渲染
│   ├── ErrorBoundary/         #   全局错误边界
│   └── EmptyState/            #   空状态
│
├── services/                  # API 层（已对接后端）
│   ├── auth.ts                #   认证（验证码/登录/注册/刷新）
│   ├── user.ts                #   用户/账号
│   ├── job.ts                 #   岗位
│   ├── resume.ts              #   简历
│   ├── application.ts         #   投递
│   ├── favorite.ts            #   收藏
│   ├── hr.ts                  #   HR
│   ├── interview.ts           #   面试协同
│   ├── offer.ts               #   Offer
│   ├── questionBank.ts        #   企业题库
│   ├── company.ts             #   企业
│   ├── message.ts             #   站内消息
│   ├── notification.ts        #   通知
│   ├── mockInterview.ts       #   AI 模拟面试
│   ├── agent.ts               #   Agent 服务
│   └── admin.ts               #   管理服务
│
├── stores/                    # 状态管理（Zustand）
│   ├── userStore.ts           #   用户状态（persist 持久化）
│   └── messageStore.ts        #   消息/会话状态
│
├── hooks/                     # 自定义 Hook
│   ├── useAuth.ts             #   认证
│   ├── useSSE.ts              #   SSE 流式通信
│   ├── useWebSocket.ts        #   WebSocket 消息
│   ├── useNotification.ts     #   通知
│   ├── useMockInterview.ts    #   AI 模拟面试
│   ├── useJobFavorites.ts     #   岗位收藏
│   ├── useRegister.ts         #   注册流程
│   └── useCountdown.ts        #   验证码倒计时
│
├── constants/                 # 常量
│   ├── enums.ts               #   枚举（角色/状态/学历等）
│   ├── apiCodes.ts            #   API 成功码（统一 200）
│   ├── apiTypes.ts            #   接口类型
│   ├── errorCodes.ts          #   错误码映射
│   ├── routes.ts              #   路由路径常量
│   └── questionBank.ts        #   题库常量
│
├── utils/                     # 工具函数
│   ├── request.ts             #   Axios 封装（拦截器：Token/错误码/大整数）
│   ├── token.ts               #   Token 管理（含过期刷新）
│   ├── storage.ts             #   localStorage 封装
│   ├── permission.ts          #   权限工具
│   ├── apiError.ts            #   业务错误类型
│   ├── userMapper.ts          #   用户信息映射
│   ├── json.ts                #   JSONBigString 解析
│   ├── requestCache.ts        #   请求缓存
│   ├── sseClient.ts           #   SSE 客户端
│   ├── websocket.ts           #   WebSocket 封装
│   ├── fileUrl.ts             #   文件 URL 处理
│   ├── validators.ts          #   校验工具
│   └── sound.ts               #   提示音
│
└── mock/                      # Mock 数据
    └── data.ts
```

## 各端入口

| 端 | 入口 URL | 色系 |
|---|----------|------|
| 求职者（C端） | `/candidate/home` | Coral `#FF6B6B` |
| HR（B端） | `/hr/dashboard` | Teal `#4ECDC4` |
| 面试官（B端） | `/interviewer/interview` | Sky Blue `#45B7D1` |
| 管理后台（A端） | `/admin/dashboard` | Purple `#A855F7` |
| 登录 | `/login` | — |
| 注册 | `/register` | — |

各端布局文件：`src/components/Layout/` 下的 `CandidateLayout.tsx` / `HRLayout.tsx` / `AdminLayout.tsx`（面试官端复用 HRLayout）。

## 设计系统

设计 Token 统一定义于 `src/global.less`，通过 CSS 变量按端换肤（`.c-mode` / `.b-mode` / `.a-mode`），详细规范见 `docs/前端开发规范文档.md`。

- **字体**：Outfit（英文标题）+ Noto Sans SC（中文正文）+ JetBrains Mono（等宽数字）
- **圆角**：6px / 10px / 14px / 18px / 24px / 32px / 9999px
- **间距**：8px 基础单位
- **卡片**：白底 + 边框 + 分层阴影 + hover 上浮
- **按钮**：accent 渐变背景 + 阴影 + 按下弹性动效

## 开发规范

- 使用 CSS Modules（`.less`），禁止内联样式
- 组件 Props 必须定义 TypeScript interface
- 命名：组件大驼峰 `JobCard.tsx`，工具小驼峰 `request.ts`
- 导入顺序：React → 第三方 → 本地组件 → 工具 → 类型
- 状态：全局用 Zustand Store，局部用 useState
- 接口成功码统一由拦截器判断，页面通过 `isSuccess` / `getErrorCode` 处理业务分支

## 部署

构建产物为纯静态 SPA（浏览器 history 路由），部署详见 `docs/前端部署指南.md`（Nginx + 反向代理 + WebSocket/SSE 配置）。

## 相关文档

- `docs/前端开发规范文档.md` — 开发规范
- `docs/前端部署指南.md` — 部署指南
- `docs/前端总体系分.md` — 前端总体设计
- `docs/后端总体系分文档.md` — 后端总体设计（接口契约）

## License

Private
