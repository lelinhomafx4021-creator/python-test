# 31_LangChain核心组件：LLM、Prompt、Tool、结构化输出

> **核心目标**: 掌握项目实际使用的 LangChain API，理解底层机制——不只是"怎么用"，更是"为什么这样设计"
> **项目代码**: `aipy2/app/core/llm.py` / `aipy2/app/prompts/investor_prompts.py` / `aipy2/app/tools/`

---

## 一、LangChain 的定位（2026 版）

**LangChain 不再是 Agent 框架，而是 LLM 应用的组件库 + 预制 Agent 工厂。**

```
2024 年：LangChain = Agent 框架（AgentExecutor → 已废弃）
2026 年：LangChain = 组件库（LLM/Prompt/Tool）+ create_agent（基于 LangGraph 的预制 Agent）
         LangGraph  = Agent 编排引擎（手写 StateGraph，完全控制）
```

**本项目的选择**：手写 StateGraph（而非 `create_agent`），完全控制图拓扑。后文会详细对比两种方案。

---

## 二、⚠️ 这些 API 已废弃，学到一半发现不对劲赶紧换

| 废弃 API | 替代方案 | 废弃时间 |
|----------|---------|---------|
| `AgentExecutor` + `initialize_agent` | `create_agent` 或手写 StateGraph | 2026.12 停止维护 |
| `create_react_agent` (langgraph.prebuilt) | `langchain.agents.create_agent` | LangGraph v1 起废弃 |
| `create_openai_tools_agent` | `create_agent` | 同上 |
| `AgentState` (Pydantic 版) | `AgentState` (TypedDict 版) | LangChain v1 起废弃 |

如果你在网上看到教程用 `from langgraph.prebuilt import create_react_agent`——那是过时的，别学。

---

## 三、LLM 组件 — 模型的统一调用入口

### 3.1 ChatOpenAI 构造函数：每个参数都重要

```python
from langchain_openai import ChatOpenAI

llm = ChatOpenAI(
    model="deepseek-v4-flash",          # ① 模型名称
    temperature=0.3,                    # ② 随机性控制
    api_key="sk-xxx",                   # ③ API 密钥
    base_url="https://api.deepseek.com",# ④ 自定义 API 端点
    streaming=True,                     # ⑤ 流式输出开关
    max_tokens=4096,                    # ⑥ 最大输出 token 数
    timeout=30,                         # ⑦ 请求超时（秒）
    max_retries=2,                      # ⑧ 失败重试次数
)
```

**逐参数详解**：

| 参数 | 类型 | 默认值 | 作用 | 什么时候改 |
|------|------|--------|------|-----------|
| `model` | `str` | 必填 | 要调用的模型名称 | 换模型时改这个就行 |
| `temperature` | `float` | 0.7 | 0=确定性输出，1=创意性输出 | 判断类用0，生成类用0.3-0.6 |
| `api_key` | `str` | 必填 | API 鉴权密钥 | 每个服务商不同 |
| `base_url` | `str` | OpenAI 地址 | API 端点 URL。**这是兼容非 OpenAI 模型的关键** | DeepSeek/Claude/本地模型 |
| `streaming` | `bool` | False | True=逐 token 返回，实现打字机效果 | 需要流式输出时开 |
| `max_tokens` | `int` | 无限制 | 限制 LLM 最大输出长度 | 防止回答过长耗尽预算 |
| `timeout` | `int` | 无 | HTTP 请求超时秒数 | 生产环境必须设，防止 LLM 挂死 |
| `max_retries` | `int` | 无 | 网络错误自动重试次数 | 生产环境建议 2-3 |

### 3.2 `base_url` 为什么重要——一行代码切换模型厂商

```python
# DeepSeek
llm = ChatOpenAI(model="deepseek-v4-flash", api_key="sk-xxx",
                 base_url="https://api.deepseek.com")

# 阿里通义千问（兼容 OpenAI 格式）
llm = ChatOpenAI(model="qwen-plus", api_key="sk-xxx",
                 base_url="https://dashscope.aliyuncs.com/compatible-mode/v1")

# 本地 Ollama
llm = ChatOpenAI(model="llama3", api_key="ollama",
                 base_url="http://localhost:11434/v1")

# 三行代码，三种模型，同一个 ChatOpenAI 类。这就是 LangChain 的核心价值——统一接口。
```

**底层原理**：ChatOpenAI 本质上就是构造 HTTP 请求发到 `{base_url}/chat/completions`。无论后端是 OpenAI、DeepSeek 还是 Ollama，只要实现了 OpenAI 兼容的 `/chat/completions` 端点，就能用同一套代码调用。

