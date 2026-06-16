# 48_记忆管理CRUD：Agent 对话历史的增删改查

> **核心问题**: LangGraph 的 checkpoint 不是"只能追加"——你能增删改查里面的任何数据
> **项目代码**: `app/graph/state.py` (AgentState 定义) + `app/core/llm.py` (checkpointer)

---

## 一、查：读当前 State

```python
from langgraph.graph import StateGraph

config = {"configurable": {"thread_id": "user_1001"}}

# 读当前完整 State
state = await graph.aget_state(config)
```

**`aget_state` 参数**：

| 参数 | 类型 | 必填 | 作用 |
|------|------|------|------|
| `config` | `RunnableConfig` | ✅ | 必须包含 `configurable.thread_id`。读到的是这个 thread 的最新 checkpoint |
| `subgraphs` | `bool` | 否(False) | True=递归查子图的状态 |

**返回值 `StateSnapshot` 的属性**：

| 属性 | 类型 | 说明 |
|------|------|------|
| `values` | `dict \| None` | 当前 State 的所有字段和值 |
| `next` | `tuple[str]` | 下一步要执行哪些节点（空=图已结束） |
| `config` | `dict` | 包含 `thread_id` 和 `checkpoint_id` |
| `metadata` | `dict` | checkpoint 元数据（来源、步骤等） |
| `created_at` | `str` | checkpoint 创建时间 |

```python
state = await graph.aget_state(config)
print(state.values["messages"])       # 所有消息
print(state.values["retry_count"])    # 重试次数
print(state.next)                      # ("answer",) — 下一步执行 answer 节点
print(state.metadata)                  # {"step": 3, "source": "loop"}

# 读历史——回到任意时间点
async for snapshot in graph.aget_state_history(config):
    print(f"checkpoint {snapshot.config['configurable']['checkpoint_id']}")
    print(f"  messages 数量: {len(snapshot.values.get('messages', []))}")
```

---

## 二、增：追加消息（默认行为）

```python
# 正常追加——因为 messages 用了 add_messages reducer
await graph.aupdate_state(
    config,
    {"messages": [HumanMessage(content="继续刚才的话题")]}
)

# 下次 invoke 时，这条消息会自动追加到已有的 messages 列表末尾
```

---

## 三、删：RemoveMessage

```python
from langgraph.graph.message import RemoveMessage

# ===== 方式1: 删除特定消息 =====
state = await graph.aget_state(config)
target_msg = state.values["messages"][2]  # 第 3 条消息
await graph.aupdate_state(
    config,
    {"messages": [RemoveMessage(id=target_msg.id)]}
)
# add_messages 遇到 RemoveMessage → 从列表里移除对应 ID 的消息

# ===== 方式2: 删除所有消息后只保留最近 N 条 =====
all_msgs = state.values["messages"]
old_msg_ids = [m.id for m in all_msgs[:-6]]  # 除了最近 3 轮
removes = [RemoveMessage(id=mid) for mid in old_msg_ids]
await graph.aupdate_state(config, {"messages": removes})

# ===== 方式3: 一键清空 =====
from langgraph.graph.message import REMOVE_ALL_MESSAGES
await graph.aupdate_state(
    config,
    {"messages": [RemoveMessage(id=REMOVE_ALL_MESSAGES)]}
)
# 消息列表全部清空，对话从头开始
```

---

## 四、改：直接覆盖任意字段

**`aupdate_state` 参数**：

| 参数 | 类型 | 必填 | 作用 |
|------|------|------|------|
| `config` | `RunnableConfig` | ✅ | 必须含 `configurable.thread_id`。在哪个 thread 上改 |
| `values` | `dict` | ✅ | 要覆盖的字段和值。会 merge 到当前 State，不传的字段保持不变 |
| `as_of` | `StateSnapshot \| tuple[str]` | 否 | **时间旅行**。传一个历史 checkpoint → 回退到那个时间点，然后应用 values |

```python
# ===== 覆盖普通字段 =====
await graph.aupdate_state(config, {
    "step": "人工修正后的状态",
    "retry_count": 0,               # 重置重试计数
    "critic_feedback": "",          # 清空评审意见
    "review_status": "",            # 清空评审状态
})

# ===== 修改 messages 里某条消息的内容 =====
# messages 里的消息不可直接修改，但你可以删掉旧的然后加新的
target_id = state.values["messages"][3].id
await graph.aupdate_state(config, {
    "messages": [
        RemoveMessage(id=target_id),
        AIMessage(content="修正后的回答", id=target_id)  # 用同一个 id
    ]
})

# ===== 回退到某个历史 checkpoint（时间旅行）=====
history = []
async for snapshot in graph.aget_state_history(config):
    history.append(snapshot)

# 回退到前一步
await graph.aupdate_state(config, {}, as_of=history[1])
```

