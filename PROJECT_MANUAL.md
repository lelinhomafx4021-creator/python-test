# AI Investor 智能投研助手 · 项目说明书

> **定位**：校招面试级「全栈 AI Agent」示范项目，深度展示 LLM 工程化落地、多智能体协作与异步高并发架构。

---

## 一、项目概况

AI Investor 是一款基于 **LangGraph + FastAPI + Spring Boot + Vue 3** 的智能投研助手。用户提出投研问题后，AI 自动检索多路数据源、生成分析报告、经过专家评审（Critic Node）校验质量，未通过则自动打回重做——形成 Self-RAG 闭环纠错。

| 维度 | 方案 |
|------|------|
| **对话代理** | LangGraph StateGraph（6 节点 + 条件边） |
| **检索引擎** | 向量 (pgvector/HNSW) + BM25 + Web 三路并发，RRF 融合 |
| **流式传输** | FastAPI SSE → Java WebClient 代理 → 浏览器 EventSource |
| **前端 UI** | Vue 3 + TypeScript + Tailwind CSS 4（Gemini 风格） |
| **中间件** | PostgreSQL 16+pgvector, MySQL 8.0, RabbitMQ 3, Redis 7, LangFuse 2 |
| **可观测** | LangFuse 全链路追踪（每个 Node 的执行耗时 / Token 用量） |
| **持久化** | LangGraph AsyncPostgresSaver（支持断点续执行与多轮对话记忆） |

---

## 二、系统架构

### 2.1 整体拓扑

```
┌─────────────────────────────────────────────────────────────┐
│  用户浏览器 (Vue 3 / Vite)                                    │
│    │  EventSource (SSE)                                       │
└────┼─────────────────────────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────────────────────────────┐
│  Java 网关 (Spring Boot 3.4 / Java 17)                       │
│    ├── LoginInterceptor: 读取 X-User-Id → 线程级 UserContext  │
│    ├── AiGatewayController: /gateway/ai/**                    │
│    ├── PythonAiClientService: WebClient 代理 SSE 流            │
│    ├── ChatHistoryService: MyBatis-Plus CRUD + 标题异步生成    │
│    ├── AiChatAuditProducer: RabbitMQ 审计事件下发              │
│    └── 端口: 8080                                             │
└──────────────────────┼───────────────────────────────────────┘
                       │ POST /ai/v1/chat/stream
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  Python AI 核心 (FastAPI / Uvicorn)                           │
│    ├── InvestorService: 编排 LangGraph 流 + 异步持久化         │
│    ├── LangGraph StateGraph (Self-RAG):                       │
│    │     intent → rewrite → search → answer → critic          │
│    │                              ↑_____________↓ (fail 时回退) │
│    ├── RAG 引擎: Vector + BM25 + Tavily Web → RRF → Rerank    │
│    ├── StockAnalysisSkill: 股票代码提取 + 实时行情             │
│    └── 端口: 8000                                             │
└──────────────────────┼───────────────────────────────────────┘
                       │
                ┌──────┴──────┐
                ▼             ▼
┌──────────────────┐  ┌──────────────────────────────┐
│ PostgreSQL 16     │  │ LangFuse 2                    │
│  + pgvector(HNSW) │  │  (全链路观测 / Token 统计)     │
│  + LangGraph 状态  │  │  http://localhost:3000        │
│  port 5433        │  └──────────────────────────────┘
└──────────────────┘
         ▲
┌──────────────────┐  ┌──────────────────────────────┐
│ MySQL 8.0         │  │ RabbitMQ 3                    │
│  (业务库 / 审计)   │  │  (审计事件削峰)               │
│  port 3306        │  │  port 5672 / mgmt:15672      │
└──────────────────┘  └──────────────────────────────┘
```

### 2.2 核心设计决策

#### 为什么 Java + Python 双后端？

| 角色 | 语言 | 职责 | 优势 |
|------|------|------|------|
| **网关** | Java (Spring Boot) | 鉴权、限流、审计、MySQL CRUD、MQ 削峰 | 高并发 IO 成熟，企业级解耦 |
| **AI 核心** | Python (FastAPI) | LangGraph 编排、RAG 检索、LLM 调用、SSE 流 | AI 生态完善 |

