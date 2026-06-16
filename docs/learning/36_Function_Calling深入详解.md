# 36_Function Calling深入详解：从 @tool 注解到 LLM 工具调用的完整链路

> **核心目标**: 理解 @tool 底层怎么把 Python 函数变成 LLM 认识的 JSON Schema，LLM 怎么"调用"工具
> **项目关联**: 项目用显式调用（代码直接调工具），但 Function Calling 原理是所有 Agent 框架的基石

---

## 一、Function Calling 的本质

**LLM 本身不能执行代码。它只能输出一段 JSON 说"我想调用这个函数，传这些参数"。真正执行的是你的应用代码。**

```
用户: "北京今天天气怎么样？"
        │
① 你的代码：
   把工具定义（JSON Schema）放在 API 请求的 tools 参数里发给 LLM
        │
② LLM 分析用户意图：
   "用户想知道天气，我应该调用 get_weather 工具"
   但它不执行任何代码，只是输出一段 JSON：
   {
     "tool_calls": [{
       "id": "call_abc123",
       "type": "function",
       "function": {
         "name": "get_weather",
         "arguments": "{\"city\": \"北京\"}"
       }
     }]
   }
        │
③ 你的代码收到这个 JSON：
   解析 → 发现 LLM 想调 get_weather("北京")
   真正执行: result = get_weather("北京")  → "北京晴天，25°C"
        │
④ 把执行结果包装成 ToolMessage 发回给 LLM：
   ToolMessage(content="北京晴天，25°C", tool_call_id="call_abc123")
        │
⑤ LLM 收到结果后生成最终回答：
   "北京今天晴天，气温25°C。"
```

**核心认知**：Function Calling 不是 LLM 在执行函数。LLM 是"点菜的食客"——它说我要什么，你的代码是"厨房"——真正去做。

---

## 二、@tool 装饰器的底层原理

### 2.0 @tool 参数一览

```python
from langchain_core.tools import tool

@tool(
    name_or_callable=None,       # ① 函数或工具名称
    return_direct=False,         # ② True=LLM 调这个工具后直接返回，不再思考
    args_schema=None,            # ③ 手动指定 Pydantic 参数模型
    infer_schema=True,           # ④ 自动从函数签名推断 Schema
    response_format="content",   # ⑤ 工具返回格式
    parse_docstring=False,       # ⑥ 从 docstring 解析参数描述
    error_on_invalid_docstring=False,  # ⑦ docstring 无效时是否报错
)
def my_tool(param1: str, param2: int = 10) -> str:
    """工具描述"""
    ...
```

| 参数 | 类型 | 默认 | 作用 |
|------|------|------|------|
| `name_or_callable` | `str \| Callable` | None | 工具名。不传=函数名 |
| `return_direct` | `bool` | False | True=调完直接返回给用户，不再思考。查询类工具用 |
| `args_schema` | `BaseModel` | None | 手动指定参数模型。自动推断不准时用 |
| `infer_schema` | `bool` | True | 从类型注解+docstring 自动生成 JSON Schema |
| `response_format` | `str` | `"content"` | `"content"`=返回字符串 / `"content_and_artifact"`=返回 (文本, 数据) 元组 |
| `parse_docstring` | `bool` | False | 从 docstring 提取参数描述 |
| `error_on_invalid_docstring` | `bool` | False | docstring 不规范时是否抛异常 |

### 2.1 从 Python 函数到 JSON Schema 的转换链

```python
from langchain_core.tools import tool

@tool
def get_weather(city: str, date: str = "today") -> str:
    """获取指定城市的天气信息。当用户询问天气时调用。

    Args:
        city: 城市名称，如 "北京"、"上海"
        date: 日期，默认为今天

    Returns:
        str: 天气描述
    """
    return f"{city} {date} 晴天，25°C"
```

**LangChain 在 `@tool` 装饰器内部做的事**：

#### 第一步：用 Python 反射提取函数元信息

```python
# LangChain 内部等价代码：
import inspect

func_name = get_weather.__name__              # → "get_weather"
func_doc = get_weather.__doc__                # → "获取指定城市的天气信息..."
sig = inspect.signature(get_weather)           # → <Signature (city: str, date: str = 'today')>

for name, param in sig.parameters.items():
    print(f"{name}: type={param.annotation}, default={param.default}")
# 输出:
# city: type=<class 'str'>, default=<class 'inspect._empty'>  ← 必填参数
# date: type=<class 'str'>, default='today'                   ← 可选参数，默认 "today"
```

#### 第二步：Python 类型 → JSON Schema 类型映射

