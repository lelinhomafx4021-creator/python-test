# 54_单 Agent vs 多 Agent：同一任务两种写法，代码对比

> **核心问题**: 单 Agent 和多 Agent 到底差在哪？写出来长什么样？什么时候该升级？
> **结论先行**: 底层都是 StateGraph，区别只在"节点里装什么"——装函数还是装 Agent。

---

## 一、同一个任务，两种写法

**任务**：用户问"帮我分析茅台，然后写一份简短的研究报告，最后审核一遍"。

这个任务有三个步骤：研究 → 写报告 → 审核。每个步骤都需要 LLM 参与。

### 1.1 单 Agent 写法（我们项目的方式）

```python
"""
单 Agent: 一个图，三个节点（每个节点是一个函数）
一个 LLM 贯穿全流程，只是每步的 prompt 不同
"""

from typing import Annotated, TypedDict
from langgraph.graph import StateGraph, START, END
from langgraph.graph.message import add_messages
from langchain.chat_models import init_chat_model

# ===== State =====
class State(TypedDict):
    messages: Annotated[list, add_messages]
    research_result: str    # 中间结果：研究阶段产出
    report: str             # 中间结果：写报告阶段产出
    final_report: str       # 最终产出：审核后的报告

# ===== 一个 LLM，三条不同 prompt =====
llm = init_chat_model("deepseek-v4")

# 节点1: 研究员（就是一个函数！）
async def research_node(state: State) -> dict:
    """函数内部写研究逻辑——调搜索工具、查数据"""
    prompt = f"""
    你是一个股票研究员。针对用户问题做深入研究。
    用户问题: {state['messages'][-1].content}

    请输出:
    1. 公司基本面概况
    2. 近期关键财务数据
    3. 行业地位分析
    """
    response = await llm.ainvoke(prompt)
    return {"research_result": response.content}

# 节点2: 写手（就是另一个函数！）
async def writer_node(state: State) -> dict:
    """函数内部写报告逻辑——把研究结果写成报告"""
    prompt = f"""
    你是一个金融报告写手。根据研究结果写一份简短报告。

    研究结果:
    {state['research_result']}

    报告格式:
    ## 公司概况
    ## 财务亮点
    ## 投资要点
    """
    response = await llm.ainvoke(prompt)
    return {"report": response.content}

# 节点3: 审核（就是第三个函数！）
async def reviewer_node(state: State) -> dict:
    """函数内部写审核逻辑——检查报告质量"""
    prompt = f"""
    你是一个报告审核员。审核以下报告，修正错误，给出最终版本。

    原始报告:
    {state['report']}

    检查: 数据是否准确、逻辑是否通顺、结论是否有依据。
    输出最终版报告。
    """
    response = await llm.ainvoke(prompt)
    return {"final_report": response.content}

# ===== 搭图：三个函数串起来 =====
workflow = StateGraph(State)

workflow.add_node("research", research_node)   # 节点 = 函数
workflow.add_node("write", writer_node)        # 节点 = 函数
workflow.add_node("review", reviewer_node)     # 节点 = 函数

workflow.add_edge(START, "research")
workflow.add_edge("research", "write")
workflow.add_edge("write", "review")
workflow.add_edge("review", END)

app = workflow.compile()

# ===== 执行 =====
result = await app.ainvoke({
    "messages": [{"role": "user", "content": "分析茅台，写研究报告并审核"}]
})
print(result["final_report"])
```

**单 Agent 的特点**：
- 一个 LLM 实例，三条 prompt
- 每个节点是**普通 async 函数**
- 节点之间通过 State 传数据（`research_result` → `report` → `final_report`）
- 流程完全固定：research → write → review，不会跳

---

### 1.2 多 Agent 写法（Supervisor 模式）

