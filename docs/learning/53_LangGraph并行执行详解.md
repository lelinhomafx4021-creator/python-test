# 53_LangGraph 并行执行详解：Fan-out、Send API、Barrier 全拆开

> **核心问题**: LangGraph 里怎么让多个节点同时跑？结果怎么合并？失败了怎么办？
> **为什么重要**: 并行是 Agent 性能的核心杠杆。不懂并行机制 = 不会做高性能 Agent。
> **前置知识**: 先看懂 note 32（StateGraph API）、note 17（asyncio 基础）

---

## 一、先理解：什么叫"图里的并行"

```
串行（普通边）:
  A ──→ B ──→ C
  时间: A 100ms → B 200ms → C 150ms = 450ms — 官方页面

并行（fan-out）:
       ┌─→ B（200ms）
  A ──┤
       └─→ C（150ms）
  时间: A 100ms + max(B, C) = 100 + 200 = 300ms  ← 省了 150ms

关键问题:
  1. B 和 C 怎么同时启动？
  2. B 和 C 的结果怎么合并回 State？
  3. B 失败了 C 怎么办？
```

三种并行方式：
- **静态 fan-out**：编译时就知道要并行哪些节点（多条边从同一个 source 出发）
- **动态 fan-out**：运行时才知道并行多少个（用 `Send` API）
- **节点内并行**：在一个节点函数里用 `asyncio.gather`（这个 note 40 详细讲了）

---

## 二、静态 Fan-out：多条边从同一个节点出发

### 2.1 机制

```python
from langgraph.graph import StateGraph, START, END
from typing import TypedDict

class State(TypedDict):
    tech_score: str
    fund_score: str

# 两个独立的分析节点
async def tech_analysis(state: State) -> dict:
    # 技术面分析：K 线、MACD、RSI...
    return {"tech_score": "技术面 75 分，MACD 金叉"}

async def fund_analysis(state: State) -> dict:
    # 基本面分析：PE、PB、ROE...
    return {"fund_score": "基本面 80 分，PE 低于行业均值"}

workflow = StateGraph(State)

workflow.add_node("tech", tech_analysis)
workflow.add_node("fund", fund_analysis)

# ⭐ 关键：从 START 同时连两条边 = fan-out
workflow.add_edge(START, "tech")   # 第 1 条边
workflow.add_edge(START, "fund")   # 第 2 条边 → tech 和 fund 同时跑

# 两条都跑完 → 汇合到 END
workflow.add_edge("tech", END)
workflow.add_edge("fund", END)

app = workflow.compile()
```

**流程图**：

```
         ┌──→ tech_analysis（200ms）──┐
START ──┤                              ├──→ END
         └──→ fund_analysis（150ms）──┘
                ↑                        ↑
            同时启动                  barrier：等两个都完
```

### 2.2 内部机制：Barrier（同步点）

`compile()` 时 LangGraph 分析图结构，自动在并行节点汇合处插入 **barrier**：

```
LangGraph 内部执行逻辑:

Step 1: START → 发现 tech 和 fund 的前驱都是 START
         → 判定它们可以并行
         → 同时调度 tech(state) 和 fund(state)

Step 2: tech 返回 {"tech_score": "..."}
         → 合并到 state: state["tech_score"] = "..."
         → 但 fund 还没返回 → barrier 等待

Step 3: fund 返回 {"fund_score": "..."}
         → 合并到 state: state["fund_score"] = "..."
         → tech 和 fund 都完成了 → barrier 释放

Step 4: 继续执行 → END
```

### 2.3 合并规则：Reducer 的作用

两个节点同时更新 State 的不同字段 → 没问题，各写各的：

```python
# tech 返回 {"tech_score": "75分"}
# fund 返回 {"fund_score": "80分"}
# 合并后 State = {"tech_score": "75分", "fund_score": "80分"}
# ✅ 没问题——改了不同字段
```

**但如果有两个节点同时更新同一个字段呢？**

