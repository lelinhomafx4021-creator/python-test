# 50_Agent错误处理与恢复：生产环境 Agent 怎么做到不崩

> **核心问题**: Agent 失败的方式和传统程序完全不同——调用链长、取决于 LLM 决策、中间步骤多
> **项目代码**: `app/graph/nodes.py` (retry+handoff) + `app/services/investor_service.py` (try/finally)

---

## 一、Agent 特有的失败模式

```
传统程序失败:
  空指针 → stack trace → 修 bug
  失败原因明确，定位简单

Agent 失败:
  LLM 返回了格式错误的 JSON → 不是 bug，LLM 就是有概率输出不稳定
  LLM 选择了错误的工具 → 不是 bug，是决策错了
  LLM 陷入了循环（调工具→不满意→再调→还不满意...）→ 不是 bug，是没有终止条件
  Tavily API 超时 → 外部依赖，不是你的问题
  DeepSeek 限流 → 同上
```

**传统程序的"异常处理"逻辑不适用于 Agent。Agent 需要"降级优雅、能恢复、能追溯"。**

---

## 二、六层错误处理模式

### 第 1 层：单步降级（try/except + fallback）

```python
# app/graph/nodes.py — 每个结构化输出都有降级
async def route_intent_node(state):
    # 主路径：走原生 function calling
    structured_llm = llm.with_structured_output(IntentRouteResult)
    try:
        result = await structured_llm.ainvoke(messages)
        use_kb = result.route == "use_kb"
    except Exception:
        # 降级路径：structured output 失败 → 用原始 LLM + 关键词匹配
        res = await llm.ainvoke(messages)
        decision = _message_text(res).strip().lower()
        use_kb = "use_kb" in decision and "no_kb" not in decision
    return {"use_kb": use_kb, ...}
```

**原则**：主路径有概率失败 → 降级路径保证功能可用（质量可能稍差）

### 第 2 层：工具失败不阻塞（return_exceptions）

```python
# app/tools/data_fetcher.py
results = await asyncio.gather(
    fetch_market_data(...),      # 腾讯行情
    fetch_financial_data(...),   # 东方财富
    fetch_announcements(...),    # 公告
    fetch_news_data(...),        # 新闻
    fetch_retrieval_data(...),   # 知识库
    return_exceptions=True,      # ← 单个失败不抛异常，其他继续
)
# 行情超时 → market_data = None → answer_node 只用检索结果回答
# 用户完全不知道行情挂了
```

### 第 3 层：循环保护（retry_count 硬限制）

```python
# app/graph/nodes.py — critic_node
new_retry = state.get("retry_count", 0) + (1 if status == "fail" else 0)

if status == "fail" and new_retry >= 3:
    # 不继续拉扯了，转人工
    return {"handoff_to_human": True, ...}
```

**没有硬限制 = Agent 可能死循环到 Token 耗尽。**

### 第 4 层：人工兜底

```python
# 两路触发
# 触发1: 用户主动要求
if _wants_human_handoff(user_msg):  # 包含"人工"、"客服"等关键词
    return {"handoff_to_human": True}

# 触发2: critic 3 次不通过
if retry_count >= 3:
    return {"handoff_to_human": True, "handoff_summary": summary}
```

### 第 5 层：外部依赖降级

```python
# app/services/investor_service.py
# Langfuse 挂了 → 追踪失败但不影响主流程
try:
    langfuse_client = _init_langfuse_client()
    trace = langfuse_client.trace(...)
except Exception:
    langfuse_client = None  # 追踪失败，Agent 继续跑

# PostgreSQL Checkpointer 挂了 → 降级到内存
# app/graph/investor_graph.py
checkpointer = llm_core.checkpointer or InMemorySaver()
```

### 第 6 层：流式错误通道

```python
# app/api/v1/chat.py
# SSE 的 HTTP 响应头已经发出去了，不能再用 HTTP 500
async def event_gen():
    try:
        async for evt in investor_service.run_investor_flow(...):
            yield f"data: {json.dumps(evt)}\n\n"
    except Exception as exc:
        # 把错误包装成 SSE 事件，前端监听 stage="error"
        yield f"data: {json.dumps({'stage': 'error', 'data': {'msg': str(exc)}})}\n\n"
```

---

## 三、错误恢复模式

### 恢复 1: 断点续跑

```python
# Agent 在第 4 步 answer_node 崩了
# 修复 bug 后，同一个 thread_id 继续跑
# LangGraph 从上一个 checkpoint 自动恢复——不会重跑前 3 步
result = await graph.ainvoke(None, config)  # input=None = 从上次断点继续
```

### 恢复 2: 手动修正后继续

```python
# 发现 search_node 的检索结果不对
# 手动修正 State → 让 Agent 从 search 之后继续
await graph.aupdate_state(config, {"knowledge": "正确的检索结果..."})
result = await graph.ainvoke(None, config)
# Agent 用修正后的 knowledge 重新生成 answer
```

### 恢复 3: 回退重试

