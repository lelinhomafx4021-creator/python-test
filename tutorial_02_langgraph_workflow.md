# 教程 02：LangGraph 工作流 —— 让 AI 像"流水线"一样工作

## 一句话概念
LangGraph 把 AI 的"思考过程"拆解成一条**有向图（Graph）**。每个节点是一个步骤，边是步骤之间的跳转规则。最关键的是：它支持**条件跳转**和**循环**，这是普通 LangChain Chain 做不到的。

---

## 1. 四个核心节点

打开 [investor_graph.py](file:///d:/ai-investor/aipy2/app/graph/investor_graph.py)，我们的工作流有 4 个节点：

### 节点 1：[rewrite_node](file:///d:/ai-investor/aipy2/app/graph/investor_graph.py#33-62) — 问题理解
```python
async def rewrite_node(state: AgentState):
    user_msg = state["messages"][-1].content  # 拿到用户原始问题
    # ... 调用 LLM 拆解搜索意图 ...
    return {"queries": queries, "step": "..."}  # 写回 State
```
**类比**：你拿到一个问题"茅台怎么样"，你会先拆解成"茅台财报"、"茅台股价"、"白酒行业趋势"等搜索词。

### 节点 2：[search_node](file:///d:/ai-investor/aipy2/app/graph/investor_graph.py#63-72) — 资料检索
```python
async def search_node(state: AgentState):
    queries = state["queries"]  # 从 State 读取搜索词
    res = await run_retrieval_async(queries=queries, mode="auto")
    return {"knowledge": res, "step": "..."}  # 把资料存回 State
```
**类比**：拿着搜索词去图书馆找资料。

### 节点 3：[answer_node](file:///d:/ai-investor/aipy2/app/graph/investor_graph.py#73-93) — 草稿撰写
```python
async def answer_node(state: AgentState):
    knowledge = state["knowledge"]  # 从 State 读取参考资料
    # ... 调用 LLM 基于资料撰写报告 ...
    return {"messages": [response], "step": "..."}
```
**类比**：根据找到的资料，写一份投研报告初稿。

### 节点 4：[critic_node](file:///d:/ai-investor/aipy2/app/graph/investor_graph.py#94-138) — 质量评审
```python
async def critic_node(state: AgentState):
    last_answer = state["messages"][-1].content  # 读取草稿
    knowledge = state["knowledge"]  # 对比参考资料
    # ... 检查是否有编造数据 ...
    return {"review_status": status, "critic_feedback": reason}
```
**类比**：导师审阅你的报告，检查数据是否靠谱。

---

## 2. 节点的返回值 = State 的更新

> **核心规则**：节点只能通过 `return` 来更新 State，不能直接修改。

```python
# ✅ 正确：返回你要更新的字段
return {"queries": ["茅台财报", "茅台股价"]}

# ❌ 错误：不能直接赋值
state["queries"] = ["茅台财报"]  # 这样不会生效！
```

这保证了状态更新的**可追踪性**——你随时知道哪个节点改了什么。

---

## 3. 条件边：Self-RAG 的灵魂

```python
def route_judge(state: AgentState) -> Literal["retry", "end"]:
    if state.get("review_status") == "fail":
        return "retry"   # 评审不通过 → 走左边，回到 rewrite
    return "end"         # 评审通过 → 走右边，结束
```

这就是**条件边 (Conditional Edge)**。它让工作流不再是单纯的"从头到尾"，而是可以**回头重来**。

```python
workflow.add_conditional_edges(
    "critic",        # 从 critic 节点出发
    route_judge,     # 用这个函数决定走哪条路
    {
        "retry": "rewrite",  # 返回 "retry" → 跳到 rewrite
        "end": END           # 返回 "end"   → 结束流程
    }
)
```

---

## 4. 兜底机制：防止死循环

```python
# 在 critic_node 中
if new_retry >= 3:
    status = "pass"  # 强制放行
```

> **面试点**：在生产环境中，AI 的自我反思可能会陷入无限循环（比如评审标准太严）。所以必须加兜底机制，这体现了**防御式编程 (Defensive Programming)** 的思想。

---

## 5. `astream` vs `ainvoke`

| 方法 | 行为 | 适用场景 |
|------|------|----------|
| `graph.ainvoke()` | 等整个流程跑完，一次性返回结果 | 后端批量处理 |
| `graph.astream()` | **每个节点跑完就推送一次更新** | 前端实时展示"思考过程" |

我们的项目用的是 `astream(stream_mode="updates")`，它会在每个节点完成时发出事件，前端实时展示 AI 的每一步思考。

```python
async for event in self.graph.astream(input_data, config=config, stream_mode="updates"):
    for node_name, updates in event.items():
        if "step" in updates:
            yield {"stage": node_name, "data": {"step": updates["step"]}}
```

---

## 6. 完整流程回顾

```
用户: "茅台最近怎么样？"
    │
    ▼
[rewrite_node] → 拆解为: ["茅台2024财报", "茅台股价走势", "白酒行业分析"]
    │
    ▼
[search_node] → 从向量库 + BM25 + 联网搜索中拿到 5 条参考资料
    │
    ▼
[answer_node] → 基于资料写出: "茅台2024年Q1净利润同比增长15.7%..."
    │
    ▼
[critic_node] → 检查: "数据是否都有出处？"
    │
    ├── ✅ pass → 输出最终答案
    └── ❌ fail → 反馈: "缺少股价走势数据" → 回到 rewrite_node
```