```python
"""
多 Agent: 一个 Supervisor 图 + 三个子 Agent
每个子 Agent 有自己的 LLM + 自己的 prompt + 自己的 tools
Supervisor 决定"谁来做"和"做完了没"
"""

from typing import Annotated, TypedDict, Literal
from langgraph.graph import StateGraph, START, END
from langgraph.graph.message import add_messages
from langchain.chat_models import init_chat_model
from langchain.agents import create_agent

# ===== 全局 State（Supervisor 和子 Agent 共享）=====
class MultiAgentState(TypedDict):
    messages: Annotated[list, add_messages]
    research_done: bool       # 研究员完成了吗
    report_done: bool         # 写手完成了吗
    next_step: str            # Supervisor 决定下一步走哪个 Agent
    final_report: str         # 最终产出

# ===== 三个子 Agent：每个是独立的 create_agent！=====

# 研究员 Agent: 有自己的 LLM + 自己的 tools
researcher = create_agent(
    model=init_chat_model("deepseek-v4"),
    tools=[search_tool, get_stock_data],     # ← 研究员专属工具
    system_prompt="""你是一个股票研究员。
任务: 深入分析用户指定的股票，输出基本面、财务数据、行业地位。
完成后说: "研究完成"。
""",
)

# 写手 Agent: 有自己的 LLM + 自己的 tools
writer = create_agent(
    model=init_chat_model("deepseek-v4"),
    tools=[format_report_tool],              # ← 写手专属工具
    system_prompt="""你是一个金融报告写手。
任务: 根据研究结果撰写结构化研究报告。
完成后说: "报告完成"。
""",
)

# 审核 Agent: 有自己的 LLM + 自己的 tools
reviewer = create_agent(
    model=init_chat_model("deepseek-v4"),
    tools=[check_data_tool],                 # ← 审核专属工具
    system_prompt="""你是一个严格的报告审核员。
任务: 审核报告的数据准确性、逻辑完整性。
完成后说: "审核通过"。
""",
)

# ===== Supervisor: 也是一个 Agent！它只管"分配任务" =====
supervisor = create_agent(
    model=init_chat_model("deepseek-v4"),
    tools=[],   # Supervisor 不干具体活，只做决策
    system_prompt="""你是一个任务调度员。根据当前进度决定下一步:

1. 如果研究还没做 → 交给研究员
2. 如果研究完成但报告没写 → 交给写手
3. 如果报告完成但没审核 → 交给审核员
4. 如果审核完成 → 结束任务

回复格式: 只回复 "researcher"、"writer"、"reviewer" 或 "done"
""",
)

# ===== 搭图：节点是 Agent，不是函数！=====
workflow = StateGraph(MultiAgentState)

# ⭐ 核心区别：add_node 注册的是 create_agent() 返回的 Agent 对象
workflow.add_node("researcher", researcher)   # 节点 = 完整 Agent
workflow.add_node("writer", writer)           # 节点 = 完整 Agent
workflow.add_node("reviewer", reviewer)       # 节点 = 完整 Agent
workflow.add_node("supervisor", supervisor)   # 节点 = 完整 Agent（调度器）

# 子 Agent 做完 → 回到 Supervisor 汇报
workflow.add_edge("researcher", "supervisor")
workflow.add_edge("writer", "supervisor")
workflow.add_edge("reviewer", "supervisor")

# Supervisor 决定下一步谁干
def route_next(state: MultiAgentState) -> Literal["researcher", "writer", "reviewer", "__end__"]:
    decision = state["next_step"]  # Supervisor 的输出
    if decision == "done":
        return END
    return decision

workflow.add_conditional_edges("supervisor", route_next)

# 从 Supervisor 开始
workflow.add_edge(START, "supervisor")

app = workflow.compile()

# ===== 执行 =====
result = await app.ainvoke({
    "messages": [{"role": "user", "content": "分析茅台，写研究报告并审核"}]
})
```

**多 Agent 的特点**：
- **四个独立的 LLM 决策中心**（Supervisor + 3 个子 Agent）
- 每个子 Agent 有自己的 system prompt、自己的 tools
- Supervisor 不干活，只管调度——"研究员你上"、"写手该你了"
- 子 Agent 之间**不直接通信**，都通过 Supervisor 中转
- 流程是**动态的**——Supervisor 根据当前状态决定下一步谁上

