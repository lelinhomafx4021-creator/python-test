# 38_Skills技能编排详解：Pydantic I/O 模式 + 结构化数据到 LLM 的桥接

> **核心目标**: 理解 Skill 设计模式的精髓——Pydantic 做输入输出约束 + `to_prompt_context()` 桥接 LLM
> **项目代码**: `aipy2/app/skills/stock_analysis_skill.py`

---

## 一、Skill 模式的三个核心价值

```
Tool 层 (原子操作):    查一只股票价格 → 返回 dict
Skill 层 (业务编排):    分析一只股票 → 编排多个 Tool + 格式化输出 + 审计追踪
Node 层 (图节点):      调 Skill → 拿结果 → 更新 State → 决定下一步
```

**Skill 的本质不是"多调几个 Tool"，而是三个设计决策**：

| 决策 | 做法 | 价值 |
|------|------|------|
| **输入约束** | Pydantic BaseModel 做 DTO | 调用方不会传错参数（类比 Java 的 `@Valid`） |
| **输出约束** | Pydantic BaseModel 做 VO | 调用方知道返回什么字段，IDE 有自动补全 |
| **格式化桥接** | `to_prompt_context()` | 把结构化数据转成 LLM 能读的文本，解耦数据层和 Prompt 层 |

---

## 二、输入模型：`Field(...)` vs `Field(default)` 的严格区分

```python
# aipy2/app/skills/stock_analysis_skill.py
from pydantic import BaseModel, Field

class StockAnalysisSkillInput(BaseModel):
    # Field(...) — 三个点 = 必填，不传直接报 ValidationError
    query: str = Field(
        ...,                           # ← 必填标记
        min_length=1,                  # 不能空字符串
        max_length=100,                # 防超长输入
        description="用户原始问题"      # 文档和 JSON Schema 的字段描述
    )

    # Field(default_factory=list) — 有默认值 = 可选
    queries: list[str] = Field(
        default_factory=list,          # ← 不传就是空列表
        description="改写后的搜索词"
    )

    # Field(default=3, ge=1, le=8) — 有默认值 + 数值范围约束
    top_k: int = Field(
        default=3,                     # ← 不传就是 3
        ge=1,                          # 最小 1（greater or equal）
        le=8,                          # 最大 8（less or equal）
        description="召回条数"
    )

    # Field(default=True) — 布尔开关
    require_quote: bool = Field(
        default=True,
        description="是否强制获取实时行情"
    )
```

**`Field(...)` 和 `Field(default=xxx)` 的区别不只在"必填/可选"——在生成的 JSON Schema 里，`...` 会把字段放入 `required` 数组，有默认值的不会。**

```python
# 验证：必填字段不传直接报错
try:
    StockAnalysisSkillInput()  # 没传 query
except ValidationError as e:
    print(e)
    # 1 validation error: query Field required

# 可选字段不传用默认值
input_obj = StockAnalysisSkillInput(query="茅台怎么样")
print(input_obj.top_k)          # 3（默认值）
print(input_obj.queries)        # []（默认空列表）
print(input_obj.require_quote)  # True（默认开启）
```

---

## 三、输出模型：`to_prompt_context()` 的设计模式

这是 Skill 模式最巧妙的地方——**结构化数据（Python 对象）和 LLM 输入（纯文本）之间的桥接**。

```python
class StockAnalysisSkillOutput(BaseModel):
    knowledge: str                         # 检索到的文本资料
    evidence: list[str] = Field(default_factory=list)  # 证据清单
    quote: dict | None = None              # 实时行情
    symbol: str | None = None              # 识别的股票代码

    def to_prompt_context(self) -> str:
        """把结构化结果转成 LLM prompt 可以直接使用的文本块。"""
        quote_text = "无实时行情"
        if self.quote:
            quote_text = json.dumps(self.quote, ensure_ascii=False)

        evidence_text = "\n".join(
            f"- {item}" for item in self.evidence
        ) if self.evidence else "- 无"

        return (
            f"【检索知识】\n{self.knowledge or '无'}\n\n"
            f"【实时行情】\n{quote_text}\n\n"
            f"【证据清单】\n{evidence_text}"
        )
```

**为什么这个设计是亮点**：

