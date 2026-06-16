# 55_LangGraph Human-in-the-Loop 详解：让人参与 Agent 执行过程

> **核心问题**: Human-in-the-loop 是不是只有"AI 搞不定了转人工"？
> **答案**: 不是。Human-in-the-loop（HITL）= Agent 执行过程中，让人参与关键决策、补充信息、审批动作、修改中间结果，然后 Agent 继续执行。
> **项目现状**: 已有"业务人工兜底"（handoff 工单），但还没有完整接入 LangGraph 的 `interrupt()` / `Command(resume=...)` 暂停恢复式 HITL。

---

## 一、先纠正一个误区：HITL 不等于人工兜底

很多人一听 Human-in-the-loop，就以为是：

```
AI 不会了
  ↓
转人工客服
```

这个理解只对了一小部分。**人工兜底只是 HITL 的一种，而且是比较被动的一种。**

更完整的 HITL 是：

```
AI 正在执行任务
  ↓
遇到需要人判断的节点
  ↓
暂停，把问题/选项/待审批动作交给人
  ↓
人做选择、补充、修改、审批
  ↓
AI 带着人的输入继续执行
```

Java 类比：

```
普通 Agent:
  像一个无人值守的定时任务，从头跑到尾

Human-in-the-loop Agent:
  像一个审批流工作流，执行到关键步骤时挂起，等人处理后继续
```

---

## 二、HITL 的 5 种常见场景

### 2.1 人工兜底：AI 处理不了，交给人

这是项目目前已经有的模式。

```
用户要求转人工
或 critic 多次失败
  ↓
handoff_node
  ↓
生成交接摘要
  ↓
结束 AI 流程
  ↓
人工工单系统继续处理
```

适合：
- AI 多次失败
- 用户明确要求人工客服
- 问题涉及投诉、支付、账号异常
- AI 不应该继续瞎答

项目对应代码：
- `aipy2/app/graph/state.py`：`handoff_to_human`、`handoff_reason`、`handoff_summary`
- `aipy2/app/graph/nodes.py`：`handoff_node`
- `aipy2/app/graph/investor_graph.py`：路由到 `handoff`

### 2.2 人工审批：敏感动作执行前必须确认

这是金融、交易、运维、写库类 Agent 最重要的 HITL。

```
AI 分析完股票
  ↓
准备调用 submit_trade 工具
  ↓
暂停
  ↓
用户选择：确认 / 拒绝 / 修改数量
  ↓
继续执行或取消
```

适合：
- 下单
- 发布交易信号
- 删除数据
- 执行 SQL
- 群发消息
- 写文件
- 调用真实支付接口

**关键原则**：只要动作有真实世界后果，就不要让 Agent 自动执行。

### 2.3 人工选择：让用户选择风格、方向、方案

你刚才说的"让我选择风格"就是这个。

```
AI 理解任务
  ↓
生成 3 种报告风格
  ↓
暂停，让用户选
  ↓
用户选择：专业研报风 / 小白解释风 / 短视频口播风
  ↓
AI 按选择继续生成
```

适合：
- 选择回答风格
- 选择报告结构
- 选择投资周期
- 选择风险偏好
- 选择数据源
- 选择要不要深入分析

这不是 AI 不会，而是**用户偏好必须由用户自己决定**。

### 2.4 人工补充信息：任务条件不完整时暂停询问

很多时候不是 AI 能力不够，而是用户输入不完整。

```
用户：帮我分析一下宁德时代
  ↓
AI 发现缺少投资周期
  ↓
暂停询问：你是短线、中线还是长线？
  ↓
用户：中线
  ↓
AI 继续按中线逻辑分析
```

适合：
- 缺少股票代码
- 缺少投资周期
- 缺少风险承受能力
- 缺少预算
- 缺少目标市场
- 缺少输出格式要求

### 2.5 人工修改中间结果：先让人改草稿，再继续

这类 HITL 很适合写作、研报、代码生成。

```
AI 生成报告大纲
  ↓
暂停
  ↓
用户删掉一节、增加一节、改标题
  ↓
AI 按修改后的大纲继续写正文
```

