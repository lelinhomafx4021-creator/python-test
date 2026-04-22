# AI-Investor (aipy2) 项目总体规划 v4.0 — 企业级架构

> ⚡ 2026 年企业标准 AI 应用架构，面试级项目
> 最后更新：2026-04-10

---

## 一、项目定位

一个 **生产级 AI 量化投资助手**，基于 Multi-Agent 架构 + Advanced RAG 实现：
- 多智能体协作分析（分析师/风控/报告员）
- 企业级 RAG 知识检索（混合检索 + 重排序）
- 实时金融数据接入 + 结构化输出
- 全链路可观测 + 流式响应

> **面试价值**：能聊架构选型、能聊性能优化、能聊工程规范、能聊 AI 原理

---

## 二、技术栈（2026 企业标准）

### 核心框架

| 层级 | 技术 | 为什么选它（面试怎么说） |
|------|------|------------------------|
| Web | **FastAPI** | 原生异步、自动生成 OpenAPI 文档、依赖注入系统 |
| ORM | **SQLModel + SQLAlchemy 2.0** | Pydantic 和 SQLAlchemy 的结合体，类型安全 |
| 数据库 | **PostgreSQL 16** | pgvector 扩展支持向量检索、JSON 支持好、企业首选 |
| 数据库迁移 | **Alembic** | 版本化管理数据库 schema 演进，团队协作必备 |
| 缓存 | **Redis 7** | 语义缓存 + 会话管理 + 限流 |

> **面试点**：为什么从 MySQL 切到 PostgreSQL？
> - pgvector 扩展可以在同一个数据库里做向量检索，减少运维复杂度
> - PostgreSQL 的 JSONB 类型对 AI 返回的结构化数据更友好
> - 企业级 AI 应用 90% 都选 PG（参考 Supabase、Vercel、各大云厂商默认选项）

### AI / Agent 层

| 组件 | 技术 | 为什么 |
|------|------|--------|
| LLM | **DeepSeek V3**（langchain-openai 兼容层） | 性价比高，中文能力强 |
| Agent 框架 | **LangGraph 0.3+** | 状态机编排，支持 Human-in-the-loop、节点级重试 |
| 多智能体 | **Supervisor 模式** | 一个管理者分派任务给专家节点，面试常考 |
| 结构化输出 | **with_structured_output()** | 强制 LLM 返回 Pydantic 模型，不是随便吐字符串 |

### RAG 管道（Advanced RAG）

| 环节 | 技术 | 为什么不用简单版 |
|------|------|-----------------|
| 向量数据库 | **Qdrant**（Docker 部署） | Rust 写的，性能好，支持过滤检索，API 友好 |
| Embedding | **BGE-M3**（BAAI 出品） | 2024-2026 最火的多语言嵌入模型，支持稠密+稀疏+ColBERT 三种检索 |
| 混合检索 | **Dense + Sparse** | 向量语义检索 + BM25 关键词检索，互补 |
| 重排序 | **BGE-reranker-v2-m3** | 对初筛结果精排，大幅提升准确率 |
| 文档解析 | **Unstructured** | 企业级文档解析，支持 PDF/Word/HTML，带 OCR |
| 文本切片 | **语义切片** | 不是固定长度切，而是按语义边界切 |

> **面试经典问题**：你们的 RAG 检索准确率怎么优化的？
> 答：三层漏斗 —— 混合检索召回（保证不漏）→ Reranker 精排（保证准）→ LLM 生成时带上 source 引用（保证可溯源）

### 工程规范

| 方面 | 技术 | 说明 |
|------|------|------|
| 可观测性 | **LangFuse**（自部署 Docker） | 每次 LLM 调用的输入/输出/token/耗时/费用全追踪 |
| 流式输出 | **SSE (Server-Sent Events)** | 像 ChatGPT 一样逐字输出，不是干等 |
| 代码规范 | **Ruff** | 替代 flake8+black+isort，快 100 倍 |
| 测试 | **pytest + httpx** | 异步接口测试、Agent mock 测试 |
| 日志 | **loguru** | 结构化日志，带上下文追踪 |
| 容器化 | **Docker Compose** | 一键拉起全部服务（PG、Redis、Qdrant、LangFuse） |

