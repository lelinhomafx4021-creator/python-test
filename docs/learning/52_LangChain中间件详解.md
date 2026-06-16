# 52_LangChain 中间件详解：Agent 的"拦截器"

> **核心问题**: Agent 调 LLM、调 Tool 的过程中，怎么加日志、限流、重试、切换模型——不改代码？
> **答案**: Middleware（中间件）。LangChain 1.0 最大的新特性。2025 年 Interrupt 大会上发布。
> **项目现状**: 尚未使用，是下一步升级方向。

---

## 一、什么是 Middleware

### 1.1 Web 中间件类比

如果你写过 Web 后端，这个概念你一定懂：

```
# FastAPI/Express 中间件
请求 → [日志中间件] → [认证中间件] → [限流中间件] → 业务逻辑
响应 ← [日志中间件] ← [认证中间件] ← [限流中间件] ← 业务逻辑

中间件 = 在"请求→响应"这条链路上插入的拦截器
每个中间件可以在请求到达业务逻辑之前/之后做事情
```

**Agent 中间件完全一样**，只是把"HTTP 请求→响应"换成"LLM 调用→返回"和"Tool 调用→返回"：

```
Agent 执行流:
  LLM 调用 → Tool 调用 → LLM 调用 → Tool 调用 → ... → 最终回答
     ↑           ↑
     中间件可以拦截这两类调用
```

### 1.2 为什么需要中间件

不用中间件时，这些逻辑会散落在代码各处：

```python
# 散落的横切关注点
async def route_intent_node(state):
    log.info(f"调用 LLM, input tokens: {count}")     # ← 日志，散落的
    try:
        result = await llm.ainvoke(messages)
    except RateLimitError:
        await asyncio.sleep(5)                        # ← 重试，散落的
        result = await llm.ainvoke(messages)
    except Exception:
        result = await fallback_llm.ainvoke(messages)  # ← 降级，散落的
    tracker.track_call(model="deepseek", tokens=...)   # ← 追踪，散落的
    return result
    # 业务逻辑只有一行 await llm.ainvoke()，其他全是横切关注点
```

**用中间件后，这些横切逻辑抽到一个地方，所有 LLM 调用自动复用**：

```python
# 中间件统一处理日志、重试、降级
agent = create_agent(
    model="deepseek-v4",
    tools=[...],
    middleware=[
        LoggingMiddleware(),       # 日志
        ModelFallbackMiddleware(...), # 降级
        ModelCallLimitMiddleware(50),  # 限流
        ToolRetryMiddleware(max_retries=3), # 工具重试
    ]
)
# 每个 LLM 调用自动经过整条中间件链
# 业务代码零侵入
```

**一句话**：中间件 = Agent 的 AOP（面向切面编程）。把横切关注点从业务逻辑中抽离出来。

---

## 二、中间件的执行生命周期

LangChain 中间件有 **8 个钩子**，分布在 Agent 执行的 4 个阶段：

```
Agent 生命周期:

① before_agent    ─→ Agent 启动前（只执行一次）
                     用于: 初始化资源、注入 system prompt、鉴权

② ┌─ before_model  ─→ 每次 LLM 调用前
   │                  用于: 记录日志、修改请求、token 计数
   │
   ├─ wrap_model_call ─→ 包裹 LLM 调用（可以: 重试/降级/切换模型/短路）
   │                     这是最强大的钩子
   │
   └─ after_model    ─→ 每次 LLM 调用后
                       用于: 记录响应、验证输出、写入 trace

③ ┌─ wrap_tool_call  ─→ 包裹 Tool 调用（可以: 重试/超时/替换结果）
   │

④ after_agent     ─→ Agent 结束后（只执行一次）
                     用于: 清理资源、审计、写入最终 trace
```

### 2.1 执行顺序图解

```
┌─────────────────────────────────────────────┐
│              Agent 执行一次                  │
│                                             │
│  ① before_agent()                           │
│                                             │
│  ┌── 循环开始 ──────────────────────────┐   │
│  │                                      │   │
│  │  ② before_model(request)              │   │
│  │       │                               │   │
│  │  ③ wrap_model_call(request, handler)  │   │
│  │       │  handler(request) 调用 LLM     │   │
│  │       │                               │   │
│  │  ② after_model(response)              │   │
│  │       │                               │   │
│  │       │  LLM 返回 tool_call           │   │
│  │       │                               │   │
│  │  ③ wrap_tool_call(request, handler)   │   │
│  │       │  handler(request) 执行 Tool    │   │
│  │       │                               │   │
│  │  ... 继续循环直到 LLM 返回最终回答 ...  │   │
│  └──────────────────────────────────────┘   │
│                                             │
│  ④ after_agent()                            │
└─────────────────────────────────────────────┘
```