---

## 二、关键差异逐项对比

```python
# ═══════════════════════════════════════════
# 差异1: 节点里装什么
# ═══════════════════════════════════════════

# 单 Agent: 节点 = 函数
workflow.add_node("research", research_node)
# research_node 就是一个 async def research_node(state) -> dict

# 多 Agent: 节点 = Agent
workflow.add_node("researcher", researcher)
# researcher = create_agent(model=..., tools=..., system_prompt=...)


# ═══════════════════════════════════════════
# 差异2: prompt 怎么给
# ═══════════════════════════════════════════

# 单 Agent: 每次调 LLM 时手动写在函数里
async def research_node(state):
    prompt = f"你是研究员，分析: {state['query']}"
    response = await llm.ainvoke(prompt)

# 多 Agent: 创建 Agent 时就固定在 system_prompt 里
researcher = create_agent(
    system_prompt="你是研究员...",  # ← 角色永不改变
)


# ═══════════════════════════════════════════
# 差异3: 工具怎么分配
# ═══════════════════════════════════════════

# 单 Agent: 一个 LLM 看所有工具
llm_with_tools = llm.bind_tools([search, get_price, write_report, check_data])
# ↑ 4 个工具全给一个 LLM → 它得自己判断什么时候用哪个

# 多 Agent: 每个 Agent 只看自己的工具
researcher_agent = create_agent(tools=[search, get_price])      # 研究员: 查数据工具
writer_agent     = create_agent(tools=[write_report])           # 写手: 格式化工具
reviewer_agent   = create_agent(tools=[check_data])             # 审核: 校验工具
# ↑ 每个 Agent 只看到和自己岗位相关的工具，不会乱调


# ═══════════════════════════════════════════
# 差异4: 流程控制
# ═══════════════════════════════════════════

# 单 Agent: 流程写死（固定边）
workflow.add_edge("research", "write")  # 100% 研究→写报告
workflow.add_edge("write", "review")    # 100% 写报告→审核
# 永远不会跳，永远不会变

# 多 Agent: 流程动态（Supervisor 实时决定）
workflow.add_edge("researcher", "supervisor")  # 做完回来汇报
workflow.add_edge("writer", "supervisor")      # 做完回来汇报
workflow.add_conditional_edges("supervisor", route_next)
# Supervisor 看情况: "研究员做完了 → 现在派写手"
#                    "写手做完了 → 现在派审核"
#                    "用户不满意 → 研究员重新做"


# ═══════════════════════════════════════════
# 差异5: 错误处理 / 重试
# ═══════════════════════════════════════════

# 单 Agent: 写死在节点函数里
async def research_node(state):
    try:
        return await do_research(state)
    except Exception:
        return {"research_result": "研究失败，用已有数据分析"}

# 多 Agent: Supervisor 自动判断要不要重试
# 研究员返回 "没找到数据" → Supervisor 看到后决定:
#   "我让你换个关键词再查一次" → 又把任务派给研究员


# ═══════════════════════════════════════════
# 差异6: 并行能力
# ═══════════════════════════════════════════

# 单 Agent + 静态 fan-out:
# 研究员和写手不能同时跑——它们是串行依赖（写手需要研究员的结果）

# 多 Agent + Supervisor:
# 研究员分析茅台技术面的同时，可以让另一个研究员分析茅台基本面
# Supervisor 同时派两个研究员干活！
```

# ═══════════════════════════════════════════
# 差异7: 防死循环（⭐ 多 Agent 最容易被忽略的坑）
# ═══════════════════════════════════════════

# 单 Agent: 一层循环，一个计数器管住
retry_count = 0
if retry_count >= 3:
    return handoff
# ↑ 3 行代码，一条循环路径

# 多 Agent: 三层嵌套循环，每一层都可能死
#
# 第1层: Supervisor 本身
#   "再让研究员查一次" → "查完了？再查一次" → 无限
#
# 第2层: 子 Agent 内部
#   调工具 → 不满意 → 再调 → 不满意 → 死循环
#
# 第3层: Supervisor + 子 Agent 之间来回
#   写手写完 → Supervisor 不满意 → 研究员重查
#   → 写手重写 → Supervisor 还是不满意 → 再来...
#   → 无限乒乓球