```python
# 映射规则表：
# str          → {"type": "string"}
# int          → {"type": "integer"}
# float        → {"type": "number"}
# bool         → {"type": "boolean"}
# list[str]    → {"type": "array", "items": {"type": "string"}}
# dict         → {"type": "object"}
# Optional[X]  → 从 required 列表中移除，变成可选参数
# Literal[A,B] → {"enum": [A, B]}
```

**`inspect._empty`（没有默认值）→ 加入 `required` 数组**  
**有默认值 → 不加入 `required`，LLM 可以不传**

#### 第三步：生成完整的 JSON Schema（这就是发给 LLM 的内容）

```json
{
    "type": "function",
    "function": {
        "name": "get_weather",
        "description": "获取指定城市的天气信息。当用户询问天气时调用。",
        "parameters": {
            "type": "object",
            "properties": {
                "city": {
                    "type": "string",
                    "description": "城市名称，如 \"北京\"、\"上海\""
                },
                "date": {
                    "type": "string",
                    "description": "日期，默认为今天"
                }
            },
            "required": ["city"]
        }
    }
}
```

**关键细节**：
- `city` 在 `required` 里，因为 Python 侧没有默认值 → LLM 必须提供
- `date` 不在 `required` 里，因为 Python 侧有 `= "today"` → LLM 可以不传
- `description` 来自函数的 docstring（Args 部分）→ **不写 docstring，LLM 就不知道参数是什么意思**

### 2.2 bind_tools：把 Schema 发给 LLM

```python
from langchain_openai import ChatOpenAI

llm = ChatOpenAI(model="gpt-4")

# bind_tools 做的事：把工具的 JSON Schema 放在 API 请求的 tools 参数里
llm_with_tools = llm.bind_tools([get_weather, search_news])

# 等价于每次调用时都在 API 请求里附带：
# {
#   "model": "gpt-4",
#   "messages": [...],
#   "tools": [
#     {"type": "function", "function": {"name": "get_weather", ...}},
#     {"type": "function", "function": {"name": "search_news", ...}}
#   ]
# }

# 调用：
response = llm_with_tools.invoke("北京今天天气怎么样？")

# LLM 看到了 tools 列表，判断用户意图 → 决定调 get_weather
# response.content 可能是空的（因为 LLM 决定调工具而非直接回答）
# response.tool_calls 里有 LLM 决定的工具调用：
print(response.tool_calls)
# [
#   {
#     "name": "get_weather",
#     "args": {"city": "北京"},
#     "id": "call_abc123",
#     "type": "tool_call"
#   }
# ]
```

### 2.3 ToolMessage：把工具执行结果返回给 LLM

```python
from langchain_core.messages import ToolMessage, HumanMessage

# 假设你的代码执行了 get_weather("北京")，得到结果
weather_result = "北京今天晴天，25°C"

# 把结果包装成 ToolMessage（关键：tool_call_id 必须匹配）
tool_msg = ToolMessage(
    content=weather_result,
    tool_call_id="call_abc123",  # 必须和 LLM 返回的 tool_call.id 一致！
    name="get_weather"           # 工具名称
)

# 把 ToolMessage 追加到 messages 列表，再次发给 LLM
messages = [HumanMessage("北京天气？"), response, tool_msg]
final_response = llm.invoke(messages)
# LLM 收到工具结果，基于它生成最终回答：
# "北京今天晴天，气温25°C。"
```

**ToolMessage 的三个必填字段**：
| 字段 | 作用 | 为什么重要 |
|------|------|-----------|
| `content` | 工具的执行结果（字符串） | LLM 基于这个内容生成最终回答 |
| `tool_call_id` | 匹配 LLM 的 tool_call.id | **不匹配 LLM 会报错**——它要知道这个结果对应哪次调用 |
| `name` | 工具名称 | 帮助 LLM 理解这个结果来自哪个工具 |

### 2.4 tool_choice：控制 LLM 的工具调用行为

```python
# 方式1: "auto" — LLM 自主决定（默认）
llm.bind_tools(tools, tool_choice="auto")
# LLM 判断：该调工具就调，不该调就直接回答

# 方式2: "any" — 强制至少调一个工具
llm.bind_tools(tools, tool_choice="any")
# LLM 必须调用至少一个工具，即使觉得不需要

# 方式3: "required" — 强制调用工具（同 any）
llm.bind_tools(tools, tool_choice="required")

# 方式4: 指定工具 — 强制调某个特定工具
llm.bind_tools(tools, tool_choice="get_weather")
# LLM 只能调 get_weather，不能调别的

# 方式5: "none" — 禁止调用工具
llm.bind_tools(tools, tool_choice="none")
# LLM 不会调任何工具，即使 tools 列表不为空
```

---

## 三、并行工具调用

当 LLM 发现多个工具可以同时调用时（互不依赖），它会一次性返回多个 tool_call：

