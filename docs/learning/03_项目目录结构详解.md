# AI-Investor 项目目录结构详解

> 了解每个目录是干嘛的，代码该往哪里放。

## 总览

```
ai-investor/
├── aipy2/                 # Python 后端（AI 服务）
├── java-ai-gateway/       # Java 后端（主业务网关）
├── frontend/              # Vue 3 前端
├── docker-compose.yml     # 中间件编排
├── start_all.ps1          # 一键启动脚本
└── docs/                  # 文档
    └── learning/          # 学习笔记
```

三个服务各管各的，通过 HTTP 互相调用：
```
前端 → Java 网关（主业务）→ Python AI 服务（模型调用）
```

---

## 一、Java 网关（java-ai-gateway）

### 整体结构

```
java-ai-gateway/
├── pom.xml                          # Maven 依赖管理（类似 Python 的 requirements.txt）
├── src/main/java/com/aiinvestor/gateway/
│   ├── JavaAiGatewayApplication.java  # 启动入口
│   ├── config/                        # 全局配置（跨模块通用）
│   └── modules/                       # 业务模块（按领域划分）
└── src/main/resources/
    ├── application.yml                # 应用配置（数据库、端口等）
    └── db/migration/                  # 数据库迁移文件（Flyway）
```

### config/ — 全局配置

| 文件 | 作用 |
|------|------|
| `GlobalExceptionHandler.java` | 全局异常处理器，统一错误响应格式 |
| `SecurityConfig.java` | 安全配置（哪些接口需要登录） |
| `WebClientConfig.java` | HTTP 客户端配置（调用 Python 服务用） |
| `WebMvcConfig.java` | Web MVC 配置（跨域等） |
| `SwaggerConfig.java` | API 文档配置 |
| `SentinelRuleConfig.java` | 限流降级配置 |
| `RabbitConfig.java` | 消息队列配置 |
| `CorsProperties.java` | 跨域属性 |
| `PythonAiProperties.java` | Python 服务地址配置 |
| `SentinelProperties.java` | Sentinel 属性 |

### modules/ — 业务模块

每个模块遵循**统一的分层结构**：

```
模块名/
├── controller/    # 控制器层：接收 HTTP 请求，返回 JSON
├── service/       # 服务层：业务逻辑
├── dao/           # 数据访问层
│   ├── entity/    # 实体类（对应数据库表）
│   └── mapper/    # MyBatis-Plus Mapper 接口
├── dto/           # 数据传输对象（请求参数）
├── vo/            # 视图对象（返回给前端的数据）
├── mq/            # 消息队列（异步处理）
└── config/        # 模块专属配置
```

**数据流向：**
```
Controller → Service → Mapper → 数据库
   ↓           ↓
  DTO        VO → 返回给前端
```

### 各模块职责

| 模块 | 职责 | 核心表 |
|------|------|--------|
| `identity` | 用户注册、登录、认证、个人资料 | `users`、`user_profiles` |
| `membership` | 会员方案、配额管理、VIP 申请审核 | `membership_plans`、`user_memberships`、`user_feature_quotas`、`vip_applications` |
| `market` | 行情查询、股票搜索、板块 | `stocks`、`sectors`、`market_quotes` |
| `watchlist` | 自选股分组管理 | `watchlists`、`watchlist_items` |
| `papertrading` | 模拟交易（下单、持仓、充值） | `paper_accounts`、`paper_positions`、`paper_orders`、`paper_trades`、`paper_daily_assets`、`paper_cash_transfers`、`transaction_logs` |
| `ai` | AI 会话管理、聊天历史、人工兜底工单 | `ai_sessions`、`ai_chat_turns`、`ai_handoff_tickets`、`ai_usage_records`、`ai_chat_audit` |
| `admin` | 管理后台（用户管理、会员修改、工单处理、公告管理） | 不直接管表，调用其他模块的 Service |
| `shared` | 跨模块公共能力（通知、公告、缓存、异常、注解） | `user_notifications`、`announcements` |
| `ops` | 运维配置 | `system_configs` |

### 一个模块的完整示例（identity）