### 2.2 中间件组合顺序

**多个中间件 → 洋葱模型**（和 Web 框架一模一样）：

```
请求方向 →
┌──────────────────────────────────────────┐
│  [Middleware A] → [Middleware B] → [Middleware C] → LLM 实际调用
│  [Middleware C] ← [Middleware B] ← [Middleware A] ← LLM 返回
└──────────────────────────────────────────┘
                                     ← 响应方向

示例:
  middleware=[Logging(), Retry(), Fallback()]

  LLM 调用请求:
    Logging.wrap_model_call 进入 → 记录 "开始调用"
    Retry.wrap_model_call 进入 → 设置重试次数
    Fallback.wrap_model_call 进入 → 设置备用模型
    → 实际 LLM 调用
    Fallback.wrap_model_call 退出 → 检查是否成功
    Retry.wrap_model_call 退出 → 决定是否重试
    Logging.wrap_model_call 退出 → 记录 "调用完成，耗时 230ms"
```

---

## 三、内置中间件一览（14 种）

LangChain 官方提供了 14 种开箱即用的中间件，覆盖了生产环境 Agent 最常见的需求：

### 3.1 可靠性类

| 中间件 | 做什么 | 核心参数 |
|--------|--------|---------|
| `ToolRetryMiddleware` | Tool 调用失败自动重试 | `max_retries=3`, `backoff_factor=2`（指数退避） |
| `ModelFallbackMiddleware` | 主模型挂了自动切备用模型 | `models=[gpt4, claude, haiku]`（按优先级降级） |

### 3.2 成本控制类

| 中间件 | 做什么 | 核心参数 |
|--------|--------|---------|
| `ModelCallLimitMiddleware` | 限制 LLM 调用次数 | `max_calls=50`（超过抛异常或走 fallback） |
| `ToolCallLimitMiddleware` | 限制 Tool 调用次数 | `max_calls=20`（防止工具调用死循环） |

### 3.3 上下文管理类

| 中间件 | 做什么 | 核心参数 |
|--------|--------|---------|
| `SummarizationMiddleware` | 上下文快满时自动压缩历史 | `context_size=ContextTokens(100000)` 或 `ContextFraction(0.8)` |
| `ContextEditingMiddleware` | 裁剪/清理工具调用结果 | 自动清理旧的 tool_result，减少噪音 |

### 3.4 安全类

| 中间件 | 做什么 | 核心参数 |
|--------|--------|---------|
| `HumanInTheLoopMiddleware` | 敏感操作暂停等人确认 | `InterruptOnConfig(tools=["delete", "sell"])` |
| `PIIMiddleware` | 检测并处理个人隐私信息 | 自动脱敏或拦截 |

### 3.5 辅助类

| 中间件 | 做什么 | 核心参数 |
|--------|--------|---------|
| `TodoListMiddleware` | 给 Agent 加待办列表能力 | Agent 自动拆分任务、跟踪进度 |
| `LLMToolSelectorMiddleware` | 用小模型先筛选工具，减少主模型 token | 主模型只看到相关工具 |
| `LLMToolEmulator` | 用 LLM 模拟工具执行（测试用） | 不实际调工具，LLM 自己编结果 |
| `ShellToolMiddleware` | 给 Agent 提供 Shell 执行能力 | 持久化会话 |
| `FilesystemFileSearchMiddleware` | 给 Agent 提供文件搜索能力 | Glob + Grep |

---

## 四、⭐ 核心钩子详解

### 4.1 `@wrap_model_call` — 最重要的钩子

它可以：**重试、降级、切换模型、修改请求、修改响应、短路（不调 LLM 直接返回缓存）**。

函数签名：
```python
@wrap_model_call
def my_wrapper(
    request: ModelRequest,                              # 本次调用的请求
    handler: Callable[[ModelRequest], ModelResponse],   # 调用下一个中间件(或实际LLM)
) -> ModelResponse:                                     # 返回响应
    # request.state     → 当前 AgentState（可以读写！）
    # request.runtime   → 运行时信息（thread_id, context 等）
    # request.messages  → 发给 LLM 的消息
    # request.tools     → 可用的工具列表
    # request.model     → 当前模型名
```

**用例 1：动态模型切换**（简单任务用小模型，复杂任务用大模型）