# 解决办法: 每层独立管，加全局上限


# ═══════════════════════════════════════════
# 第1层: 子 Agent 内部 — 用 middleware 管
# ═══════════════════════════════════════════

from langchain.agents.middleware import ModelCallLimitMiddleware, ToolCallLimitMiddleware

researcher = create_agent(
    model=llm,
    tools=[get_stock_data, get_news],
    middleware=[
        ModelCallLimitMiddleware(max_calls=5),   # 最多调 5 次 LLM → 强制停
        ToolCallLimitMiddleware(max_calls=10),   # 最多调 10 次工具 → 强制停
    ],
    system_prompt="你是研究员...",
)

writer = create_agent(
    model=llm,
    tools=[],
    middleware=[
        ModelCallLimitMiddleware(max_calls=3),   # 写手 3 次调不出来就停
    ],
    system_prompt="你是写手...",
)


# ═══════════════════════════════════════════
# 第2层: Agent 间来回 — 计数器防乒乓球
# ═══════════════════════════════════════════

MAX_HANDOFFS = 3  # 研究员和写手之间最多来回 3 次

def supervisor_decision(state: MultiAgentState) -> str:
    handoffs = state.get("handoff_count", 0)

    if handoffs >= MAX_HANDOFFS:
        return "force_stop"  # ← "你们别来回传了，到此为止"

    # 正常决策...


# ═══════════════════════════════════════════
# 第3层: 全局轮次 — 保底兜底
# ═══════════════════════════════════════════

MAX_GLOBAL_ROUNDS = 5  # Supervisor 最多派发 5 轮

def supervisor_node(state: MultiAgentState) -> dict:
    global_round = state.get("global_round", 0)

    if global_round >= MAX_GLOBAL_ROUNDS:
        return {"next_step": "done"}  # 超了 → 用已有数据结束

    return {"next_step": "continue", "global_round": global_round + 1}


# ═══════════════════════════════════════════
# 保底: force_stop 节点
# ═══════════════════════════════════════════

async def force_stop_node(state: MultiAgentState) -> dict:
    """所有上限都到了，用已有数据强行返回"""
    return {
        "messages": [AIMessage(content=f"""
            【自动终止】已达到最大分析轮次。
            技术面: {state.get('tech_analysis', '未完成')}
            基本面: {state.get('fund_analysis', '未完成')}
            基于已有数据给出结论。
        """)],
    }

workflow.add_node("force_stop", force_stop_node)


# ═══════════════════════════════════════════
# 单 vs 多：防循环代码量对比
# ═══════════════════════════════════════════

# 单 Agent:
#   retry_count >= 3 → handoff
#   ↑ 1 个计数器，3 行代码

# 多 Agent:
#   每个子 Agent → ModelCallLimitMiddleware + ToolCallLimitMiddleware
#   Supervisor → handoff_count 防乒乓球
#   Supervisor → global_round 防全局死循环
#   force_stop 节点 → 保底兜底
#   ↑ 三层防护，配置散落在三个地方

# 不是做不到，是复杂了。
# 这也是"能用单 Agent 就别上多 Agent"的原因之一。
```

---

## 三、同一张图里的混合模式（实际大项目常用）

```python
"""
真实项目很少"纯单 Agent"或"纯多 Agent"，通常是混合的:
"""

workflow = StateGraph(State)

# 入口走函数节点（轻量）
workflow.add_node("intent", route_intent)        # 节点 = 函数

# 需要深度研究的步骤走 Agent 节点（重量）
workflow.add_node("research_agent", research_agent)  # 节点 = Agent

# 格式化输出走函数节点（轻量）
workflow.add_node("format_output", format_result)    # 节点 = 函数

# 质量把关走 Agent 节点（重量——需要独立 LLM 判断）
workflow.add_node("critic_agent", critic_agent)      # 节点 = Agent