---

## 三、系统架构图

```
┌─────────────────────────────────────────────────────────┐
│                    Frontend (后续)                        │
│                 Next.js / Streamlit                       │
└───────────────────────┬─────────────────────────────────┘
                        │ SSE / WebSocket
┌───────────────────────▼─────────────────────────────────┐
│                   FastAPI Gateway                        │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐              │
│  │ /health  │  │ /v1/chat │  │ /v1/rag  │              │
│  └──────────┘  └────┬─────┘  └────┬─────┘              │
│                     │              │                     │
│  ┌──────────────────▼──────────────▼───────────────┐    │
│  │              LangGraph Engine                    │    │
│  │  ┌───────────────────────────────┐              │    │
│  │  │       Supervisor Agent        │              │    │
│  │  │  (任务分派 + 流程控制)         │              │    │
│  │  └───┬──────────┬──────────┬────┘              │    │
│  │      │          │          │                     │    │
│  │  ┌───▼───┐ ┌────▼────┐ ┌──▼──────┐            │    │
│  │  │分析师  │ │风控专家  │ │报告员   │            │    │
│  │  │Analyst│ │Risk Mgr │ │Reporter │            │    │
│  │  └───┬───┘ └────┬────┘ └─────────┘            │    │
│  │      │          │                               │    │
│  │  ┌───▼──────────▼───┐                          │    │
│  │  │   Tool Layer     │                          │    │
│  │  │ ┌──────┐┌──────┐ │                          │    │
│  │  │ │股票  ││RAG   │ │                          │    │
│  │  │ │行情  ││检索  │ │                          │    │
│  │  │ └──┬───┘└──┬───┘ │                          │    │
│  │  └────┼───────┼─────┘                          │    │
│  └───────┼───────┼────────────────────────────────┘    │
└──────────┼───────┼─────────────────────────────────────┘
           │       │
    ┌──────▼───┐ ┌─▼──────────┐  ┌───────┐  ┌────────┐
    │ akshare  │ │  Qdrant    │  │ Redis │  │LangFuse│
    │ 金融数据  │ │ 向量数据库  │  │ 缓存  │  │ 追踪   │
    └──────────┘ └────────────┘  └───────┘  └────────┘
                 ┌────────────┐
                 │ PostgreSQL │
                 │ 业务数据库  │
                 └────────────┘
```

---

## 四、目录结构（企业级）

