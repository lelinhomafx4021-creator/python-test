# 32_LangGraph核心架构：StateGraph 每个 API 拆开讲

> **核心目标**: 理解 StateGraph 的每一个 API 方法——签名、参数、内部做了什么
> **项目代码**: `aipy2/app/graph/state.py` → `nodes.py` → `routes.py` → `investor_graph.py`
> **面试价值**: 手写 StateGraph 是 2026 年 AI 工程师面试的硬通货

---

## 一、StateGraph 类：先看构造函数

```python
from langgraph.graph import StateGraph

workflow = StateGraph(AgentState)
```

**StateGraph 构造函数只接受一个参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| `state_schema` | `Type[TypedDict]` | **必须是 TypedDict 子类**（LangGraph v1 不再支持 Pydantic 做 State） |

**TypedDict 为什么是必须的**：LangGraph 需要在编译时知道 State 有哪些字段、每个字段的类型。TypedDict 提供了类型信息，而且它是纯 Python 类型（运行时就是 dict），不需要序列化/反序列化。

```python
# ✅ 正确：TypedDict
class AgentState(TypedDict):
    messages: Annotated[list, add_messages]
    step: str

# ❌ 错误：Pydantic BaseModel（LangGraph v1 不支持）
class AgentState(BaseModel):
    messages: list = []
    step: str = ""
```

---

## 二、add_node：注册节点

### 2.1 API 签名

```python
workflow.add_node(
    node_name: str,     # 节点的唯一标识符
    action: Callable,   # 节点函数
) -> None
```

### 2.2 参数详解

**`node_name`**：字符串标识符，全局唯一。这个名称在 `add_edge` 和 `add_conditional_edges` 中引用，也在 `stream_mode="updates"` 时作为 chunk 的 key 返回。

```python
workflow.add_node("intent", route_intent_node)
#                 ↑         ↑
#                 名称      函数
# 之后的所有边定义都用 "intent" 引用这个节点
```

**`action`**：节点函数，可以是同步或异步函数。签名必须是 `(state: StateType) -> dict` 或其 async 版。

```python
# 同步 Node
def my_sync_node(state: AgentState) -> dict:
    return {"step": "done"}

# 异步 Node（项目全用这个）
async def my_async_node(state: AgentState) -> dict:
    result = await some_async_call()
    return {"step": result}

# 都支持——LangGraph 自动检测是 sync 还是 async
workflow.add_node("sync", my_sync_node)
workflow.add_node("async", my_async_node)
```

### 2.3 内部发生了什么

```
add_node("intent", route_intent_node)
    │
    ▼
LangGraph 内部:
  存储映射: "intent" → route_intent_node
  分析函数签名: 提取 State 类型 → 生成输入 Schema
  分析返回值: 提取 dict 的键 → 确定这个节点会更新哪些 State 字段
  注册到图: 节点加入图的数据结构
```

---

## 三、add_edge：普通边（固定流向）

### 3.1 API 签名

```python
workflow.add_edge(
    start_node: str,   # 起始节点名
    end_node: str,     # 目标节点名
) -> None
```

### 3.2 使用

```python
# 从 START（图开始执行的虚拟起点）
workflow.add_edge(START, "intent")    # 执行一开始 → 先进 intent 节点

# 节点间连接
workflow.add_edge("rewrite", "search")# rewrite 执行完 → 必定走 search
workflow.add_edge("search", "answer") # search 执行完 → 必定走 answer

# 到 END（图执行结束的虚拟终点）
workflow.add_edge("answer", END)      # answer 执行完 → 图结束
```

**`START` 和 `END` 是两个特殊的虚拟节点**：
- `START`：图的入口。`add_edge(START, "xxx")` 指定第一个要执行的节点。
- `END`：图的出口。`add_edge("xxx", END)` 表示这个节点执行完后图就结束了。

### 3.3 普通边的局限

普通边是"必然发生"的连接——A 执行完 100% 走 B。现实中大多数决策不是这样的——需要条件边。

---

## 四、add_conditional_edges：条件边（图的灵魂）

### 4.1 API 签名

```python
workflow.add_conditional_edges(
    source: str,                          # 起始节点名
    path: Callable[[State], str],         # 路由函数
    path_map: dict[str, str] | None = None,  # 路由映射
) -> None
```

### 4.2 逐参数详解

**`source`**：从哪个节点出发后执行条件判断。路由函数会在 source 节点执行完成后被调用。

**`path`**：路由函数。接收当前 State 作为参数，返回一个字符串（目标节点名）。

```python
def route_intent(state: AgentState) -> str:
    """路由函数的返回值必须是一个节点名称（字符串）。"""
    if state.get("handoff_to_human"):
        return "handoff"           # → 返回节点名
    return "use_kb" if state.get("use_kb", True) else "no_kb"
```

