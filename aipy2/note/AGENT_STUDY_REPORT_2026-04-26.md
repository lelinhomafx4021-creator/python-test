# Python Agent 项目系统学习报告

更新时间：2026-04-26
位置：`aipy2/note/AGENT_STUDY_REPORT_2026-04-26.md`

---

## 0. 这份报告是干什么的

这份报告不是简单的“代码注释集合”，而是一份面向学习者的系统教材，目标是帮我们真正理解这个 Python Agent 项目：

1. 这个项目的 Python 部分到底在干什么。
2. LLM 是怎么组装起来的。
3. LangGraph Agent 为什么这样设计。
4. RAG、Skill、Tool、流式输出、数据库、审计、记忆之间是什么关系。
5. 面试时每一层该怎么讲。
6. 作为初学者，应该按什么顺序学习，才不会被代码量吓到。

如果你是从 Java 过来的，可以先把这个项目理解成：

- `FastAPI` 类似 `Spring Boot Controller`
- `Pydantic` 类似 `DTO + 参数校验`
- `Service 层` 和 Java 很像
- `LangGraph` 类似“带状态的工作流引擎”
- `AgentState` 类似“贯穿整个流程的上下文对象”
- `Tool / Skill` 类似“被工作流调用的外部能力模块”
- `SSE` 类似“服务端持续推送事件的长连接接口”

---

## 1. 本轮安全清理结论

你要求“先删除没有必要的代码”，但考虑到这个项目之前出现过误删关键初始化代码的情况，这次我只做了**明确安全**的清理，没有碰业务主链路。

### 1.1 已删除的明确临时文件

这轮只删除了以下明显用于临时排障的文件：

1. `aipy2/start_dev_8002.ps1`
2. `aipy2/uvicorn-8002.log`
3. `aipy2/run.log`
4. `aipy2/run.err.log`

### 1.2 没动的部分

以下内容我故意没删：

1. `app/models/*`
2. `app/core/db.py` 里的初始化函数
3. `alembic/*`
4. `app/graph/*`
5. `app/tools/*`
6. `app/skills/*`
7. `app/prompts/*`

原因很简单：这些不是“冗余代码”，而是 Agent 项目最核心的骨架。

### 1.3 仍然没删掉的文件

`aipy2/aipy2.log` 当时被进程占用，没强删。它属于运行日志，不是业务代码，后面服务停掉后可以再删。

### 1.4 当前对“删代码”的建议

这个项目目前真正应该做的，不是继续大面积删，而是：

1. 先理解主链路。
2. 识别“教学保留代码”和“历史遗留代码”的边界。
3. 只删临时脚本、废弃日志、失效 demo。

因为对 Agent 项目来说，很多“看起来没在当前调用链里直接 import 的代码”，其实是：

- 初始化能力
- 回退能力
- 运维能力
- 未来扩展位

这些不能当普通死代码删。

---

## 2. 项目一句话概述

这是一个基于 `FastAPI + LangGraph + RAG + PostgreSQL(pgvector)` 的**投研问答 Agent 系统**。

它做的事情不是“直接把用户问题丢给大模型”，而是：

1. 先判断用户问题是不是需要知识库检索。
2. 如果不用检索，就直接回答。
3. 如果需要检索，就先改写 query。
4. 再并发执行检索与行情工具。
5. 再让模型基于知识生成回答。
6. 再让一个 critic 节点评审答案是否可靠。
7. 如果不可靠，就回退重来，直到通过或达到上限。
8. 整个过程支持 SSE 流式输出。

这就是典型的 `Self-RAG Agent` 思路。

---

## 3. 先看目录：哪些才是 Python 主战场

Python 主代码主要集中在 `aipy2/app`。

### 3.1 主目录说明