```
aipy2/
├── main.py                           # FastAPI 入口 + lifespan
├── alembic/                          # 数据库迁移
│   ├── alembic.ini
│   ├── env.py
│   └── versions/                     # 迁移版本文件
├── docker-compose.yml                # 一键部署：PG + Redis + Qdrant + LangFuse
├── .env                              # 环境变量
├── pyproject.toml                    # 依赖 + Ruff 配置
├── data/
│   └── docs/                         # 原始 PDF 研报
├── tests/                            # 测试
│   ├── conftest.py                   # pytest fixtures
│   ├── test_api/                     # 接口测试
│   ├── test_agents/                  # Agent 测试
│   └── test_rag/                     # RAG 管道测试
├── scripts/                          # 运维脚本
│   ├── ingest_docs.py                # 文档入库脚本
│   └── seed_data.py                  # 初始数据填充
├── app/
│   ├── __init__.py
│   ├── core/                         # 核心基础设施
│   │   ├── config.py                 # pydantic-settings 配置（支持多环境）
│   │   ├── database.py               # PG 异步引擎 + Session
│   │   ├── redis.py                  # Redis 连接池
│   │   ├── deps.py                   # FastAPI 依赖注入集中管理
│   │   └── logging.py                # loguru 日志配置
│   │
│   ├── llm/                          # LLM 管理（独立模块，不塞在 core 里）
│   │   ├── client.py                 # DeepSeek 客户端工厂
│   │   ├── embeddings.py             # BGE-M3 嵌入模型
│   │   └── callbacks.py              # LangFuse 回调追踪
│   │
│   ├── api/                          # 接口层
│   │   ├── router.py                 # 总路由聚合
│   │   ├── middleware.py             # 异常处理 + 请求日志中间件
│   │   ├── health.py                 # GET /health（标准健康检查）
│   │   └── v1/
│   │       ├── chat.py               # POST /api/v1/chat（SSE 流式）
│   │       ├── rag.py                # POST /api/v1/rag/ingest（文档上传入库）
│   │       └── stock.py              # GET /api/v1/stock/{code}（行情查询）
│   │
│   ├── models/                       # 数据库模型 (SQLModel)
│   │   ├── conversation.py           # 对话记录表
│   │   ├── message.py                # 消息表（支持多轮）
│   │   ├── document.py               # 文档元数据表
│   │   └── stock.py                  # 股票信息表
│   │
│   ├── schemas/                      # 请求/响应 DTO
│   │   ├── chat.py                   # ChatRequest / ChatResponse
│   │   ├── stock.py                  # StockQuery / StockReport
│   │   └── rag.py                    # IngestRequest / SearchResult
│   │
│   ├── agents/                       # LangGraph 多智能体
│   │   ├── state.py                  # AgentState 定义
│   │   ├── supervisor.py             # Supervisor 节点（任务分派）
│   │   ├── graph.py                  # 图编排 + 编译
│   │   └── nodes/
│   │       ├── analyst.py            # 分析师：调用行情工具
│   │       ├── risk_manager.py       # 风控：评估风险等级
│   │       └── reporter.py           # 报告员：生成结构化报告
│   │
│   ├── tools/                        # Agent 工具注册
│   │   ├── stock_price.py            # akshare 实时行情
│   │   ├── rag_search.py             # RAG 知识检索工具
│   │   └── registry.py              # 工具注册表（集中管理）
│   │
│   ├── rag/                          # Advanced RAG 管道
│   │   ├── parser.py                 # 文档解析（Unstructured）
│   │   ├── chunker.py                # 语义切片
│   │   ├── vector_store.py           # Qdrant 操作封装
│   │   ├── retriever.py              # 混合检索（Dense + Sparse）
│   │   ├── reranker.py               # BGE-reranker 重排序
│   │   └── pipeline.py               # RAG 管道编排（解析→切片→入库）
│   │
│   ├── services/                     # 业务逻辑层
│   │   ├── chat_service.py           # 聊天业务（调用 Agent + 存库）
│   │   └── ingest_service.py         # 文档入库业务
│   │
│   └── cache/                        # 缓存策略
│       └── semantic_cache.py         # Redis 语义缓存（相似问题命中缓存）
```

---

## 五、开发阶段规划

### ✅ Phase 1：Walking Skeleton（已完成）

- [x] Docker 部署数据库容器
- [x] FastAPI 项目初始化
- [x] pydantic-settings 配置管理
- [x] 异步数据库引擎
- [x] DeepSeek LLM 接入验证

---

### 🔨 Phase 2：基础设施升级（当前优先 ← 先做这个）

> 目标：把地基从"课程级"升级到"企业级"，后续所有功能都建立在这上面

#### 2.1 数据库迁移到 PostgreSQL + Alembic
- [ ] docker-compose.yml 加入 PostgreSQL 16（替代 MySQL）
- [ ] 修改 config.py 支持 PG 连接串
- [ ] 配置 Alembic 迁移框架
- [ ] 创建初始迁移文件

> **知识点**：Alembic 是什么？
> 就像 Git 管理代码版本一样，Alembic 管理数据库的"版本"。
> 每次你改了表结构（加字段、改类型），它会生成一个迁移脚本。
> 部署时执行 `alembic upgrade head` 就能自动把数据库更新到最新。
> 面试必问：你们数据库 schema 怎么管理的？答 create_all 直接减分。

