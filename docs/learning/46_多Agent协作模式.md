# 46_多Agent协作模式：Supervisor / Swarm / Handoff 三种实战模式

> **核心目标**: 理解三种多 Agent 协作模式的原理、代码实现、选型依据
> **项目关联**: 项目目前是单 Agent（一个 StateGraph 搞定全部），多 Agent 是架构演进的下一步

---

## 一、单 Agent vs 多 Agent：什么时候需要拆分

```
单 Agent（项目现状）:
  intent → rewrite → search → answer → critic
  一个 StateGraph 7 个节点，全在一个图里

什么时候一个图不够：
  ① 上下文窗口不够 — 一个任务的信息量超出了单个 LLM 的处理能力
  ② 需要并行处理 — 技术面和基本面分析可以同时做
  ③ 需要角色隔离 — 研究员不该看到写手的内部推理，写手不该看到评审员的偏见
  ④ 需要独立迭代 — 研究员可以自己查-审-改，不依赖外部评审
```

---

## 二、模式 1：Supervisor（监督者模式）

### 2.1 架构

```
用户请求
    │
    ▼
┌──────────────┐
│  Supervisor   │  ← 唯一的路由决策者
│  (主Agent)    │     分析任务 → 决定派给谁 → 收结果 → 决定下一步
└───┬────┬─────┘
    │    │
    ▼    ▼
┌──────┐ ┌──────┐ ┌──────┐
│研究员 │ │分析师 │ │写手  │  ← Worker Agents
│Agent │ │Agent │ │Agent │     各自有独立的 LLM + 工具 + State
└──────┘ └──────┘ └──────┘
```

**Supervisor 有最终决定权**：Worker 只干活，不决定"接下来干什么"。Supervisor 说"研究员去查资料"→研究员查完报告→Supervisor 说"分析师做分析"→分析师做完→Supervisor 说"写手写报告"。

### 2.2 代码实现

```python
from langgraph.graph import StateGraph, START, END
from langchain.agents import create_agent

# ===== Step 1: 定义全局 State =====
class SupervisorState(TypedDict):
    messages: Annotated[list, add_messages]
    next_agent: str       # Supervisor 决定下一个谁干活
    research_result: str  # 研究员的产出
    analysis_result: str  # 分析师的产出
    final_report: str     # 写手的产出

# ===== Step 2: 创建 Worker Agents（每个是独立的 Agent）=====
researcher = create_agent(
    model="openai:gpt-4o-mini",
    tools=[search_tool, web_search_tool],
    system_prompt="你是研究员。搜索和整理信息，不做分析。"
)

analyst = create_agent(
    model="openai:gpt-4o",
    tools=[calculator_tool],
    system_prompt="你是分析师。基于研究员的结果做数据分析。"
)

writer = create_agent(
    model="openai:gpt-4o",
    tools=[],
    system_prompt="你是写手。把分析结果写成专业报告。"
)

# ===== Step 3: Supervisor 节点 — 决定谁干活 =====
def supervisor_node(state: SupervisorState) -> dict:
    llm = ChatOpenAI(model="gpt-4o", temperature=0)

    # Supervisor 看到所有上下文后决定下一个 Agent
    prompt = ChatPromptTemplate.from_messages([
        ("system", """你是任务调度员。根据当前状态决定下一个 Agent:
        - researcher: 需要查资料时
        - analyst: 资料齐了需要分析时
        - writer: 分析完了需要写报告时
        - FINISH: 报告已完成"""),
        ("human", "当前状态:\n研究结果: {research}\n分析结果: {analysis}\n报告: {report}"),
    ])

    result = llm.with_structured_output(SupervisorDecision).invoke(
        prompt.format_messages(research=state.get("research_result", ""),
                               analysis=state.get("analysis_result", ""),
                               report=state.get("final_report", ""))
    )
    return {"next_agent": result.next}

# ===== Step 4: Worker 调用节点（封装 Agent 调用）=====
async def call_researcher(state: SupervisorState) -> dict:
    result = await researcher.ainvoke({
        "messages": [("user", f"研究这个任务: {state['messages'][-1].content}")]
    })
    return {"research_result": result["messages"][-1].content}

async def call_analyst(state: SupervisorState) -> dict:
    result = await analyst.ainvoke({
        "messages": [("user", f"分析这份研究: {state['research_result']}")]
    })
    return {"analysis_result": result["messages"][-1].content}

async def call_writer(state: SupervisorState) -> dict:
    result = await writer.ainvoke({
        "messages": [("user", f"把这份分析写成报告: {state['analysis_result']}")]
    })
    return {"final_report": result["messages"][-1].content}

# ===== Step 5: 路由 — Supervisor 说了算 =====
def route_to_worker(state: SupervisorState) -> str:
    return state["next_agent"]  # "researcher" | "analyst" | "writer" | "FINISH"

# ===== Step 6: 构建图 =====
workflow = StateGraph(SupervisorState)
workflow.add_node("supervisor", supervisor_node)
workflow.add_node("researcher", call_researcher)
workflow.add_node("analyst", call_analyst)
workflow.add_node("writer", call_writer)

workflow.add_edge(START, "supervisor")
workflow.add_conditional_edges("supervisor", route_to_worker, {
    "researcher": "researcher",
    "analyst": "analyst",
    "writer": "writer",
    "FINISH": END,
})
# Worker 干完活回到 Supervisor 决定下一步
workflow.add_edge("researcher", "supervisor")
workflow.add_edge("analyst", "supervisor")
workflow.add_edge("writer", "supervisor")

app = workflow.compile(checkpointer=InMemorySaver())
```

