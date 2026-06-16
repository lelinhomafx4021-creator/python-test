# 41_Deep Agents：代码对比 + 选型指南

> **核心目标**: 知道 Deep Agents 解决什么问题，和手写 StateGraph 的代码级对比
> **项目关联**: ⚠️ 项目用的是手写 StateGraph。用这篇笔记理解"什么场景该升级到 Deep Agents"。

---

## 一、同一个任务，两种写法

**任务**：做一个投研 Agent，能搜索资料 + 分析数据 + 生成报告。

### 手写 StateGraph（项目做法，~150 行）

```python
from typing import Annotated, TypedDict
from langgraph.graph import StateGraph, START, END
from langgraph.graph.message import add_messages
from langgraph.checkpoint.postgres import PostgresSaver
from langchain.chat_models import init_chat_model

llm = init_chat_model("deepseek-v4")

# ===== 1. 定义 State =====
class AgentState(TypedDict):
    messages: Annotated[list, add_messages]
    knowledge: str     # 搜索结果
    analysis: str      # 分析结果
    report: str        # 最终报告

# ===== 2. 定义 3 个节点 =====
async def search_node(state: AgentState) -> dict:
    """搜索相关资料"""
    query = state["messages"][-1].content
    response = await llm.ainvoke(
        f"用户的查询是: {query}。请列出搜索到的关键信息。"
    )
    return {"knowledge": response.content, "step": "搜索完成"}

async def analyze_node(state: AgentState) -> dict:
    """分析搜索到的数据"""
    response = await llm.ainvoke(
        f"请分析以下信息，提取关键数据和趋势:\n{state['knowledge']}"
    )
    return {"analysis": response.content, "step": "分析完成"}

async def report_node(state: AgentState) -> dict:
    """生成最终报告"""
    response = await llm.ainvoke(
        f"基于分析结果写一份简短报告:\n{state['analysis']}"
    )
    return {"report": response.content, "step": "报告生成完成"}

# ===== 3. 手动构建图 =====
workflow = StateGraph(AgentState)
workflow.add_node("search", search_node)
workflow.add_node("analyze", analyze_node)
workflow.add_node("report", report_node)
workflow.add_edge(START, "search")
workflow.add_edge("search", "analyze")
workflow.add_edge("analyze", "report")
workflow.add_edge("report", END)

# ===== 4. 编译（注入 PostgreSQL checkpoint）=====
DB_URI = "postgresql://postgres:postgres@localhost:5432/postgres?sslmode=disable"
with PostgresSaver.from_conn_string(DB_URI) as checkpointer:
    checkpointer.setup()
    app = workflow.compile(checkpointer=checkpointer)

    # ===== 5. 执行 =====
    config = {"configurable": {"thread_id": "research_001"}}
    result = await app.ainvoke(
        {"messages": [{"role": "user", "content": "分析茅台2024年财报"}]},
        config
    )
    print(result["report"])  # 最终报告
```

**你看这 60 行代码干了什么**：定义 State → 写 3 个节点函数 → 注册到图 → 连线 → 编译 → 执行。每一步你都看得见、改得了。

### Deep Agents（同样任务，~15 行）

```python
from deepagents import create_deep_agent

agent = create_deep_agent(
    model="openai:gpt-4o",
    tools=[search_tool, analyze_tool, report_tool],
    system_prompt="你是投研助手。先搜索资料，再分析数据，最后生成报告。",
)

result = agent.invoke({"messages": [{"role": "user", "content": "分析茅台"}]})
```

**代码量差距**：150 行 vs 15 行。但代码少不一定好——往下看。

---

## 二、Deep Agents 替你做了什么（你不写但它在做的事）

| 你手写时要做的 | Deep Agents 自动做的 | 代价 |
|--------------|---------------------|------|
| 定义 AgentState | 内置 TodoList + Filesystem + Messages | 状态结构不透明 |
| 写 search/analyze/report 节点 | LLM 自动规划步骤 | 执行顺序不可控 |
| 手动管理上下文 | 自动摘要 + 卸载到磁盘 | 摘要质量不可控 |
| 手动 add_edge 连线 | 内部是标准 ReAct 循环 | 复杂路由做不到 |
| 手动写子 Agent | `SubAgentMiddleware` 声明式定义 | 子 Agent 调试困难 |

---

## 三、选型决策：一张表就够了

| 需求 | 手写 StateGraph | Deep Agents |
|------|----------------|-------------|
| 流程固定（搜索→分析→报告） | ✅ 确定性高 | ⚠️ LLM 可能跳步骤 |
| 流程不确定（"帮我研究这个行业"） | ❌ 要写很多条件边 | ✅ LLM 自主规划 |
| 需要多角色路由（VIP/普通） | ✅ 编译时选图 | ❌ 不支持 |
| 需要 critic 闭环 | ✅ 条件边轻松实现 | ❌ 需要自己改内部图 |
| 需要精确控制每一步 | ✅ 每个节点可见 | ❌ 内部黑盒 |
| 快速原型 | ❌ 代码量大 | ✅ 一行代码 |
| 生产环境 | ✅ 可调试可优化 | ⚠️ 出问题难定位 |
| 代码行数 | ~150 行 | ~15 行 |

---

## 四、面试怎么讲

**"你们为什么不用 Deep Agents？"**

> Deep Agents 适合开放式任务——比如"帮我做一份行业研究报告"，用户没说具体步骤，需要 Agent 自己规划。我们的投研场景是确定性的——用户问"茅台股价多少"，步骤固定（识别意图→搜索→回答），不需要动态规划。手写 StateGraph 的确定性更高——每一步输入输出都可观测、可调试、可优化。

**"什么时候会考虑升级到 Deep Agents？"**

> 当业务从"单轮投研查询"扩展到"多步骤开放式研究"时——比如用户说"帮我做一份白酒行业的深度研究报告，包括龙头企业对比、财务分析、行业趋势、投资建议"。这种场景下 LLM 自主规划比手写固定流程更高效。

---

## 五、一句话总结

```
create_agent  = 标准 ReAct 循环，开箱即用（适合简单 Agent）
Deep Agents   = 自动规划 + 子 Agent + 上下文管理（适合开放式复杂任务）
手写 StateGraph = 完全控制权（适合确定流程 + 复杂路由）
本项目选后者，因为投研流程固定 + 需要 VIP/普通双图 + critic 闭环。
```