- `app/api/`：接口层，负责接收 HTTP 请求
- `app/core/`：底层配置、日志、数据库、LLM 组装
- `app/graph/`：LangGraph 工作流，也就是 Agent 大脑
- `app/services/`：业务编排层
- `app/tools/`：工具层，给 Agent 或 Skill 调用
- `app/skills/`：更高一级的能力编排
- `app/rag/`：向量检索、切块、解析
- `app/prompts/`：提示词资产
- `app/models/`：数据库模型
- `app/schemas/`：接口请求/响应结构
- `main.py`：FastAPI 应用入口

### 3.2 我们学习时的顺序

建议按下面顺序看：

1. `main.py`
2. `app/api/v1/chat.py`
3. `app/services/investor_service.py`
4. `app/graph/investor_graph.py`
5. `app/core/llm.py`
6. `app/prompts/investor_prompts.py`
7. `app/skills/stock_analysis_skill.py`
8. `app/tools/retriever_tool.py`
9. `app/rag/vector_store.py`
10. `app/models/chat_turn.py` 与 `app/core/db.py`

这个顺序的好处是：

- 先看“请求怎么进来”
- 再看“谁负责编排”
- 再看“Agent 内部怎么跑”
- 最后再钻到检索与存储细节

---

## 4. 先建立总图：一次请求怎么走完整条链路

我们把用户问一句话之后的完整链路画出来：

1. 前端发请求到 `/gateway/ai/chat/stream`
2. Java 网关转发到 Python `/ai/v1/chat/stream`
3. FastAPI 路由进入 `app/api/v1/chat.py`
4. 路由调用 `investor_service.run_investor_flow(...)`
5. Service 调用 `multi_graph_agent.ask_stream_events(...)`
6. Agent 内部用 LangGraph 跑工作流
7. 工作流节点依次执行：
   - `intent`
   - `direct_answer` 或 `rewrite`
   - `search`
   - `answer`
   - `critic`
8. 中间不断产生：
   - `step`
   - `content_delta`
   - `final_answer`
9. API 层把这些事件包装成 SSE
10. Java 网关把 SSE 原样转发
11. 前端边收边渲染
12. 最终答案异步落库到 PostgreSQL

这个图必须记住，因为后面所有模块其实都是这条链上的一个环节。

---

## 5. 入口层：`main.py` 在做什么

文件：`aipy2/main.py`

### 5.1 它的职责

`main.py` 是整个 Python 服务的启动入口。它的工作不是写业务，而是：

1. 创建 FastAPI 应用
2. 注册路由
3. 注册中间件
4. 在启动时初始化资源

### 5.2 生命周期管理 `lifespan`

这里最重要的是 `lifespan`。

它相当于：

- Java 里的 `@PostConstruct` + `@PreDestroy`
- 或者 Spring Boot 启动后执行的初始化逻辑

它在启动阶段做了两件核心事：

1. `await init_llm_components()`
   - 初始化 LLM 相关异步资源
   - 包括 LangGraph checkpoint 需要的 PostgreSQL 连接池

2. 创建向量表
   - `VectorStore(...).create_collection()`
   - 保证 RAG 检索表和 HNSW 索引存在

### 5.3 中间件 `trace_middleware`

这里还加了一个 HTTP 中间件，用来：

1. 生成 `trace_id`
2. 记录请求耗时
3. 把 `trace_id` 回传到响应头

这相当于整个系统的“链路追踪基础设施”。

### 5.4 面试 30 秒话术

