# 43_Agent评估与评测：怎么知道你的 Agent 好不好

> **核心目标**: 掌握 RAG 和 Agent 的评估方法——面试必问"你怎么衡量 Agent 的质量"
> **为什么重要**: 89% 的企业有可观测性，但评估是最大挑战。代码能跑≠Agent 好用。
> **项目关联**: 项目用了 Langfuse 做追踪，但目前没有系统化的评估体系。这是下一步要补的。

---

## 一、为什么需要专门的评估

### 1.1 LLM 应用和传统软件的测试完全不同

```
传统软件测试：
  输入 2+3 → 期望输出 5 → 断言 assert result == 5
  ✅ 对就是对，错就是错

Agent 测试：
  输入 "茅台值得投资吗" → Agent 输出一份分析报告
  怎么判断这份报告好不好？
  - 数据准确吗？
  - 推理有逻辑吗？
  - 有编造事实吗？
  - 遗漏了关键信息吗？
  ❌ 没有"标准答案"，不能用 assert result == expected
```

### 1.2 2026 年业界现状

根据 LangChain 2025 年调研（1340 份问卷）：
- 89% 的企业有某种形式的 Agent 可观测性
- 62% 有详细 tracing（能看到每一步）
- **但只有不到 30% 有系统化的评估体系**

**"能跑"和"能放心用"之间的差距，就是评估。**

---

## 二、评估什么：三个层次

```
层次 1: 检索质量 — 找到的东西对不对？
  指标：命中率、MRR（平均倒数排名）、NDCG

层次 2: 生成质量 — 回答的内容好不好？
  指标：忠实度（有没有编造）、相关性（有没有跑题）

层次 3: Agent 行为 — 决策过程对不对？
  指标：工具选择准确率、步骤数是否合理、是否有死循环
```

---

## 三、层次 1：检索质量评估

### 3.1 核心指标

| 指标 | 含义 | 怎么算 | 好坏的判断 |
|------|------|--------|-----------|
| **Hit Rate（命中率）** | 检索到的文档里有没有正确答案 | 有答案的查询数 / 总查询数 | > 80% 算合格 |
| **MRR（平均倒数排名）** | 第一个正确答案排在第几位 | Σ(1/排名) / 查询数 | 越接近 1 越好 |
| **NDCG** | 排名质量（考虑了位置权重） | 复杂公式 | > 0.7 算好 |

### 3.2 项目怎么测

```python
# 准备测试集：query → 期望的 doc_id 列表
test_queries = [
    {"query": "茅台2024年净利润", "relevant_docs": ["doc_001", "doc_015"]},
    {"query": "白酒行业市场规模",   "relevant_docs": ["doc_023", "doc_041"]},
    # ... 至少 50 条
]

# 跑检索，算命中率
hits = 0
for item in test_queries:
    results = vector_store.search(item["query"], top_k=5)
    retrieved_ids = [r.get("source") for r in results]
    if any(doc_id in retrieved_ids for doc_id in item["relevant_docs"]):
        hits += 1

hit_rate = hits / len(test_queries)
print(f"命中率: {hit_rate:.1%}")
```

**面试话术**："我们通过标注测试集来评估检索质量。每条 query 标记了相关文档 ID，然后算命中率和 MRR。目前命中率在 85% 左右，改进方向是混合检索 + Reranking。"

---

## 四、层次 2：生成质量评估（RAGAS 框架）

### 4.1 RAGAS 四个核心指标