```python
# 假设两个节点都返回 messages
# tech 返回 {"messages": [AIMessage("技术面看好")]}
# fund 返回 {"messages": [AIMessage("基本面稳健")]}

# 合并时:
# 如果没有 reducer → 后执行完的覆盖先执行完的（不确定！取决于谁跑得快）
# 如果有 add_messages reducer → [AIMessage("技术面看好"), AIMessage("基本面稳健")]
# ✅ add_messages 自动合并列表
```

**这就是为什么 `Annotated[list, add_messages]` 很重要**——在并行场景下，reducer 决定了多个节点对同一字段的修改如何合并。

```python
# 没有 reducer: 覆盖（race condition——不确定哪个值赢）
field: str  # tech 和 fund 都改 → 最后完成的覆盖前面

# 有 add_messages reducer: 追加到列表
messages: Annotated[list, add_messages]  # 所有新增消息都累积

# 有自定义 reducer: 你定义的合并逻辑
score: Annotated[int, lambda left, right: max(left, right)]  # 取最大值
```

### 2.4 带 reducer 的完整例子

```python
from typing import Annotated, TypedDict
from langgraph.graph.message import add_messages
from langchain_core.messages import AIMessage

class ParallelState(TypedDict):
    messages: Annotated[list, add_messages]  # ← 自动合并
    scores: Annotated[list, operator.add]    # ← 列表相加

async def node_a(state: ParallelState) -> dict:
    return {
        "messages": [AIMessage(content="A 的结果")],
        "scores": [85],
    }

async def node_b(state: ParallelState) -> dict:
    return {
        "messages": [AIMessage(content="B 的结果")],
        "scores": [92],
    }

# A 和 B 并行执行后:
# messages = [AIMessage("A 的结果"), AIMessage("B 的结果")]  ← add_messages 合并
# scores = [85, 92]  ← operator.add 合并
```

---

## 三、动态 Fan-out：Send API（运行时决定并行几个）

静态 fan-out 的局限：**编译时就必须知道要并行哪些节点**。但实际场景中——

```
用户问了 3 只股票 → 需要 3 个分析节点并行跑
用户问了 5 只股票 → 需要 5 个分析节点并行跑

编译时你不知道用户会问几只股票 → 不能用静态 fan-out
```

**Send API 解决的就是这个问题**。

### 3.1 Send 是什么

```python
from langgraph.graph import Send

# Send 是两个字段:
#   node: str   — 目标节点名
#   arg: dict   — 传给目标节点的 state 覆盖
Send(node="analyze_stock", arg={"current_stock": "600519"})
```

**Send 不是边，是返回值**。路由函数可以返回 `Send` 对象（或 `list[Send]`），告诉 LangGraph："生成 N 个并行任务"。

### 3.2 完整例子：多只股票并行分析

```python
from typing import Annotated, TypedDict
from langgraph.graph import StateGraph, START, END, Send
from langgraph.graph.message import add_messages

class AnalysisState(TypedDict):
    messages: Annotated[list, add_messages]
    stocks: list[str]           # 用户要查询的股票列表
    current_stock: str          # 当前正在分析的股票（每个并行任务不同）
    results: Annotated[list, operator.add]  # 所有分析结果汇合到这里

# 路由函数：为每只股票生成一个 Send
def continue_to_stocks(state: AnalysisState) -> list[Send]:
    """动态生成 N 个并行任务，每个任务分析一只股票"""
    return [
        Send(
            node="analyze_stock",                     # 目标节点
            arg={"current_stock": stock}               # 每个任务拿到不同的股票
        )
        for stock in state["stocks"]                   # 3 只股票 → 3 个并行任务
    ]

# 分析节点：只分析一只股票
async def analyze_stock(state: AnalysisState) -> dict:
    stock = state["current_stock"]  # 从 state 拿到当前要分析的股票
    # 实际项目里这里调行情 API、查知识库...
    result = f"{stock}：(技术面 75，基本面 80)"
    return {"results": [result]}  # operator.add 自动合并所有结果

workflow = StateGraph(AnalysisState)

workflow.add_node("analyze_stock", analyze_stock)
workflow.add_conditional_edges(
    "analyze_stock",
    lambda s: "done",  # 每只股票分析完 → 继续
    {"done": END}
)

# ⭐ 关键：从 START 用 Send 动态分发
workflow.add_conditional_edges(START, continue_to_stocks)

app = workflow.compile()
```