适合：
- 修改报告大纲
- 修改邮件草稿
- 修改 SQL 条件
- 修改交易计划
- 修改 Prompt
- 修改检索关键词

---

## 三、项目现有 handoff 和 LangGraph HITL 的区别

### 3.1 项目现有：业务人工兜底

项目现在是：

```
AI 判断自己不适合继续处理
  ↓
设置 handoff_to_human = True
  ↓
路由到 handoff_node
  ↓
生成 handoff_summary
  ↓
本次图执行结束
  ↓
Java 网关 / 管理后台处理人工工单
```

特点：
- 不暂停等待用户输入
- 不从原节点继续执行
- 更像"降级到人工工单"
- 适合客服兜底

### 3.2 LangGraph HITL：框架级暂停恢复

真正的 LangGraph HITL 是：

```
图执行到某个节点
  ↓
interrupt() 暂停
  ↓
LangGraph 用 checkpointer 保存当前状态
  ↓
前端展示问题、选项、审批按钮
  ↓
用户提交选择
  ↓
后端用 Command(resume=...) 恢复图
  ↓
图从暂停点继续执行
```

特点：
- 会暂停图
- 必须有 checkpointer
- 必须用同一个 `thread_id` 恢复
- 适合审批、选择、补充信息、修改草稿

### 3.3 对比表

| 对比点 | 业务 handoff | LangGraph interrupt HITL |
|--------|-------------|--------------------------|
| 核心目标 | AI 不行了，交给人 | AI 跑到关键步骤，等人参与后继续 |
| 是否继续原图 | 通常不继续 | 继续 |
| 是否需要 checkpointer | 不强制 | 强制需要 |
| 前端交互 | 工单列表、客服回复 | 选择框、审批按钮、表单 |
| 典型场景 | 转人工客服 | 交易确认、风格选择、补充信息 |
| 项目现状 | 已有 | 还没完整实现 |

一句话：

```
handoff = 退出 AI 流程，把任务交给人
interrupt HITL = 暂停 AI 流程，让人输入，再继续 AI 流程
```

---

## 四、LangGraph HITL 的核心机制

### 4.1 三个关键词

```python
from langgraph.types import interrupt, Command
from langgraph.checkpoint.memory import InMemorySaver
```

| 组件 | 作用 |
|------|------|
| `interrupt(value)` | 暂停当前图，把 `value` 返回给调用方 |
| `Command(resume=...)` | 把人的输入传回图，让图继续执行 |
| `checkpointer` | 保存暂停时的状态，保证恢复时能接着跑 |

### 4.2 执行过程

```
第一次调用:
  app.invoke(input, config)
    ↓
  执行到 interrupt(...)
    ↓
  图暂停，返回 __interrupt__

第二次调用:
  app.invoke(Command(resume=用户输入), same_config)
    ↓
  interrupt(...) 这一行拿到用户输入
    ↓
  节点继续向下执行
    ↓
  图继续跑到 END
```

注意这里最重要的是：

```python
config = {"configurable": {"thread_id": "user_1001_session_abc"}}
```

第一次暂停和第二次恢复，必须使用同一个 `thread_id`。否则 LangGraph 找不到之前暂停的 checkpoint。

---

## 五、最小示例：让用户选择报告风格

这个例子不接真实大模型，先把 HITL 机制跑通。

### 5.1 完整代码