> `main.py` 主要承担应用装配职责，不写业务逻辑。它负责 FastAPI 生命周期初始化、向量表准备、LLM 组件准备以及链路追踪中间件注册。这样业务逻辑就能放在更清晰的 Service 和 Graph 层里。`

---

## 6. 配置层：`.env`、`.env.example`、`config.py`

### 6.1 程序真正读取哪个文件

当前程序真正读取的是：

- `aipy2/.env`

而不是：

- `aipy2/.env.example`

`config.py` 里写死了：

- `ENV_FILE = os.path.join(ROOT_DIR, ".env")`

### 6.2 `.env.example` 的作用

`.env.example` 只是模板，用来告诉开发者需要配置哪些环境变量。

### 6.3 当前关键环境变量

- `DATABASE_URL`：PostgreSQL 地址
- `DEEPSEEK_API`：主聊天模型 key
- `DASH_API_KEY`：Embedding / 向量化相关能力 key
- `SEARCHER_API`：联网搜索 key
- `LANGFUSE_*`：观测配置

### 6.4 配置层的工程价值

配置层的意义是：

1. 代码和密钥分离
2. 开发/测试/生产环境切换方便
3. 启动时就能校验变量是否缺失

### 6.5 这次我们学到的坑

这次切 DeepSeek 时就暴露了配置统一的重要性：

1. `config.py` 期待的是 `DEEPSEEK_API`
2. `.env` 必须真的提供 `DEEPSEEK_API`
3. `llm.py` 也必须读 `settings.DEEPSEEK_API`

三处不统一，就会出现“看起来改了，但运行没改”的错觉。

---

## 7. LLM 组装层：`app/core/llm.py`

这是整个项目最值得学习的文件之一。

### 7.1 它不是“大模型本身”，而是“大模型工厂”

`get_llm()` 不是模型，而是一个统一的创建入口。

意思是：

- 业务层不要自己到处 `ChatOpenAI(...)`
- 全项目统一走 `get_llm()`

这样以后：

1. 换服务商
2. 换模型名
3. 改 base_url
4. 改 streaming 策略
5. 改 token 参数

只需要改一个地方。

### 7.2 当前 DeepSeek 版本的核心思想

我们现在的 `get_llm()` 需要承担 3 件事：

1. 统一组装 `ChatOpenAI`
2. 兼容流式参数 `streaming`
3. 兼容输出长度参数 `max_completion_tokens`

### 7.3 为什么 DeepSeek 版代码比最简版长

最简版其实可以写成：

```python
return ChatOpenAI(
    model="deepseek-v4-pro",
    temperature=temperature,
    api_key=settings.DEEPSEEK_API,
    base_url="https://api.deepseek.com",
)
```

但项目里别处会这样调用：

```python
llm_core.get_llm(
    temperature=0.4,
    streaming=True,
    max_completion_tokens=4096,
)
```

所以 `get_llm()` 如果不兼容这些参数，就会报错。

### 7.4 为什么要把 `max_completion_tokens` 转成 `max_tokens`

这就是典型的“服务商差异适配层”。

项目内部我们想统一用：

- `max_completion_tokens`

但底层兼容接口更常见的名字是：

- `max_tokens`

所以 `llm.py` 在做一层翻译。

### 7.5 和小米版的对比启发

以前小米版本是：

- `model="mimo-v2-pro"`
- `api_key=settings.XIAOMIMINO_KEY`
- `base_url="https://api.xiaomimimo.com/v1"`

切到 DeepSeek 之后变成：

- `model="deepseek-v4-pro"`
- `api_key=settings.DEEPSEEK_API`
- `base_url="https://api.deepseek.com"`

变的不是业务层，而是适配层。

这就是 `包装一层 LLM 工厂` 的价值。

### 7.6 面试 30 秒话术

> 我们没有在业务代码里直接 new 模型，而是统一通过 `get_llm()` 做组装。这样做的好处是能把服务商差异封装在一处，比如模型名、key、base_url、streaming 开关和 token 参数映射。上层 graph 节点只关心“我要一个什么能力的模型”，不关心底层具体是哪家服务商。`

---

## 8. API 层：`app/api/v1/chat.py`

### 8.1 这个文件的职责

它只做两件事：

1. 接收请求
2. 把 Service 产出的事件转成 HTTP 响应

它不负责：

- 决定业务流程
- 决定检索逻辑
- 决定 Agent 节点如何跳转

### 8.2 同步接口 `/chat`

同步接口的做法是：

