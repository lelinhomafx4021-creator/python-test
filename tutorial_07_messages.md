# 教程 07：Messages 消息体系 —— AI 的"聊天记录"

## 一句话概念
`state["messages"]` 就是 AI 和用户的**完整聊天记录**。每条消息都有"类型"（谁说的）和"内容"（说了什么），LangGraph 通过它来记住"上下文"。

---

## 1. 三种核心消息类型

```python
from langchain_core.messages import HumanMessage, AIMessage, ToolMessage
```

| 类型 | 代表谁 | JSON 结构 | 产生时机 |
|------|--------|-----------|----------|
| `HumanMessage` | 用户 | `{"type": "human", "content": "..."}` | 用户发问时 |
| `AIMessage` | AI | `{"type": "ai", "content": "..."}` | LLM 回答时 |
| `ToolMessage` | 工具 | `{"type": "tool", "content": "...", "name": "search"}` | 工具返回结果时 |

---

## 2. messages 列表的完整样貌

当用户问"茅台怎么样"并经过一轮完整的 Self-RAG 后，`state["messages"]` 长这样：

```python
[
    # 第1条：用户的原始问题
    HumanMessage(content="茅台最近的财报表现怎么样？"),
    
    # 第2条：answer_node 生成的第一版草稿（可能被 critic 打回）
    AIMessage(content="根据参考资料，贵州茅台2024年...（第一版草稿）"),
    
    # 如果被打回，rewrite → search → answer 再来一次
    # 第3条：answer_node 生成的修改版
    AIMessage(content="根据财务数据，茅台2024年Q1净利润同比增长15.7%...（改进版）"),
]
```

### 为什么要用 `add_messages` 归约器？

```python
messages: Annotated[list, add_messages]
```

- **没有 `add_messages`**：每次 `return {"messages": [new_msg]}` 会**覆盖**整个列表
- **有 `add_messages`**：会**追加**到列表末尾

```python
# answer_node 返回时
return {"messages": [response]}  
# 实际效果：state["messages"].append(response)
# 而不是：state["messages"] = [response]  ← 这样上下文就全丢了！
```

---

## 3. 消息的内部 JSON 结构

每个 Message 对象在内部序列化后长这样：

### HumanMessage
```json
{
  "type": "human",
  "content": "茅台最近的财报表现怎么样？",
  "additional_kwargs": {},
  "response_metadata": {}
}
```

### AIMessage（LLM的回复）
```json
{
  "type": "ai",
  "content": "根据财务数据，贵州茅台2024年Q1净利润同比增长15.7%...",
  "additional_kwargs": {},
  "response_metadata": {
    "token_usage": {
      "prompt_tokens": 520,
      "completion_tokens": 380,
      "total_tokens": 900
    },
    "model_name": "mimo-v2-pro",
    "finish_reason": "stop"
  }
}
```

> **重点**：`response_metadata.token_usage` 就是我们在 [investor_graph.py](file:///d:/ai-investor/aipy2/app/graph/investor_graph.py) 中读取 Token 消耗的来源！

### ToolMessage（工具调用结果）
```json
{
  "type": "tool",
  "content": "[BM25 匹配] 财务手册: 贵州茅台2024年一季度实现净利润...",
  "name": "search_knowledge_base",
  "tool_call_id": "call_abc123"
}
```

---

## 4. 消息在节点间的流转

```
[rewrite_node]
    读取: state["messages"][-1].content  → 拿到用户原始问题
    不写入 messages（只写 queries）

[search_node]
    不读取 messages
    不写入 messages（只写 knowledge）

[answer_node]
    读取: state["messages"] 全部 → 作为上下文传给 LLM
    写入: return {"messages": [AIMessage(...)]}  → 追加 AI 草稿

[critic_node]
    读取: state["messages"][-1].content → 拿到最新的 AI 草稿
    不写入 messages（只写 review_status、critic_feedback）
```

图示：
```
messages: [HumanMsg]
           │
     rewrite_node (读[0])
           │
     search_node (不碰 messages)
           │
     answer_node (读全部, 追加 AIMsg)
           │
messages: [HumanMsg, AIMsg_v1]
           │
     critic_node (读[-1])
           │ fail → 回到 rewrite
           │
     answer_node (读全部, 追加 AIMsg)
           │
messages: [HumanMsg, AIMsg_v1, AIMsg_v2]
           │
     critic_node (读[-1]) → pass!
```

---

## 5. 多轮对话的消息积累

如果用户连续问两个问题：

**第一轮：**
```python
messages = [
    HumanMessage("茅台怎么样？"),
    AIMessage("茅台2024年Q1净利润增长15.7%...")
]
```

**第二轮（用户追问）：**
```python
messages = [
    HumanMessage("茅台怎么样？"),           # 第一轮的问题
    AIMessage("茅台2024年Q1净利润增长15.7%..."), # 第一轮的答案
    HumanMessage("那和五粮液比呢？"),        # 第二轮的问题 ← 新追加
    AIMessage("对比来看，五粮液...")          # 第二轮的答案 ← 新追加
]
```

> **面试点**：这就是"上下文窗口"。AI 能"记住"之前聊过什么，是因为**所有历史消息都传给了 LLM**。但消息太多会超出 Token 限制，所以生产系统需要做"消息裁剪"（只保留最近 N 轮）。

---

## 6. 消息与 PostgreSQL Checkpointer

LangGraph 使用 `PostgresSaver` 把 State（包括 messages）持久化到数据库：

```python
checkpointer = PostgresSaver(memory_pool)
graph = workflow.compile(checkpointer=checkpointer)
```

持久化后的数据结构大概是这样的（简化版）：

```json
{
  "thread_id": "user_001:session_abc",
  "checkpoint_id": "ckpt_20240421_001",
  "channel_values": {
    "messages": [
      {"type": "human", "content": "茅台怎么样？"},
      {"type": "ai", "content": "茅台2024年Q1..."}
    ],
    "queries": ["茅台2024财报", "茅台股价"],
    "knowledge": "[BM25 匹配] 财务手册: ...",
    "retry_count": 1,
    "review_status": "pass",
    "total_tokens": 2340
  }
}
```

> **关键**：`thread_id` 是区分不同会话的唯一标识。同一个 `thread_id` 的多次对话会**追加**到同一条记录中，实现了"记忆"功能。