```python
# 发现 critic 判断错了（该过的没过）
# 回退到 critic 之前，修改 review_status 再继续
await graph.aupdate_state(
    config,
    {"review_status": "pass", "retry_count": 0},
    as_of=history_before_critic
)
```

---

## 四、成本控制与限流：Agent 怎么不烧钱

错误处理管的是"不崩"，成本控制管的是"不烧钱"。生产环境 Agent 最大的风险不是报错，是**静默烧钱**——Agent 陷入循环、用户问了一堆问题、LLM 被反复调用，一天下来账单爆炸。

### 4.1 Agent 烧钱的五个源头

```
1. LLM 调用次数爆炸
   Agent 循环中每轮都调 LLM → 20 轮 = 20 次调用
   5 个并行 Agent 每个 20 轮 → 100 次调用
   每次调用 5000 token → 500K token/会话

2. Token 数膨胀
   对话历史累积 → 每轮上下文越来越长
   第 1 轮: 1000 token → 第 20 轮: 20000 token
   20 轮平均 10000 token × 20 = 200K token

3. 工具调用浪费
   get_quote("茅台") 在第 2 轮调了 → 第 5 轮又调了一次（数据不变）
   5 个并行数据源每次全调 → 其实用户只问了其中 1 个

4. 无效重试
   LLM 输出格式错误 → 重试 3 次 → 3 次全失败（模型就是不支持这个格式）
   应该第 2 次失败就降级，不再试

5. 用户滥用
   一个用户 1 分钟发 30 条消息 → 30 次 Agent 调用
   不一定是恶意，但必须限制
```

### 4.2 Token 预算制（每一轮设上限）

```python
from dataclasses import dataclass

@dataclass
class TokenBudget:
    per_call: int = 4000     # 单次 LLM 调用最多 4K token
    per_turn: int = 20000    # 单轮对话最多 20K token
    per_session: int = 200000 # 单次会话最多 200K token

    spent_per_turn: int = 0
    spent_per_session: int = 0

    def can_call(self) -> bool:
        if self.spent_per_turn >= self.per_turn:
            return False
        if self.spent_per_session >= self.per_session:
            return False
        return True

    def spend(self, tokens: int):
        self.spent_per_turn += tokens
        self.spent_per_session += tokens

# 在每次 LLM 调用前检查
async def answer_node(state):
    budget: TokenBudget = state.get("token_budget")
    if not budget.can_call():
        return {"answer": "已达到本轮 Token 上限，请简化问题重试。"}

    response = await llm.ainvoke(messages)
    budget.spend(response.usage_metadata["total_tokens"])
    return {"answer": response.content}
```

### 4.3 调用次数硬限制（防循环）

```python
# 简单版: 计数器
class CallLimiter:
    def __init__(self, max_llm_calls: int = 20, max_tool_calls: int = 50):
        self.max_llm_calls = max_llm_calls
        self.max_tool_calls = max_tool_calls
        self.llm_calls = 0
        self.tool_calls = 0

    def check_llm(self) -> bool:
        self.llm_calls += 1
        if self.llm_calls > self.max_llm_calls:
            raise CallLimitExceeded(f"LLM 调用超过 {self.max_llm_calls} 次")
        return True

    def check_tool(self) -> bool:
        self.tool_calls += 1
        if self.tool_calls > self.max_tool_calls:
            raise CallLimitExceeded(f"工具调用超过 {self.max_tool_calls} 次")
        return True

# LangChain 1.0 直接用内置中间件
from langchain.agents.middleware import ModelCallLimitMiddleware, ToolCallLimitMiddleware
agent = create_agent(
    model="deepseek-v4",
    middleware=[
        ModelCallLimitMiddleware(max_calls=20),      # 最多调 20 次 LLM
        ToolCallLimitMiddleware(max_calls=50),       # 最多调 50 次工具
    ]
)
```

### 4.4 用户级限流（Rate Limiting）

```python
# 方案 A: 滑动窗口（Redis）
import time
import redis

class SlidingWindowRateLimiter:
    def __init__(self, redis_client, max_requests=10, window_sec=60):
        self.redis = redis_client
        self.max_requests = max_requests
        self.window_sec = window_sec

    def is_allowed(self, user_id: str) -> bool:
        now = time.time()
        key = f"rate_limit:{user_id}"

        # 删除窗口外的记录
        self.redis.zremrangebyscore(key, 0, now - self.window_sec)

        # 统计窗口内的请求数
        count = self.redis.zcard(key)
        if count >= self.max_requests:
            return False

        # 记录本次请求
        self.redis.zadd(key, {str(now): now})
        self.redis.expire(key, self.window_sec + 10)
        return True

# 方案 B: 令牌桶（适合允许突发流量的场景）
class TokenBucket:
    def __init__(self, capacity=10, refill_rate=1.0):  # 每秒恢复 1 个
        self.capacity = capacity
        self.tokens = capacity
        self.refill_rate = refill_rate
        self.last_refill = time.monotonic()

    def consume(self) -> bool:
        # 先补充令牌
        now = time.monotonic()
        elapsed = now - self.last_refill
        self.tokens = min(self.capacity, self.tokens + elapsed * self.refill_rate)
        self.last_refill = now

        if self.tokens >= 1:
            self.tokens -= 1
            return True
        return False  # 限流！
```