1. 调用 `run_investor_flow()`
2. 把整条事件流读完
3. 只取 `final_answer`
4. 一次性返回

### 8.3 流式接口 `/chat/stream`

流式接口的做法是：

1. 调用 `run_investor_flow()`
2. 每来一个事件就立刻 `yield`
3. 包装成 SSE 格式

每条事件大概长这样：

```text
event: message
data: {"stage": "content_delta", "data": {...}}
```

### 8.4 为什么 SSE 很适合这里

因为这个项目不是只关心最终答案，还想展示：

1. 正在路由
2. 正在改写
3. 正在检索
4. 正在生成
5. 正在评审
6. 正文 token 增量

SSE 对这种“服务端持续发消息给前端”的场景非常合适。

### 8.5 面试 30 秒话术

> API 层保持很薄，只负责协议转换。同步接口会消费完整事件流后提取最终答案，流式接口则把事件流原样转换成 SSE，确保前端可以实时看到 step 和正文增量。`

---

## 9. Service 层：`app/services/investor_service.py`

### 9.1 为什么需要 Service 层

因为 API 层如果直接操作 Graph，会导致：

1. 路由层太重
2. 不好测试
3. 业务编排和协议处理混在一起

所以这里单独抽了 `InvestorService`。

### 9.2 `run_investor_flow()` 在干什么

它主要做三件事：

1. 组装 LangFuse callback（如果开启）
2. 调用 `multi_graph_agent.ask_stream_events(...)`
3. 把收到的事件继续往上层 `yield`

### 9.3 为什么最终答案要异步落库

这段代码非常值得学：

- 不 `await` 落库
- 而是 `asyncio.create_task(...)`

这意味着：

1. 先把结果回给用户
2. 再在后台慢慢写数据库

好处是：

- 首响应更快
- 用户体验更好
- 数据库故障也不会阻塞当前问答

### 9.4 这是典型的“用户优先”工程设计

对于聊天系统，用户最关心的是：

- 快点看到答案

而不是：

- 这条记录是不是已经 100% 落库完成

所以把持久化放后台，是很合理的优化。

### 9.5 面试 30 秒话术

> Service 层负责业务编排和观测接入，不负责 HTTP 协议。最终答案采用后台异步落库，保证首响应延迟最小化，这样数据库的波动不会直接影响用户对话体验。`

---

## 10. Agent 核心：`app/graph/investor_graph.py`

这是整个项目的灵魂。

### 10.1 先理解：它不是“一个函数”，而是一张图

LangGraph 的核心思想是：

1. 有状态 `State`
2. 有节点 `Node`
3. 有边 `Edge`
4. 有条件路由 `Conditional Edge`

所以它不是单纯 if/else，而是一张可执行工作流图。

### 10.2 `AgentState` 是什么

`AgentState` 是整个系统的“公文包”。

它里面放的东西包括：

- `messages`：历史消息
- `queries`：改写后的检索词
- `knowledge`：检索回来的知识
- `step`：前端显示的当前步骤
- `retry_count`：已经回退了几次
- `review_status`：评审是否通过
- `critic_feedback`：评审意见
- `total_tokens`：累计 token
- `use_kb`：是否走知识库
- `skill_context`：Skill 提供的结构化上下文

如果用 Java 类比，它像是：

- 一个在多个 Service 方法之间不断被更新的上下文对象

### 10.3 为什么 `messages` 要用 `Annotated[list, add_messages]`

这个细节很重要。

它告诉 LangGraph：

- 新消息来了要“追加”
- 而不是“覆盖”

否则模型上一轮消息就没了。

### 10.4 `_latest_user_query()` 为什么关键

这个函数是一个非常典型的工程修复点。

原因是：

- `messages` 里既有用户消息，也有模型消息
- 如果直接取最后一条，很可能误拿到 AI 草稿
- 一旦误拿，`rewrite/search` 就会基于 AI 草稿继续跑，逻辑会错

