# LangChain Middleware + LangGraph HITL 学习工作区

这个工作区专门用来学习两个点：

1. **LangChain Middleware**：Agent 调 LLM / Tool 时，怎么统一加日志、限流、重试。
2. **LangGraph Human-in-the-loop**：图执行到一半，怎么暂停问人，拿到人的输入后继续把新信息交给 AI。

本工作区所有示例都默认使用本地教学假模型，不需要 OpenAI / DeepSeek API Key。

---

## 目录结构

```text
middleware_hitl_lab/
  README.md
  examples/
    teaching_fake_model.py        # 离线假模型，模拟 LLM 和 tool call
    01_agent_middleware_demo.py   # middleware 怎么拦截 Agent 的 LLM/Tool 调用
    02_hitl_style_choice_demo.py  # 一个节点里暂停，让用户选择风格，再继续
    03_hitl_add_info_to_ai.py     # 一个节点里补充信息，再把信息加入 prompt 给 AI
```

---

## 运行命令

在项目根目录执行：

```powershell
cd D:\ai-investor\learning_workspace\middleware_hitl_lab
D:\ai-investor\aipy2\.venv\Scripts\python.exe examples\01_agent_middleware_demo.py
D:\ai-investor\aipy2\.venv\Scripts\python.exe examples\02_hitl_style_choice_demo.py
D:\ai-investor\aipy2\.venv\Scripts\python.exe examples\03_hitl_add_info_to_ai.py
```

---

## 你先记住一张图

### Middleware 挂在 Agent 上

```text
用户输入
  ↓
create_agent(...)
  ↓
middleware: log_model_call
  ↓
middleware: ModelCallLimitMiddleware
  ↓
LLM
  ↓
Tool
  ↓
LLM
  ↓
最终回答
```

Middleware 不是给普通 `LLMChain` 用的，也不是直接挂在裸 LLM 上。它挂在 `create_agent()` 返回的 Agent 上。

### HITL 挂在 LangGraph 节点里

```text
节点开始执行
  ↓
interrupt(...) 暂停
  ↓
前端/用户给出选择
  ↓
Command(resume=用户输入)
  ↓
同一个节点重新执行
  ↓
interrupt(...) 这行拿到用户输入
  ↓
后面的代码继续，把用户输入加入 prompt
  ↓
调用 AI
```

注意：恢复时节点会从头重新执行一遍，所以 `interrupt()` 前不要写下单、发邮件、写数据库这种有副作用的代码。

---

## Java 类比

### Middleware

```text
Spring Interceptor / AOP
  ↓
请求进入 Controller 前后统一加逻辑

LangChain Middleware
  ↓
Agent 调 LLM / Tool 前后统一加逻辑
```

### HITL

```text
审批流工作流
  ↓
流程走到"经理审批"节点时挂起
  ↓
经理点通过/驳回
  ↓
流程继续

LangGraph HITL
  ↓
图走到 interrupt 节点时挂起
  ↓
用户选择/补充/审批
  ↓
Command(resume=...) 恢复图
```

---

## 学习顺序

1. 先跑 `01_agent_middleware_demo.py`，看 middleware 怎么包住模型调用和工具调用。
2. 再跑 `02_hitl_style_choice_demo.py`，看同一个节点暂停和恢复。
3. 最后跑 `03_hitl_add_info_to_ai.py`，看如何把人的新信息加进 prompt 给 AI。

---

## 30 秒面试讲解稿

我理解 LangChain middleware 和 LangGraph HITL 是两个不同层级的能力。Middleware 挂在 `create_agent()` 上，负责拦截 Agent 内部的模型调用和工具调用，适合做日志、重试、限流、降级。HITL 是 LangGraph 的暂停恢复机制，节点里调用 `interrupt()` 后图会保存状态并暂停，前端收集用户选择后用 `Command(resume=...)` 恢复。前者解决横切工程能力，后者解决人在流程中参与决策的问题。

---

## 新手视角总结

Middleware 像"拦截器"，不改变主流程，只是在 LLM/Tool 调用前后加统一逻辑。

HITL 像"流程挂起"，Agent 不是一口气跑完，而是在关键位置停下来问人，人的答案会变成图后续执行的输入。

---

## 课后练习

把 `02_hitl_style_choice_demo.py` 改成四个选项：

```text
保守稳健
专业研报
小白解释
短视频口播
```

验收标准：

1. 第一次运行时 `__interrupt__` 里能看到四个选项。
2. `Command(resume="短视频口播")` 后，最终回答里包含"短视频口播"。
3. 你能解释为什么必须使用同一个 `thread_id`。