```python
from langchain.agents.middleware import wrap_model_call, ModelRequest, ModelResponse
from langchain.chat_models import init_chat_model

complex_model = init_chat_model("claude-sonnet-4-6")
simple_model = init_chat_model("claude-haiku-4-5-20251001")

@wrap_model_call
def dynamic_model_router(
    request: ModelRequest,
    handler: Callable[[ModelRequest], ModelResponse],
) -> ModelResponse:
    # 判断当前任务复杂度
    msg_count = len(request.state.get("messages", []))
    query = request.state.get("query", "")

    # 简单任务：用便宜模型
    if msg_count < 3 and len(query) < 20:
        request = request.override(model=simple_model)

    # 复杂任务：用强模型
    else:
        request = request.override(model=complex_model)

    return handler(request)  # 继续调用链
```

**用例 2：带重试+降级的 LLM 调用**

```python
@wrap_model_call
def resilient_model_call(
    request: ModelRequest,
    handler: Callable[[ModelRequest], ModelResponse],
) -> ModelResponse:
    max_retries = 3
    for attempt in range(max_retries):
        try:
            return handler(request)
        except RateLimitError:
            if attempt < max_retries - 1:
                wait = 2 ** attempt  # 1s → 2s → 4s
                time.sleep(wait)
                continue
            raise
        except Exception:
            # 降级到备用模型
            request = request.override(model="claude-haiku-4-5-20251001")
            return handler(request)
```

**用例 3：缓存 LLM 响应**（相同输入不重复调 LLM）

```python
import hashlib
cache = {}  # 生产环境用 Redis

@wrap_model_call
def cache_model_response(
    request: ModelRequest,
    handler: Callable[[ModelRequest], ModelResponse],
) -> ModelResponse:
    # 基于消息内容生成缓存 key
    msg_text = "|".join(str(m) for m in request.messages)
    cache_key = hashlib.md5(msg_text.encode()).hexdigest()

    if cache_key in cache:
        return cache[cache_key]  # 短路！不调 LLM

    response = handler(request)
    cache[cache_key] = response
    return response
```

**这是 `wrap_model_call` 最强大的能力——可以短路，不调 LLM 直接返回缓存。**

### 4.2 `@wrap_tool_call` — 拦截工具调用

```python
@wrap_tool_call
def retry_tool_on_failure(
    request: ToolRequest,
    handler: Callable[[ToolRequest], ToolResponse],
) -> ToolResponse:
    for attempt in range(3):
        try:
            return handler(request)
        except Exception as e:
            if attempt == 2:
                # 最后一次失败，返回错误信息而不是抛异常
                return ToolResponse(content=f"工具 {request.tool_name} 执行失败: {e}")
            time.sleep(2 ** attempt)
```

### 4.3 `@before_model` + `@after_model` — 轻量级钩子

比 `wrap_model_call` 轻——不能控制是否调用 LLM，只能观察。

```python
@before_model
def log_model_call(request: ModelRequest) -> None:
    print(f"[调用] 模型={request.model}, 消息数={len(request.messages)}")

@after_model
def track_token_usage(response: ModelResponse) -> None:
    print(f"[响应] tokens: {response.usage_metadata}")
```

### 4.4 类方式：继承 `AgentMiddleware`

装饰器适合简单场景。复杂中间件用类：

```python
from langchain.agents.middleware import AgentMiddleware, ModelRequest, ModelResponse

class DynamicModelMiddleware(AgentMiddleware):
    """复杂任务用 Sonnet，简单任务用 Haiku"""

    def __init__(self):
        self.complex_model = init_chat_model("claude-sonnet-4-6")
        self.simple_model = init_chat_model("claude-haiku-4-5-20251001")

    def wrap_model_call(self, request, handler):
        if self._is_complex(request):
            request = request.override(model=self.complex_model)
        else:
            request = request.override(model=self.simple_model)
        return handler(request)

    def _is_complex(self, request) -> bool:
        query = request.state.get("query", "")
        return len(query) > 100 or any(kw in query for kw in ["分析", "报告", "对比"])

# 使用
agent = create_agent(
    model="claude-sonnet-4-6",
    middleware=[DynamicModelMiddleware()],
)
```

---

## 五、中间件 vs 项目现有做法对比

我们项目目前用"代码内联"方式实现了部分中间件功能：

| 功能 | 项目当前做法 | 中间件做法 | 哪个好 |
|------|-------------|-----------|--------|
| LLM 重试 | `try/except` 在每个节点里 | `@wrap_model_call` 统一重试 | 中间件（不散落） |
| 结构化输出降级 | `with_structured_output` try → `llm.ainvoke` fallback | `@wrap_model_call` + `@after_model` | 中间件（DRY） |
| 工具并行容错 | `asyncio.gather(return_exceptions=True)` | `ToolRetryMiddleware` | 中间件（带重试） |
| 死循环保护 | `retry_count >= 3 → handoff` | `ModelCallLimitMiddleware` + `ToolCallLimitMiddleware` | 中间件（配置化） |
| 成本追踪 | 无 | `@after_model` 记录 token 消耗 | 中间件 |
| 上下文压缩 | ❌ 缺失 | `SummarizationMiddleware` 自动压缩 | 中间件 |