Python 不处理用户/审计逻辑；Java 不碰大模型编排。各司其职，边界清晰。

#### SSE vs WebSocket？

选用 **SSE（单向流）**：LLM 问答是“一问一答、持续输出”，SSE 是单向的、内置重连、RESTful 风格，比 WebSocket 更轻量。

#### 为什么 pgvector 而不是独立向量数据库？

减少运维复杂度。PostgreSQL + pgvector + HNSW 索引在 1024 维向量上实现毫秒级 ANN 搜索，无需额外部署 Qdrant/ChromaDB 实例。

---

## 三、目录结构

```
ai-investor/
│
├── aipy2/                          # 🔥 Python AI 核心服务
│   ├── main.py                     # FastAPI 启动入口 (Uvicorn, 中间件)
│   ├── pyproject.toml              # 依赖声明 (Python >= 3.11)
│   ├── .env.example                # 环境变量模板
│   ├── app/
│   │   ├── api/v1/                 # HTTP 接口层
│   │   │   ├── chat.py             # POST /ai/v1/chat、/chat/stream (SSE)
│   │   │   └── util.py             # POST /ai/v1/util/generate_title
│   │   ├── core/                   # 基础设施
│   │   │   ├── config.py           # pydantic-settings 配置管理
│   │   │   ├── db.py               # SQLModel 引擎 + 会话工厂
│   │   │   ├── llm.py              # ChatOpenAI + AsyncPostgresSaver
│   │   │   └── logger.py           # 轮转日志 (10MB × 20)
│   │   ├── graph/                  # ★ LangGraph 状态机
│   │   │   └── investor_graph.py   # 6 节点 Self-RAG 图 + 流式包装器
│   │   ├── rag/                    # RAG 文档管线
│   │   │   ├── parser.py           # PDF (PyMuPDF) / DOCX / TXT 解析
│   │   │   ├── chunker.py          # 递归语义切分 (500 字 / 重叠 100)
│   │   │   └── vector_store.py     # pgvector 表创建 + HNSW 索引 + DashScope 向量化
│   │   ├── tools/                  # 检索 & 数据工具
│   │   │   ├── retriever_tool.py   # ★ 混合检索：Vector + BM25 + Web + RRF + Rerank
│   │   │   └── stockdata_tool.py   # 东方财富实时行情 (LangChain @tool)
│   │   ├── skills/                 # 技能编排
│   │   │   └── stock_analysis_skill.py  # 投研全流程：检索 + 行情 + 证据拼装
│   │   ├── services/
│   │   │   └── investor_service.py # InvestorService: 图调度 + 异步持久化
│   │   ├── models/                 # 数据模型 (SQLModel)
│   │   │   ├── chat_turn.py        # ai_chat_turns 表
│   │   │   ├── stock.py            # stock 表 + StockDTO
│   │   │   └── user_profile.py     # user_profiles 表
│   │   ├── schemas/
│   │   │   └── chat_schema.py      # ChatRequest / ChatResponse (Pydantic)
│   │   └── agents/nodes/           # 预留：未来多 Agent 节点扩展
│   ├── scripts/                    # 工具脚本
│   │   └── ingest_docs.py          # 语义切分入库
│   ├── alembic/                    # 数据库迁移
│   │   └── versions/               # DDL 版本文件
│   └── tests/                      # 测试目录
│
├── java-ai-gateway/                # 🔒 Java 业务网关
│   ├── pom.xml                     # Spring Boot 3.4 + MyBatis-Plus 3.5.7
│   └── src/main/java/com/aiinvestor/gateway/
│       ├── JavaAiGatewayApplication.java
│       ├── config/                 # 配置类
│       │   ├── SecurityConfig.java       # Spring Security (全部放行 + CORS)
│       │   ├── PythonAiProperties.java   # python.ai.* 配置
│       │   ├── RabbitConfig.java         # RabbitMQ 队列/交换机声明
│       │   └── WebMvcConfig.java         # 拦截器注册
│       ├── controller/
│       │   └── AiGatewayController.java  # /gateway/ai/** (流式 + 历史 + 会话)
│       ├── service/
│       │   ├── PythonAiClientService.java    # WebClient SSE 代理
│       │   ├── ChatHistoryService.java       # 对话历史 + 异步标题生成
│       │   └── UserService.java              # 用户查询
│       ├── dao/
│       │   ├── entity/          # UserDO, ChatTurnDO, AiChatAuditDO
│       │   └── mapper/          # MyBatis-Plus BaseMapper 扩展
│       ├── dto/                 # AiChatRequest, PythonChatRequest, PythonChatResponseDTO
│       ├── model/vo/            # ApiResult<T>, ChatSessionSummaryVO, ChatTurnVO
│       ├── mq/                  # AiChatAuditEvent, Producer, Consumer
│       ├── interceptor/
│       │   └── LoginInterceptor.java      # X-User-Id → UserContext (ThreadLocal)
│       └── context/
│           └── UserContext.java           # 线程级用户上下文持有者
│
├── frontend/                       # 🎨 Vue 3 前端
│   ├── src/
│   │   ├── App.vue                # ★ 单文件应用：双视图 + SSE 解析 + 思考过程
│   │   ├── main.ts                # createApp 入口
│   │   ├── style.css              # Tailwind + 自定义全局样式
│   │   └── assets/                # 静态资源
│   ├── index.html
│   ├── vite.config.ts
│   ├── tailwind.config.js         # 自定义主题色 (accent=indigo-600 等)
│   ├── package.json               # Vue 3.5 + TypeScript 6.0 + Vite 8.0
│   └── tsconfig.json
│
├── docker-compose.yml             # 中间件一键启动 (PG / MySQL / RabbitMQ / LangFuse / Redis)
│
├── README.md                      # 项目简介 + 快速启动
├── ARCHITECTURE.md                # 架构深度解析 (流程图 + 设计决策)
├── PROJECT_PLAN.md                # 项目规划书 (模块分解 + 完成状态)
├── AGENT_HANDBOOK.md              # Agent 状态机详解
├── RAG_DEEP_DIVE.md               # RAG 算法原理 (RRF / HNSW / Rerank)
└── .agents/skills/               # Agent Skill 定义 (开发规范)
```