### 2.3 优缺点

| 优点 | 缺点 |
|------|------|
| 集中控制——Supervisor 说了算，不会乱 | Supervisor 是单点瓶颈——它挂了全停 |
| 适合顺序依赖的任务（先研究→再分析→再写） | 不支持 Worker 之间直接对话 |
| 代码结构清晰——一个图搞定 | 全部上下文都要经 Supervisor 传递 |

---

## 三、模式 2：Swarm（群体模式）

### 3.1 架构

```
┌──────────┐     handoff     ┌──────────┐
│ 接待员    │ ──────────────→ │ 研究员    │
│ Agent    │ ←────────────── │ Agent    │
└──────────┘     handoff     └────┬─────┘
                                  │ handoff
                                  ▼
                            ┌──────────┐
                            │ 分析师    │
                            │ Agent    │
                            └──────────┘

没有 Supervisor！Agent 之间直接 handoff（交接）。
每个 Agent 自己决定"我搞不定了，转给谁"。
```

### 3.2 和 Supervisor 的核心区别

| | Supervisor | Swarm |
|------|-----------|-------|
| 谁决策下一步 | 只有 Supervisor | 每个 Agent 都可以 |
| Agent 之间能对话吗 | 不能，都经 Supervisor | 可以，直接 handoff |
| 适用场景 | 任务顺序固定 | 任务走向不确定，Agent 需要自主交接 |
| 复杂度 | 低 | 中 |

### 3.3 代码

```python
# Swarm 模式的核心：handoff 工具
# 每个 Agent 注册一个 handoff_to_xxx 工具，
# 当它觉得自己搞不定时，调用 handoff 工具把任务转给另一个 Agent

from langchain.agents import create_agent
from langchain.tools import tool

# 研究员可以把自己接到的任务转给分析师
@tool
def handoff_to_analyst(task_summary: str) -> str:
    """当你需要数据分析时，把任务转给分析师。"""
    return f"任务已转交给分析师: {task_summary}"

# 分析师也可以转回给研究员
@tool
def handoff_to_researcher(missing_info: str) -> str:
    """当缺少信息时，转回给研究员补充资料。"""
    return f"需要补充信息: {missing_info}"

researcher = create_agent(
    model="gpt-4o",
    tools=[search_tool, handoff_to_analyst],  # ← 可以转给分析师
    system_prompt="你是研究员。如果找到足够资料但需要分析，调 handoff_to_analyst。"
)

analyst = create_agent(
    model="gpt-4o",
    tools=[calculator_tool, handoff_to_researcher],  # ← 可以转回给研究员
    system_prompt="你是分析师。如果资料不足，调 handoff_to_researcher。"
)
```

---

## 四、模式 3：Hierarchical（层级模式）

```
                    ┌──────────────┐
                    │  总 Supervisor │
                    └───┬──────┬───┘
                        │      │
              ┌─────────┘      └─────────┐
              ▼                          ▼
    ┌──────────────────┐      ┌──────────────────┐
    │ 研究 Supervisor   │      │ 写作 Supervisor   │  ← 第二层 Supervisor
    │  ├── 搜索 Agent   │      │  ├── 草稿 Agent   │
    │  ├── 验证 Agent   │      │  ├── 润色 Agent   │
    │  └── 汇总 Agent   │      │  └── 校对 Agent   │
    └──────────────────┘      └──────────────────┘
```

每一层 Supervisor 只管自己下面的 Worker。适合超复杂任务——一个 Supervisor 管不过来，需要分层。

### 4.1 完整代码