所以这里专门倒序找最近一条 `HumanMessage`。

这个函数很值得在面试里讲，因为它体现了你真的遇到过 Agent 状态污染问题。

---

## 11. 每个节点分别在做什么

### 11.1 `route_intent_node`

职责：判断当前问题是：

1. 闲聊 / 不需要知识库
2. 投研 / 需要知识库

输入：
- 最近一条用户问题

输出：
- `use_kb`
- `step`
- `total_tokens`

### 11.2 `direct_answer_node`

职责：处理不需要检索的简单问题。

这里有一个重要升级：

- 默认走简短回答
- 如果用户明显要求“详细回答 / 1000字 / 分点展开”
- 就切到更详细的 direct answer 提示词

所以现在这个节点不再只会“敷衍两三句”。

### 11.3 `rewrite_node`

职责：把用户的大白话改写成更适合搜索的 query。

这是 Self-RAG 非常核心的一步，因为用户原问题通常不适合直接搜索。

比如：

- 用户说：`帮我看下茅台最近值不值得买`
- 改写后可能变成：
  - `贵州茅台 2024 一季度净利润 增长`
  - `贵州茅台 分红 创新高`
  - `贵州茅台 毛利率 护城河`

### 11.4 `search_node`

职责：调用 `stock_analysis_skill`。

注意这里不是直接写检索逻辑，而是：

- Graph -> Skill -> Tool / RAG

这说明项目在做“能力分层”。

### 11.5 `answer_node`

职责：基于 `knowledge + skill_context + feedback` 生成正式回答。

这是最贵的节点之一，因为它通常输出最长。

### 11.6 `critic_node`

职责：评审刚才的答案是不是可靠。

它会输出：

- `pass`
- `fail`
- 失败原因

如果失败，就会打回重新走 `rewrite -> search -> answer`。

这个节点是你项目里最像“多角色协作”的部分。

---

## 12. 为什么说当前是“单 Agent 工作流”，不是严格意义的多智能体

虽然项目里有多个节点：

- intent
- rewrite
- search
- answer
- critic

但它们仍然属于**同一张图里的同一套执行体**。

严格的多智能体通常意味着：

1. 有多个独立 Agent
2. 每个 Agent 有独立角色边界
3. Agent 间进行消息传递或任务交接

而你当前项目更准确的说法是：

- 单 Agent
- 多节点工作流
- 带 critic 回退闭环

这是一个非常合理的工程落地方向。

---

## 13. LangGraph 的边和路由

### 13.1 第一段路由：`intent -> use_kb / no_kb`

这一步决定：

- 是去 `direct_answer`
- 还是去 `rewrite`

### 13.2 第二段路由：`critic -> retry / end`

这一步决定：

- 评审通过，结束
- 评审失败，回到 `rewrite`

### 13.3 为什么这是闭环

因为它不是直线：

- `rewrite -> search -> answer -> critic`
- critic fail 后会回到 rewrite

这就是 Self-RAG 的“自我修正回路”。

---

## 14. 流式输出是怎么做出来的

### 14.1 不是只有 `final_answer` 在流

当前项目流的是两类东西：

1. `step`：阶段状态
2. `content_delta`：正文增量

### 14.2 `ask_stream_events()` 的设计很关键

它订阅了两种流：

- `updates`
- `messages`

#### `updates`

拿到的是节点状态更新，比如：

- `step`
- `messages`
- `total_tokens`

#### `messages`

拿到的是更细粒度的模型输出 chunk。

### 14.3 为什么还需要“兜底拆分”

有些服务商虽然支持流式，但在某些适配层上不一定老老实实给 token chunk。

所以这里又做了一层兜底：

- 如果底层 chunk 不可靠
- 就从完整消息里做差量拆分
- 再人工切成多个小片段发 `content_delta`

这一步很工程，很实用，也很适合面试讲。