### 3.2b `ChatOpenAI` vs `init_chat_model`：项目为什么用前者

```python
# ⚠️ 很多教程用 init_chat_model（自动挡）:
from langchain.chat_models import init_chat_model
llm = init_chat_model("deepseek:deepseek-v4")
# 内部做的事: 识别 "deepseek:" 前缀 → 找 DeepSeek 的默认 base_url → 创建 ChatOpenAI

# ✅ 项目用 ChatOpenAI（手动挡）:
from langchain_openai import ChatOpenAI
llm = ChatOpenAI(
    model=settings.LLM_MODEL,        # "deepseek-v4-flash" — 从配置读
    api_key=settings.DEEPSEEK_API,   # 从环境变量读，不写死在代码里
    base_url=settings.LLM_BASE_URL,  # 从配置读，方便换地址
)
```

| | `init_chat_model()` | `ChatOpenAI()` |
|------|------|------|
| 定位 | 快速原型、教程用 | 生产环境 |
| 传参方式 | 字符串 `"provider:model"` | 显式传 `model`/`api_key`/`base_url` |
| 配置来源 | 环境变量或默认值 | 完全由你控制 |
| 透明度 | 低（内部做了什么不清楚） | 高（每个参数都看得见） |
| 项目用了吗 | ❌ | ✅ `app/core/llm.py` |

**选 `ChatOpenAI` 的原因**：DeepSeek 的 API 地址、API Key 都从 `settings` 读取——方便切换模型供应商、方便不同环境用不同配置。`init_chat_model` 把配置藏在内部，生产环境不好管理。

### 3.3 三种调用方式：invoke / ainvoke / astream

```python
llm = get_llm(temperature=0)

# 方式1: invoke — 同步调用，发请求 → 等完整结果 → 返回
# 适用场景：后台脚本、批处理
response = llm.invoke("你好")
print(response.content)  # "你好！有什么可以帮助你的？"
# response 是 AIMessage 对象，不是纯字符串
#   response.content        → 文本内容
#   response.response_metadata → token 用量等元信息

# 方式2: ainvoke — 异步调用，不阻塞事件循环
# 适用场景：FastAPI 异步路由、Web 服务（项目全用这个）
response = await llm.ainvoke(messages)
# ainvoke 和 invoke 返回相同类型，区别只是异步

# 方式3: astream — 异步流式调用，逐 token 产出
# 适用场景：需要打字机效果的聊天界面
async for chunk in llm.astream(messages):
    print(chunk.content, end="", flush=True)
# 每个 chunk 是 AIMessageChunk，只包含当前 token
```

### 3.4 项目的 LLM 工厂方法

```python
# aipy2/app/core/llm.py
def get_llm(
    temperature: float = 0.2,
    *,
    streaming: bool = False,
    max_completion_tokens: int | None = None,
) -> ChatOpenAI:
    """为什么是工厂方法而不是全局单例？
    因为不同节点需要不同的 temperature 和 streaming 配置：
    - intent_node:  temperature=0, streaming=False（判断类，要稳定）
    - answer_node:  temperature=0.4, streaming=True（生成类，要流式）
    - critic_node:  temperature=0, streaming=False（评审类，要客观）
    """
    return ChatOpenAI(
        model=settings.LLM_MODEL,
        temperature=temperature,
        api_key=settings.DEEPSEEK_API,
        base_url=settings.LLM_BASE_URL,
        streaming=streaming,
        max_tokens=max_completion_tokens,  # DeepSeek 用 max_tokens
    )
```

---

## 四、Prompt 组件 — 提示词模板化

### 4.1 ChatPromptTemplate.from_messages：把提示词变成可复用模板

```python
from langchain_core.prompts import ChatPromptTemplate

# 定义模板（只定义一次，所有请求复用）
INTENT_PROMPT = ChatPromptTemplate.from_messages([
    ("system", "你是投研助手的路由分类器。只负责判断，不回答问题。"),
    ("human", "用户问题：<query>{user_msg}</query>\n{format_instructions}"),
])

# 运行时注入变量（每次请求调用）
messages = INTENT_PROMPT.format_messages(
    user_msg="茅台股价多少",
    format_instructions="请返回 JSON：...",
)
# 返回值是 list[BaseMessage]:
# [
#   SystemMessage(content="你是投研助手的路由分类器..."),
#   HumanMessage(content="用户问题：<query>茅台股价多少</query>\n请返回 JSON：...")
# ]
```