**返回值类型**：用 `Literal` 约束可以防止拼写错误：
```python
from typing import Literal

def route_intent(state: AgentState) -> Literal["use_kb", "no_kb", "handoff"]:
    ...
    # 如果你返回了不在 Literal 里的值，类型检查器会报错
```

**`path_map`**：路由函数返回值 → 目标节点的映射字典。不是必须的——如果不传，路由函数的返回值直接作为目标节点名。

```python
workflow.add_conditional_edges(
    "intent",            # 从 intent 节点之后开始判断
    route_intent,        # 路由函数
    {                    # path_map：将路由函数返回值映射到实际节点
        "use_kb": "rewrite",        # 返回 "use_kb" → 跳到 rewrite 节点
        "no_kb": "direct_answer",   # 返回 "no_kb" → 跳到 direct_answer 节点
        "handoff": "handoff",       # 返回 "handoff" → 跳到 handoff 节点
    }
)
```

### 4.3 项目里的三条条件边

```python
# 边1: 意图分类 → 改写/直接回答/转人工
workflow.add_conditional_edges("intent", route_intent, {
    "use_kb": "rewrite",
    "no_kb": "direct_answer",
    "handoff": "handoff",
})

# 边2: 数据源选择 → 并行获取/旧串行
workflow.add_conditional_edges("rewrite", route_data_source, {
    "parallel": "fetch_data",
    "legacy": "search",
})

# 边3: 评审结论 → 打回重写/转人工/结束
workflow.add_conditional_edges("critic", route_judge, {
    "retry": "rewrite",
    "handoff": "handoff",
    "end": END,
})
```

### 4.4 path_map 不传也行

```python
# 如果路由函数的返回值直接就是目标节点名，可以不传 path_map：
workflow.add_conditional_edges("intent", route_intent)
# route_intent 返回 "rewrite" → 就去 rewrite 节点
# route_intent 返回 "direct_answer" → 就去 direct_answer 节点
```

**但项目选择传 path_map**——更安全，路由函数改了返回值会立刻报错而非静默跳转到错误的节点。

---

## 五、compile：把图变成可执行应用

### 5.1 API 签名

```python
app = workflow.compile(
    checkpointer: BaseCheckpointSaver | None = None,
    interrupt_before: list[str] | None = None,
    interrupt_after: list[str] | None = None,
    debug: bool = False,
) -> CompiledStateGraph
```

### 5.2 逐参数详解

| 参数 | 类型 | 默认值 | 作用 |
|------|------|--------|------|
| `checkpointer` | `BaseCheckpointSaver` | None | 状态持久化器。不传 = 每次调用都是新会话，无记忆。 |
| `interrupt_before` | `list[str]` | None | 在这些节点**之前**暂停（HITL 用） |
| `interrupt_after` | `list[str]` | None | 在这些节点**之后**暂停（HITL 用） |
| `debug` | `bool` | False | 开启调试模式，输出更详细的日志 |

### 5.3 项目的编译

```python
# aipy2/app/graph/investor_graph.py
checkpointer = llm_core.checkpointer or InMemorySaver()
# 优先用 Postgres 持久化，数据库不可用时降级到内存

app = workflow.compile(checkpointer=checkpointer)
```

### 5.4 compile 内部做了什么

```
workflow.compile(checkpointer=...)
    │
    ├── 1. 验证图结构：
    │      - 所有 add_edge 引用的节点名都存在
    │      - 所有条件边的映射目标都存在
    │      - 没有孤立节点（除了 START 谁都不连）
    │      - 每条路径最终都能到达 END（无死循环）
    │
    ├── 2. 构建执行计划：
    │      - 确定每个节点的前驱和后继
    │      - 对并行节点建立同步点（barrier）
    │      - 包装每个节点：执行前记录 checkpoint，执行后合并 State
    │
    ├── 3. 注入 checkpointer：
    │      - 在每次节点执行后保存 State 快照
    │      - 在每次图启动时恢复上次 State（同一 thread_id）
    │
    └── 4. 返回 CompiledStateGraph：
           - 这是一个可执行的应用对象
           - 暴露 .invoke() 和 .astream() 两个执行入口
```

### 5.5 CompiledStateGraph 的执行方法

编译后的 `app` 对象有以下方法：

**① `ainvoke` — 异步执行，等完整结果后返回**

```python
result = await app.ainvoke(
    input: dict,                              # 初始状态
    config: RunnableConfig | None = None,     # 线程配置
    context: Any | None = None,               # 传入 context_schema 的上下文
)
```

