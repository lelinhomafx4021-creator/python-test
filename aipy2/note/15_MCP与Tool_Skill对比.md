# 15 MCP 与 Tool / Skill 对比

这篇笔记只解决一个问题：

> `tool`、`skill`、`MCP` 到底是什么关系？

先记一句最重要的话：

- `tool`：一个具体能力
- `skill`：一段业务流程编排
- `MCP`：调用这些能力的一种标准协议

所以：

- `tool` 和 `skill` 是“业务层级”
- `MCP` 是“通信方式”

它们不是简单的“谁包含谁”，而是两个不同维度。

---

## 1. 你们项目现在的 `tool`

你们项目里已经有真正的 `tool`。

例如：

- `search_intelligent`
- `get_stock_quote_core`

代码位置：

- `app/tools/retriever_tool.py`
- `app/tools/stockdata_tool.py`

### 1.1 检索 Tool

```python
@tool
async def search_intelligent(query: str) -> str:
    return await run_retrieval_async(queries=[query], mode="auto")
```

这就是一个典型 `tool`：

- 输入：`query`
- 输出：检索结果字符串
- 职责：只做“搜索”这一件事

### 1.2 行情 Tool

```python
@tool
def get_stock_quote_core(symbol: str) -> str:
    resp = requests.get(...)
    payload = resp.json()
    return json.dumps(payload, ensure_ascii=False)
```

这也是一个 `tool`：

- 输入：`symbol`
- 输出：行情 JSON 字符串
- 职责：只做“查行情”这一件事

你可以把 `tool` 理解成 Java 里的一个单方法能力：

```java
String search(String query);
String getQuote(String symbol);
```

---

## 2. 你们项目现在的 `skill`

`skill` 不是只干一件事，而是把多个步骤组合起来。

你们项目里最典型的是：

- `app/skills/stock_analysis_skill.py`

核心写法大概是这样：

```python
class StockAnalysisSkill:
    async def run(self, payload: StockAnalysisSkillInput) -> StockAnalysisSkillOutput:
        merged_queries = [q.strip() for q in payload.queries if q and q.strip()]
        if payload.query.strip() not in merged_queries:
            merged_queries.append(payload.query.strip())

        knowledge = await run_retrieval_async(
            queries=merged_queries,
            mode="auto",
            top_k=payload.top_k,
        )

        symbol = self._extract_symbol(payload.query)

        quote = None
        if symbol and payload.require_quote:
            raw = get_stock_quote_core.invoke({"symbol": symbol})
            quote = json.loads(raw)

        evidence = self._build_evidence(merged_queries, symbol, bool(quote))

        return StockAnalysisSkillOutput(
            knowledge=knowledge,
            evidence=evidence,
            quote=quote,
            symbol=symbol,
        )
```

这个 `skill` 做了很多步：

1. 整理检索词
2. 调检索 `tool`
3. 提取股票代码
4. 调行情 `tool`
5. 组装证据
6. 返回完整结果

所以：

- `tool` 像工人
- `skill` 像工头

---

## 3. 你们现在没有 MCP 时，调用链是什么

当前项目是这种模式：

```text
Graph / Service
-> Skill
-> 直接 import Tool
-> 直接调 SDK / 数据库 / HTTP API
```

举例：

```python
from app.tools.retriever_tool import run_retrieval_async
from app.tools.stockdata_tool import get_stock_quote_core

knowledge = await run_retrieval_async(...)
quote = get_stock_quote_core.invoke({"symbol": symbol})
```

这叫：

> 项目内部直连工具

优点：

- 简单
- 好调试
- 性能路径短

缺点：

- 工具很难复用给别的 Agent / 客户端
- 每个项目都要自己接一遍

---

## 4. 如果改成 MCP，会长什么样

MCP 不是新的业务能力，而是“把能力标准化暴露出去”的一层协议。

你可以理解成：

> 现在你们是“函数直调”
> MCP 是“先注册成标准工具，再通过协议调用”

### 4.1 MCP Server 写法示意

下面是示意代码，不是你们当前项目真实代码。

