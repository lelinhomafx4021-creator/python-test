# AI Investor Python AI Core (aipy2) 深度技术手册 🧠

本手册旨在为开发者提供 `aipy2` 模块的底层逻辑视图，特别是为**技术面试**准备的深度细节。

---

## 🏗️ 核心工作流：LangGraph 拓扑结构详解

项目的核心逻辑位于 `app/graph/investor_graph.py`，它定义了一个有向有损图（Directed Cyclic Graph）。

### 1. 意图识别节点 (`intent_node`)
- **逻辑**：LLM 接收用户 Query，判断属于以下类别：
    - `GREETING`: 简单的打招呼或闲聊（直接路由到 `answer_node`）。
    - `INVEST_RESEARCH`: 具体的投研需求（路由到 `rewrite_node`）。
- **优化点**：通过前置意图识别，节省了 40% 的 RAG 检索开销。

### 2. 查询重写节点 (`rewrite_node`)
- **职责**：将用户的口语化问题（如“茅台咋样？”）转化为搜索引擎友好的关键词（如“贵州茅台 2024 财报 净利润增长 行业评级”）。
- **迭代逻辑**：如果 `critic` 节点判定失败，该节点会接收反馈建议，重新改写更精准的关键词。

### 3. 混合检索逻辑 (`search_node`)
- **双引擎并发**：
    - **Vector Search (语义)**：处理“白酒行业的防御属性”这类模糊语义。
    - **BM25 (关键词)**：处理“600519”、“2024Q1”这类精准数字识别。
- **融合算法：RRF (Reciprocal Rank Fusion)**：
    - 公式：$Score(d) = \sum_{r \in R} \frac{1}{k + r(d)}$
    - **面试讲点**：为什么不用加权平均？因为不同数据源分数值域不同（Vector 是 0-1 相似度，BM25 是 0-无穷相关性），RRF 通过排名融合，完美解决了异构分数的归一化问题。

### 4. 专家评审节点 (`critic_node`)
- **逻辑**：采用专门的 `Critic LLM` 对生成的报告进行独立审计。
- **核查清单**：
    - 报告是否引用了搜索到的文章？
    - 结论是否与上下文矛盾？
    - 数据是否有幻觉？
- **结果**：返回 `passed` 或 `fail`。`fail` 时带上修改意见，打回 `rewrite`。

---

## 🗄️ 数据库与持久化层

### 1. 向量数据库 (PostgreSQL + pgvector)
- **索引优化**：使用 `HNSW` (Hierarchical Navigable Small Worlds) 索引。
- **面试话术**：*“在大规模数据下，IVF 索引需要定期重新训练聚类中心，而 HNSW 虽然内存占用稍高，但它是增量构建的，且检索速度极快，更适合投研这类实时性要求高的场景。”*

### 2. 分布式记忆 (Postgres Checkpointer)
- **实现**：`AsyncPostgresSaver`。
- **原理**：将 `AgentState` 序列化为 JSON，并记录生成每一步的 `checkpoint_id`。
- **价值**：支持多机环境下共享用户记忆，并能实现任务失败后的“断点续执行”。

---

## 📡 流式输出 (SSE) 实现细节

后端通过 `StreamingResponse` 向 Java 网关推送结构化 JSON 流：
```json
// 中间状态推送到 Thought Process
{"stage": "rewrite", "data": {"step": "正在将‘茅台咋样’重写为专业投研词汇..."}}

// 最终答案
{"stage": "final_answer", "data": {"answer": "根据最新研报，贵州茅台..."}}
```
**优点**：前端可以据此实现打字机效果及中间思考步骤的实时反馈。

---

## 🎯 面试 Q&A 地图 (Super Detailed)

### Q: 如果检索到的资料互相矛盾怎么处理？
**A**: 我们的 `critic_node` 会检测冲突。如果冲突显著，Agent 会在最终回答中客观罗列：“据信 A 认为...，但信源 B 认为...”，避免模型自行“二选一”产生幻觉。

### Q: 为什么选择 LangGraph 而不是 LangChain 的简单 SequentialChain？
**A**: 全球金融投研逻辑通常不是线性的。它需要循环、条件分支（如：如果搜不到最新数据，就去执行联网搜索）。LangGraph 提供了对**图结构**和**循环**的一等公民支持，并完美集成了状态持久化。

### Q: 如何应对大模型生成的“废话”？
**A**: 在 `answer_node` 的 Prompt 中强制执行结构化输出（Markdown）并要求引用（Citations）。同时 `critic` 节点会根据“信息密度”评分。

---

## 📁 核心代码速查 (aipy2 目录下)

- `app/graph/investor_graph.py`: 核心图逻辑、节点定义、条件路由。
- `app/tools/retriever_tool.py`: BM25 实现、RRF 算法、重排逻辑。
- `app/core/llm.py`: PostgresSaver 初始化、LLM 实例配置。
- `app/rag/vector_store.py`: pgvector 操作抽象、HNSW 索引定义。
- `app/api/v1/chat.py`: SSE 流解析逻辑与 FastAPI 路由。