**迁移建议**：不是全部替换。已有的 `return_exceptions` 和 `try/except` 逻辑保留。中间件补我们缺失的：`SummarizationMiddleware`（会话压缩）、`ModelCallLimitMiddleware`（防死循环）、`@after_model`（Token 追踪）。

---

## 六、中间件 vs LangGraph 的 StateGraph

一个重要区分：**中间件管的是 Agent 内部的 LLM/Tool 调用，StateGraph 管的是节点的执行流程**。

```
StateGraph 层面:  intent → rewrite → search → answer → critic
                   ↑ 这些是"节点"，管的是"先做什么后做什么"

Middleware 层面:   每个节点内部调 LLM 时，中间件在 LLM 调用前后插入逻辑
                   ↑ 这些是"拦截器"，管的是"每次调 LLM 时额外做什么"

两者互补，不是替代关系:
  StateGraph = 宏观流程编排
  Middleware = 微观调用拦截
```

**但注意**：LangChain 1.0 的 `create_agent()` 内置了 state graph + middleware，你不用自己搭 StateGraph。如果你用 `create_agent()` 而不是手写 StateGraph，中间件就是主要扩展点。

---

## 七、企业实践建议

### 7.1 中间件选择清单

```
小项目（< 100 用户）:
  ✅ ToolRetryMiddleware          — 工具重试
  ✅ SummarizationMiddleware      — 长对话压缩

中型项目（100-1000 用户）:
  ✅ 以上 +
  ✅ ModelFallbackMiddleware      — 模型降级
  ✅ ModelCallLimitMiddleware     — 防止单用户跑飞
  ✅ @after_model (自定义)        — Token 追踪 + Langfuse 集成

大型项目（1000+ 用户）:
  ✅ 以上 +
  ✅ HumanInTheLoopMiddleware     — 敏感操作审批
  ✅ PIIMiddleware                — 合规
  ✅ @wrap_model_call (自定义)    — 动态路由 + 缓存
  ✅ ToolCallLimitMiddleware      — 防止工具调用爆炸
```

### 7.2 不要踩的坑

```
❌ 中间件太多:
   10 个中间件每层都拦截 → 每个 LLM 调用多 200ms 开销
   → 控制在 3-5 个核心中间件

❌ wrap_model_call 里做重逻辑:
   缓存 key 计算太复杂 → 可能比 LLM 调用还慢
   → 缓存 key 尽量简单（消息 hash）

❌ 中间件里改 state 后继续:
   改了 state 但没更新 checkpoint → 下次恢复时丢失
   → StateGraph 模式下中间件改 state 需谨慎

❌ 假装中间件能解决所有问题:
   Middleware 是拦截器，不是业务逻辑
   → 业务逻辑还是在节点里写，中间件只管横切关注点
```

---

## 八、面试速记

**Q: LangChain 的 Middleware 是什么？**
A: Agent 的"拦截器"。在 LLM 调用/Tool 调用前后插入逻辑——日志、重试、降级、限流、缓存。类似 Web 框架的中间件（洋葱模型），多个中间件顺序进入、逆序退出。LangChain 1.0 的最大新特性，2025 年发布。

**Q: 中间件和 StateGraph 是什么关系？**
A: StateGraph 管宏观流程（节点执行顺序），Middleware 管微观调用（每次 LLM/Tool 调用的拦截）。两者互补——StateGraph 是"先做什么再做什么"，Middleware 是"每次做的时候额外干什么"。`create_agent()` 内置了两者。

**Q: 什么时候用装饰器方式，什么时候用类方式？**
A: 简单逻辑（几行的 log/track）用 `@before_model` / `@after_model` 装饰器。需要初始化资源（模型实例、Redis 连接）、有内部状态、或者需要共享配置的，用类继承 `AgentMiddleware`。区别不大，类方式更好测试和复用。

**Q: wrap_model_call 和 before_model/after_model 的区别？**
A: `before_model` / `after_model` 只能观察（在调用前后执行逻辑，不能控制是否调用）。`wrap_model_call` 可以控制——能重试（多次调 handler）、降级（换请求后再调 handler）、短路（不调 handler 直接返回缓存）。**最强大的是短路能力——实现 LLM 响应缓存。**

**Q: 你们项目用中间件了吗？**
A: 目前还没用——项目用 StateGraph 手写流程，横切关注点（日志、重试、降级）散落在各节点里。这是已知的技术债。下一步计划用 `SummarizationMiddleware` 解决长对话压缩问题，用 `ModelCallLimitMiddleware` + `ToolRetryMiddleware` 替代手写的 retry_count。