### 4.5 分级服务控制（VIP 不限，普通用户限制）

```python
# 和项目的角色分级路由配合
RATE_LIMIT_CONFIG = {
    "vip":    {"max_llm_calls": 50,  "max_tool_calls": 100, "rate_per_min": 30},
    "normal": {"max_llm_calls": 10,  "max_tool_calls": 30,  "rate_per_min": 5},
    "guest":  {"max_llm_calls": 3,   "max_tool_calls": 10,  "rate_per_min": 2},
}

def get_limits(role: str) -> dict:
    return RATE_LIMIT_CONFIG.get(role, RATE_LIMIT_CONFIG["guest"])
```

### 4.6 小模型先行（省大模型 Token）

```python
# 用便宜模型做预筛选，只让大模型处理关键步骤
# 这是单次调用成本降低最直接的方法

cheap_llm = init_chat_model("qwen-turbo")  # ~0.3 元/百万 token
expensive_llm = init_chat_model("deepseek-v4")  # ~2 元/百万 token

async def smart_route(query: str) -> str:
    # 第一步: 便宜模型判断意图
    intent = await cheap_llm.ainvoke(f"判断意图: {query}")
    if "闲聊" in intent:
        return await cheap_llm.ainvoke(query)  # 闲聊用便宜模型
    else:
        return await expensive_llm.ainvoke(query)  # 投研用贵模型

# 成本降低:
#   100 次闲聊调用: 小模型 0.03 元 vs 大模型 0.2 元 → 省 85%
#   20 次投研调用: 大模型 0.04 元 ← 不省
#   综合: 省 ~70% 闲聊流量
```

### 4.7 成本监控（必须做的）

```python
# 每次 LLM 调用后记录 Token 消耗
import logging

class CostTracker:
    def __init__(self):
        self.total_input_tokens = 0
        self.total_output_tokens = 0
        self.total_cost = 0.0
        # DeepSeek v4 官方定价
        self.price_input_per_m = 2.0   # 2 元/百万 input token
        self.price_output_per_m = 8.0  # 8 元/百万 output token

    def record(self, usage: dict):
        input_tokens = usage.get("input_tokens", 0)
        output_tokens = usage.get("output_tokens", 0)
        self.total_input_tokens += input_tokens
        self.total_output_tokens += output_tokens
        cost = (input_tokens / 1e6 * self.price_input_per_m
              + output_tokens / 1e6 * self.price_output_per_m)
        self.total_cost += cost

        # 超过阈值告警
        if self.total_cost > 1.0:  # 单次会话超过 1 元
            logging.warning(f"会话成本过高: {self.total_cost:.2f} 元")

    def summary(self) -> str:
        return (
            f"Token 消耗: input={self.total_input_tokens}, "
            f"output={self.total_output_tokens}, "
            f"费用={self.total_cost:.4f} 元"
        )
```

---

## 五、面试速记

**Q: Agent 的错误处理怎么做？**
A: 六层。第一层单步降级（主路径失败走 fallback），第二层工具隔离（return_exceptions 单个失败不阻塞），第三层循环保护（retry_count 硬限制），第四层人工兜底（搞不定就转人工），第五层外部依赖降级（Langfuse/数据库挂了不影响主流程），第六层流式错误通道（SSE 响应头发出后不能 HTTP 500，走事件通道通知前端）。

**Q: Agent 中途崩了怎么恢复？**
A: 三种。断点续跑（同一个 thread_id，LangGraph 从上一个 checkpoint 自动恢复）、手动修正后继续（aupdate_state 改掉错误数据后继续）、回退重试（as_of 回到历史 checkpoint 重新执行）。

**Q: Agent 怎么控制成本不烧钱？**
A: 五层。Token 预算制（每次调用/每轮/每会话设上限，超了就拒绝）、调用次数硬限制（LLM 最多 20 次/工具最多 50 次，直接抛异常或走 fallback）、用户级限流（Redis 滑动窗口/令牌桶，VIP 放宽限制普通用户收紧）、小模型先行（闲聊/意图识别用便宜模型，只有投研分析用贵模型）、成本实时追踪（每次调用后累加 Token，超过阈值告警，月末生成账单）。

**Q: 缓存能省多少成本？**
A: 精确匹配缓存命中时零成本（不调 LLM）。语义缓存命中时 0.001 元（一次 embedding 调用）vs LLM 调用 0.01-0.05 元，省 10-50 倍。FAQ/客服场景缓存命中率 60-80%，能省一半以上。投研场景不适合语义缓存（问题太长、表述差异大），适合模板缓存（行情填充）和 embedding 缓存（文档向量）。