---

## 四、核心模块详解

### 4.1 LangGraph Self-RAG 工作流

**文件**: `aipy2/app/graph/investor_graph.py`

#### 状态定义 (`AgentState` — TypedDict)

| 字段 | 类型 | 说明 |
|------|------|------|
| `messages` | `list` (add_messages) | 完整对话历史 |
| `queries` | `list[str]` | Rewrite 节点生成的搜索关键词 |
| `knowledge` | `str` | Search 节点检索到的背景知识 |
| `step` | `str` | 当前进度标识（供 SSE 流推送） |
| `retry_count` | `int` | 重试计数器（≥3 强制放行） |
| `review_status` | `str` | Critic 裁定：`pass` / `fail` |
| `critic_feedback` | `str` | Critic 的具体修改建议 |
| `total_tokens` | `int` | 累计 Token 用量 |
| `use_kb` | `bool` | Intent 路由判定：是否启动知识库检索 |
| `skill_context` | `str` | StockAnalysisSkill 输出的格式化投研上下文 |

#### 节点链路

```
                 ┌─────────────┐
                 │  用户提问     │
                 └──────┬──────┘
                        ▼
                 ┌─────────────┐
                 │   intent    │  LLM 路由：投研意图 → use_kb / 寒暄 → no_kb
                 └──┬───────┬──┘
           use_kb  │       │  no_kb
                   ▼       ▼
          ┌────────────┐  ┌──────────────────┐
          │  rewrite   │  │  direct_answer   │  (温度 0.6, 3 句以内)
          │  问题重写   │  └──────────────────┘
          └─────┬──────┘
                ▼
          ┌────────────┐
          │   search   │  StockAnalysisSkill 编排：混合检索 + 实时行情
          └─────┬──────┘
                ▼
          ┌────────────┐
          │   answer   │  基于 knowledge + skill_context 生成投研报告
          └─────┬──────┘
                ▼
          ┌────────────┐
          │   critic   │  pass → END / fail → rewrite (重新检索)
          └────────────┘
```

#### 自纠错机制

1. **Critic 评审**：检查报告中是否存在幻觉、未支撑论断、信息缺失
2. **条件打回**：`review_status = "fail"` → 携带 `critic_feedback` 回退到 `rewrite` 节点
3. **最多 3 轮**：`retry_count >= 3` 时强制 pass，防止无限循环