### 4.2 三种消息角色

| 角色 | 对应的类 | 作用 | LLM 如何看待它 |
|------|---------|------|---------------|
| `"system"` | `SystemMessage` | 设定 AI 的行为规则和身份 | **最高优先级指令**，LLM 必须遵守 |
| `"human"` | `HumanMessage` | 用户说的话 | **当前要处理的任务** |
| `"assistant"` | `AIMessage` | AI 之前的回答 | **历史上下文**，帮助 LLM 理解对话进展 |

### 4.3 占位符变量 `{variable}`

```python
prompt = ChatPromptTemplate.from_messages([
    ("system", "你是{role}，请用{style}风格回答。"),
    ("human", "{question}")
])

# {role} {style} {question} 三个占位符，运行时注入
messages = prompt.format_messages(
    role="A股分析师",
    style="专业严谨",
    question="茅台估值合理吗？"
)
# SystemMessage: "你是A股分析师，请用专业严谨风格回答。"
# HumanMessage: "茅台估值合理吗？"
```

### 4.4 项目的高级用法：消息拼接

```python
# aipy2/app/graph/nodes.py — answer_node
# 这个写法的精妙之处：
response = await llm.ainvoke(
    prompt_messages[:1]     # [SystemMessage] — 行为约束在最前面
    + state["messages"]     # [HumanMessage, AIMessage, ...] — 完整历史对话
    + prompt_messages[1:]   # [HumanMessage] — 本轮检索结果 + 用户问题
)
```

**为什么不用 `MessagesPlaceholder`**：MessagesPlaceholder 只能插入到一个固定位置。这个写法让你能精确控制 system prompt、历史消息、本轮资料三者的顺序——system 永远在最前面不会被历史消息淹没。

---

## 五、Tool 组件 — 从 Python 函数到 LLM 可理解的工具定义

### 5.1 `@tool` 装饰器的底层机制

这是最容易被误解的概念。让我们一步步拆解 `@tool` 做了什么。

```python
from langchain_core.tools import tool

@tool
async def search_intelligent(query: str) -> str:
    """给 Agent 调用的统一搜索工具。"""
    return await run_retrieval_async(queries=[query], mode="auto")
```

**当你写 `@tool` 时，LangChain 在背后做了三件事**：

#### 第一步：提取函数元信息

```python
# LangChain 内部做的事（简化版）：
func_name = search_intelligent.__name__        # → "search_intelligent"
func_doc = search_intelligent.__doc__          # → "给 Agent 调用的统一搜索工具。"
# 类型注解从 __annotations__ 中读取：
type_hints = search_intelligent.__annotations__ # → {'query': str, 'return': str}
```

#### 第二步：把类型注解转换成 JSON Schema

```python
# Python 类型 → JSON Schema 类型的映射：
# str   → {"type": "string"}
# int   → {"type": "integer"}
# float → {"type": "number"}
# bool  → {"type": "boolean"}
# list[str] → {"type": "array", "items": {"type": "string"}}

# 以 search_intelligent 为例，生成的 JSON Schema：
{
    "name": "search_intelligent",
    "description": "给 Agent 调用的统一搜索工具。",
    "parameters": {
        "type": "object",
        "properties": {
            "query": {
                "type": "string",
                "description": ""    # 注意：Python 函数参数没有独立的 docstring，这里为空
            }
        },
        "required": ["query"]
    }
}
```

**这就是 docstring 为什么至关重要**：它变成了 JSON Schema 里的 `description` 字段。LLM 就是靠这个 `description` 来决定"什么时候该调这个工具"。

#### 第三步：把 JSON Schema 发给 LLM

当你调用 `llm.bind_tools([search_intelligent])` 或使用 `create_agent` 时，LangChain 把上面的 JSON Schema 放在 API 请求的 `tools` 参数里，一起发给 LLM。

LLM 收到后，会根据用户消息和工具描述，自主决定要不要调用工具。如果决定调用，它返回的不是函数执行结果，而是一个 **tool_call JSON**：

```json
{
    "id": "call_abc123",
    "type": "function",
    "function": {
        "name": "search_intelligent",
        "arguments": "{\"query\": \"茅台 最新财报\"}"
    }
}
```

**关键认知**：LLM 不执行你的 Python 函数。它只是说"我想调用 search_intelligent，参数是 query='茅台 最新财报'"。你的应用代码收到这个 JSON 后，才真正执行 `search_intelligent("茅台 最新财报")`。