**执行流程**：

```
用户输入: stocks = ["600519", "000858", "002594"]
                    │
                    ▼
  continue_to_stocks() 被调用
                    │
       ┌────────────┼────────────┐
       ▼            ▼            ▼
  Send(600519)  Send(000858)  Send(002594)
       │            │            │
       ▼            ▼            ▼
  analyze_stock  analyze_stock  analyze_stock   ← 三个同时跑！
       │            │            │
       ▼            ▼            ▼
  "600519: ..." "000858: ..." "002594: ..."
       │            │            │
       └────────────┼────────────┘
                    ▼
           results 自动合并（operator.add）
                    │
                    ▼
                  END
```

**和静态 fan-out 的区别**：

```
静态 fan-out:
  编译时定义的固定并行分支（比如永远并行 tech + fund 两个节点）
  用 workflow.add_edge() 实现

动态 fan-out:
  运行时根据数据决定并行几个分支（3 只股票 = 3 个，5 只 = 5 个）
  用 Send 从路由函数返回实现
```

---

## 四、Barrier 机制深入：怎么知道"并行任务都做完了"

### 4.1 Barrier 是什么

Barrier 是 LangGraph 在并行分支汇合处自动插入的同步点。它的逻辑：

```
对于节点 X，如果它有 N 个前驱节点:
  → 必须等这 N 个前驱**全部执行完**
  → 才能执行节点 X

汇合到 END:
  → 必须等所有通向 END 的路径**全部执行完**
  → 图才结束
```

### 4.2 图解

```
           ┌── B（200ms）──┐
    A ────┤                ├──→ D（汇合点，barrier）
           └── C（150ms）──┘

B 跑完了 → "B 完成，但 C 还没完成 → 等 C"
C 跑完了 → "B 和 C 都完成了 → 释放 barrier → 执行 D"

如果 C 失败了呢？→ 取决于错误处理策略（见第五节）
```

### 4.3 compile 时自动分析

```python
# compile() 内部会构建依赖图：

# 图定义:
workflow.add_edge("A", "B")
workflow.add_edge("A", "C")
workflow.add_edge("B", "D")
workflow.add_edge("C", "D")

# compile 分析结果:
# B 的前驱: [A]
# C 的前驱: [A]  ← 和 B 的前驱相同 → B、C 可以并行
# D 的前驱: [B, C]  ← 两个前驱 → D 是 barrier 点
```

---

## 五、并行中的错误处理

### 5.1 默认行为：一个失败 → 全部失败

```python
# 默认 LangGraph 行为:
# 并行分支中任何一个抛异常 → 整个图执行中断
# B 成功了，C 抛异常了 → B 的结果被丢弃
```

### 5.2 项目里的做法：节点内 try/except

```python
async def analyze_stock(state: AnalysisState) -> dict:
    stock = state["current_stock"]
    try:
        result = await fetch_stock_data(stock)
        return {"results": [f"{stock}: {result}"]}
    except Exception as e:
        # ⭐ 失败了返回错误标记，不抛异常
        return {"results": [f"{stock}: 分析失败 ({e})"]}
```

### 5.3 图级错误处理：return_exceptions 不适用

注意：`asyncio.gather(return_exceptions=True)` 是 **Python 层面的机制**，只适用于节点内并行。图级并行（多个节点）用的是 LangGraph 自己的调度器——不经过 `asyncio.gather`，所以 `return_exceptions` 对它无效。

**图级并行的错误处理只有两种方式**：
1. 每个节点内部 try/except，不抛异常
2. LangGraph 编译后的 `.with_retry()` 配置（实验性，一般不依赖）

---

## 六、三种并行方式对比

