# 45_Langfuse可观测性详解：看懂 Agent 每一步在做什么

> **核心目标**: 理解 Langfuse 的四个核心概念，能通过 UI 追踪和调试 Agent 行为
> **项目代码**: `aipy2/app/services/investor_service.py` — InvestorService 中的 Langfuse 集成
> **前置知识**: 项目已经在用 Langfuse，但你可能没打开看过——这笔记帮你读懂它

---

## 一、Langfuse 是什么

### 1.1 一句话

**Langfuse = 给 LLM 应用装的"行车记录仪"。** 每次 Agent 跑一次，Langfuse 就录一条完整的"行驶轨迹"——经过了哪些步骤、每步耗时多久、LLM 输入输出是什么。

### 1.2 没有 Langfuse 时

```
用户问："茅台股价多少"
Agent 回答："1856元"

你只知道：输入 A → 输出 B。
中间发生了什么？不知道。
为什么这次慢了 2 秒？不知道。
critic 有没有打回重写？不知道。
哪一步耗的 Token 最多？不知道。
```

### 1.3 有 Langfuse 后

```
用户问："茅台股价多少"
Agent 回答："1856元"

你能看到每一步：
  intent_node:      120ms, 150 tokens, 判断 "use_kb"
  rewrite_node:     350ms, 200 tokens, 改写为 3 条搜索词
  search_node:      500ms, 检索到 3 条文档
  answer_node:      800ms, 500 tokens, 生成回答
  critic_node:      200ms, 100 tokens, 评审 "pass"

哪步慢？search_node 花了 500ms → 检索慢。
哪步贵？answer_node 花了 500 tokens → 回答是最贵的。
```

---

## 二、四个核心概念

Langfuse 的数据模型只有四个概念，一层套一层：

```
Trace（一次完整请求）
  │
  ├── Span（一个步骤，可以嵌套）
  │     │
  │     └── Generation（一次 LLM 调用，附带了 token 用量）
  │
  └── Event（一个时间点，用于标记阶段性事件）
```

### 2.1 Trace（轨迹）

**Trace = 一次完整的用户请求**。从用户发消息到 Agent 返回最终答案，整条链路就是一个 Trace。

```python
# 项目代码中创建 Trace
langfuse_trace = langfuse_client.trace(
    id=trace_id,              # 唯一 ID，和数据库审计对齐
    name="investor_chat",     # Trace 名称，在 UI 里按这个分组
    user_id="customer_pro",   # 用户标识
    session_id=thread_id,     # 会话 ID，同一 thread 的多个 Trace 归为一组
    input={"query": query},   # 输入（用户的原始问题）
    metadata={
        "thread_id": thread_id,
        "response_mode": "stream",
    },
)
```

**在 UI 里**：你能看到一张表，每行是一个 Trace。点进去能看到这条 Trace 的所有 Span 和 Generation。

### 2.2 Span（步骤）

**Span = 一个执行步骤**。Span 可以嵌套——一个大 Span 里面包含多个子 Span。

```python
# 项目代码中创建 Span
langfuse_root_span = langfuse_trace.span(
    name="investor_flow",    # Span 名称
    input={"query": query},  # 这个步骤的输入
    metadata={"thread_id": thread_id},
)

# 步骤结束时
langfuse_root_span.end(
    output={
        "status": status,
        "duration_ms": duration_ms,
        "first_step_ms": first_step_ms,      # 首步耗时
        "first_content_ms": first_content_ms, # 首 token 耗时
    }
)
```

**在 UI 里**：你能看到 Span 的嵌套关系——investor_flow 里面包含了哪些子步骤。

### 2.3 Generation（LLM 调用）

**Generation = 一次 LLM API 调用**。这是最内层的概念——记录了 prompt 是什么、LLM 返回了什么、用了多少 token。

```python
# 项目代码中创建 Generation
langfuse_trace.generation(
    name="final_answer",
    model="investor-graph",
    input={"query": query},       # 发给 LLM 的 prompt
    output=final_answer,          # LLM 返回的答案
    usage_details={"total": total_tokens},  # Token 用量
    metadata={
        "use_kb": use_kb,
        "retry_count": retry_count,
        "review_status": review_status,
    },
)
```