```python
"""
Hierarchical 多 Agent: 两层 Supervisor
顶层 Supervisor 决定"研究还是写作"
研究 Supervisor 自己决定"搜索还是验证"
写作 Supervisor 自己决定"草稿还是润色"
"""

# ===== 底层 Worker Agents =====
search_agent = create_agent(
    model=llm,
    tools=[web_search],
    system_prompt="你是搜索员，只搜索不分析",
)
verify_agent = create_agent(
    model=llm,
    tools=[check_facts],
    system_prompt="你是验证员，验证搜索结果的准确性",
)

draft_agent = create_agent(
    model=llm,
    tools=[],
    system_prompt="你是草稿写手，根据资料写初稿",
)
polish_agent = create_agent(
    model=llm,
    tools=[],
    system_prompt="你是润色师，改进表述但不改数据",
)

# ===== 中层 Supervisor：每层有自己的图 =====
# 研究子图: 搜索 + 验证 协同
research_subgraph = StateGraph(ResearchState)
research_subgraph.add_node("supervisor", research_supervisor_node)
research_subgraph.add_node("search", search_agent)
research_subgraph.add_node("verify", verify_agent)
# ... 连线 ...

# 写作子图: 草稿 + 润色 协同
writing_subgraph = StateGraph(WritingState)
writing_subgraph.add_node("supervisor", writing_supervisor_node)
writing_subgraph.add_node("draft", draft_agent)
writing_subgraph.add_node("polish", polish_agent)
# ... 连线 ...

# ===== 顶层 Supervisor: 管中层两个 Supervisor =====
class TopState(TypedDict):
    messages: Annotated[list, add_messages]
    phase: str              # "research" | "writing" | "done"
    research_output: str    # 研究子图完成后的产出
    final_report: str       # 写作子图完成后的产出

async def top_supervisor_node(state: TopState) -> dict:
    """只决定大方向，不管底层细节"""
    llm = init_chat_model("deepseek-v4", temperature=0)

    class TopDecision(BaseModel):
        phase: Literal["research", "writing", "done"]

    decision = await llm.with_structured_output(TopDecision).ainvoke([
        SystemMessage(content="""你是总调度。决定当前阶段:
        - research: 还没有研究结果，需要先研究
        - writing: 研究结果有了，需要写作
        - done: 报告已完成"""),
        HumanMessage(content=f"研究输出: {state.get('research_output', '无')}\n"
                            f"报告: {state.get('final_report', '无')}")
    ])
    return {"phase": decision.phase}

# ===== 顶层路由 =====
def route_top(state: TopState) -> str:
    return state["phase"]  # "research" | "writing" | "done"

top_workflow = StateGraph(TopState)
top_workflow.add_node("supervisor", top_supervisor_node)
top_workflow.add_node("research_subgraph", research_subgraph.compile())  # ⭐ 子图！
top_workflow.add_node("writing_subgraph", writing_subgraph.compile())    # ⭐ 子图！

top_workflow.add_edge(START, "supervisor")
top_workflow.add_conditional_edges("supervisor", route_top, {
    "research": "research_subgraph",
    "writing": "writing_subgraph",
    "done": END,
})
# 子图做完 → 回顶层 Supervisor 汇报
top_workflow.add_edge("research_subgraph", "supervisor")
top_workflow.add_edge("writing_subgraph", "supervisor")

app = top_workflow.compile()
```

**和单层 Supervisor 的区别：**

```
单层 Supervisor:   Supervisor → Worker1, Worker2, Worker3
                   一个 Supervisor 管所有 Agent，上下文压力大

Hierarchical:      顶层 Supervisor → 中层 Supervisor → 底层 Worker
                   每层只管自己的一亩三分地，上下文分层隔离
```

---

## 五、选型决策

```
任务顺序固定、需要集中控制 → Supervisor
Agent 之间需要灵活交接      → Swarm
任务超大需要分层管理        → Hierarchical
任务简单、一个 Agent 够用   → 单图（项目现状，继续用）
```

---

## 六、面试速记

**Q: 多 Agent 协作有哪些模式？**
A: 三种。Supervisor（一个主 Agent 调度多个 Worker，集中决策）、Swarm（Agent 之间直接 handoff，去中心化）、Hierarchical（多层 Supervisor，适合超复杂任务）。选型看任务是否顺序固定、是否需要 Agent 间灵活交接。

**Q: 为什么不一开始就用多 Agent？**
A: 多 Agent 增加延迟（Agent 间上下文传递）、增加 Token 消耗（Supervisor 本身吃 Token）、增加调试复杂度（多个 Agent 的决策链更长）。我们的投研场景步骤固定（意图→检索→回答→评审），单图足够。当上下文窗口不够或需要并行处理时再拆分。