```python
from typing import TypedDict

from langgraph.checkpoint.memory import InMemorySaver
from langgraph.graph import END, START, StateGraph
from langgraph.types import Command, interrupt


class ReportState(TypedDict):
    question: str
    style: str
    answer: str


def choose_style_node(state: ReportState) -> dict:
    """暂停图，让用户选择报告风格。"""

    user_choice = interrupt({
        "type": "style_choice",
        "question": "请选择报告风格",
        "options": [
            "保守稳健",
            "专业研报",
            "短视频口播",
        ],
        "default": "专业研报",
    })

    return {
        "style": user_choice,
    }


def write_report_node(state: ReportState) -> dict:
    """拿到用户选择后，继续生成答案。"""

    question = state["question"]
    style = state["style"]

    answer = f"按照【{style}】风格分析：{question}。这里后续可以接真实 LLM。"

    return {
        "answer": answer,
    }


builder = StateGraph(ReportState)
builder.add_node("choose_style", choose_style_node)
builder.add_node("write_report", write_report_node)

builder.add_edge(START, "choose_style")
builder.add_edge("choose_style", "write_report")
builder.add_edge("write_report", END)

checkpointer = InMemorySaver()
app = builder.compile(checkpointer=checkpointer)

config = {
    "configurable": {
        "thread_id": "demo_user_001",
    }
}

# 第一次调用：会暂停在 choose_style_node
first_result = app.invoke(
    {
        "question": "帮我分析一下宁德时代",
        "style": "",
        "answer": "",
    },
    config=config,
)

print("第一次结果：")
print(first_result)

# 第二次调用：模拟用户选择了"专业研报"
second_result = app.invoke(
    Command(resume="专业研报"),
    config=config,
)

print("恢复后的结果：")
print(second_result)
```

### 5.2 第一次运行会发生什么

第一次执行到这里：

```python
user_choice = interrupt({
    "type": "style_choice",
    "question": "请选择报告风格",
    "options": ["保守稳健", "专业研报", "短视频口播"],
})
```

图会暂停。

调用方会拿到类似这样的信息：

```json
{
  "__interrupt__": [
    {
      "value": {
        "type": "style_choice",
        "question": "请选择报告风格",
        "options": ["保守稳健", "专业研报", "短视频口播"],
        "default": "专业研报"
      },
      "resumable": true
    }
  ]
}
```

前端看到这个 payload 后，就可以渲染一个选择框。

### 5.3 用户选择后怎么恢复

用户点了"专业研报"，后端恢复图：

```python
app.invoke(
    Command(resume="专业研报"),
    config=config,
)
```

这时 `interrupt(...)` 那一行的返回值就是：

```python
user_choice = "专业研报"
```

然后节点继续执行：

```python
return {
    "style": user_choice,
}
```

最终进入 `write_report_node`。

---

## 六、前后端交互怎么设计

### 6.1 后端 SSE 返回 interrupt 事件

如果项目要接到现有 SSE 流里，可以设计一个新事件：

```json
{
  "stage": "interrupt",
  "data": {
    "type": "style_choice",
    "question": "请选择报告风格",
    "options": ["保守稳健", "专业研报", "短视频口播"],
    "thread_id": "user_1:session_88"
  }
}
```

前端收到 `stage == "interrupt"` 后：

```
暂停显示"正在生成"
  ↓
展示选择卡片 / 审批按钮 / 表单
  ↓
用户提交
  ↓
调用后端 resume 接口
```

### 6.2 后端需要一个 resume 接口

示例接口：

```python
@router.post("/chat/resume")
async def resume_chat(req: ResumeRequest):
    config = {
        "configurable": {
            "thread_id": req.thread_id,
        }
    }

    result = await graph.ainvoke(
        Command(resume=req.value),
        config=config,
    )

    return result
```

`ResumeRequest` 可以长这样：

```python
from pydantic import BaseModel


class ResumeRequest(BaseModel):
    thread_id: str
    value: str | dict
```

### 6.3 Vue 前端伪代码

```ts
if (event.stage === 'interrupt') {
  store.pendingInterrupt = event.data
  store.streaming = false
}

async function submitHumanInput(value: string) {
  await api.resumeChat({
    threadId: store.pendingInterrupt.thread_id,
    value,
  })
}
```

### 6.4 为什么不能只靠普通 HTTP 请求

因为 HITL 是两段式：

```
请求 1: 启动图，跑到 interrupt 后暂停
请求 2: 用户提交选择，恢复图继续跑
```

中间可能隔几秒、几分钟、几小时。不能依赖内存里的局部变量，必须靠 checkpointer 保存状态。

---

## 七、投资项目里的 4 个 HITL 例子

### 7.1 风格选择：低风险，最适合先练