**在 UI 里**：你能看到每个 Generation 的输入输出、Token 用量、耗时。这里的 `input` 就是**发给 LLM 的完整 prompt**——debug 时直接复制出来重现问题。

### 2.4 Event（事件）

**Event = 一个时间点标记**。不跟踪耗时，只是"在某个时刻发生了什么"的记录。

```python
# 项目代码中创建 Event
langfuse_trace.event(
    name=f"stage:{stage}",  # 事件名，如 "stage:intent", "stage:search"
    input=event.get("data", {}),  # 事件携带的数据
)
```

**在 UI 里**：你能看到 Agent 经过了哪些阶段——intent → rewrite → search → answer → critic。

### 2.5 四层结构的关系

```
Trace "investor_chat" (一次用户请求)
  │
  ├── Event "stage:intent"      ← 走到了 intent 阶段
  ├── Event "stage:rewrite"     ← 走到了 rewrite 阶段
  │
  ├── Span "investor_flow" (整个推理流程)
  │     │
  │     ├── Event "stage:search"  ← 走到了 search 阶段
  │     ├── Event "stage:answer"  ← 走到了 answer 阶段
  │     │
  │     └── Generation "final_answer" ← LLM 生成最终答案
  │           input:  prompt 内容
  │           output: 生成的答案
  │           usage:  500 tokens
  │
  └── 结束: duration_ms=1850ms, status="success"
```

---

## 三、项目中的实际集成

### 3.1 配置

```python
# aipy2/.env
LANGFUSE_PUBLIC_KEY=pk-xxx
LANGFUSE_SECRET_KEY=sk-xxx
LANGFUSE_HOST=http://localhost:3000    # 本地 Langfuse 服务
LANGFUSE_ENABLED=true                  # 开关
```

### 3.2 完整调用流程

```python
# aipy2/app/services/investor_service.py（简化关键路径）

async def run_investor_flow(self, query, thread_id, trace_id, role):
    # ① 创建 Trace
    langfuse_trace = _init_langfuse_client().trace(
        id=trace_id, name="investor_chat",
        session_id=thread_id, input={"query": query}
    )
    langfuse_root_span = langfuse_trace.span(name="investor_flow")

    try:
        # ② 消费 Agent 事件，每到一个阶段就打一个 Event
        async for event in multi_graph_agent.ask_stream_events(...):
            stage = event.get("stage", "")
            if stage not in {"content_delta", "final_answer", "done"}:
                langfuse_trace.event(name=f"stage:{stage}", ...)

            # ③ 首步/首 Token 耗时
            if "first_step" not yet recorded:
                first_step_at = time.perf_counter()

            # ④ final_answer 阶段创建 Generation
            if stage == "final_answer":
                langfuse_trace.generation(
                    name="final_answer",
                    output=final_answer,
                    usage_details={"total": total_tokens}
                )

            yield event

    finally:
        # ⑤ 无论成败都关闭 Span/Trace，落盘
        langfuse_root_span.end(output={...})
        langfuse_trace.update(output=final_answer)
        langfuse_client.flush()  # 确保数据写入
```

### 3.3 关键设计

**`finally` 块保证记录**：即使 Agent 中途抛异常，trace 和 span 也会被 end，能看到"这个请求失败了"。

**降级：Langfuse 挂了不影响主流程**：
```python
try:
    langfuse_client = _init_langfuse_client()
except Exception:
    langfuse_client = None  # 追踪失败，Agent 继续跑
```

**`session_id=thread_id`**：同一个对话的所有 Trace 在 UI 里归为一组，方便看到完整对话链。

---

## 四、Langfuse UI 怎么用

### 4.1 启动本地 Langfuse

```bash
# 用 Docker 启动
docker run -p 3000:3000 langfuse/langfuse:2
# 打开 http://localhost:3000
```

### 4.2 你能看到什么