```
identity/
├── controller/
│   ├── AuthController.java          # 登录、注册接口
│   └── IdentityController.java      # 个人资料接口
├── service/
│   ├── UserService.java             # 用户业务逻辑
│   ├── IdentityService.java         # 身份认证逻辑
│   ├── EmailVerificationService.java # 邮箱验证码
│   ├── AliyunOssService.java        # 头像上传
│   └── MockEmailDeliveryService.java # 模拟发邮件（开发用）
├── dao/
│   ├── entity/
│   │   ├── UserDO.java              # 用户表实体（DO = Data Object）
│   │   └── UserProfileDO.java       # 用户画像实体
│   └── mapper/
│       ├── UserMapper.java          # 用户表 Mapper
│       └── UserProfileMapper.java   # 用户画像 Mapper
├── dto/
│   ├── LoginRequest.java            # 登录请求参数
│   ├── RegisterRequest.java         # 注册请求参数
│   └── UpdateUserProfileRequest.java # 更新资料请求
├── vo/
│   ├── LoginUserVO.java             # 登录返回数据
│   └── UserProfileVO.java           # 个人资料返回数据
├── interceptor/
│   └── LoginInterceptor.java        # 登录拦截器
└── config/
    └── AliyunOssProperties.java     # OSS 配置属性
```

### 命名约定

| 后缀 | 全称 | 含义 |
|------|------|------|
| `DO` | Data Object | 数据库实体，对应一张表 |
| `VO` | View Object | 返回给前端的数据 |
| `DTO` | Data Transfer Object | 前端传给后端的请求参数 |
| `Mapper` | — | 数据库操作接口（类似 DAO） |
| `Service` | — | 业务逻辑 |
| `Controller` | — | HTTP 接口 |

---

## 二、Python AI 服务（aipy2）

### 整体结构

```
aipy2/
├── main.py                    # FastAPI 启动入口
├── requirements.txt           # Python 依赖
├── app/
│   ├── api/v1/               # API 路由层
│   ├── core/                 # 核心配置（LLM、数据库、日志）
│   ├── graph/                # LangGraph 工作流
│   ├── models/               # 数据模型
│   ├── prompts/              # AI 提示词
│   ├── rag/                  # RAG 检索增强
│   ├── schemas/              # 请求/响应 Schema
│   ├── services/             # 业务逻辑
│   ├── skills/               # AI 技能
│   └── tools/                # AI 工具（调用外部数据）
└── tests/                    # 测试
```

### 各目录职责

| 目录 | 作用 | 关键文件 |
|------|------|---------|
| `api/v1/` | HTTP 接口层 | `chat.py`（AI 聊天）、`kline.py`（K 线）、`news.py`（新闻）、`vip.py`（VIP） |
| `core/` | 基础设施 | `config.py`（配置）、`llm.py`（大模型调用）、`db.py`（数据库）、`logger.py`（日志） |
| `graph/` | LangGraph 工作流 | `investor_graph.py`（投资分析图）、`state.py`（状态定义）、`nodes.py`（节点）、`routes.py`（路由） |
| `rag/` | 检索增强生成 | `chunker.py`（文本分块）、`parser.py`（文档解析）、`vector_store.py`（向量存储） |
| `tools/` | AI 可调用的工具 | `data_fetcher.py`（数据获取）、`stockdata_tool.py`（股票数据）、`news_tool.py`（新闻）、`retriever_tool.py`（RAG 检索） |
| `prompts/` | 提示词模板 | `investor_prompts.py` |
| `schemas/` | 数据格式定义 | `chat_schema.py` |
| `services/` | 业务逻辑 | `investor_service.py` |
| `skills/` | AI 技能封装 | `stock_analysis_skill.py` |

### 数据流向

```
前端/Java 网关
      ↓
  api/v1/chat.py（接收请求）
      ↓
  services/investor_service.py（业务逻辑）
      ↓
  graph/investor_graph.py（LangGraph 编排）
      ↓
  tools/ + prompts/（调用工具、拼接提示词）
      ↓
  core/llm.py（调用大模型）
      ↓
  返回结果
```

---

## 三、Vue 3 前端（frontend）

### 整体结构

```
frontend/
├── package.json              # Node 依赖管理
├── vite.config.ts            # Vite 构建配置
├── index.html                # 入口 HTML
├── public/                   # 静态资源
└── src/
    ├── main.ts               # 应用入口
    ├── App.vue               # 根组件
    ├── AppTerminal.vue       # 用户端根组件
    ├── AppAdmin.vue          # 管理端根组件
    ├── api/index.ts          # API 请求层（所有接口集中在这里）
    ├── types/terminal.ts     # TypeScript 类型定义
    ├── router/index.ts       # 路由配置
    ├── views/                # 页面组件
    ├── components/           # 通用组件
    ├── composables/          # 组合式函数（Vue 3 特有）
    └── utils/                # 工具函数
```

### views/ — 页面组件

