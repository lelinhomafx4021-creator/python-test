# AI Investor 智能投研助手 📈

> **定位**：校招面试级“全栈 AI Agent”示范项目，深度展示大模型工程化落地、多智能体协作与异步高并发架构。

---

## 🌟 核心技术亮点 (面试必杀技)

### 1. 闭环 AI 工作流 (Self-RAG)
 
 基于 **LangGraph** 实现具备“自我反思”能力的投研工作流。
 
- **自纠错机制**：通过 `Critic Node` 判定回答质量，检测到幻觉或信息缺失时自动触发 `Rewrite` 节点重新检索。
- **高确定性控制**：利用状态机控制重试次数，平衡生成质量与响应耗时。
 
 ### 2. 企业级混合检索 (Hybrid Search)
打破传统单路检索的局限，实现三路并发召回：
 
- **语义路 (Vector)**：基于 pgvector 实现深度语义匹配。
- **关键词路 (BM25)**：处理专有名词、股票代码等精准匹配。
- **联网路 (Web Search)**：集成 Tavily 捕获最新市场资讯。
- **融合算法**：使用 **RRF (Reciprocal Rank Fusion)** + **Rerank** 二次加权重排。
 
 ### 3. 高并发异步网关架构
采用 **Java (Spring Boot) + Python (FastAPI)** 双后端架构：
 
- **Java 网关**：负责鉴权、限流、审计（MySQL）及消息削峰（RabbitMQ）。
- **Python AI**：专注 LangGraph 工作流编排与 SSE 流式输出。
- **流式追踪**：全链路透传 `TraceId`，实现端到端的日志追踪与性能监控。
 
 ### 4. Gemini 风格“心流”交互
- **思考过程可视化**：前端实时解析 SSE 流，动态展示 Agent 的思维链路（Thought Process）。
- **投研安全防护**：内置防复制代码拦截，模拟专业金融终端的安全策略。

### 5. 工业级可观测性 (LLMOps)
集成 **LangFuse** 全链路追踪系统，告别 Agent 运行的“黑盒”状态：

- **思维链路追踪**：可视化展示 LangGraph 内部每一个 Node（从 RAG 到 Critic）的真实执行流。
- **成本与性能监控**：毫秒级统计 Token 消耗量与节点的运行耗时。
- **面试话术**：展示你对 AI 应用从“能跑”到“工业级运维”的深度理解。
 
 ---

## 🏗️ 系统架构图

    subgraph Storage ["存储层"]
        PG[("Postgres + pgvector")]
        MySQL[("MySQL 业务库")]
        MQ["RabbitMQ 消息队列"]
        LF["LangFuse (观测平台)"]
    end

    User -- SSE --> Gateway
    Gateway -- Async --> Python
    
    Python --> Graph
    Graph -- Callbacks --> LF
    Graph --> PG
    Gateway --> MySQL
    Gateway --> MQ
```

---

## 📁 目录结构

```text
ai-investor/
├── java-ai-gateway/     # Java 侧业务网关：鉴权、审计、限流、MQ 削峰
├── aipy2/               # Python 侧 AI 核心：[👉 点击查看详细技术详解](file:///d:/ai-investor/aipy2/README.md)
├── frontend/            # Vue 3 前端：Gemini 风格 UI、SSE 解析
└── docker-compose.yml   # 一键启动中间件 (MySQL, RabbitMQ, Postgres)
```

---

## 🚀 快速启动

### 1. 启动中间件 
 ```bash
 # 自动启动 MySQL, RabbitMQ, PostgreSQL (pgvector)
 docker-compose up -d
 ```
 
 ### 2. 配置环境
复制 `aipy2/.env.example` 为 `.env` 并填写相关 API Key。
 
 ### 3. 运行服务
- **后端**：`cd aipy2 && pip install -r requirements.txt && python main.py`
- **网关**：`cd java-ai-gateway && mvn spring-boot:run`
- **前端**：`cd frontend && npm install && npm run dev`
 
 ---
 
 ## 💡 开发者说 (备战面试)
 
 本项目特别适合展现：
 
- **工程化思维**：为什么选 LangGraph？为什么要做异步网关？
- **RAG 深度**：如何解决检索不准、模型幻觉？
- **全栈视野**：从 Vue 到 Spring Boot 到算法编排。