```python
@tool
def get_stock_price(symbol: str) -> str:
    """获取股票价格"""
    ...

@tool
def get_stock_news(symbol: str) -> str:
    """获取股票新闻"""
    ...

llm = ChatOpenAI(model="gpt-4").bind_tools([get_stock_price, get_stock_news])

response = llm.invoke("茅台和五粮液的股价和新闻")
# LLM 判断：两个工具可以同时调，互不依赖
print(response.tool_calls)
# [
#   {"name": "get_stock_price", "args": {"symbol": "600519"}, "id": "call_1"},
#   {"name": "get_stock_news",  "args": {"symbol": "600519"}, "id": "call_2"},
#   {"name": "get_stock_price", "args": {"symbol": "000858"}, "id": "call_3"},
#   {"name": "get_stock_news",  "args": {"symbol": "000858"}, "id": "call_4"},
# ]
# 4 个调用可以并行执行！
```

**并行调用的条件**：工具之间没有数据依赖。如果 Tool B 需要 Tool A 的输出作为输入，就不能并行。

---

## 四、StructuredTool：完全手动控制 Schema

当 @tool 装饰器自动生成的 Schema 不够精确时，用手动定义：

```python
from langchain_core.tools import StructuredTool
from pydantic import BaseModel, Field

# 1. 手动定义输入 Schema（精确控制每个字段的描述和约束）
class StockQueryInput(BaseModel):
    symbol: str = Field(
        description="6位股票代码。上海6开头，深圳0/3开头。例如600519",
        min_length=6,
        max_length=6,
        pattern=r"^\d{6}$"    # 正则约束：必须是6位数字
    )
    period: str = Field(
        default="daily",
        description="K线周期",
        pattern=r"^(daily|weekly|monthly)$"
    )

# 2. 实现函数
def get_stock_history(symbol: str, period: str = "daily") -> str:
    """获取股票历史K线数据"""
    ...

# 3. 创建 StructuredTool
tool = StructuredTool.from_function(
    func=get_stock_history,
    name="get_stock_history",
    description="获取A股历史K线数据。当用户询问走势、K线、历史行情时调用。",
    args_schema=StockQueryInput,  # 绑定手动定义的 Schema
)

# 生成的 JSON Schema 会包含 pattern、minLength 等 @tool 无法自动生成的约束
```

---

## 五、项目为什么用显式调用而不是 bind_tools

```python
# bind_tools 模式（项目没用）：
llm.bind_tools([search, get_quote, get_financials])
response = llm.invoke("茅台财务怎么样")
# LLM 自主决定先调 search 还是先调 get_quote
# 问题：顺序不可控，可能先调了 get_quote 再 search，逻辑错误

# 显式调用模式（项目实际使用）：
async def search_node(state):
    """这个节点里，代码决定调什么、按什么顺序调。"""
    skill_result = await stock_analysis_skill.run(input)
    # 内部顺序：检索 → 行情 → 组装 → 返回
    # 每一步都可控、可调试、可审计
```

**项目的选择逻辑**：投研工具链有确定的依赖关系——必须先检索知识库拿到上下文，再决定是否需要补充行情数据。这种有依赖的步骤不适合交给 LLM 自主决策。

---

## 六、面试速记

**Q: @tool 装饰器底层做了什么？**
A: 用 Python 的 `inspect.signature()` 和 `__doc__` 提取函数签名和文档字符串 → Python 类型注解映射成 JSON Schema 类型 → 生成 LLM 能理解的工具定义 JSON。docstring 变成 description，类型注解变成 parameters，有默认值的参数不在 required 里。

**Q: bind_tools 的原理？**
A: 它修改了每次 LLM API 请求，在请求体中附加 `tools` 参数（包含所有工具的 JSON Schema）。LLM 收到后根据用户消息决定是否调用工具、调哪个、传什么参数。

**Q: ToolMessage 为什么需要 tool_call_id？**
A: LLM 可能同时调用多个工具，每个结果回来时需要知道对应哪次调用。`tool_call_id` 就是这条"回应链"的追踪 ID。不匹配会导致 LLM 报错。

**Q: 并行工具调用什么时候可行？**
A: 工具之间没有数据依赖时。LLM 发现多个独立工具可以同时调，会一次性返回多个 tool_call。应用代码用 `asyncio.gather` 并行执行。

---

## 项目代码索引

| 文件 | 对应内容 |
|------|---------|
| `aipy2/app/tools/retriever_tool.py` | @tool 装饰器实例 |
| `aipy2/app/tools/stockdata_tool.py` | @tool + StructuredTool |
| `aipy2/app/tools/data_fetcher.py` | asyncio.gather 并行工具执行 |
| `aipy2/app/skills/stock_analysis_skill.py` | 显式调用模式（Skill 编排） |