```
用户：帮我分析贵州茅台
  ↓
AI：请选择报告风格
      1. 保守稳健
      2. 专业研报
      3. 小白解释
  ↓
用户选择
  ↓
AI 按风格生成
```

优点：
- 没有真实风险
- 前端好做
- 能完整练通 interrupt/resume

### 7.2 投资周期补充：让分析更准确

```
用户：宁德时代能买吗？
  ↓
AI：你打算持有多久？
      1. 1-7 天短线
      2. 1-3 个月波段
      3. 6-12 个月中长线
  ↓
用户选择
  ↓
AI 按投资周期分析
```

这个比直接回答更专业。因为短线看情绪和技术面，中长线看基本面、行业景气度和估值。

### 7.3 交易确认：高风险，必须 HITL

```
AI：建议模拟买入 AAPL 10 股
  ↓
interrupt 审批
  ↓
用户选择：
    approve: 确认买入
    reject: 取消
    edit: 改成 5 股
  ↓
确认后才调用 submit_paper_trade
```

这个是生产系统最有价值的 HITL。

### 7.4 报告大纲修改：适合研报生成

```
AI 生成大纲：
  1. 公司概况
  2. 财务分析
  3. 技术面
  4. 风险提示
  ↓
用户修改：
  删除技术面，增加行业竞争格局
  ↓
AI 按新大纲生成报告
```

这类交互能明显提升最终质量。

---

## 八、审批型 HITL 示例：交易确认

### 8.1 状态定义

```python
from typing import Literal, TypedDict


class TradeState(TypedDict):
    symbol: str
    side: Literal["buy", "sell"]
    quantity: int
    approved: bool
    final_message: str
```

### 8.2 审批节点

```python
from langgraph.types import interrupt


def approve_trade_node(state: TradeState) -> dict:
    """交易执行前暂停，等待用户确认。"""

    decision = interrupt({
        "type": "trade_approval",
        "title": "确认模拟交易",
        "trade": {
            "symbol": state["symbol"],
            "side": state["side"],
            "quantity": state["quantity"],
        },
        "options": [
            {"value": "approve", "label": "确认执行"},
            {"value": "reject", "label": "取消交易"},
        ],
    })

    return {
        "approved": decision == "approve",
    }
```

### 8.3 路由函数

```python
from typing import Literal


def route_after_approval(state: TradeState) -> Literal["execute_trade", "cancel_trade"]:
    if state["approved"]:
        return "execute_trade"
    return "cancel_trade"
```

### 8.4 执行节点

```python
def execute_trade_node(state: TradeState) -> dict:
    # 真实项目里这里才调用交易工具或模拟交易接口。
    return {
        "final_message": f"已执行模拟交易：{state['side']} {state['symbol']} x {state['quantity']}",
    }


def cancel_trade_node(state: TradeState) -> dict:
    return {
        "final_message": "交易已取消，未执行任何操作。",
    }
```

### 8.5 图结构

```python
from langgraph.graph import END, START, StateGraph
from langgraph.checkpoint.memory import InMemorySaver


builder = StateGraph(TradeState)
builder.add_node("approve_trade", approve_trade_node)
builder.add_node("execute_trade", execute_trade_node)
builder.add_node("cancel_trade", cancel_trade_node)

builder.add_edge(START, "approve_trade")
builder.add_conditional_edges(
    "approve_trade",
    route_after_approval,
    {
        "execute_trade": "execute_trade",
        "cancel_trade": "cancel_trade",
    },
)
builder.add_edge("execute_trade", END)
builder.add_edge("cancel_trade", END)

app = builder.compile(checkpointer=InMemorySaver())
```

这个例子的重点：

```
敏感动作 execute_trade_node
  不能放在 interrupt 前
  必须等 approve_trade_node 拿到用户确认后才能执行
```

---

## 九、和 LangChain HumanInTheLoopMiddleware 的关系

LangGraph 原生 HITL：

```python
from langgraph.types import interrupt, Command
```

适合手写 StateGraph：

```
你自己决定在哪个节点暂停
你自己决定前端展示什么
你自己决定恢复后走哪条边
```