```
没有 to_prompt_context():
  answer_node 需要知道 StockAnalysisSkillOutput 的内部结构
  → 耦合：改 Skill 输出字段，answer_node 也要改

有 to_prompt_context():
  answer_node 只需要 skill_result.to_prompt_context()
  → 解耦：Skill 内部怎么变，answer_node 不用管
  → 单一职责：格式化逻辑属于 Skill，不属于 answer_node
```

**在上游的使用**：

```python
# aipy2/app/graph/nodes.py — search_node
skill_result = await stock_analysis_skill.run(input)

return {
    "knowledge": skill_result.knowledge,          # 原始文本，供 critic 核查
    "skill_context": skill_result.to_prompt_context(),  # 格式化文本，喂给 answer_node
}
```

---

## 四、Skill 引擎：编排逻辑 + 错误隔离

```python
class StockAnalysisSkill:
    async def run(self, payload: StockAnalysisSkillInput) -> StockAnalysisSkillOutput:
        # Step 1: 合并搜索词（防原始 query 不在列表里）
        merged = [q.strip() for q in payload.queries if q.strip()]
        if payload.query.strip() not in merged:
            merged.append(payload.query.strip())

        # Step 2: 异步检索（auto 模式 = 先本地再联网）
        knowledge = await run_retrieval_async(
            queries=merged, mode="auto", top_k=payload.top_k
        )

        # Step 3: 条件获取行情（有股票代码 + require_quote=True 才调）
        symbol = extract_stock_code(payload.query)
        quote = None
        if symbol and payload.require_quote:
            quote = self._get_quote_safe(symbol)  # 失败返回 None

        # Step 4: 组装证据（审计追踪用）
        evidence = self._build_evidence(merged, symbol, bool(quote))

        return StockAnalysisSkillOutput(
            knowledge=knowledge, evidence=evidence,
            quote=quote, symbol=symbol
        )

    def _get_quote_safe(self, symbol: str) -> dict | None:
        """行情失败不拖垮主流程。"""
        try:
            raw = get_stock_quote_core.invoke({"symbol": symbol})
            return json.loads(raw)
        except Exception:
            return None
```

**三个关键设计决策**：

1. **`_get_quote_safe` 返回 None 而非抛异常**：行情 API 是外部依赖，不可靠。挂了用知识库内容照样回答，用户感知不到。
2. **`require_quote` 参数控制**：调用方可以选择不要行情（节省一次 API 调用）。灵活性在调用方，不在 Skill 内部硬编码。
3. **`evidence` 审计追踪**：记录实际检索了哪些词、是否命中行情。出问题时能回溯。

---

## 五、Skill 模式适用场景判断

```
用 Skill 模式：
  ✅ 需要编排 2 个以上 Tool
  ✅ 输入/输出有明确的业务含义（不只是 dict）
  ✅ 需要格式化输出喂给 LLM
  ✅ 需要审计追踪（evidence）

直接用 Tool：
  ✅ 原子操作（搜一次、查一次价格）
  ✅ 输入/输出就是简单的 str → str
  ✅ 不需要编排逻辑
```

---

## 六、面试加分点

**"你的 Skill 设计有什么亮点？"**

> 三个亮点。第一，Pydantic 做 I/O 约束——输入有 `Field(ge=1, le=8)` 防非法参数，输出有类型提示 IDE 友好。第二，`to_prompt_context()` 桥接模式——Skill 内部结构化数据怎么变，answer_node 不需要改，解耦数据层和 Prompt 层。第三，错误隔离——行情挂了返回 None，不影响检索结果继续回答，用户无感知。

**"为什么不用 dict 传参？"**

> dict 没有类型检查——传错字段名、传错类型、漏传必填项都只能在运行时发现。Pydantic 在调用时就会报 `ValidationError`，问题早发现。而且 IDE 有自动补全，团队协作不会传错参数。

---

## 项目代码索引

| 文件 | 核心内容 |
|------|---------|
| `aipy2/app/skills/stock_analysis_skill.py` | StockAnalysisSkillInput/Output/StockAnalysisSkill |
| `aipy2/app/graph/nodes.py` | search_node 中调用 Skill + to_prompt_context() |
| `aipy2/app/tools/retriever_tool.py` | Skill 内部调用的检索 Tool |
