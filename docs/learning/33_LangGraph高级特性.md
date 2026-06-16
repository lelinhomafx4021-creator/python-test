# 33_LangGraph高级特性：流式输出深剖 + Checkpointing + 并行 + 人工兜底

> **核心目标**: 理解 SSE 协议、astream 内部机制、错误处理——不只是"怎么用"而是"为什么这样设计"
> **项目代码**: `aipy2/app/api/v1/chat.py` / `app/graph/investor_graph.py` / `app/core/llm.py`

---

## 一、流式输出：从 LLM token 到浏览器逐字显示的全链路

### 1.1 为什么要流式

```
非流式（invoke）：
  用户发送请求 → [等待 3 秒...] → 完整答案一次性返回
  体验：用户对着白屏等 3 秒

流式（astream）：
  用户发送请求 → 0.5s 后开始逐字显示 → 3s 后显示完毕
  体验：用户看到字在动，知道 AI 在工作
```

**认知心理学角度**：用户感知的延迟不是"总耗时"，而是"第一个字的出现时间"。流式让首字延迟从 3s 降到 0.5s。

### 1.2 全链路：从 LLM 到浏览器

```
DeepSeek API                    LangGraph                  FastAPI                     浏览器
    │                              │                          │                          │
    │ token: "贵" ────────────→   │                          │                          │
    │                              │ astream("messages")      │                          │
    │                              │ yield content_delta      │                          │
    │                              │ ─────────────────────→   │                          │
    │                              │                          │ SSE: event: message      │
    │                              │                          │ data: {"delta":"贵"}     │
    │                              │                          │ ──────────────────────→  │
    │                              │                          │                          │ 显示 "贵"
    │                              │                          │                          │
    │ token: "州" ────────────→   │                          │                          │
    │                              │ yield content_delta      │                          │
    │                              │ ─────────────────────→   │                          │
    │                              │                          │ SSE: data: {"delta":"州"}│
    │                              │                          │ ──────────────────────→  │
    │                              │                          │                          │ 显示 "贵州"
    ...                            ...                        ...                        ...
```

**每个环节的职责**：
- **DeepSeek API**：支持 `stream: true`，逐 token 返回
- **LangGraph astream**：订阅 `stream_mode=["messages"]`，拿到 token chunk 后 yield
- **FastAPI StreamingResponse**：把 yield 出来的 dict 转成 SSE 格式（`event: message\ndata: {json}\n\n`）
- **浏览器 EventSource**：接收 SSE 事件，逐字追加到 DOM

### 1.3 SSE 协议：为什么不是 WebSocket

| | SSE | WebSocket |
|------|-----|-----------|
| 方向 | 单向（服务器→客户端） | 双向 |
| 协议 | HTTP（普通 HTTP 请求升级为长连接） | 独立协议 `ws://` |
| 断线重连 | 浏览器自动重连 | 需要自己实现 |
| 穿透代理 | HTTP 协议天然穿透 | 部分代理不支持 |
| 复杂度 | 极低（就是 HTTP 长连接） | 需要握手、心跳、帧解析 |

**项目选 SSE 因为**：AI 对话是单向流（服务器推给客户端），不需要客户端主动发消息。SSE 比 WebSocket 简单得多，而且浏览器 `EventSource` API 原生支持自动重连。

### 1.4 SSE 消息格式详解

```
HTTP 响应头:
  Content-Type: text/event-stream; charset=utf-8
  Cache-Control: no-cache, no-transform    ← 禁止中间代理缓存
  Connection: keep-alive                    ← 保持长连接
  X-Accel-Buffering: no                    ← 告诉 Nginx 不要缓冲

消息体（每条消息由 event + data 组成，以 \n\n 分隔）:
  event: message
  data: {"stage":"intent","data":{"step":"正在判断意图..."}}

  event: message
  data: {"stage":"content_delta","data":{"node":"answer","delta":"贵州"}}

  event: message
  data: {"stage":"content_delta","data":{"node":"answer","delta":"茅台"}}

  event: message
  data: {"stage":"final_answer","data":{"answer":"贵州茅台...","usage":1250}}

  event: message
  data: {"stage":"done","data":{"status":"success"}}
```

**双流设计**：项目同时用了 `stream_mode=["updates", "messages"]`：

```python
# investor_graph.py — ask_stream_events()
async for mode, chunk in graph.astream(input, config, stream_mode=["updates", "messages"]):

    if mode == "updates":
        # chunk = {node_name: {updated_fields}}
        # 例如: {"intent": {"step": "正在判断意图...", "use_kb": True}}
        for node_name, updates in chunk.items():
            if "step" in updates:
                yield {"stage": node_name, "data": {"step": updates["step"]}}

    elif mode == "messages":
        # chunk = (AIMessageChunk, metadata)
        # 例如: (AIMessageChunk(content="贵州"), {"langgraph_node": "answer"})
        message_chunk, metadata = chunk
        yield {"stage": "content_delta", "data": {"delta": message_chunk.content}}
```