LangChain Agent HITL middleware：

```python
from langchain.agents.middleware import HumanInTheLoopMiddleware
```

适合 `create_agent()`：

```
Agent 准备调用某些工具时
middleware 自动拦截
自动触发 interrupt
等待人 approve / edit / reject / respond
```

对比：

| 方案 | 适用场景 | 优点 | 缺点 |
|------|----------|------|------|
| `interrupt()` | 手写 StateGraph | 灵活，能暂停任意节点 | 要自己写前后端协议 |
| `HumanInTheLoopMiddleware` | `create_agent()` | 工具审批开箱即用 | 主要围绕 tool call，不适合所有自定义流程 |

项目当前用的是手写 StateGraph，所以优先学：

```python
interrupt()
Command(resume=...)
checkpointer
```

---

## 十、容易踩的坑

### 10.1 没有 checkpointer

错误写法：

```python
app = builder.compile()
```

HITL 需要暂停后恢复，如果没有 checkpointer，就没有稳定的状态保存。

正确写法：

```python
app = builder.compile(checkpointer=InMemorySaver())
```

生产环境应该用 Postgres checkpointer：

```python
app = builder.compile(checkpointer=postgres_saver)
```

### 10.2 resume 时 thread_id 不一致

错误：

```python
# 第一次
config = {"configurable": {"thread_id": "user_1"}}
app.invoke(input_data, config=config)

# 第二次换了 thread_id
config = {"configurable": {"thread_id": "user_2"}}
app.invoke(Command(resume="专业研报"), config=config)
```

这样恢复不了，因为第二次不是同一个线程。

### 10.3 interrupt 前做了有副作用的事

危险写法：

```python
def node(state):
    send_email()  # 有副作用
    answer = interrupt("是否继续？")
    return {"answer": answer}
```

恢复时节点可能重新执行，`send_email()` 有重复执行风险。

安全写法：

```python
def node(state):
    answer = interrupt("是否发送邮件？")

    if answer == "yes":
        send_email()

    return {"answer": answer}
```

原则：

```
interrupt 前不要做不可重复的外部动作
比如发邮件、下单、写数据库、扣费
```

### 10.4 interrupt 的 value 不适合前端解析

不推荐：

```python
interrupt("你选一下")
```

推荐：

```python
interrupt({
    "type": "style_choice",
    "question": "请选择报告风格",
    "options": [
        {"value": "safe", "label": "保守稳健"},
        {"value": "pro", "label": "专业研报"},
    ],
})
```

前端最喜欢结构化数据，不喜欢解析自然语言。

### 10.5 把所有问题都做成 interrupt

HITL 不是越多越好。

```
太少：高风险动作无人审批，危险
太多：用户一直被打断，体验差
```

建议只在这些点暂停：
- 用户偏好确实不明确
- 动作有真实风险
- 中间结果需要人确认
- AI 缺少关键参数
- 继续执行的成本很高

---

## 十一、项目怎么落地最合适

### 11.1 第一阶段：风格选择

目标：

```
用户问股票
  ↓
如果没有指定输出风格
  ↓
interrupt 让用户选风格
  ↓
按风格继续 answer_node
```

为什么先做这个：
- 风险低
- 对前端要求简单
- 适合理解 `interrupt/resume`
- 不影响真实交易或数据写入

状态可以新增：

```python
class AgentState(TypedDict):
    report_style: str
    pending_interrupt_type: str
```

### 11.2 第二阶段：投资周期补充

目标：

```
如果用户问"能买吗"但没说周期
  ↓
interrupt 询问短线/中线/长线
  ↓
把选择写入 state.investment_horizon
  ↓
后续 prompt 根据周期变化
```

这对回答质量提升很明显。

### 11.3 第三阶段：模拟交易审批

目标：

```
AI 生成模拟交易建议
  ↓
interrupt 审批
  ↓
用户确认后才调用 paper trading 接口
```

这才是生产级 HITL 的核心价值。

### 11.4 第四阶段：Langfuse 记录 HITL

每次 interrupt 都应该写入 Langfuse：