# 组合: 函数 → Agent → 函数 → Agent
workflow.add_edge("intent", "research_agent")
workflow.add_edge("research_agent", "format_output")
workflow.add_edge("format_output", "critic_agent")

# 什么都不矛盾，所有节点对 StateGraph 来说都是 "输入 State，输出 dict"
```

---

## 四、选型决策表

```
┌────────────────────┬─────────────────────┬─────────────────────┐
│                    │     单 Agent        │     多 Agent        │
├────────────────────┼─────────────────────┼─────────────────────┤
│ 图结构             │ StateGraph          │ StateGraph          │
│                    │                     │  ← 底层完全一样！    │
├────────────────────┼─────────────────────┼─────────────────────┤
│ 节点内容           │ async 函数          │ create_agent()      │
├────────────────────┼─────────────────────┼─────────────────────┤
│ LLM 数量           │ 1 个 LLM 实例        │ N 个 LLM 实例       │
│                    │ 不同 prompt          │ 不同 prompt + tools │
├────────────────────┼─────────────────────┼─────────────────────┤
│ 工具分配           │ 全给一个 LLM         │ 按角色分配给各 Agent │
├────────────────────┼─────────────────────┼─────────────────────┤
│ 流程               │ 编译时固定           │ 运行时动态决定       │
├────────────────────┼─────────────────────┼─────────────────────┤
│ Token 消耗          │ 低（1 个 LLM）       │ 高（N 个 LLM）       │
├────────────────────┼─────────────────────┼─────────────────────┤
│ 延迟               │ 低（无调度开销）     │ 高（Supervisor 也要调 LLM）│
├────────────────────┼─────────────────────┼─────────────────────┤
│ 调试难度           │ 低（函数好调试）     │ 高（Agent 间交互难追踪）│
├────────────────────┼─────────────────────┼─────────────────────┤
│ 适用场景           │ 流程固定的任务       │ 开放任务、复杂任务    │
│                    │ 投研查询、客服       │ 研究报告、多步骤创作  │
└────────────────────┴─────────────────────┴─────────────────────┘

判断标准:
  你的流程能画成固定流程图吗？
    ✅ 能 → 单 Agent 够用
    ❌ 不能（需要动态决策"下一步谁上"）→ 多 Agent
```

---

## 五、面试速记

**Q: 单 Agent 和多 Agent 的本质区别？**
A: 底层都是 StateGraph，区别只在 `add_node` 注册的是什么。单 Agent 注册的是普通 async 函数（一个 LLM 切换不同 prompt），多 Agent 注册的是 `create_agent()` 返回的完整 Agent（每个有独立的 LLM + prompt + tools）。单 Agent 的流程是编译时固定的边，多 Agent 的流程是 Supervisor 在运行时动态决定的。

**Q: 什么时候从单 Agent 升级到多 Agent？**
A: 三个信号。①一个 LLM 的 system prompt 太长（要覆盖多种角色）→ 拆成多个 Agent，各管各的 prompt；②工具太多（10+），一个 LLM 选不过来 → 按角色分配工具；③流程不再是固定的，需要根据中间结果动态调整 → 用 Supervisor 做调度。

**Q: 多 Agent 增加了什么成本？**
A: Token 消耗（Supervisor 每次调度也要调一次 LLM）、延迟（Agent 间传递上下文有开销）、调试难度（Agent A 的决策影响 Agent B，出问题难定位）、状态管理（多个 Agent 对同一 State 的修改要小心 reducer）。

**Q: 你们项目为什么选了单 Agent？**
A: 投研查询的流程是固定的——意图识别 → 改写 query → 并行获取数据 → 生成回答 → critic 审核。每一步做什么是确定的，不需要动态调度。一个 LLM 切换不同 prompt 就能完成，省了 Supervisor 的 Token 开销。但如果将来用户说"帮我做一个完整的行业分析报告"（需要自主决定查什么、写什么、找谁帮忙），那就是多 Agent 的场景。