**为什么需要两个流**：
- `updates` 流告诉你"Agent 走到了哪一步"（意图识别→改写→搜索→回答→评审）
- `messages` 流告诉你"LLM 正在输出什么字"（打字机效果）
- 前端用 updates 渲染"思考步骤面板"，用 messages 渲染"对话气泡的打字效果"

### 1.5 流式错误处理

```python
# aipy2/app/api/v1/chat.py
async def event_gen():
    try:
        async for evt in investor_service.run_investor_flow(...):
            yield f"event: message\ndata: {json.dumps(evt)}\n\n"
    except Exception as exc:
        # 流式中途出错了——不能抛 HTTP 500，因为响应头已经发出去了
        # 必须把错误包装成一条 SSE 事件
        yield f"event: message\ndata: {json.dumps({'stage': 'error', 'data': {'msg': str(exc)}})}\n\n"

return StreamingResponse(event_gen(), ...)
```

**为什么不能直接抛异常**：SSE 的响应头（`Content-Type: text/event-stream`）在第一个 `yield` 时就发出去了。之后 HTTP 状态码不能再改。所以流式接口的错误只能通过**事件通道**通知前端。

### 1.6 Nginx 缓冲问题

```
问题：Nginx 默认会缓冲反向代理的响应，等积累到一定大小再发给客户端。
      这对 SSE 是致命的——打字机效果变成"等 5 秒然后一次蹦出全部文字"。

解决：两个 header 配合
  X-Accel-Buffering: no   ← 告诉 Nginx 不要缓冲这个请求
  Cache-Control: no-cache, no-transform  ← 告诉中间代理不要缓存
```

---

## 二、Checkpointing — 状态持久化

### 2.1 没有 Checkpointing 会怎样

```python
# 没有 checkpointer：每次调用都是全新的 Agent
config = {"configurable": {"thread_id": "user_1001"}}

# 第一轮
agent.invoke({"messages": [HumanMessage("茅台股价多少")]}, config)
# → "1856元"

# 第二轮（同一个 thread_id）
agent.invoke({"messages": [HumanMessage("那五粮液呢")]}, config)
# → 没有 checkpointer: LLM 不知道"那"指的是什么，因为看不到上轮对话
# → 有 checkpointer: LLM 看到历史消息 ["茅台股价多少", "1856元", "那五粮液呢"]
#                    理解了"那"指的是"股价"
```

### 2.2 Checkpoint 里存了什么

```json
// LangGraph 内部存储的 checkpoint 结构（简化）
{
  "thread_id": "user_1001",
  "checkpoint_id": "1ef9a3...",
  "values": {
    "messages": [
      {"type": "human", "content": "茅台股价多少"},
      {"type": "ai", "content": "贵州茅台(600519)当前1856元..."},
      {"type": "human", "content": "那五粮液呢"},
    ],
    "step": "✍️ 正在生成数据查询回答...",
    "retry_count": 0,
    "total_tokens": 1250,
    ...
  },
  "next": ["answer"]  // 下一步要执行哪个节点
}
```

**关键**：不只是消息历史，而是**完整的 AgentState**。包括当前步骤、重试次数、检索结果——全部在数据库里。即使服务器重启，同一个 thread_id 也能原地继续。

### 2.3 PostgreSQL 里实际的三张表

```sql
-- LangGraph PostgresSaver.setup() 自动建的三张表

-- ① checkpoint_blobs: 存序列化后的 State 大对象
CREATE TABLE checkpoint_blobs (
    thread_id TEXT NOT NULL,
    checkpoint_ns TEXT NOT NULL DEFAULT '',
    channel TEXT NOT NULL,
    version TEXT NOT NULL,
    type TEXT NOT NULL,
    blob BYTEA,                            -- msgpack 序列化的 State 数据
    PRIMARY KEY (thread_id, checkpoint_ns, channel, version)
);

-- ② checkpoint_writes: 存每个节点执行后的增量写入
CREATE TABLE checkpoint_writes (
    thread_id TEXT NOT NULL,
    checkpoint_ns TEXT NOT NULL DEFAULT '',
    checkpoint_id TEXT NOT NULL,
    task_id TEXT NOT NULL,
    idx INTEGER NOT NULL,
    channel TEXT NOT NULL,
    type TEXT,
    blob BYTEA,                            -- 节点返回值的序列化数据
    task_path TEXT NOT NULL DEFAULT '',
    PRIMARY KEY (thread_id, checkpoint_ns, checkpoint_id, task_id, idx)
);

-- ③ checkpoints: 存每个 checkpoint 的元数据
CREATE TABLE checkpoints (
    thread_id TEXT NOT NULL,
    checkpoint_ns TEXT NOT NULL DEFAULT '',
    checkpoint_id TEXT NOT NULL,
    parent_checkpoint_id TEXT,
    type TEXT,
    checkpoint JSONB NOT NULL,             -- {"v": 5, "ts": "2026-06-07...", "channel_values": {...}}
    metadata JSONB NOT NULL DEFAULT '{}',  -- {"step": 3, "source": "loop", ...}
    PRIMARY KEY (thread_id, checkpoint_ns, checkpoint_id)
);
```

