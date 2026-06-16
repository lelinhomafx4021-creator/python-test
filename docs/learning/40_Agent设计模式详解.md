# 40_Agent设计模式：项目 4 个实战模式 + 选型决策框架

> **核心目标**: 能说清楚项目为什么用这 4 个模式，什么场景换什么模式
> **面试价值**: 设计模式的"为什么选 A 不选 B"是区分中高级工程师的关键

---

## 一、模式 1：Self-RAG（项目核心）

### 1.1 图结构

```
intent → rewrite → [路由] → fetch_data 或 search → answer → critic
           ↑                                                    |
           └──────────────── 不通过，打回 ─────────────────────┘
                                      (最多 3 次)
```

### 1.2 为什么 critic 必须独立

```
单 LLM 自检:
  answer("茅台值得买") → 同一个 LLM 审自己 → "没问题，通过"
  问题: LLM 看不出自己的盲区。就像一个作者给自己的文章打分。

独立 critic:
  answer("茅台值得买") → 另一个 LLM + 独立 Prompt(temperature=0) 审稿
  → "结论缺少财报数据支撑 → fail"
  优势: 不同视角，专门找问题。
```

**面试话术**："同一个 LLM 既写答案又审答案，就像自己给自己的考卷打分。独立 LLM + 独立 Prompt + temperature=0 做 critic，模拟的是'一人写、一人审'的四眼原则。"

### 1.4 Self-RAG 的进化：Agentic RAG 三种变体

Self-RAG 审的是**回答**质量。但检索本身也可能出问题——Agentic RAG 在检索环节也加了判断：

```
Self-RAG（我们）:  检索 → 回答 → critic 审回答 → 不通过则重写
CRAG:             检索 → grader 审检索质量 → 不相关则补联网搜索 → 回答
Adaptive RAG:     router 判断问题复杂度 → 简单直接答 / 中等普通RAG / 复杂多轮检索
```

| 模式 | 审什么 | 什么时候审 | 项目用了吗 |
|------|--------|-----------|-----------|
| Self-RAG | 回答质量 | 回答之后 | ✅ critic 节点 |
| CRAG | 检索质量 | 检索之后、回答之前 | ❌ 下一步可加 |
| Adaptive RAG | 问题复杂度 | 检索之前 | ✅ route_intent 简化版 |

**面试话术**："Self-RAG 审回答，CRAG 审检索。我们目前有 Self-RAG——critic 节点独立评审回答。下一步可以在 search 之后加 retrieval_grader，检索结果不好直接补联网搜索，不等 critic 才发现问题。"

### 1.3 防死循环：retry_count 的三层保护

```python
# aipy2/app/graph/nodes.py — critic_node
new_retry = state.get("retry_count", 0) + (1 if status == "fail" else 0)

# 第 1-2 次失败 → 打回 rewrite，带上 feedback
if status == "fail" and new_retry < 3:
    return {"critic_feedback": reason, "retry_count": new_retry}

# 第 3 次失败 → 不再打回，转人工兜底
if status == "fail" and new_retry >= 3:
    return {"handoff_to_human": True, "handoff_summary": ...}
```

**三层保护**：
1. `retry_count` 计数器硬限制
2. 到达上限后不是静默失败，而是生成交接摘要转人工
3. `temperature=0` 保证 critic 每次判断一致（不会同一份答案一次 pass 一次 fail）

---

## 二、模式 2：角色分级路由

### 2.1 设计

```
同一套节点函数，不同 role → 不同的图拓扑：

build_self_rag_graph(role="normal"):
  START → intent → lite_rewrite → search → answer(lite) → END
  - 无 critic（省 Token）
  - lite_rewrite 跳过 LLM 改写（省一次调用）
  - answer 用 ANSWER_PROMPT_LITE（禁止买卖建议）

build_self_rag_graph(role="vip"):
  START → intent → rewrite → [并行|串行] → answer(deep) → critic → END
                                                          ↑_______|
  - 完整 Self-RAG 闭环
  - 并行数据获取（asyncio.gather 五路）
  - answer 用 ANSWER_PROMPT（深度分析）
```

### 2.2 为什么不用动态路由（同一个图里用 if-else）

```python
# 做法 A: 同一张图里用条件边切换（复杂，容易出 bug）
if role == "vip":
    workflow.add_node("critic", critic_node)
    workflow.add_conditional_edges("critic", ...)
else:
    workflow.add_edge("answer", END)

# 做法 B: 编译时根据 role 构建不同的图（简单，每种角色图独立）
# 项目选 B。两张独立编译的图，互不干扰。
graph_normal = build_self_rag_graph("normal")
graph_vip = build_self_rag_graph("vip")
```