---

## 五、增删改查实战场景

### 场景 1: "忘掉之前的对话，重新开始"

```python
state = await graph.aget_state(config)
all_ids = [m.id for m in state.values["messages"]]
await graph.aupdate_state(
    config,
    {"messages": [RemoveMessage(id=mid) for mid in all_ids]}
)
```

### 场景 2: "刚才那段回答不对，删掉重来"

```python
state = await graph.aget_state(config)
# 删掉最后一条 AI 回答
last_ai = state.values["messages"][-1]
await graph.aupdate_state(
    config,
    {
        "messages": [RemoveMessage(id=last_ai.id)],
        "step": "重新生成回答..."
    }
)
# 再调一次 invoke，Agent 会基于修正后的历史重新生成
```

### 场景 3: "这个用户的 VIP 状态变了，更新 role 字段"

```python
await graph.aupdate_state(config, {"role": "vip"})
# 下个节点读到 state["role"] = "vip"，走 VIP 流程
```

### 场景 4: "检索结果不对，手动注入正确的资料"

```python
await graph.aupdate_state(config, {
    "knowledge": "手动修正后的资料：贵州茅台2024年净利润747亿...",
    "skill_context": "【手动修正】最新行情：1856元"
})
```

---

## 六、完整端到端场景：对话 → 压缩 → 跨会话恢复

```python
"""
完整演示:
1. 用户和 Agent 聊了 30 轮（模拟）
2. 对话太长 → 摘要压缩
3. 用户关闭浏览器，第二天回来
4. 同一个 thread_id → 恢复压缩后的上下文 → 继续对话
"""
from langchain_core.messages import HumanMessage, AIMessage, SystemMessage
from langgraph.graph.message import RemoveMessage, REMOVE_ALL_MESSAGES
from langchain.chat_models import init_chat_model

llm = init_chat_model("deepseek-v4")

# ===== 第 1 天: 用户聊了 30 轮 =====
config = {"configurable": {"thread_id": "user_1001"}}

# 模拟对话（实际项目里每次用户发消息 → graph.ainvoke）
for i in range(30):
    result = await graph.ainvoke(
        {"messages": [HumanMessage(content=f"第{i+1}轮问题...")]},
        config
    )
    # 实际项目中: AIMessage 从 graph 返回，自动写入 checkpoint

# ===== 检查当前状态大小 =====
state = await graph.aget_state(config)
msg_count = len(state.values["messages"])
print(f"当前消息数: {msg_count} 条")  # ~60 条（30 轮 × 用户消息 + AI回复）
print(f"估算 token: ~{msg_count * 200} tokens")  # 假设每条 ~200 token

# ===== 压缩 =====
async def compress_conversation(config, keep_last=10):
    """保留最近 10 轮，其余用 LLM 总结"""
    state = await graph.aget_state(config)
    messages = state.values["messages"]

    keep_count = keep_last * 2  # 每轮 = human + ai
    old_msgs = messages[:-keep_count]
    recent_msgs = messages[-keep_count:]

    if not old_msgs:
        return

    # LLM 总结旧对话
    summary_text = ""
    for m in old_msgs:
        role = "用户" if isinstance(m, HumanMessage) else "AI"
        summary_text += f"[{role}]: {m.content[:200]}\n"  # 截断长消息

    summary_response = await llm.ainvoke(
        f"用 3-5 句话总结这段对话的关键信息:\n{summary_text}"
    )

    # 删旧消息 + 注入摘要 + 保留最近
    old_ids = [m.id for m in old_msgs]
    await graph.aupdate_state(config, {
        "messages": [
            RemoveMessage(id=mid) for mid in old_ids
        ] + [
            SystemMessage(content=f"[历史摘要] {summary_response.content}"),
        ] + recent_msgs,
    })

    new_state = await graph.aget_state(config)
    print(f"压缩: {msg_count} 条 → {len(new_state.values['messages'])} 条")

await compress_conversation(config, keep_last=5)

# ═════════════════════════════════════
# 第 2 天: 用户回来继续聊
# ═════════════════════════════════════

# 同一个 thread_id，Agent 自动从 checkpoint 恢复
result = await graph.ainvoke(
    {"messages": [HumanMessage(content="接着昨天的话题，茅台现在什么价？")]},
    config  # ← 同一个 config，同一个 thread_id
)

# LLM 看到的上下文:
#   [System] [历史摘要] 用户昨天主要讨论了茅台和五粮液的股价对比...
#   [Human] 第28轮问题...
#   [AI] 第28轮回答...
#   ... (最近 5 轮完整对话)
#   [Human] 接着昨天的话题，茅台现在什么价？   ← 今天的新问题

# ✅ Agent 知道"昨天的话题"是什么——因为摘要里有
# ✅ Token 节省: ~60 条消息 → ~15 条（摘要 + 最近 5 轮）
```