### 14.4 面试 30 秒话术

> 我们的流式不是只流最终答案，而是把节点状态和正文增量分开传。正文优先走底层 message chunk，如果供应商兼容层不给稳定 chunk，就会回退到完整文本差量拆分，保证前端始终能拿到平滑的增量输出。`

---

## 15. Tool 和 Skill 的区别

这个点特别适合学习 Agent。

### 15.1 Tool 是什么

Tool 更像：

- 一个原子能力
- 一个具体函数
- 一个单点动作

比如：

- 查行情
- 搜索网络
- 向量检索

### 15.2 Skill 是什么

Skill 更像：

- 一个能力编排器
- 一个高级业务模块
- 它内部可以再调用多个 Tool

在这个项目里：

- `stock_analysis_skill` 就是 Skill
- 它内部会：
  - 调检索
  - 识别股票代码
  - 取实时行情
  - 打包 evidence

所以 Skill 是“比 Tool 更高一层的能力封装”。

---

## 16. `stock_analysis_skill.py` 讲解

### 16.1 输入输出模型

这里用 Pydantic 定义了：

- `StockAnalysisSkillInput`
- `StockAnalysisSkillOutput`

这和 Java 的：

- DTO
- VO

非常像。

### 16.2 Skill 干的事

1. 合并 query 和 queries
2. 调 `run_retrieval_async()`
3. 从用户问题里提取股票代码
4. 如果识别到代码且 `require_quote=True`
5. 再调用行情工具
6. 最后打包成统一结果

### 16.3 这层的价值

如果没有 Skill，Graph 节点里就要直接写很多检索和行情细节，代码会很乱。

有了 Skill 之后：

- Graph 只关心“调用一个高级能力”
- Skill 负责内部业务细节

这是一种很好的分层设计。

---

## 17. RAG 检索层：`retriever_tool.py`

### 17.1 为什么不只用向量检索

因为向量检索擅长“语义相似”，但不擅长：

- 股票代码
- 精确关键词
- 专有名词强匹配

### 17.2 为什么还要 BM25

BM25 是典型关键词检索算法，擅长：

- 一字不差匹配
- 文本召回

所以这里采用：

1. 向量检索
2. BM25 检索
3. Web 搜索

三路并发。

### 17.3 并发怎么做的

核心是 `asyncio.gather(...)`。

这意味着：

- 本地向量搜
- BM25
- 外网搜索

同时起飞，而不是串行等。

### 17.4 为什么还要做 RRF 融合

因为三路检索分数体系不一样，不能直接加。

所以这里用了：

- `RRF (Reciprocal Rank Fusion)`

它不直接比较原始分数，而是按“排名位置”融合。

这个算法非常适合混合检索。

### 17.5 当前本地语料兜底

这里还有 `LOCAL_CORPUS`，相当于系统空库时的一层保底样例知识。

这意味着：

- 知识库没真文档时不一定炸
- 但可能会退回样例知识

这个点需要理解，因为它会影响回答真实性。

---

## 18. 向量层：`vector_store.py`

### 18.1 为什么用 PostgreSQL + pgvector

因为这样可以：

1. 不额外引入一个独立向量数据库
2. 把结构化数据和向量数据放在一套数据库里
3. 降低系统复杂度

### 18.2 表里存了什么

向量表里大概包括：

- `content`
- `source`
- `page`
- `chunk_index`
- `file_type`
- `embedding`

### 18.3 检索过程怎么走

1. 把 query 调 embedding 接口变成向量
2. 在 PG 里用 `<=>` 计算余弦距离
3. 按相似度排序
4. 返回最相关的 chunks

### 18.4 为什么创建 HNSW 索引

因为没有索引，向量检索会越来越慢。

HNSW 是当前非常常见的近似最近邻索引方案，特点是：

- 搜得快
- 准确率高
- 很适合线上场景

---

## 19. 行情工具层：`stockdata_tool.py`

它负责：

1. 把股票代码转成行情接口需要的 `secid`
2. 调东方财富接口
3. 清洗字段
4. 统一返回 JSON 字符串

这个工具的价值在于：

- Agent 不需要懂东方财富字段码
- Skill 只要关心“有没有行情结果”

这就是 Tool 的典型封装价值。

---

## 20. 数据库层：`app/core/db.py` 与 `models/chat_turn.py`

### 20.1 `db.py`

这里主要是：

- 创建 SQLModel engine
- 提供 `init_db()`
- 提供 `get_session()`

### 20.2 为什么 Engine 和 Session 要分开

- `Engine`：连接池管理者
- `Session`：一次具体业务会话

这和 Java 里：

- DataSource
- SqlSession / EntityManager

的分工很像。

### 20.3 `ChatTurn` 模型的意义

它用于落库记录：

- query
- answer
- trace_id
- source
- session_id
- thread_id

相当于对话审计表。

---

## 21. Token 控制原理

这个知识点你前面已经在学了，这里系统总结一下。

### 21.1 我们最容易直接控制的是输出 token

比如：

- `direct_answer_node` 给 `2048`
- `answer_node` 给 `4096`

### 21.2 为什么不能一键控制整个流程总 token

因为整个流程是动态的：

1. 节点数量不同
2. 输入长度不同
3. 是否 retry 不确定
4. 检索内容长短也不确定

### 21.3 所以标准做法是什么

1. 每个节点限制输出上限
2. 在 `AgentState` 里累计 `total_tokens`
3. 在路由层根据预算决定是否继续 retry 或降级

这就是“节点预算 + 状态预算”的组合控制。

---

## 22. 当前项目到底算不算多智能体

严格说，当前不是纯正多智能体，而是：

- 单 Agent
- 多节点工作流
- 带 critic 反馈闭环

但它已经具备多智能体的雏形：

1. 有不同角色节点
2. 有评审反馈
3. 有回退机制
4. 有工具编排

所以后续如果要升级到多 Agent，可以这样拆：

1. Researcher Agent：只管检索
2. Analyst Agent：只管写答案
3. Reviewer Agent：只管评审
4. Orchestrator Agent：只负责调度

---

## 23. 前端为什么能看到“思考过程 + 正文流式”

虽然你现在主要学 Python，但前端体验要理解，因为这会反过来帮助你理解后端事件设计。

后端现在会发三类关键事件：

1. `step`
2. `content_delta`
3. `final_answer`

前端逻辑是：

- `step` -> 放到思考过程面板
- `content_delta` -> 直接拼正文
- `final_answer` -> 最终兜底替换一次完整答案

所以你看到的“像打字机一样输出”，不是模型魔法，而是前后端事件约定。

---

## 24. 这个项目最值得你学的 10 个知识点

1. FastAPI 生命周期管理
2. Pydantic 作为 DTO + 校验
3. LangGraph 的 State / Node / Edge
4. SSE 流式输出
5. AgentState 贯穿式状态传递
6. Tool / Skill 分层
7. RAG 混合检索
8. PostgreSQL + pgvector
9. Critic 回退闭环
10. Token 成本控制

---

## 25. 面试总讲稿（90 秒版本）

> 我的 Python 项目是一个基于 FastAPI、LangGraph 和 RAG 的投研 Agent。它不是直接把用户问题丢给大模型，而是先做意图路由：如果是闲聊，就 direct answer；如果是投研问题，就进入 Self-RAG 流程。这个流程包含 rewrite、search、answer、critic 四个核心环节。rewrite 负责把用户自然语言改造成检索 query；search 会并发做向量检索、BM25 和联网搜索，再融合重排；answer 基于召回知识生成报告；critic 负责做事实性评审，如果不通过会回退重写。整个流程通过 LangGraph 的 StateGraph 编排，状态都集中放在 AgentState 里，例如 messages、knowledge、retry_count、total_tokens。对外接口支持 SSE 流式输出，不仅能流 step，还能流正文 content_delta。数据库方面用了 PostgreSQL 和 pgvector，把结构化数据和向量检索放在一套系统里，降低了架构复杂度。`