**选 B 的理由**：图拓扑差异大（普通 5 节点 vs VIP 7 节点 + 闭环），放一张图里条件边爆炸。独立图更清晰、更好测试、出问题影响范围小。

### 2.3 面试话术

"不是写两套代码，是同一套节点函数 + 同一个 State + 编译时根据 role 选图拓扑。这叫'一套能力，分级服务'——能力是复用的，服务等级是可配的。"

---

## 三、模式 3：节点内并行（asyncio.gather）

### 3.1 为什么不在图层面做并行（add_edge(START, "a") + add_edge(START, "b")）

```python
# 图层面并行：LangGraph 的 fan-out 机制
workflow.add_edge(START, "fetch_market")
workflow.add_edge(START, "fetch_financial")
# 两个节点同时跑，但它们是"独立步骤"，不是"同一数据获取步骤的子任务"

# 节点内并行：asyncio.gather（项目做法）
async def fetch_data_node(state):
    results = await asyncio.gather(
        fetch_market_data(...),
        fetch_financial_data(...),
        fetch_announcements(...),
        fetch_news_data(...),
        fetch_retrieval_data(...),
        return_exceptions=True,
    )
```

**选节点内并行的理由**：五个数据源是语义上的"一件事"（获取数据），不是五个独立步骤。放在一个节点里，概念清晰、错误处理统一、结果合并简单。

### 3.2 性能收益

```
串行: 行情 200ms + 财务 300ms + 公告 150ms + 新闻 400ms + 检索 500ms = 1550ms
并行: max(200, 300, 150, 400, 500) ≈ 500ms  → 减少 68%
```

---

## 四、模式 4：渐进式人工兜底

### 4.1 两种触发路径

```
路径 1: 用户主动要求
  "转人工" / "人工客服" → _wants_human_handoff() 关键词检测
  → 不走 LLM，直接路由到 handoff_node

路径 2: AI 多次失败后自动转
  critic 打回 3 次 → retry_count >= 3
  → handoff_node 生成包含上下文摘要的交接信息
```

### 4.2 交接摘要的设计

```python
# aipy2/app/graph/state.py — _build_handoff_summary
def _build_handoff_summary(state, reason):
    return (
        f"用户问题：{query}\n"
        f"转人工原因：{reason}\n"
        f"当前重试次数：{retry_count}\n"
        f"评审反馈：{critic_feedback}"
    )
```

**人工客服看到的不只是"用户问什么"，还有"AI 已经试了什么、为什么失败"——不用让用户重复描述问题。**

---

## 五、选型决策框架（面试加分）

```
问题：这个场景用什么 Agent 模式？

Step 1: 流程是确定的还是开放的？
  确定（投研查询固定步骤）→ 手写 StateGraph
  开放（"帮我做一份行业报告"）→ Plan-and-Execute 或 Deep Agents

Step 2: 需要质量保证吗？
  需要 → 加 Self-RAG（独立 critic 闭环）
  不需要 → ReAct 标准循环

Step 3: 不同用户有不同需求吗？
  有 → 角色分级路由（编译时选图拓扑）
  没有 → 单图

Step 4: 数据获取有依赖关系吗？
  有依赖（A 的结果决定 B 是否执行）→ 串行
  无依赖 → asyncio.gather 并行

Step 5: 需要有失败兜底吗？
  需要 → 渐进式人工兜底（retry_count + handoff）
  不需要 → 直接报错
```

---

## 六、面试速记

**Q: 你的项目用了哪些 Agent 设计模式？为什么选它们？**
A: 四个。Self-RAG——投研需要质量保证，独立 critic 比单 LLM 自检可靠。角色分级路由——VIP 和普通用户需求不同，独立图拓扑比一张图里 if-else 清晰。节点内并行——数据源之间无依赖，并行减少 68% 延迟。渐进式人工兜底——AI 也有搞不定的时候，自动转人工比让用户干等强。

**Q: 为什么不用 Multi-Agent？**
A: 投研步骤固定——意图→检索→回答→评审，不需要多个 Agent 动态协商。如果将来需要并行分析技术面和基本面，可以在现有架构上扩展（加两个 answer_node 并行 → merge_node 合并）。架构预留了扩展点。

**Q: 如果 critic 和 answer 意见不一致怎么解决？**
A: critic 说了算——它只审稿不写稿，客观性更高。但如果 3 次都过不了，不是继续拉扯，而是转人工。这是生产环境的关键设计——不给 AI 无限试错的空间。

**Q: 并行获取中一个数据源失败怎么处理？**
A: `return_exceptions=True`——单个失败不抛异常，返回 None 继续。比如行情 API 超时 → market_data 为 None → answer 只用检索结果回答。用户不知道行情挂了，体验不受影响。