| 文件 | 作用 | 对应路由 |
|------|------|---------|
| `LandingPage.vue` | 首页/着陆页 | `/` |
| `TerminalAuth.vue` | 登录/注册 | `/auth` |
| `TerminalOverview.vue` | 会员总览（仪表盘） | `/overview` |
| `TerminalChat.vue` | AI 智能副驾 | `/chat` |
| `TerminalWatchlists.vue` | 自选股管理 | `/watchlist` |
| `TerminalPaper.vue` | 模拟交易终端 | `/paper` |
| `TerminalTransactions.vue` | 交易记录 | `/transactions` |
| `TerminalNews.vue` | 财经热点 | `/news` |
| `TerminalHandoff.vue` | 人工工单 | `/handoff` |
| `TerminalProfile.vue` | 个人中心 | `/profile` |
| `TerminalAdmin.vue` | 管理后台 | `/admin` |
| `TerminalAdminTickets.vue` | 工单处理 | `/admin-tickets` |
| `VipApply.vue` | VIP 申请 | `/vip-apply` |

### components/ — 通用组件

| 文件 | 作用 |
|------|------|
| `TerminalHeader.vue` | 顶部导航栏（标题、会员标识、时钟、刷新、通知） |
| `TerminalSidebar.vue` | 侧边栏（导航菜单、会员标识、升级按钮） |
| `PaginationBar.vue` | 分页组件 |
| `ToastNotification.vue` | Toast 消息提示 |
| `NewsFeed.vue` | 新闻列表 |
| `PortfolioPieChart.vue` | 持仓饼图 |
| `EquityCurve.vue` | 权益曲线图 |
| `KLineChart.vue` | K 线图 |

### composables/ — 组合式函数

| 文件 | 作用 |
|------|------|
| `useMarketWebSocket.ts` | 行情 WebSocket 连接（实时推送股价） |
| `useToast.ts` | Toast 消息管理 |

### utils/ — 工具函数

| 文件 | 作用 |
|------|------|
| `format.ts` | 时间、金额格式化 |
| `lazyEcharts.ts` | 懒加载 ECharts 图表库 |

### api/index.ts — API 请求层

这是前端最重要的文件，所有与后端的交互都集中在这里：

```
api/index.ts
├── store（reactive）      # 全局状态（用户、会员、行情、会话等）
├── 认证函数               # login、register、logout、fetchMe
├── 会员函数               # refreshTerminal、pollMembership
├── 行情函数               # fetchQuotes、fetchMarketStocks
├── 自选函数               # fetchWatchlists、createWatchlist
├── 交易函数               # submitOrder、cancelOrder、deposit、withdraw
├── AI 函数                # sendChat、newChat、loadSession
├── 管理函数               # fetchAdmin、updateUserRole
├── 公告函数               # fetchAnnouncements、createAnnouncement
└── 工单函数               # fetchTickets
```

### 三个根组件的分工

| 组件 | 作用 | 路由 |
|------|------|------|
| `App.vue` | 路由容器，根据路径切换 | — |
| `AppTerminal.vue` | 用户端主框架（侧边栏 + 内容区） | `/overview`、`/chat` 等 |
| `AppAdmin.vue` | 管理端主框架（管理后台 + 工单处理） | `/admin` |

---

## 四、模块间的对应关系

```
前端页面          Java 模块         数据库表
─────────       ──────────       ──────────
登录/注册    →   identity     →   users
会员总览    →   membership   →   membership_plans, user_memberships, user_feature_quotas
行情/自选   →   market + watchlist → stocks, sectors, market_quotes, watchlists, watchlist_items
模拟交易    →   papertrading  →   paper_*, transaction_logs
AI 会话     →   ai + (调用 Python) → ai_sessions, ai_chat_turns, ai_handoff_tickets
管理后台    →   admin         →   调用各模块 Service
通知/公告   →   shared        →   user_notifications, announcements
```

---

## 五、新增功能该往哪里加

### 新增一个 Java API 接口

```
1. 在对应模块的 dao/entity/ 创建实体类（DO）
2. 在 dao/mapper/ 创建 Mapper 接口
3. 在 service/ 创建服务类
4. 在 controller/ 创建接口
5. 在 dto/ 创建请求对象（需要校验时）
6. 在 vo/ 创建返回对象
7. 在 resources/db/migration/ 添加数据库迁移文件
```

### 新增一个前端页面

```
1. 在 views/ 创建 Vue 组件
2. 在 types/terminal.ts 添加类型定义
3. 在 api/index.ts 添加 API 函数和 store 字段
4. 在 router/index.ts 添加路由
5. 在 AppTerminal.vue 或 AppAdmin.vue 中引用组件
```

### 新增一个 Python AI 功能

```
1. 在 tools/ 创建工具函数
2. 在 prompts/ 添加提示词
3. 在 graph/ 添加节点和路由
4. 在 api/v1/ 添加接口
```