#### 2.2 Docker Compose 统一编排
- [ ] PostgreSQL 16 + pgvector 扩展
- [ ] Redis 7
- [ ] Qdrant（向量数据库）
- [ ] LangFuse（可观测性平台）
- [ ] 一个 `docker-compose up -d` 全拉起来

#### 2.3 工程规范
- [ ] Ruff 配置（pyproject.toml）
- [ ] loguru 日志系统
- [ ] 统一异常处理中间件
- [ ] 依赖注入层 deps.py

---

### 🚀 Phase 3：Advanced RAG 管道（核心亮点 ← 硬盘坏之前在这里）

> 目标：构建企业级检索增强生成管道，这是面试最大亮点

#### 3.1 文档解析层
- [ ] `app/rag/parser.py`
  - 用 Unstructured 库解析 PDF（支持表格、图片 OCR）
  - 提取元数据（标题、日期、作者、页码）
  - **面试点**：为什么不用 PyPDF？Unstructured 能处理扫描件（OCR）、能识别表格结构

#### 3.2 智能切片
- [ ] `app/rag/chunker.py`
  - 语义切片：按段落语义边界切，不是固定 500 字切
  - 保留上下文窗口（overlap）
  - 每个 chunk 带元数据标签
  - **面试经典**：chunk_size 怎么调？太大→检索不精准（噪声多），太小→上下文断裂。实践中 512-1024 token 效果较好，但要根据业务调

#### 3.3 向量存储（Qdrant）
- [ ] `app/rag/vector_store.py`
  - Qdrant Docker 部署
  - BGE-M3 生成 embedding（同时产出稠密向量和稀疏向量）
  - 批量写入 + 元数据过滤索引
  - **知识点**：
    - Embedding 原理：把文本映射到高维向量空间，语义相近的文本距离近
    - 为什么选 Qdrant 不选 ChromaDB？Qdrant 是 Rust 写的生产级数据库，支持过滤检索、分布式、WAL 持久化。ChromaDB 是个嵌入式库，单机、无 WAL、数据容易丢

#### 3.4 混合检索 + 重排序
- [ ] `app/rag/retriever.py`
  - **Dense 检索**：向量相似度（余弦距离），擅长语义理解
  - **Sparse 检索**：BM25 关键词匹配，擅长精确匹配专有名词
  - **Reciprocal Rank Fusion (RRF)**：合并两路结果
  - **面试热点**："你们检索怎么做的？"
    → 不是简单 top-k，而是混合检索 + RRF 融合

- [ ] `app/rag/reranker.py`
  - 用 BGE-reranker 对初筛的 20 条精排到 top-5
  - **原理**：reranker 是个 cross-encoder，把 query 和 document 一起输入，比 bi-encoder（embedding）更准但更慢，所以只在精排阶段用
  - **面试杀手锏**：Bi-encoder vs Cross-encoder 的区别？什么时候用哪个？

#### 3.5 RAG 管道编排
- [ ] `app/rag/pipeline.py`
  - 串联：解析 → 切片 → 嵌入 → 入库
  - 支持增量更新（新文档入库不影响旧数据）
  - 文档去重（基于 hash）

---

### 🤖 Phase 4：Multi-Agent 系统

> 目标：不是简单的链，而是有"管理者"的多智能体系统

#### 4.1 Agent State
- [ ] `app/agents/state.py`
  - TypedDict + Annotated[list, add_messages]
  - 包含：messages, current_stock, risk_level, report
  - **知识点**：LangGraph 的 State 就像一个"共享黑板"，所有节点都能读写

#### 4.2 Supervisor（管理者）
- [ ] `app/agents/supervisor.py`
  - 接收用户问题，判断应该交给谁处理
  - 决定流程走向（需要先查数据？需要查知识库？直接回答？）
  - **面试点**：Supervisor vs ReAct vs Plan-and-Execute 三种 Agent 模式对比