[RAGAS](https://docs.ragas.io/) 是业界最常用的 RAG 评估框架。它定义了四个指标：

```
                    ┌─────────────────┐
用户问题 ──────────→│    检索系统      │──────────→ 检索到的上下文
                    └─────────────────┘
                            │
                            ▼
                    ┌─────────────────┐
                    │      LLM        │──────────→ 生成的回答
                    └─────────────────┘

RAGAS 用另一个 LLM（评估 LLM）来判断质量：

① Context Precision（上下文精确度）
  问题：检索到的上下文和问题相关吗？
  判断："检索到的 5 个文档块中，有几个真正和问题有关？"

② Context Recall（上下文召回率）
  问题：该检索到的都检索到了吗？
  判断："标准答案里提到的信息，检索结果里都有吗？"

③ Faithfulness（忠实度）
  问题：回答里有没有编造的内容？
  判断："回答里的每句话，都能在检索到的上下文中找到依据吗？"

④ Answer Relevancy（答案相关性）
  问题：回答有没有跑题？
  判断："回答真正回答了用户的问题吗？有没有说一堆无关的？"
```

### 4.2 RAGAS 代码示例

```python
# pip install ragas
from ragas import evaluate
from ragas.metrics import faithfulness, answer_relevancy, context_precision, context_recall
from datasets import Dataset

# 准备评估数据
eval_data = Dataset.from_dict({
    "question": ["茅台2024年净利润是多少？"],
    "answer": ["贵州茅台2024年净利润约为747亿元，同比增长15.2%..."],
    "contexts": [["贵州茅台2024年报显示...", "白酒行业整体增长..."]],
    "ground_truth": ["2024年茅台净利润747亿元，同比增长15.2%"]
})

# 一键评估
result = evaluate(
    eval_data,
    metrics=[faithfulness, answer_relevancy, context_precision, context_recall]
)
print(result)
# {'faithfulness': 0.92, 'answer_relevancy': 0.87, 'context_precision': 0.85, ...}
```

### 4.3 面试话术

"我们用 RAGAS 做生成质量评估。四个指标：上下文精确度看检索回来的东西是否相关，召回率看该检索的有没有漏，忠实度看回答有没有编造事实，相关性看有没有跑题。每次改 Prompt 或改检索策略后跑一遍 RAGAS，用数据说话而不是凭感觉。"

---

## 五、层次 3：Agent 行为评估

### 5.1 Agent 特有的评估维度

| 维度 | 评估什么 | 怎么测 |
|------|---------|--------|
| **工具选择准确率** | LLM 是否选了正确的工具 | 准备测试集，对比期望的工具调用和实际的工具调用 |
| **步骤效率** | Agent 用了多少步完成任务 | 统计平均步骤数。步骤过多 = 在绕圈 |
| **终止率** | Agent 是否正常结束 | 统计成功完成的比例。没终止 = 死循环 |
| **critic 准确率** | critic 的判断是否可信 | 人工标注 pass/fail 后对比 critic 的判断 |

### 5.2 工具选择准确率示例

```python
test_cases = [
    {"query": "茅台股价", "expected_tools": ["get_stock_quote"]},
    {"query": "你好",       "expected_tools": []},              # 应该不调工具
    {"query": "白酒行业分析", "expected_tools": ["search_intelligent"]},
]

correct = 0
for case in test_cases:
    # 跑 Agent，记录实际调了哪些工具
    actual_tools = run_agent_and_track_tools(case["query"])
    if set(actual_tools) == set(case["expected_tools"]):
        correct += 1

print(f"工具选择准确率: {correct}/{len(test_cases)}")
```

---

## 六、评估落地：怎么集成到项目里

### 6.1 项目现在的状态

```
✅ 有 Langfuse — 能看 trace，知道 Agent 走了哪几步
✅ 有 Langfuse generation — 能记录最终答案
❌ 没有评估 — 不知道答案质量好不好
❌ 没有测试集 — 改 Prompt 后只能手动试几条
```

### 6.2 最小可行的评估方案

```python
# 加入项目的评估脚本：aipy2/scripts/eval_rag.py
# 不做完整的 CI/CD 集成，先做到 "改完 Prompt 跑一遍看分数"

import json
from ragas import evaluate
from ragas.metrics import faithfulness, answer_relevancy
from datasets import Dataset

# 1. 加载测试集
with open("tests/eval_queries.json") as f:
    test_data = json.load(f)

# 2. 对每条 query 跑 Agent，收集 answer 和 context
results = []
for item in test_data:
    answer, contexts = run_agent(item["question"])  # 调你的 Agent
    results.append({
        "question": item["question"],
        "answer": answer,
        "contexts": contexts,
        "ground_truth": item.get("ground_truth", "")
    })

# 3. RAGAS 评估
dataset = Dataset.from_list(results)
scores = evaluate(dataset, metrics=[faithfulness, answer_relevancy])
print(scores)
# {'faithfulness': 0.92, 'answer_relevancy': 0.87, 'context_precision': 0.85, ...}

# 4. 对比上次评估结果
with open("baseline_scores.json") as f:
    baseline = json.load(f)

for metric, score in scores.items():
    old = baseline.get(metric, 0)
    delta = score - old
    emoji = "📈" if delta > 0 else "📉"
    print(f"{metric}: {score:.3f} ({emoji} {delta:+.3f} vs baseline)")
```

### 6.3 完整的可运行评估脚本

```python
"""
scripts/eval_agent.py — 一行命令跑完评估
用法: python eval_agent.py --test-set tests/eval_queries.json
"""

import json
import asyncio
import argparse
from ragas import evaluate
from ragas.metrics import faithfulness, answer_relevancy, context_precision, context_recall
from datasets import Dataset

# 导入你的 Agent
from app.graph.investor_graph import build_self_rag_graph
from app.core.llm import get_llm

async def run_agent_and_collect(question: str) -> dict:
    """跑一次 Agent，收集回答 + 检索到的上下文"""
    graph = build_self_rag_graph(role="vip")
    config = {"configurable": {"thread_id": f"eval_{hash(question)}"}}

    # 跑 Agent
    result = await graph.ainvoke(
        {"messages": [{"role": "user", "content": question}]},
        config
    )

    # 找到最终回答
    messages = result.get("messages", [])
    answer = ""
    for m in reversed(messages):
        if hasattr(m, "content") and m.type == "ai":
            answer = m.content
            break

    # 从 State 里拿到检索上下文
    contexts = result.get("knowledge") or result.get("skill_context") or ""

    return {
        "question": question,
        "answer": answer,
        "contexts": [contexts] if isinstance(contexts, str) else contexts,
    }

async def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--test-set", required=True, help="测试集 JSON 文件")
    parser.add_argument("--output", default="eval_results.json")
    args = parser.parse_args()

    # 加载测试集
    with open(args.test_set) as f:
        test_data = json.load(f)

    print(f"评估 {len(test_data)} 条测试用例...")

    # 跑每一条
    results = []
    for i, item in enumerate(test_data):
        print(f"  [{i+1}/{len(test_data)}] {item['question'][:50]}...")
        result = await run_agent_and_collect(item["question"])
        result["ground_truth"] = item.get("ground_truth", "")
        results.append(result)

    # RAGAS 评估
    dataset = Dataset.from_list(results)
    scores = evaluate(dataset, metrics=[
        faithfulness,
        answer_relevancy,
        context_precision,
        context_recall,
    ])
    print(f"\n评估结果:")
    for metric, score in scores.items():
        print(f"  {metric}: {score:.3f}")

    # 保存结果
    with open(args.output, "w") as f:
        json.dump({"scores": scores, "details": results}, f, ensure_ascii=False, indent=2)
    print(f"结果已保存到 {args.output}")

if __name__ == "__main__":
    asyncio.run(main())
```

### 6.4 测试集格式

```json
// tests/eval_queries.json
[
    {
        "question": "茅台2024年净利润是多少？",
        "ground_truth": "747亿元，同比增长15.2%"
    },
    {
        "question": "五粮液和茅台谁市值更大？",
        "ground_truth": "茅台市值约2.3万亿，五粮液约7000亿"
    },
    {
        "question": "白酒行业现在景气吗？",
        "ground_truth": ""
    }
]
// ground_truth 可以空（开放式问题），RAGAS 的 faithfulness 不需要 ground_truth
```

---

## 七、面试速记

**Q: 你怎么评估 Agent 好不好？**
A: 三个层次。检索层用命中率和 MRR 测检索质量。生成层用 RAGAS 测忠实度（有没有编造）和相关性（有没有跑题）。行为层用工具选择准确率和终止率测 Agent 决策对不对。每个层次有对应的测试集，改代码后跑一遍看分数。

**Q: 没有标准答案的开放式问题怎么评估？**
A: 用 LLM-as-Judge。另一个 LLM（评估 LLM）来判断回答质量——忠实度看回答是否有上下文依据，相关性看是否真正回答了问题。RAGAS 就是这么做的。LLM 评 LLM 不完美但可用，关键是评估用的 Prompt 要写好评估标准。

**Q: 改了一个 Prompt，怎么知道改好了还是改坏了？**
A: 准备 30-50 条固定的测试 query，跑 RAGAS 拿到改之前的分数作为 baseline。改 Prompt 后再跑一遍，对比四个指标的变化。如果忠实度降了，说明新 Prompt 可能诱导 LLM 编造。数据驱动，不凭感觉。

**Q: 你们项目怎么做评估的？**
A: 目前有 Langfuse 做全链路追踪——能看到 Agent 每一步的耗时和 Token。评估体系正在搭建——计划先用 RAGAS 覆盖生成质量，再补检索质量的标注测试集。评估是生产部署前必须完成的一步。