---

## 26. 新手学习路线（建议 7 天）

### Day 1

目标：看懂请求入口

学习文件：

1. `main.py`
2. `app/api/v1/chat.py`
3. `app/schemas/chat_schema.py`

### Day 2

目标：看懂 Service 和 Graph 的边界

学习文件：

1. `app/services/investor_service.py`
2. `app/graph/investor_graph.py` 前半部分

### Day 3

目标：看懂 AgentState 和节点流转

学习文件：

1. `AgentState`
2. `route_intent_node`
3. `rewrite_node`
4. `answer_node`
5. `critic_node`

### Day 4

目标：看懂流式输出

学习文件：

1. `ask_stream_events()`
2. `post_chat_stream()`
3. 前端 EventSource 消费逻辑

### Day 5

目标：看懂 Tool / Skill / RAG

学习文件：

1. `stock_analysis_skill.py`
2. `retriever_tool.py`
3. `stockdata_tool.py`

### Day 6

目标：看懂向量检索和数据库

学习文件：

1. `vector_store.py`
2. `db.py`
3. `models/chat_turn.py`

### Day 7

目标：学会讲出来

输出：

1. 写一份自己的 90 秒项目介绍
2. 画一张项目链路图
3. 手动模拟一次完整调用链

---

## 27. 课后练习（非常重要）