```
┌─────────────┬──────────────────┬──────────────────┬──────────────────┐
│             │  静态 Fan-out     │  动态 Send       │  asyncio.gather  │
├─────────────┼──────────────────┼──────────────────┼──────────────────┤
│ 何时决定    │ 编译时（写代码时）│ 运行时（数据驱动）│ 运行时（代码内） │
│ 并行数量    │ 固定              │ 动态，取决于数据  │ 固定（代码里写的）│
│ 实现方式    │ add_edge 多条边   │ Send() 返回值     │ asyncio.gather() │
│ 例子        │ 技术面+基本面     │ N 只股票分析      │ 行情+财务+新闻   │
│ 隔离级别    │ 独立节点          │ 同一节点多实例    │ 同一节点内       │
│ 错误处理    │ try/except 每个节点│ try/except 每个实例│ return_exceptions│
│ 适用场景    │ 语义上不同的步骤  │ 同类型任务×N      │ 同一步骤的多 IO  │
└─────────────┴──────────────────┴──────────────────┴──────────────────┘
```

---

## 七、项目里的实际用法

我们项目用了**两种并行**：

### 7.1 节点内 asyncio.gather（主力）

```python
# app/tools/data_fetcher.py
async def fetch_all_data_parallel(query, queries, top_k=3):
    results = await asyncio.gather(
        fetch_market_data(query),
        fetch_financial_data(query),
        fetch_announcements(query),
        fetch_news_data(query),
        fetch_retrieval_data(queries, top_k),
        return_exceptions=True,
    )
    # 总耗时 = max(单个耗时) 而不是 sum(所有耗时)
```

### 7.2 静态 Fan-out 的潜在用法

如果将来需要技术面和基本面分开分析（不同 prompt、不同节点逻辑），就可以用静态 fan-out：

```python
# 将来可能的扩展:
workflow.add_edge("rewrite", "tech_analysis")     # 技术面分析
workflow.add_edge("rewrite", "fund_analysis")     # 基本面分析（同时跑）

# 汇合节点合并两个分析结果
workflow.add_edge("tech_analysis", "merge")
workflow.add_edge("fund_analysis", "merge")
# merge 节点把两个分析结果组装成最终 prompt
```

---

## 八、面试速记

**Q: LangGraph 怎么实现并行执行？**
A: 三种。静态 fan-out（编译时多条 add_edge 从同一节点出发，LangGraph 自动识别并行并插入 barrier）、动态 fan-out（用 Send API 在运行时生成 N 个并行任务，数量取决于数据）、节点内并行（asyncio.gather 在单个节点内并发执行多个 IO 操作）。前两种是图级并行，第三种是代码级并行。

**Q: 并行节点同时改了同一个 State 字段怎么办？**
A: 看字段有没有 reducer。没有 reducer → 最后完成的覆盖前面的（不确定谁赢）。有 `add_messages` → 消息自动追加到列表。有自定义 reducer → 走自定义合并逻辑（比如 `max`、列表相加）。**这是 parallel 场景下最大的坑——字段没配 reducer 会丢数据。**

**Q: 并行分支中一个失败了会影响其他吗？**
A: 图级并行（fan-out/Send）：默认行为是一个分支抛异常 → 整个图中断，其他分支的结果丢失。解决方案是每个节点内部 try/except 不抛异常。节点内并行（asyncio.gather）：`return_exceptions=True` 让单个失败返回 None，不影响其他。

**Q: 静态 fan-out 和 Send 有什么本质区别？**
A: 静态 fan-out 编译时固定，Send 运行时动态。静态适合"永远并行 A+B"的场景，Send 适合"A×N"的场景（N 取决于输入数据）。Send 是函数返回值，不是边——它在路由函数里动态决定"创建多少个并行任务"。

**Q: Barrier 是什么？**
A: 并行分支汇合处的同步点。compile() 时 LangGraph 自动分析图结构，在需要"等所有分支完成"的地方插入 barrier。你不需要手动管理——框架自动保证：有 N 个前驱的节点，必须等这 N 个前驱全部执行完才能执行。