#### 持久化

- 使用 `AsyncPostgresSaver`（`langgraph-checkpoint-postgres`）将完整状态写入 PostgreSQL
- 支持多轮对话：用户同一 `thread_id` 下的消息自动关联历史上下文
- 启动失败时自动降级为 `InMemorySaver`

---

### 4.2 混合检索引擎

**文件**: `aipy2/app/tools/retriever_tool.py`

#### 三路并发检索

| 检索路 | 实现 | 适用场景 |
|--------|------|---------|
| **向量语义** (Vector) | pgvector HNSW + DashScope `text-embedding-v3` (1024 维) | 语义相似匹配 |
| **关键词** (BM25) | jieba 分词 + rank_bm25 BM25Okapi | 股票代码、专有名词精准匹配 |
| **联网搜索** (Web) | Tavily API (httpx 异步) | 最新市场资讯 |

三路通过 `asyncio.gather()` 并发执行。

#### RRF 融合 (Reciprocal Rank Fusion)

```
Score(d) = Σ (1 / (k + rank(d)))    k = 60
```

- 不依赖分数的绝对值，只看排名
- 第 1 名的权重远高于第 10 名
- 天然适配向量得分 (0~1) 与 BM25 得分 (0~∞) 的异构融合

#### Rerank 重排

基于原始 query 中的关键词在候选文本中的出现次数进行简单重排，取 Top 3 供 LLM 使用。

---

### 4.3 文档管线 (RAG Pipeline)

**目录**: `aipy2/app/rag/`

```
PDF / DOCX / TXT / MD
    │
    ▼ parser.py (PyMuPDF / python-docx)
    │
list[DocChunk]
    │
    ▼ chunker.py (递归切分：500 字 / 重叠 100)
    │
list[DocChunk]
    │
    ▼ vector_store.py
    │  ├── DashScope text-embedding-v3 (1024 维, 每批 25 条)
    │  ├── INSERT INTO doc_chunks (ON CONFLICT DO NOTHING 幂等)
    │  └── HNSW 索引 (m=16, ef_construction=200)
    │
PostgreSQL + pgvector
```

---

### 4.4 StockAnalysisSkill 投研技能

**文件**: `aipy2/app/skills/stock_analysis_skill.py`

编排完整的投研数据查询管道：

1. **股票代码提取**：正则匹配 6 位 A 股代码
2. **混合检索**：调用 `retriever_tool.run_retrieval_async()`
3. **实时行情**：调用东方财富 push2 API 获取价格/涨跌幅/PE/PB/换手率
4. **证据拼装**：合并检索结果 + 行情数据
5. **上下文格式化**：输出 `to_prompt_context()` 供 LLM 生成报告

---

### 4.5 Java 网关

**包**: `com.aiinvestor.gateway`

| 模块 | 说明 |
|------|------|
| **AiGatewayController** | `/gateway/ai/chat/stream`, `/sessions`, `/history` |
| **LoginInterceptor** | 从 `X-User-Id` 读取用户 → 查 MySQL → 注入 `UserContext`（ThreadLocal） |
| **PythonAiClientService** | 通过 WebClient 将 SSE 流从 Python 代理到前端 |
| **ChatHistoryService** | MyBatis-Plus CRUD；首轮对话异步生成 AI 标题 |
| **AiChatAuditProducer** | 流结束后向 RabbitMQ 发送 `AiChatAuditEvent`（可选，RabbitMQ 不可用时静默跳过） |
| **Flyway** | V1 迁移自动建表 `ai_chat_turns` + `ai_chat_audit` |

**优雅降级设计**：
- RabbitMQ 不可用 → 审计事件静默跳过
- Python 服务不可用 → SSE 流错误传播到前端
- 用户未认证 → 使用默认 userId="1"

---

### 4.6 Vue 3 前端

**文件**: `frontend/src/App.vue` (单文件应用)