| 页面 | 内容 |
|------|------|
| **Traces 列表** | 所有请求，按时间排序。绿色=成功，红色=失败 |
| **Trace 详情** | 点进去：所有 Span、Generation、Event 的时间线视图 |
| **Generation 详情** | LLM 的输入（prompt）、输出（answer）、Token 用量 |
| **Sessions** | 同一个 thread_id 的所有 Trace 归组，看到完整对话链 |
| **Dashboard** | 整体统计：总请求数、平均耗时、Token 消耗趋势、错误率 |

### 4.3 Debug 场景

```
场景 1: "用户说回答不对"
  → 打开那次请求的 Trace
  → 看 Generation → 看 input（发给 LLM 的完整 prompt）
  → 检查 knowledge 字段——是不是检索结果有问题？
  → 如果 knowledge 是空的或不对的，问题在 search_node

场景 2: "这次回答特别慢"
  → 打开 Trace → 看 Span 的时间线
  → search_node 花了 2s？→ 检索慢，可能是 Tavily API 超时
  → answer_node 花了 5s？→ DeepSeek 响应慢
  → 时间线直接告诉你瓶颈在哪

场景 3: "Token 花费突然变多了"
  → Dashboard → 看 Token 趋势
  → 某个时间段 Token 翻倍 → 可能是那次改了 answer_node 的 max_tokens 参数
  → 或者 retry_count 变多（critic 多次打回，每次重写都是额外的 Token）
```

---

## 五、LangSmith vs Langfuse：原理一样，选谁看场景

两家底层模型完全一致，都来自 OpenTelemetry 标准：

```
LangSmith:                  Langfuse:
  Trace                      Trace
  ├── Span                   ├── Span
  │   ├── Generation         │   ├── Generation
  │   └── ToolExecution      │   └── ToolExecution
  └── Feedback               └── Score
```

| | LangSmith | Langfuse |
|------|------|------|
| 开发者 | LangChain 官方 | 德国开源社区 |
| 部署方式 | ✅ SaaS（美国） | ✅ SaaS + **自部署** |
| 国内访问 | ❌ 慢/不稳定 | ✅ 自部署无延迟 |
| 数据出境 | ❌ 数据存美国 | ✅ 数据在自己服务器 |
| LangChain 集成 | **更深**（一行 `LANGSMITH_TRACING=true`） | 需要手动 SDK（但也不复杂） |
| LangGraph Studio | ✅ 原生配合 | ❌ 不支持 |
| 价格 | 免费额度有限 | 开源免费，自部署成本 = 服务器 |
| 国内公司选谁 | 几乎不选（合规问题） | **标配** |

**原理一样，选 Langfuse 的唯一原因就是"数据不出境 + 自己能部署"。** LangSmith 集成更深（和 LangGraph Studio 联调体验好），但国内金融合规卡死——对话记录传到美国服务器 = 红线。

---

## 六、面试速记

**Q: 你们怎么追踪 Agent 的行为？**
A: 用 Langfuse。每次请求创建一条 Trace，Agent 的每个阶段打一个 Event（stage:intent / stage:search 等），最终答案创建 Generation 记录 LLM 的输入输出和 Token 用量。出问题时打开那次请求的 Trace，沿着时间线就能看到哪步出了问题。

**Q: Langfuse 和 LangSmith 什么区别？为什么要会两个？**
A: 底层一样（都是 OpenTelemetry 的 Trace/Span 模型），差别在部署和集成深度。LangSmith 集成更深（官方出品，`LANGSMITH_TRACING=true` 一行搞定），但数据存美国。Langfuse 开源可自部署，数据留自己服务器——国内金融项目只能用 Langfuse。面试时说"两个都会，选型看合规"就够了。

**Q: 怎么追踪 Token 用量和成本？**
A: Langfuse 的 Generation 记录了每次 LLM 调用的 `usage_details`。Dashboard 里能看到 Token 消耗趋势。我们还在 `final_answer` 阶段汇总了全链路的 total_tokens，落审计库做成本分析。