---

## 七、跨会话记忆（Store API）：记住用户偏好

Checkpoint 是**线程级别的**——同一个 thread_id 才能恢复。如果用户开了新对话（新 thread_id），之前存的东西全没了。

**Store 解决这个问题：跨线程持久化**。

```python
from langgraph.store.memory import InMemoryStore
from langgraph.store.base import IndexConfig
from collections.abc import Sequence

# ═════════════════════════════════════
# 初始化 Store（生产环境用 PostgresStore）
# ═════════════════════════════════════

def embed_texts(texts: Sequence[str]) -> list[list[float]]:
    """生产环境用真实的 embedding 函数"""
    return [[0.0] * 128 for _ in texts]

store = InMemoryStore(
    index=IndexConfig(embed=embed_texts, dims=128)
)

# 把 store 注入 Agent（create_agent 模式）
agent = create_agent(
    model=llm,
    tools=[get_stock_price],
    store=store,  # ← 跨会话记忆
)

# ═════════════════════════════════════
# 对话 A: 用户说了一些偏好
# ═════════════════════════════════════
config_a = {"configurable": {"thread_id": "thread_a"}}

# Agent 内部工具调用 store 存偏好
# 或者你手动存:
store.put(
    namespace=("users", "user_1001"),  # 按用户组织
    key="preferences",
    value={
        "focus_stocks": ["600519", "000858"],
        "prefer_short_answer": True,
        "risk_level": "稳健",
    }
)

# ═════════════════════════════════════
# 对话 B: 新对话，但能读到以前的偏好
# ═════════════════════════════════════
config_b = {"configurable": {"thread_id": "thread_b"}}  # ← 新线程！

# 读该用户的所有记忆
items = store.search(
    namespace=("users", "user_1001"),
    query="股票偏好"  # 语义搜索
)
for item in items:
    print(f"key={item.key}, value={item.value}")

# Agent 在新对话里自动知道: 用户关注 600519、喜欢简短回答、风险稳健
# 不需要用户再说一遍

# ═════════════════════════════════════
# 完整工具函数: Agent 自己读写记忆
# ═════════════════════════════════════

from langchain.tools import tool, ToolRuntime

@tool
async def save_user_fact(
    fact: str,
    runtime: ToolRuntime,
) -> str:
    """保存用户说的重要信息（偏好、关注股票、风险偏好等）"""
    store = runtime.store
    user_id = runtime.context.user_id
    store.put(
        namespace=("users", user_id),
        key=f"fact_{hash(fact)}",  # 简单去重
        value={"fact": fact, "saved_at": str(datetime.now())},
    )
    return f"已记住: {fact}"

@tool
async def recall_user_info(
    query: str,
    runtime: ToolRuntime,
) -> str:
    """回忆用户之前说过的相关信息"""
    store = runtime.store
    user_id = runtime.context.user_id
    items = store.search(
        namespace=("users", user_id),
        query=query,
        limit=5,
    )
    if not items:
        return "没有找到相关信息"
    return "\n".join([item.value["fact"] for item in items])
```

**Checkpoint vs Store 对比**：

```
Checkpoint（短期记忆）:
  - 范围: 单个 thread_id
  - 存什么: 完整对话历史
  - 生命周期: 对话期间
  - 例子: "上一条消息是什么"

Store（长期记忆）:
  - 范围: 跨所有 thread
  - 存什么: 用户偏好、关键事实
  - 生命周期: 永久
  - 例子: "这个用户喜欢简短回答、关注茅台"
```

---

## 八、企业实际怎么做跨记忆对话

**不靠 LangGraph Store（太新），靠普通数据库 + prompt 注入**。

### 8.1 三张业务表

```sql
-- ① 用户画像（手动维护或 Agent 更新）
CREATE TABLE users (
    user_id TEXT PRIMARY KEY,
    risk_level TEXT,        -- "保守" / "稳健" / "激进"
    focus_stocks TEXT[],    -- ["600519", "000858"]
    preferences JSONB       -- {"prefer_short": true}
);

-- ② 从对话中抽取的事实（Agent 自动写入）
CREATE TABLE user_facts (
    id SERIAL PRIMARY KEY,
    user_id TEXT NOT NULL,
    fact TEXT NOT NULL,          -- "用户偏好白酒板块"
    source TEXT,                 -- 来自哪个对话: thread_id
    created_at TIMESTAMP DEFAULT NOW()
);

-- ③ 对话摘要（每段对话结束后 LLM 总结写入）
CREATE TABLE conversations (
    thread_id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    summary TEXT,                -- "用户咨询了茅台估值和五粮液对比"
    created_at TIMESTAMP DEFAULT NOW()
);
```