### 5.2 写一个好的 Tool 描述的黄金法则

```python
# ❌ 差：描述太模糊，LLM 不知道什么时候用
@tool
def get_data(code: str) -> str:
    """获取数据"""
    ...

# ❌ 差：函数名不直观
@tool
def func1(x: str) -> str:
    """处理输入"""
    ...

# ✅ 好：函数名一目了然，docstring 说清楚用途、参数、返回值
@tool
def get_stock_quote(symbol: str) -> dict:
    """获取A股股票实时行情。
    当用户询问某只股票的当前价格、涨跌幅、成交量时调用此工具。

    Args:
        symbol: 6位数字股票代码，例如 "600519"（贵州茅台）、"000001"（平安银行）

    Returns:
        dict: 包含 price（最新价）、change（涨跌幅%）、volume（成交量手）
    """
    ...
```

### 5.3 项目的两种工具调用方式

```python
# 方式1: bind_tools — LLM 自主决定调不调工具（项目没用，但业界常用）
llm_with_tools = llm.bind_tools([get_stock_quote, search_news])
response = llm_with_tools.invoke("茅台现在多少钱")
# LLM 自动判断：这需要查行情 → 返回 tool_call 而不是文本回答
# response.tool_calls[0] → {"name": "get_stock_quote", "arguments": {"symbol": "600519"}}

# 方式2: 显式调用 — 代码决定何时调工具（项目实际使用）
# search_node 里：
skill_result = await stock_analysis_skill.run(
    StockAnalysisSkillInput(query=user_query, queries=queries, top_k=3)
)
# 不走 LLM 决策，代码直接调用。更可控，更可调试。
```

**为什么项目选显式调用**：投研的工具调用顺序是确定性的——先检索→再行情→最后回答。让 LLM 自主决定反而会引入不确定性。显式调用每一步都能看到输入输出，调试方便。

---

## 六、结构化输出 — 让 LLM 返回可解析的 Python 对象

~~2026 年推荐方案~~：`llm.with_structured_output(Model)`，走原生 function calling。旧方案 `PydanticOutputParser`（靠 prompt 指令让 LLM 输出 JSON）不稳定，项目已全面迁移。

### 6.1 with_structured_output：原生 function calling（推荐）

```python
from pydantic import BaseModel, Field
from typing import Literal

# ① 定义输出结构（和旧方案一样）
class IntentRouteResult(BaseModel):
    route: Literal["use_kb", "no_kb"] = Field(description="是否需要知识库检索")
    reason: str = Field(min_length=1, max_length=120, description="原因")

# ② 创建结构化 LLM
llm = get_llm(temperature=0)
structured_llm = llm.with_structured_output(IntentRouteResult)

# ③ 调用 — 返回直接是 Pydantic 对象，不需要手动 parse
result = await structured_llm.ainvoke(messages)
# → IntentRouteResult(route="use_kb", reason="涉及股票数据查询")
print(result.route)   # 直接访问属性，类型安全
print(result.reason)
```

**底层原理**：`with_structured_output` 不是靠 prompt 让 LLM 输出 JSON 再解析。它走的是模型原生的 **function calling / JSON mode**——LLM 把结构化输出当作"工具调用"的参数返回，LangChain 自动提取并转成 Pydantic 对象。比 prompt 方式可靠得多。

**`with_structured_output` 的完整参数**：

```python
llm.with_structured_output(
    schema,           # ① Pydantic Model 或 TypedDict 或 JSON Schema
    method=None,      # ② 强制指定模式: "function_calling" | "json_mode" | "json_schema"
    include_raw=False,# ③ True=返回 (parsed, raw_AIMessage) 元组，用于调试
    strict=False,     # ④ 是否使用严格模式（只允许 schema 里定义的字段）
)
```

| 参数 | 类型 | 默认 | 作用 |
|------|------|------|------|
| `schema` | `BaseModel \| TypedDict \| dict` | 必填 | 输出结构定义。Pydantic 最常用，TypedDict 适合简单结构 |
| `method` | `str \| None` | auto | `"function_calling"`=声明一个工具让 LLM 调 / `"json_mode"`=prompt 里加"输出 JSON" / `"json_schema"`=OpenAI 原生的结构化输出 API。不传则 LangChain 自动选最合适的 |
| `include_raw` | `bool` | False | 设为 True 时返回 `(parsed_object, raw_AIMessage)`，可以看到原始响应用于调试 token 用量 |
| `strict` | `bool` | False | 设为 True 时，LLM 不能输出 schema 里没有的字段。部分模型支持（OpenAI gpt-4o+），DeepSeek 部分支持 |