| 功能 | 实现 |
|------|------|
| **双视图切换** | Splash（欢迎页 + 推荐卡片）↔ Chat（对话流） |
| **SSE 解析** | 原生 `EventSource` + `parseEventPayload()` 解析 `{ stage, data }` |
| **思考过程** | 可折叠面板，展示每个 LangGraph 节点的状态信息 |
| **Markdown 渲染** | `markdown-it` 将 AI 回复转为 HTML |
| **安全防护** | `document.oncontextmenu` + `document.oncopy` 全局禁用右键/复制 |
| **会话管理** | 客户端生成 sessionId，侧边栏加载历史会话列表 |

**事件流阶段**：

| 阶段 (stage) | 说明 |
|------|------|
| `accepted` | 请求已受理 |
| `rewrite` | 正在重写查询 |
| `search` | 正在检索资料 |
| `answer` | 正在生成回复 |
| `critic` | 正在评审质量 |
| `final_answer` | 播放最终回复 + 结束流 |
| `done` | 流结束确认 |
| `error` | 错误处理 |

---

## 五、数据层

### 5.1 PostgreSQL (端口 5433)

| 表 | 用途 | 扩展 |
|----|------|------|
| `doc_chunks` | 文档向量库 (embedding 1024 维) | pgvector + HNSW |
| LangGraph checkpoint 表 | Agent 状态持久化 | `langgraph-checkpoint-postgres` |

### 5.2 MySQL (端口 3306)

| 表 | 用途 |
|----|------|
| `users` | 用户信息 |
| `ai_chat_turns` | 对话轮次（query/answer/trace_id/thread_id/review_passed） |
| `ai_chat_audit` | 审计日志（面向异步削峰消费） |

### 5.3 RabbitMQ (端口 5672 / 管理页 15672)

- Exchange: `ai.chat.audit.exchange` (Direct)
- Queue: `ai.chat.audit.queue`
- Routing Key: `ai.chat.audit.routing`
- 序列化：`Jackson2JsonMessageConverter`

---

## 六、环境变量

**文件**: `aipy2/.env` (从 `.env.example` 复制)

| 变量 | 说明 |
|------|------|
| `DATABASE_URL` | PostgreSQL 连接 (e.g. `postgresql://postgres:password@localhost:5432/ai_investor`) |
| `DASH_API_KEY` | 阿里云 DashScope API 密钥（向量化用 `text-embedding-v3`） |
| `XIAOMIMINO_KEY` | xiaomimimo API 密钥（LLM 对话用 `mimo-v2-pro`） |
| `SEARCHER_API` | Tavily 搜索 API 密钥 |
| `LANGFUSE_PUBLIC_KEY` | LangFuse 公钥 |
| `LANGFUSE_SECRET_KEY` | LangFuse 密钥 |
| `LANGFUSE_HOST` | LangFuse 地址 (默认 `http://localhost:3000`) |
| `LANGFUSE_ENABLED` | 是否启用追踪 (`true` / `false`) |

---

## 七、快速启动

### 1. 启动中间件

```bash
docker-compose up -d
```

自动启动：PostgreSQL 16 + pgvector / MySQL 8.0 / RabbitMQ 3 / LangFuse 2 / Redis 7

### 2. 配置 Python 环境

```bash
cd aipy2
cp .env.example .env
# 编辑 .env 填入 API Key
uv sync                          # 安装依赖
```

### 3. 启动服务

| 服务 | 命令 | 端口 |
|------|------|------|
| Python AI | `cd aipy2 && python main.py` | `8000` |
| Java 网关 | `cd java-ai-gateway && mvn spring-boot:run` | `8080` |
| Vue 前端 | `cd frontend && npm install && npm run dev` | `5173` |

### 4. 文档入库

```bash
cd aipy2
python scripts/ingest_docs.py    # 将 data/raw/ 中的文档向量化入库
```

---

## 八、技术栈一览