#### 4.3 专家节点
- [ ] `analyst.py`：分析师，调用 akshare 拿实时数据
- [ ] `risk_manager.py`：风控专家，评估风险等级（0-10 分）
- [ ] `reporter.py`：报告员，生成结构化投资报告
  - 用 `with_structured_output()` 强制返回 Pydantic 模型

#### 4.4 图编排
- [ ] `app/agents/graph.py`
  ```
  Start → Supervisor → { Analyst | Risk Manager } → Reporter → End
                ↑                    │
                └────────────────────┘  (需要更多信息时循环)
  ```

---

### 🌊 Phase 5：流式输出 + 缓存

#### 5.1 SSE 流式
- [ ] `app/api/v1/chat.py` 改为 StreamingResponse
  - 用 `astream_events()` 逐 token 输出
  - 前端能看到"AI 正在打字"效果
  - **知识点**：SSE vs WebSocket？SSE 是单向推送、更简单，ChatGPT 也用的 SSE

#### 5.2 Redis 语义缓存
- [ ] `app/cache/semantic_cache.py`
  - 把 query 做 embedding → 在 Redis 里找相似的历史问题
  - 命中率高的话能省大量 API 调用费用
  - **面试加分**："你们怎么控制 LLM 调用成本的？"

---

### 📡 Phase 6：可观测性 + 生产化

#### 6.1 LangFuse 集成
- [ ] Docker 部署 LangFuse
- [ ] 每次 LLM 调用自动上报：输入/输出/token 数/延迟/费用
- [ ] 可以在 LangFuse 面板看到完整的调用链路
  - **面试亮点**："我们用 LangFuse 做了全链路追踪，每个 Agent 节点的耗时和 token 消耗都能看到"

#### 6.2 测试策略
- [ ] `tests/test_api/` - 接口级别，用 httpx AsyncClient
- [ ] `tests/test_rag/` - RAG 检索质量，用 Recall@K 指标
- [ ] `tests/test_agents/` - Agent 行为，mock LLM 调用

#### 6.3 完善部署
- [ ] docker-compose.yml 最终版
- [ ] GitHub Actions CI/CD
- [ ] README.md 写好（面试官会看你的 README！）

---

## 六、待修复的已知问题

| # | 问题 | 优先级 |
|---|------|--------|
| 1 | `main.py` 拼写错误 `AI-Invesntro` | 高 |
| 2 | `healthy.py` 混入了 LLM 测试逻辑 | 高 |
| 3 | `chat.py` 空壳未实现 | 中 |
| 4 | `models/stock.py` 中 DTO 和 Model 混在一起 | 中 |
| 5 | MySQL 需要切换到 PostgreSQL | 高 |
| 6 | 缺少 docker-compose.yml | 高 |

---

## 七、面试高频问题预备

这个项目能撑起以下面试题：

### 架构层面
- 为什么选 FastAPI 不选 Django/Flask？
- 你们的 Agent 架构是怎样的？Supervisor 模式是什么？
- RAG 和 Fine-tuning 什么区别？什么场景用哪个？

### RAG 深度
- 你们的检索准确率怎么优化的？（三层漏斗：混合检索→重排序→引用溯源）
- Chunk size 怎么调的？踩过什么坑？
- Bi-encoder vs Cross-encoder 区别？
- 向量数据库选型考虑了什么？

### 工程规范
- 数据库迁移怎么管理的？（Alembic）
- LLM 调用怎么追踪的？（LangFuse）
- 怎么控制 API 调用成本？（语义缓存）
- 测试怎么做的？Agent 怎么测？

---

## 八、下一步行动

**当前应该先做 Phase 2（基础设施升级）**，把地基打好：

1. 写 `docker-compose.yml`（PG + Redis + Qdrant）
2. 切换数据库连接到 PostgreSQL
3. 配置 Alembic
4. 完善 deps.py 和中间件

地基稳了，再开始做 Phase 3 的 RAG 管道。

**准备好了告诉我，我来一步步带你做！**