**`method` 参数的三种模式怎么选**：

```python
# mode="function_calling" — 稳定，兼容性最好
# LangChain 生成一个虚拟工具，让 LLM 以 tool_call 的形式返回结构化数据
llm.with_structured_output(MyModel, method="function_calling")
# ✅ 几乎所有模型都支持  ❌ 有时 LLM 选择不"调"这个虚拟工具

# mode="json_schema" — OpenAI 原生，最可靠
# OpenAI 模型在 API 层直接保证输出符合 schema
llm.with_structured_output(MyModel, method="json_schema")
# ✅ 模型层面保证格式  ❌ 只有 OpenAI 支持

# mode="json_mode" — 兜底，靠 prompt
# 在 prompt 里加 "Output JSON"，不太可靠
llm.with_structured_output(MyModel, method="json_mode")
# ✅ 任何模型都能用  ❌ 格式错误率最高
```

### 6.2 旧方案 PydanticOutputParser（已废弃，仅作对比）

```python
# ❌ 旧方案：靠 prompt 指令让 LLM 输出 JSON，不稳定
from langchain_core.output_parsers import PydanticOutputParser

parser = PydanticOutputParser(pydantic_object=IntentRouteResult)
format_instructions = parser.get_format_instructions()
# → 生成一段超长的格式说明文字塞进 prompt

prompt = ChatPromptTemplate.from_messages([
    ("human", "...{format_instructions}")   # ← 占用了大量 prompt token
])
res = await llm.ainvoke(prompt.format_messages(...))
result = parser.parse(res.content)   # ← LLM 输出格式偶尔出错，需要 try/except 兜底
```

**旧方案的问题**：LLM 可能在 JSON 外包 Markdown 代码块、少字段、多字段。parse 失败率在生产环境可达 5-10%。

### 6.3 项目实际使用模式

```python
# aipy2/app/graph/nodes.py — route_intent_node
async def route_intent_node(state: AgentState):
    llm = llm_core.get_llm(temperature=0)
    messages = INTENT_ROUTE_PROMPT.format_messages(user_msg=user_msg)
    structured_llm = llm.with_structured_output(IntentRouteResult)

    try:
        result = await structured_llm.ainvoke(messages)
        use_kb = result.route == "use_kb"
    except Exception:
        # 降级：原生 structured output 也失败时，用原始 LLM + 关键词兜底
        res = await llm.ainvoke(messages)
        decision = _message_text(res).strip().lower()
        use_kb = "use_kb" in decision and "no_kb" not in decision

    return {"use_kb": use_kb, "step": "..."}
```

**同样的模式用于**：
- `route_intent_node` → `IntentRouteResult`
- `rewrite_node` → `RewriteQueriesResult`
- `critic_node` → `CriticReviewResult`
- `util.py generate_title` → `TitleResult`

### 6.4 Prompt 模板的变化

```python
# 旧方案：Prompt 里有 {format_instructions}
("human", "用户问题：{user_msg}\n{format_instructions}")

# 新方案：不需要了！直接描述任务即可
("human", "用户问题：\n<query>\n{user_msg}\n</query>")
```

**省掉了什么**：每次调用不再需要把超长的 JSON Schema 文本注入 prompt。对 token 消耗有微小改善，更重要的是 prompt 更干净。

---

## 七、create_agent vs 手写 StateGraph（项目选后者的完整分析）

### 7.1 create_agent：一行代码出 Agent

```python
from langchain.agents import create_agent
from langchain.agents.structured_output import ToolStrategy

agent = create_agent(
    model="deepseek:deepseek-v4-flash",  # 模型标识字符串或实例
    tools=[search, get_weather],          # 工具列表
    system_prompt="你是投研助手",          # 系统提示词
    middleware=[...],                      # 中间件（新概念，见下文）
    response_format=ToolStrategy(MyOutput),# 结构化输出策略
    checkpointer=PostgresSaver(...),       # 状态持久化
)
result = agent.invoke({"messages": [{"role": "user", "content": "茅台怎么样"}]})
```

**create_agent 内部做了什么**：它在 LangGraph 上构建了一个标准的 ReAct 循环图（model → tools → model → ... → response），然后编译返回。你不需要手写 StateGraph，但图的内部结构对你是黑盒。

### 7.2 create_agent 的参数全解