**手动查 checkpoint 的 SQL**：

```sql
-- 查某个用户的所有 checkpoint（按时间排序）
SELECT thread_id, checkpoint_id, metadata, checkpoint
FROM checkpoints
WHERE thread_id = 'user_1001'
ORDER BY checkpoint->>'ts' DESC;

-- 查某个 checkpoint 的完整内容
SELECT cb.thread_id, cb.channel, cb.type, encode(cb.blob, 'escape')
FROM checkpoint_blobs cb
WHERE cb.thread_id = 'user_1001'
  AND cb.version = '1ef9a3...';  -- checkpoint_id

-- 查某个 checkpoint 中各节点的执行结果
SELECT task_id, task_path, channel, type
FROM checkpoint_writes
WHERE thread_id = 'user_1001'
  AND checkpoint_id = '1ef9a3...'
ORDER BY task_path, idx;
```

### 2.4 代码里直接操作 checkpoint

```python
# 场景：用户在对话中途关闭了浏览器，2 小时后重新打开
config = {"configurable": {"thread_id": "user_1001"}}

# 不需要从头开始——LangGraph 自动加载上次的 checkpoint
agent.invoke({"messages": [HumanMessage("继续刚才的话题")]}, config)
# LLM 看到完整的对话历史，知道"刚才的话题"是什么
```

---

## 三、并行执行 — asyncio.gather vs 图级并行

### 3.1 两种并行方式的区别

```python
# 图级并行：不同节点同时跑（适合语义上独立的任务）
workflow.add_edge(START, "technical_analysis")   # 技术面分析
workflow.add_edge(START, "fundamental_analysis") # 基本面分析
# 两个节点同时执行，互不依赖

# 节点内并行：同一个节点里多个 IO 操作同时跑（项目做法）
async def fetch_data_node(state):
    results = await asyncio.gather(
        fetch_market_data(...),      # HTTP 请求1
        fetch_financial_data(...),   # HTTP 请求2
        fetch_announcements(...),    # HTTP 请求3
        fetch_news_data(...),        # HTTP 请求4
        fetch_retrieval_data(...),   # HTTP 请求5
        return_exceptions=True,      # ← 关键：单个失败不拖垮全部
    )
```

### 3.2 为什么项目选节点内并行

**图级并行的问题**：并行节点结束后需要一个"汇合点"（barrier）。汇合时如果某个节点失败了，其他节点的结果怎么办？LangGraph 需要额外的错误处理逻辑。

**节点内并行的优势**：`asyncio.gather(return_exceptions=True)` 是一个成熟的错误处理模式——单个数据源失败返回 `None`，其他数据源的正常结果继续使用。用户无感知。

---

## 四、人工兜底 — 业务决策 vs 框架机制

### 4.1 两种 HITL 的区别

```
LangGraph 的 interrupt（框架机制）：
  Agent 执行到 "发送邮件" 前暂停 → 等待人工点"确认" → 继续或取消
  适合：审批流、敏感操作确认

项目的人工兜底（业务决策）：
  critic 3 次都不通过 → 自动路由到 handoff_node → 生成交接摘要 → 结束
  适合：AI 搞不定时自动降级，不需要人工实时干预
```

### 4.2 为什么项目没选 LangGraph interrupt

**项目的人工兜底是自动的**——critic 判定 3 次失败后，不是"暂停等待"，而是"直接转人工"。不需要人为介入的步骤不需要 interrupt。如果用 `interrupt_before`，反而需要额外的前端逻辑来"显示审批按钮"。

---

## 五、面试速记

**Q: SSE 和 WebSocket 的区别？为什么选 SSE？**
A: SSE 是 HTTP 单向推送（服务器→客户端），WebSocket 是双向通信。AI 对话是单向流——服务器推回答给客户端，客户端不需要主动发消息。SSE 更简单，浏览器原生 `EventSource` 自动重连，HTTP 协议天然穿透代理。

**Q: stream_mode 的 updates 和 messages 分别用来做什么？**
A: `updates` 产出节点状态变化（"正在搜索..."），前端用做思考步骤面板。`messages` 产出 LLM token 增量（逐字），前端用做对话气泡的打字效果。双流配合实现"思考过程 + 逐字回答"的完整体验。

**Q: 流式输出的错误怎么处理？**
A: 不能用 HTTP 状态码（响应头已发送）。必须把错误包装成 SSE 事件，通过事件通道告知前端。前端监听 `stage: "error"` 事件做降级展示。

**Q: 为什么 Checkpoint 用 PostgreSQL 而不是 Redis？**
A: PostgreSQL 是项目已有基础设施（同一数据库做业务存储、向量检索、对话状态）。Redis 适合缓存但重启丢失，checkpoint 需要持久化保证——服务器重启后用户回到对话不丢失上下文。

**Q: 什么时候用图级并行，什么时候用节点内并行？**
A: 图级并行适合语义上独立的不同步骤（技术面分析 vs 基本面分析）。节点内并行适合同一语义下多个 IO 操作（行情+财务+公告都是"获取数据"）。项目选后者因为数据获取是同一件事的不同维度。