```python
from mcp.server.fastmcp import FastMCP

from app.tools.retriever_tool import run_retrieval_async
from app.tools.stockdata_tool import get_stock_quote_core

mcp = FastMCP("ai-investor-tools")


@mcp.tool()
async def search_intelligent(query: str) -> str:
    return await run_retrieval_async(queries=[query], mode="auto")


@mcp.tool()
def get_stock_quote(symbol: str) -> str:
    return get_stock_quote_core.invoke({"symbol": symbol})


if __name__ == "__main__":
    mcp.run()
```

这个时候重点不是函数本身，而是：

- 工具被注册成了 MCP Tool
- 外部客户端可以发现它
- 外部客户端可以按统一协议调用它

### 4.2 MCP Client 调用示意

```python
client = MCPClient(...)

tools = await client.list_tools()

quote = await client.call_tool(
    "get_stock_quote",
    {"symbol": "600519"},
)
```

这里和“直接 import 函数”最大的区别是：

- 调用方不关心底层实现在哪
- 调用方只关心：
  - 工具名
  - 参数 schema
  - 返回结果

---

## 5. 如果 `skill` 走 MCP，会怎么写

### 5.1 现在的 Skill

```python
from app.tools.retriever_tool import run_retrieval_async
from app.tools.stockdata_tool import get_stock_quote_core

class StockAnalysisSkill:
    async def run(self, payload):
        knowledge = await run_retrieval_async(...)
        quote = get_stock_quote_core.invoke({"symbol": payload.symbol})
```

特点：

- `skill` 直接知道 `tool` 的代码位置
- `skill` 直接 import 它们

### 5.2 如果改成 MCP 风格

```python
class StockAnalysisSkill:
    def __init__(self, mcp_client):
        self.mcp_client = mcp_client

    async def run(self, payload):
        knowledge = await self.mcp_client.call_tool(
            "search_intelligent",
            {"query": payload.query},
        )

        quote = await self.mcp_client.call_tool(
            "get_stock_quote",
            {"symbol": payload.symbol},
        )
```

特点：

- `skill` 还是 `skill`
- 但它不再直接 import `tool`
- 而是通过 MCP 协议去调 `tool`

所以要记住：

> `skill` 会用 `tool`
> `tool` 可以选择通过 MCP 暴露

而不是：

> `skill > MCP > tool`

---

## 6. 一张图彻底记住

### 6.1 你们现在的项目

```text
LangGraph / Service
-> Skill
-> Tool
-> SDK / DB / HTTP
```

### 6.2 如果以后用了 MCP

```text
LangGraph / Service
-> Skill
-> MCP Client
-> MCP Server
-> Tool
-> SDK / DB / HTTP
```

所以：

- `tool` 没变
- `skill` 也没变
- 只是 `tool` 的调用方式变了

---

## 7. 一句话结论

- `tool`：干具体活
- `skill`：组织多个 `tool` 干活
- `MCP`：规定这些活怎么被标准化调用

你们当前项目：

- 有 `tool`
- 有 `skill`
- 没有 `MCP`

而且这完全正常。

---

## 8. 30 秒面试讲法

> 在我的项目里，Tool 是单一能力，比如检索和行情查询；Skill 是更高层的业务编排，比如股票分析技能会组合检索、代码抽取、行情和证据整理；MCP 则不是能力本身，而是把这些能力标准化暴露给 Agent 的协议层。当前项目还没有引入 MCP，而是采用项目内部直连 Tool/Skill 的方式，这样工程更轻、更容易调试。

---

## 9. 新手大白话

你这次真正要分清的是：

- `tool` 是干活的
- `skill` 是安排干活顺序的
- `MCP` 是规定“怎么叫这些人来干活”的

---

## 10. 课后练习

目标：

把 `get_stock_quote_core(symbol)` 这类直接函数调用，先在脑子里改写成 MCP 风格调用。

你可以自己试着写出下面两段伪代码：

1. MCP Server 版 `get_stock_quote`
2. Skill 里通过 `mcp_client.call_tool("get_stock_quote", ...)` 来调用它

验收标准：

- 你能说清楚哪一层是 `tool`
- 你能说清楚哪一层是 `skill`
- 你能说清楚 `MCP` 只是调用协议，不是业务能力本身