| 层级 | 技术 | 版本 |
|------|------|------|
| **LLM 编排** | LangChain + LangGraph | 1.2.x / 1.1.x |
| **LLM 模型** | miaomimo-v2-pro (via ChatOpenAI 兼容) | — |
| **向量化** | DashScope text-embedding-v3 | 1024 维 |
| **Python Web** | FastAPI + Uvicorn | 0.135.x + 0.44.x |
| **Python ORM** | SQLModel + SQLAlchemy | 0.0.38 |
| **向量存储** | pgvector (PostgreSQL) | 0.4.2 |
| **数据库迁移** | Alembic | 1.15.x |
| **Java Web** | Spring Boot 3.4 + MyBatis-Plus 3.5.7 | — |
| **Java ORM** | MyBatis-Plus | 3.5.7 |
| **Java 迁移** | Flyway | — |
| **前端** | Vue 3 + TypeScript 6 + Vite 8 | — |
| **CSS** | Tailwind CSS 4 | — |
| **分词** | jieba | 0.42.x |
| **BM25** | rank-bm25 | 0.2.x |
| **股票数据** | akshare + 东方财富 push2 API | 1.18.x |
| **PDF 解析** | PyMuPDF | 1.27.x |
| **DOCX 解析** | python-docx | 1.2.x |
| **可观测** | LangFuse | 2.57.x |

---

## 九、面试话术要点

### 1. 为什么用 LangGraph 而不用 Chain？

> Chain 是线性的 A→B→C，如果 B 搜到垃圾，C 只能对着垃圾生成。
> **Agent 是环形的**——我的 Critic 节点会检查质量，发现幻觉/不详实时**强制回退（Backtracking）** 到 Rewrite 重新检索。
> 这展示了**对大模型局限性的深刻认识**——我不信任 LLM 第一次就能答对，通过工程链路建立了闭环校验。

### 2. 为什么 Vector + BM25 混合检索？

> 向量检索擅长语义匹配，但对股票代码、日期等精确字段不敏感；
> BM25 擅长关键词匹配，但无法理解"利润暴跌"和"业绩大幅下滑"是同一个意思。
> 两者互补 + RRF 排名融合 + Rerank 重排，召回率和准确率都大幅提升。

### 3. RRF 融合为什么比简单归一化好？

> 向量得分范围是 0~1，BM25 得分无上界，直接归一化会丢失精度。
> RRF 不关注分数绝对值，只看**排位**（rank），天然适配异构检索系统的结果融合。

### 4. 为什么 Java + Python 双后端？

> Python AI 生态（LangChain/LangGraph/PyTorch）不可替代；
> Java 在高并发 IO、数据库事务、消息队列方面成熟稳定。
> 两层解耦后，AI 模块可独立伸缩，业务层改动不影响推理管道。体现了企业级架构思维。

### 5. 如何保证系统可观测？

> 全链路透传 `TraceId`（前端 → Java → Python）。
> 集成 LangFuse 对 Agent 内部每个 Node 进行毫秒级监控——**Token 用量、耗时瀑布图、Prompt 迭代历史**一目了然。
> 意味着 Agent 运行从"黑盒"变为"白盒"。这是从"能跑"到"工业级运维"的关键跨越。

### 6. 如何防止 Agent 死循环？

> Critic 打回的次数通过 `retry_count` 追踪，`>= 3` 时**强制通过**。
> 同时通过 LangGraph 状态机 + Postgres Checkpointer 保证每次运行都可审计、可断点续执行。

---

## 十、开发路线图

### ✅ 已完成

- [x] 全异步 FastAPI + LangGraph 架构
- [x] Self-RAG 闭环 (Intent → Rewrite → Search → Answer → Critic)
- [x] 向量 + BM25 + Web 三路混合检索 + RRF 融合 + Rerank
- [x] 语义切分文档入库 (PyMuPDF + python-docx + Semantic Chunking)
- [x] Java 网关 (SSE 代理 + 审计 + RabbitMQ 削峰)
- [x] Vue 3 Gemini 风格 UI + 思考过程可视化 + 安全防护
- [x] LangFuse 全链路追踪
- [x] Postgres Checkpointer 多轮对话记忆
- [x] stockdata_tool 实时行情集成
- [x] Docker Compose 中间件集群

### 🔲 后续可拓展

- [ ] 接入真实 Rerank 模型 (bge-reranker-v2)
- [ ] 多 Agent 协作 (agents/nodes/ 下拆分独立子 Agent)
- [ ] MCP (Model Context Protocol) 工具标准化
- [ ] 前端暗色/亮色主题切换
- [ ] 前端组件拆分 (Vue SFC 子组件)
- [ ] 前端 Pinia 状态管理
- [ ] Java 网关完整鉴权链路
- [ ] K8s Helm Chart 部署
- [ ] A/B 实验 + Prompt 版本管理
