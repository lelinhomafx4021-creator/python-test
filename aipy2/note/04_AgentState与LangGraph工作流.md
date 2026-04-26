# 04 AgentState 与 LangGraph 工作流

## 1. 先建立一个核心认知

`investor_graph.py` 不是“一个大函数”，而是一张工作流图。

LangGraph 的核心不是 if-else 嵌套，而是：

1. 定义状态
2. 定义节点
3. 定义边
4. 定义条件路由
5. 让状态在节点之间流动

## 2. `AgentState` 是什么

`AgentState` 可以理解成：

- 整条 Agent 流程共享的上下文对象
- 每个节点都能读、都能写的状态容器

它通常会装这些东西：

1. `messages`
2. `query`
3. `intent`
4. `rewritten_queries`
5. `retrieved_knowledge`
6. `final_answer`
7. `retry_count`
8. `total_tokens`

如果用 Java 比喻，它就像一个在工作流里一直往下传的 `Context` 对象。

## 3. 为什么 Agent 需要显式状态

普通函数调用里，局部变量足够。

但 Agent 工作流不一样：

1. 多个节点分步执行
2. 节点之间可能回退
3. 可能流式输出中间事件
4. 可能多轮重试

这时候必须有一个“统一状态中枢”，否则每个节点都像在猜别人在干什么。

## 4. `messages` 为什么不是普通 list

在 LangGraph 里，`messages` 常和 `add_messages` 一起使用，这是为了让状态聚合更安全。

它的意义是：

1. 每个节点追加消息时有明确规则
2. 图回退或合并时消息不会乱
3. LangGraph 能更稳地处理消息流演进

## 5. `_latest_user_query()` 为什么非常关键

这个点特别值得记。

如果你直接拿 `messages[-1]`，在带 critic 回退的流程里，最后一条消息未必还是用户消息，可能已经是 AI 草稿答案。

于是会发生严重问题：

1. 系统把 AI 草稿当成新的用户 query
2. 后续 search/skill/critic 全部基于错误输入继续跑
3. 整条链路逻辑就歪了

所以正确做法是：

- 倒序找最近一条 `HumanMessage`

这说明在 Agent 项目里，状态读取不是小事，而是主逻辑正确性的关键。

## 6. LangGraph 和普通函数编排的区别

普通函数链：

1. 调用顺序写死
2. 没有显式状态图
3. 回退和条件分支不优雅

LangGraph：

1. 节点职责更清晰
2. 状态流转更明确
3. 条件路由更自然
4. 回退重试更适合表达

## 7. 为什么这层特别值得学

因为 Agent 开发真正难的不是“会不会调 LLM”，而是：

1. 状态怎么设计
2. 节点边界怎么划分
3. 回退条件怎么定义
4. 状态一致性怎么保证

这才是从“会写接口”走向“会做 Agent 系统”的关键。

## 8. 这一篇你最该记住的结论

1. LangGraph 的核心是“状态驱动工作流”。
2. `AgentState` 是 Agent 的共享大脑内存。
3. 读错状态比 prompt 写差更危险，因为它会直接把整条链跑歪。