| 参数 | 类型 | 必填 | 作用 |
|------|------|------|------|
| `input` | `dict` | ✅ | 初始 State。至少包含 State schema 定义的字段。传 `None`=从上次 checkpoint 继续（断点续跑） |
| `config` | `RunnableConfig` | 否 | 最重要的字段是 `configurable.thread_id`。相同的 thread_id → 自动加载 checkpoint → 多轮对话。不传=每次全新 |
| `context` | `Any` | 否 | 当 `compile()` 时传了 `context_schema` 才用到。传入用户信息、请求 ID 等 |

**② `astream` — 异步流式执行（项目用这个）**

```python
async for mode, chunk in app.astream(
    input: dict,
    config: RunnableConfig | None = None,
    stream_mode: str | list | StreamMode = "values",
    subgraphs: bool = False,
    context: Any | None = None,
):
```

| 参数 | 类型 | 默认 | 作用 |
|------|------|------|------|
| `input` | `dict` | 必填 | 同 ainvoke。传 `None` 从上次 checkpoint 继续 |
| `config` | `RunnableConfig` | None | 同 ainvoke |
| `stream_mode` | `str \| list` | `"values"` | **最关键参数**。见 5.6 详解 |
| `subgraphs` | `bool` | False | True=连子图的更新也输出。多 Agent 场景用到 |
| `context` | `Any` | None | 同 ainvoke |

**③ `aget_state` / `aget_state_history` / `aupdate_state` — 不执行图，只操作 checkpoint**

```python
state = await app.aget_state(config)           # 查询当前状态
history = await app.aget_state_history(config)  # 历史快照
await app.aupdate_state(config, {"step": "人工已审核"})  # 修改状态（HITL 用）
# 详细参数见 note 48
```

### 5.6 stream_mode 详解（面试高频）

```python
# stream_mode="values" — 每次状态更新后返回完整 State
async for chunk in app.astream(input, config, stream_mode="values"):
    print(chunk["messages"][-1].content)  # 完整 State 的快照

# stream_mode="updates" — 返回每次更新的增量
async for node_name, updates in app.astream(input, config, stream_mode="updates"):
    print(f"节点 {node_name} 更新了: {updates}")

# stream_mode="messages" — 返回 LLM 的 token 增量
async for message_chunk, metadata in app.astream(input, config, stream_mode="messages"):
    print(message_chunk.content)  # 单个 token

# 组合使用（项目做法）：
async for mode, chunk in app.astream(input, config, stream_mode=["updates", "messages"]):
    if mode == "updates":
        ...  # 处理节点状态变化
    elif mode == "messages":
        ...  # 处理 token 增量
```

---

## 六、完整图构建流程回顾

```python
from langgraph.graph import StateGraph, START, END

# ① 定义 State
class AgentState(TypedDict):
    messages: Annotated[list, add_messages]
    step: str

# ② 定义 Node 函数
async def my_node(state: AgentState) -> dict:
    return {"step": "done"}

# ③ 定义路由函数
def my_router(state: AgentState) -> Literal["a", "b"]:
    return "a"

# ④ 创建 StateGraph
workflow = StateGraph(AgentState)

# ⑤ 注册节点
workflow.add_node("my_node", my_node)
workflow.add_node("a", node_a)
workflow.add_node("b", node_b)

# ⑥ 连线
workflow.add_edge(START, "my_node")
workflow.add_conditional_edges("my_node", my_router, {"a": "a", "b": "b"})
workflow.add_edge("a", END)
workflow.add_edge("b", END)

# ⑦ 编译（注入 checkpointer）
app = workflow.compile(checkpointer=PostgresSaver(...))

# ⑧ 执行
async for mode, chunk in app.astream(input, config, stream_mode=["updates", "messages"]):
    ...
```

**记住这 8 步**，就能手写任何 LangGraph 应用。

---

## 七、面试速记

**Q: StateGraph 构造函数为什么必须是 TypedDict？**
A: LangGraph v1 不再支持 Pydantic 做 State，必须是 TypedDict。因为 State 在运行时就是普通 dict，TypedDict 提供了类型提示而不引入序列化开销。

**Q: add_conditional_edges 的三个参数分别做什么？**
A: `source` 是从哪个节点出发，`path` 是路由函数（入参 state，出参字符串），`path_map` 是返回值到目标节点的映射字典。

**Q: compile 做了什么？**
A: 四件事——验证图结构（无孤立节点、无死路径）、构建执行计划（确定前后继关系）、注入 checkpointer（每步存快照）、返回 CompiledStateGraph。

**Q: stream_mode 的三种模式？**
A: `values` 返回完整 State 快照，`updates` 返回增量更新，`messages` 返回 LLM token 增量。项目用 `["updates", "messages"]` 双流——updates 驱动"思考步骤"，messages 驱动"打字机效果"。