### 8.2 每次新对话：查出来 → 注入 prompt

```python
async def build_user_context(user_id: str) -> str:
    """新对话开始前，拼装用户上下文——纯 SQL，无框架依赖"""
    
    user = await db.fetch_one(
        "SELECT risk_level, focus_stocks FROM users WHERE user_id = $1", user_id
    )
    facts = await db.fetch_all(
        "SELECT fact FROM user_facts WHERE user_id = $1 ORDER BY created_at DESC LIMIT 20", user_id
    )
    summaries = await db.fetch_all(
        "SELECT summary FROM conversations WHERE user_id = $1 ORDER BY created_at DESC LIMIT 5", user_id
    )
    
    # 拼成纯文本，塞进 system prompt
    return f"""【用户画像】风险偏好: {user['risk_level']}，关注: {user['focus_stocks']}
【用户关键事实】{chr(10).join(f'- {f["fact"]}' for f in facts)}
【近期对话摘要】{chr(10).join(f'- {s["summary"]}' for s in summaries)}"""

# 新对话时注入
system_prompt = f"你是投研助手。\n{await build_user_context(user_id)}"
```

### 8.3 什么时候存：三种时机

```
① 每轮对话后:
  用户说一句 → 立刻 LLM 提取事实 → 存
  优点: 不漏  缺点: 每轮都调 LLM，费 Token

② 对话结束后（用户关闭/切换话题/超时）:
  一整段对话 → LLM 一次总结 → 存
  优点: 省 Token  缺点: 对话中途崩溃就没存上

③ 关键节点触发（Agent 自己判断）:
  Agent 发现"用户说了重要的事" → 调 remember_user_fact 工具
  优点: 精准  缺点: 依赖 Agent 判断，可能漏

企业做法: ② + ③ 混合 — 对话结束统一总结，关键信息实时存。
```

### 8.4 压缩记忆的实现

```python
# 就是你理解的那样：删掉旧消息 → LLM 总结 → 注入一条 SystemMessage

async def compress_conversation(config, keep_last_n_pairs=5):
    state = await graph.aget_state(config)
    messages = state.values["messages"]
    
    keep_count = keep_last_n_pairs * 2
    old_msgs = messages[:-keep_count]
    recent_msgs = messages[-keep_count:]
    
    # ① LLM 总结旧消息
    summary = await llm.ainvoke(
        f"用几句话总结这段对话的关键信息:\n" +
        "\n".join([f"{'用户' if m.type == 'human' else 'AI'}: {m.content[:200]}" for m in old_msgs])
    )
    
    # ② 删掉旧的 + 注入总结
    old_ids = [m.id for m in old_msgs]
    await graph.aupdate_state(config, {
        "messages": [
            RemoveMessage(id=mid) for mid in old_ids
        ] + [
            SystemMessage(content=f"[历史摘要] {summary.content}"),
        ] + recent_msgs,
    })
    
# 原来: 60 条消息，30000 token
# 现在: 1 条摘要 + 最近 5 轮，2000 token
```

### 8.5 整个记忆管理就三个操作

```
查: aget_state(config)              → 读当前 State
改: aupdate_state(config, {字段: 值}) → 覆盖任意字段
删: RemoveMessage(id=xxx)           → add_messages reducer 自动删除

没有更多 API 了。记忆管理 = 操作一个 dict。
Checkpoint 持久化到 PostgreSQL，所有操作自动落盘。
```

---

## 九、面试速记

**Q: LangGraph 的记忆怎么管理？**
A: 不是只能追加。读用 `aget_state()`（当前）和 `aget_state_history()`（历史）。增是正常的节点 return。删用 `RemoveMessage(id=xxx)`，`add_messages` reducer 会自动从列表里移除对应消息。改用 `aupdate_state()` 直接覆盖任意字段。还支持 `as_of` 参数回退到任意历史 checkpoint——这就是时间旅行。

**Q: 用户说"忘掉刚才说的"，你怎么实现？**
A: 拿 `aget_state()` 读到当前 messages，找到最后一条用户消息和对应的 AI 回复，用 `RemoveMessage` 删掉这两条，`aupdate_state` 写回去。下次 invoke 时 Agent 看到的历史就像那段对话没发生过。