| 参数 | 类型 | 说明 |
|------|------|------|
| `model` | `str \| BaseChatModel` | 模型标识符 `"openai:gpt-4"` 或实例 |
| `tools` | `list[BaseTool]` | 工具列表，空列表 = 不带工具的纯 LLM |
| `system_prompt` | `str \| None` | 系统提示词。不传则 LLM 从消息中自行推断任务 |
| `middleware` | `list[AgentMiddleware]` | 中间件钩子列表（LangChain v1 的杀手级特性） |
| `response_format` | `ToolStrategy \| ProviderStrategy \| None` | 结构化输出策略 |
| `state_schema` | `Type[AgentState] \| None` | 自定义状态（必须是 TypedDict） |
| `checkpointer` | `Checkpointer \| None` | 状态持久化（多轮对话必需） |
| `interrupt_before` | `list[str] \| None` | 在这些节点前暂停（HITL 用） |
| `interrupt_after` | `list[str] \| None` | 在这些节点后暂停 |

### 7.3 手写 StateGraph：完全控制（项目方案）

```python
# aipy2/app/graph/investor_graph.py
from langgraph.graph import StateGraph, START, END

workflow = StateGraph(AgentState)

# 注册节点（每个节点是一个 async 函数）
workflow.add_node("intent", route_intent_node)
workflow.add_node("rewrite", rewrite_node)
workflow.add_node("search", search_node)
workflow.add_node("answer", answer_node)
workflow.add_node("critic", critic_node)
workflow.add_node("handoff", handoff_node)

# 连线（控制流转方向）
workflow.add_edge(START, "intent")
workflow.add_conditional_edges("intent", route_intent, {
    "use_kb": "rewrite",    # 需要查资料 → 改写搜索词
    "no_kb": "direct_answer", # 闲聊 → 直接回答
    "handoff": "handoff",    # 转人工 → 兜底
})
workflow.add_conditional_edges("critic", route_judge, {
    "retry": "rewrite",      # 不合格 → 打回重写
    "handoff": "handoff",    # 3次失败 → 转人工
    "end": END,              # 通过 → 结束
})

app = workflow.compile(checkpointer=checkpointer)
```

### 7.4 选型决策树

```
能不能用 create_agent？
├── 流程是标准的 model→tools→model 循环？ → ✅ 用 create_agent
├── 需要多套图拓扑（VIP/普通走不同流程）？→ ❌ 手写 StateGraph
├── 需要 critic 闭环（不通过则打回重写）？→ ❌ 手写 StateGraph
├── 需要精确控制流式输出的每个阶段？    → ❌ 手写 StateGraph
├── 需要节点内并行（asyncio.gather）？  → ❌ 手写 StateGraph
└── 只是想快速原型验证想法？            → ✅ 用 create_agent

本项目：四样全中 → 手写 StateGraph。
```

---

## 八、面试速记

**Q: @tool 装饰器底层做了什么？**
A: 三件事——提取函数名和 docstring → 把 Python 类型注解转成 JSON Schema → 把这个 Schema 发给 LLM。LLM 看到的是 `{"name": "search", "description": "...", "parameters": {...}}`，它据此决定什么时候调、传什么参数。

**Q: PydanticOutputParser 的完整工作流程？**
A: 五步——定义 Pydantic 模型（含 Field 约束）→ 创建 Parser → get_format_instructions() 生成 JSON Schema 文本 → 注入 Prompt 发给 LLM → parse() 把 LLM 返回的 JSON 转成 Python 对象。异常时必须降级兜底。

**Q: bind_tools 和显式调用的区别？**
A: bind_tools 让 LLM 自主决策调不调工具（适合开放场景）。显式调用是代码决定何时调（适合确定流程）。项目选后者，因为投研工具链是确定性的。

**Q: create_agent 和手写 StateGraph 怎么选？**
A: create_agent 适合标准 ReAct 循环。手写适合多角色路由、critic 闭环、并行获取。项目的 VIP/普通双流程 + critic 打回机制在手写 StateGraph 里更自然。

---

## 项目代码索引

| 文件 | 对应内容 |
|------|---------|
| `aipy2/app/core/llm.py` | ChatOpenAI 工厂、PostgreSQL Checkpointer |
| `aipy2/app/prompts/investor_prompts.py` | 6 个 Prompt 模板 + 4 个 Pydantic 输出模型 + 4 个 Parser |
| `aipy2/app/tools/retriever_tool.py` | @tool 装饰器实例、本地/联网双引擎 |
| `aipy2/app/graph/nodes.py` | Parser + Prompt + LLM 的真实组合调用 |
