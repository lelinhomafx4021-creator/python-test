# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 用户身份与协作方式

- 用户是学生/学习者，正在通过这个项目学习技术
- **教学优先于执行**：每一步都要解释原理和为什么，不要只给结果
- 遇到新概念、新技术、新工具时，主动科普背景知识
- 有多种实现方式时，简要说明各自的优缺点和适用场景
- 代码注释可以省略，但口头讲解不能省
- 教学协议详见 `.agents/skills/ai-investor-dev/SKILL.md`

## 项目概述

面向投顾会员场景的 AI 原生投资工作台，把会员体系、行情查询、自选股、AI 投研副驾、模拟交易、AI 转人工闭环串起来的产品骨架。

## 技术栈速查

| 层 | 技术 | 版本 |
|---|---|---|
| 前端 | Vue 3 + TypeScript + Tailwind CSS 4 + ECharts | Vite 6 |
| Java 网关 | Spring Boot 3.4 + MyBatis-Plus + Spring Security | Java 21 |
| Python AI | FastAPI + LangGraph + LangChain | Python 3.12 (venv) |
| 数据库迁移 | Flyway (Java) / Alembic (Python) | — |
| 包管理 | npm (前端) / Maven (Java) / pip -e (Python) | — |

## 常用命令

### 一键启动（推荐）

```powershell
.\start_all.ps1
```

脚本自动完成：检查依赖 → 启动 Docker 中间件 → 启动三个服务 → 健康检查 → 输出访问地址。

### 分模块启动

**中间件（先行）：**
```bash
docker compose up -d
```

**Python AI 服务（端口 8000）：**
```bash
cd aipy2
python main.py
```

**Java 网关（端口 8080）：**
```bash
cd java-ai-gateway
mvn spring-boot:run
```

**前端（端口 5173）：**
```bash
cd frontend
npm install
npm run dev
```

### 构建 & 检查

```bash
# 前端类型检查 + 构建
cd frontend && npm run build

# 前端 lint
cd frontend && npm run lint
cd frontend && npm run lint:fix   # 自动修复

# Java 编译
cd java-ai-gateway && mvn compile

# Python 无编译步骤，安装依赖用 pip install -e aipy2/
```

### 测试

```bash
# Java 单测
cd java-ai-gateway && mvn test

# Java 单个测试类
cd java-ai-gateway && mvn test -Dtest=MarketServiceTest
```

### 中间件

| 服务 | 端口 | 用途 |
|------|------|------|
| MySQL | 3306 | 业务数据（用户、会员、自选、订单） |
| Redis | 6379 | 缓存 / Session |
| RabbitMQ | 5672 | 异步消息（AI 审计、工单等） |
| Postgres + pgvector | 5432 | AI 状态持久化、向量存储 |
| Langfuse | 3000 | LLM 调用链追踪 |
| Sentinel | 8858 | 流量控制控制台 |

## 架构

采用 **Java + Python 双后端**：

```
Vue 3 Frontend (5173)
  → Java Gateway (8080) — 主业务真相：身份/会员/行情/自选/模拟交易/AI会话/人工工单
    → Python AI (8000) — 模型调用、SSE 流式输出、LangGraph、RAG
```

- **Java 管业务**：高并发 IO、数据库审计、消息队列、对外 API
- **Python 管 AI**：Agent 编排、流式问答、RAG、LangGraph 状态图
- **关键设计**：AI 问答通过 Java 透传到 Python，SSE 流式输出原路返回前端；LangGraph checkpoint 持久化到 Postgres
- **可观测**：LangFuse 追踪 Agent 内部每个 Node 的耗时与 Token 消耗

## 前端架构

**路由结构** (`frontend/src/router/index.ts`)：
- `/` → `LandingPage.vue`（宣传页，已登录自动跳 `/overview`）
- `/vip-apply` → VIP 申请页
- `/admin` → `AppAdmin.vue`（管理后台）
- `/*` → `AppTerminal.vue`（主工作台壳，内部根据 navKey 切换视图）

**状态管理**：无 Vuex/Pinia，使用 `api/index.ts` 中的 `reactive` store 对象集中管理全局状态。

**API 层** (`frontend/src/api/index.ts`)：
- 所有 HTTP 请求通过 axios 封装，Token 存 `localStorage('ai-investor-token')`
- SSE 流式用 `fetch` + `ReadableStream` 实现（原生 EventSource 不支持自定义 Header）
- 开发环境直连 `http://127.0.0.1:8080`，部署时通过 `VITE_API_BASE_URL` 环境变量切换

**前端有 Husky + lint-staged**：提交时自动对 `*.ts, *.vue` 执行 eslint --fix + vue-tsc --noEmit。

## Java 网关架构

**模块化按业务域划分** (`java-ai-gateway/src/main/java/com/aiinvestor/gateway/modules/`)：

| 模块 | 职责 |
|------|------|
| `identity/` | 注册、登录、用户资料、邮箱验证 |
| `membership/` | 会员计划、权益、配额 |
| `market/` | 行情查询、板块、股票搜索 |
| `watchlist/` | 自选股分组管理 |
| `papertrading/` | 模拟账户、持仓、订单、交易流水 |
| `ai/` | AI 会话入口、SSE 透传、审计、转人工工单 |
| `admin/` | 管理后台（用户管理、工单处理、公告） |
| `shared/` | 通用组件：`ApiResult` 统一响应、`UserContext` 线程上下文、`@LoginRequired` 注解、全局异常处理 |

**鉴权**：Spring Security + 自定义 `@LoginRequired` 注解，通过 `UserContext` ThreadLocal 传递当前用户。

**数据库迁移**：Flyway，SQL 脚本在 `src/main/resources/db/migration/V{N}__*.sql`。

## Python AI 架构

**目录结构** (`aipy2/app/`)：

| 目录 | 职责 |
|------|------|
| `api/v1/` | FastAPI 路由：chat、kline、news、util、vip |
| `graph/` | LangGraph 工作流：`investor_graph.py` 构建状态图，`nodes.py` 定义各节点，`routes.py` 定义条件路由 |
| `core/` | 配置 (`config.py`)、LLM 初始化 (`llm.py`)、日志、数据库连接 |
| `rag/` | RAG 组件：向量存储、文档解析、分块 |
| `tools/` | Agent 工具：股票数据、新闻检索、向量检索 |
| `skills/` | 高级技能编排（如股票分析技能） |
| `prompts/` | Prompt 模板 |

**LangGraph 工作流**（`app/graph/investor_graph.py`）：
- 普通用户精简流：`intent → lite_rewrite → search → answer → END`
- VIP 用户完整流：`intent → rewrite → search/fetch_data → answer → critic → END`（critic 不通过可打回 rewrite）
- 关键节点：意图识别、查询重写、混合检索（向量 + BM25 + RRF 融合）、专家评审

**配置**：通过 `aipy2/.env` 文件管理，使用 pydantic-settings 自动加载。首次启动需从 `.env.example` 复制并补齐 API 密钥。

**Windows 兼容**：`main.py` 中自动切换 `SelectorEventLoop`（解决 psycopg 异步兼容问题）。

## 演示账号

- 用户名：`admin`，密码：`123456`

## 演示路线

**AI 投研 + 转人工闭环：** 登录 → AI 会话页发起投研问题（观察 SSE 流式） → 输入"转人工"触发升级 → 人工工单面板查看 → 工单返回原会话

**会员工作台：** 登录 → 查看会员信息 → 查行情 → 创建自选股分组 → 初始化模拟账户 → 下单