```python
langfuse_trace.event(
    name="hitl:interrupt",
    input={
        "type": "style_choice",
        "thread_id": thread_id,
    },
)

langfuse_trace.event(
    name="hitl:resume",
    input={
        "type": "style_choice",
        "value": "专业研报",
    },
)
```

这样 UI 里能看到：

```
用户什么时候被询问
用户选择了什么
图暂停了多久
恢复后走了哪条路径
```

---

## 十二、面试速记

### Q1: Human-in-the-loop 是什么？

A: Human-in-the-loop 是让人在 Agent 执行过程中参与关键决策的机制。它不只是 AI 失败后转人工，还包括审批敏感工具、补充缺失参数、选择输出风格、修改中间结果等。核心是让 Agent 在关键节点暂停，等待人的输入后继续执行。

### Q2: LangGraph 里怎么实现 HITL？

A: 用 `interrupt()` 和 `Command(resume=...)`。节点里调用 `interrupt(value)` 后，图会暂停并通过 checkpointer 保存状态；前端把 `value` 展示给用户；用户提交后，后端用同一个 `thread_id` 调用 `Command(resume=用户输入)`，图从暂停处继续执行。

### Q3: HITL 和项目里的 handoff 有什么区别？

A: handoff 是业务人工兜底，AI 判断自己不适合继续处理时生成工单并结束当前流程。LangGraph HITL 是框架级暂停恢复，图不会结束，而是等待人输入后继续执行。前者是降级，后者是交互式工作流。

### Q4: 为什么 HITL 必须配合 checkpoint？

A: 因为人可能几秒、几分钟甚至几小时后才回复。服务端不能靠内存局部变量保存暂停状态，必须把图状态持久化。checkpoint 负责保存当前 state、下一步节点、执行上下文，恢复时才能接着跑。

### Q5: 什么场景一定要 HITL？

A: 有真实世界副作用的动作必须 HITL，比如下单、发交易信号、删除数据、执行 SQL、群发消息、扣费。还有用户偏好不明确但会显著影响结果的场景，也适合 HITL，比如投资周期、报告风格、风险偏好。

---

## 十三、新手视角总结

你真正要记住的是：

```
普通 Agent:
  一口气跑完

业务 handoff:
  AI 跑不下去，交给人工，流程结束

LangGraph HITL:
  AI 跑到关键点，暂停问人，人回答后继续跑
```

最重要的三行代码：

```python
from langgraph.types import interrupt, Command

answer = interrupt({"question": "请选择风格"})
app.invoke(Command(resume="专业研报"), config=config)
```

最重要的工程条件：

```python
app = builder.compile(checkpointer=checkpointer)
config = {"configurable": {"thread_id": "同一个线程 ID"}}
```

---

## 十四、课后练习

### 练习目标

给项目设计一个"报告风格选择" HITL 流程。

### 要求

1. 当用户没有指定风格时，暂停图。
2. 返回三个选项：`保守稳健`、`专业研报`、`小白解释`。
3. 用户选择后，把结果写入 `state.report_style`。
4. 后续回答节点根据 `report_style` 调整 prompt。
5. 用 Langfuse 记录 `hitl:interrupt` 和 `hitl:resume` 两个事件。

### 验收标准

```
输入：帮我分析一下贵州茅台

第一次响应：
  stage = interrupt
  options = ["保守稳健", "专业研报", "小白解释"]

用户选择：
  专业研报

恢复后：
  最终回答使用专业研报风格
  Langfuse 能看到 hitl:interrupt 和 hitl:resume
```

---

## 十五、官方参考

- LangGraph Interrupts: https://docs.langchain.com/oss/python/langgraph/interrupts
- LangGraph Graph API: https://docs.langchain.com/oss/python/langgraph/graph-api
- LangGraph Persistence: https://docs.langchain.com/oss/python/langgraph/persistence
- LangChain Human-in-the-loop Middleware: https://docs.langchain.com/oss/python/langchain/human-in-the-loop
- LangGraph interrupt changelog: https://changelog.langchain.com/announcements/interrupt-simplifying-human-in-the-loop-agents