### 练习 1：自己画调用链

目标：把这条链路画出来：

`前端 -> Java -> FastAPI -> Service -> Graph -> Skill -> Tool/RAG -> Graph -> SSE -> 前端`

验收标准：

1. 能说清每层职责
2. 能指出哪一层负责流式
3. 能指出哪一层负责检索

### 练习 2：手动解释 `AgentState`

目标：不用看代码，自己解释每个字段用途。

验收标准：

1. 能说清 `messages`
2. 能说清 `knowledge`
3. 能说清 `retry_count`
4. 能说清 `total_tokens`

### 练习 3：解释 `get_llm()` 为什么不是最简版

验收标准：

1. 能说出服务商适配层概念
2. 能说出 `streaming` 的作用
3. 能说出 `max_completion_tokens` 的作用

### 练习 4：解释为什么这不是严格多智能体

验收标准：

1. 能区分多节点工作流和多 Agent
2. 能说出 critic 为什么像“评审员”
3. 能说出后续如何拆成多 Agent

---

## 28. 这次你真正应该记住的结论

1. 这个项目最核心的不是“调用了大模型”，而是“把调用大模型这件事工程化了”。
2. LangGraph 的本质是：状态 + 节点 + 路由。
3. AgentState 是贯穿整条链的上下文容器。
4. LLM 组装层的意义是屏蔽服务商差异。
5. Service 层的意义是解耦 API 协议和业务编排。
6. Skill 是高级能力封装，Tool 是原子能力。
7. RAG 不只是向量检索，而是混合检索 + 重排。
8. SSE 流式体验背后是严格的事件设计，不是“自动出现”的。
9. critic 回退闭环是这个项目最像 Agent 的地方。
10. 真正学会这个项目，不是把文件都看一遍，而是能从入口一路讲到答案怎么回给前端。

---

## 29. 最后一句大白话总结

如果用最通俗的话来讲，这个 Python 项目做的事情其实是：

> 用户提一个投资问题，系统先判断要不要查资料；如果要，就先把问题改写成搜索词，再去知识库、BM25 和网上找资料，再结合实时行情生成分析，再让“评审员”检查答案靠不靠谱，最后一边把结果流式打给前端，一边把记录写数据库。`

这句话如果你能讲顺，再把每层代码对应上，你就已经真正进入 Agent 项目学习状态了。
