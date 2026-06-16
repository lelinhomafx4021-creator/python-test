# 42_MCP实战教学：从零写一个可跑的 MCP Server + Client

> **核心目标**: 手把手写出 MCP Server 和 Client，理解每一步在做什么
> **前置知识**: [[37-mcp-protocol]]（MCP 协议概念）、Python 基础
> **项目代码**: `aipy2/mcp_demo/weather_server.py` + `test_client.py`

---

## 第 0 步：先想清楚 MCP 到底是什么

在写代码之前，用一句话理解 MCP：

**MCP 就是"把 Python 函数暴露成 HTTP API 那样的东西，但用的是 JSON-RPC 协议而不是 REST"。**

```
普通 Python 函数调用:
  result = get_weather("北京")         # 同一个进程里直接调

MCP 调用:
  ① Client 把 "get_weather" + {"city": "北京"} 打包成 JSON-RPC 请求
  ② 通过 stdio（或 HTTP）发给 Server 进程
  ③ Server 解析请求 → 执行 get_weather("北京") → 结果打包成 JSON-RPC 响应
  ④ 发回 Client

区别只有一个：调用者和被调用者不在同一个进程里，需要一个标准协议来通信。
MCP 就是这个协议——仅此而已。
```

---

## 第 1 步：安装

```bash
cd aipy2
uv add mcp
```

装的是官方 Python SDK `mcp`（当前版本 1.27.2）。它干了什么：
- 帮你处理 JSON-RPC 消息的构造和解析
- 帮你处理 `initialize` 握手
- 帮你把 Python 函数的类型注解自动转成 JSON Schema

---

## 第 2 步：写一个最简 Server（3 个工具 + 1 个资源 + 1 个提示词）

创建文件 `mcp_demo/weather_server.py`：

```python
from mcp.server.fastmcp import FastMCP

# ===== ① 创建 Server 实例 =====
# "投研工具服务器 v1.0" 是 server 名称，会在 initialize 握手中发送给 Client
mcp = FastMCP("投研工具服务器 v1.0")

# ===== ② 注册 Tool：get_weather =====
# @mcp.tool() 做两件事：
#   1. 把函数名和 docstring 变成 tools/list 返回的 JSON Schema
#   2. 收到 tools/call("get_weather", ...) 时自动执行这个函数
@mcp.tool()
def get_weather(city: str) -> str:
    """获取指定城市的实时天气信息。当用户询问天气时调用。"""
    db = {"北京": "晴天 25°C", "上海": "多云 28°C", "深圳": "阵雨 30°C"}
    return db.get(city, f"{city}：晴转多云 22°C")

# ===== ③ 注册 Tool：get_stock_quote =====
@mcp.tool()
def get_stock_quote(symbol: str) -> str:
    """获取A股实时行情。当用户询问股价、涨跌幅时调用。"""
    db = {
        "600519": "贵州茅台 1856.00 +2.35%",
        "000001": "平安银行 12.85 -0.62%",
        "000858": "五粮液 168.50 +1.20%",
    }
    return db.get(symbol, f"{symbol}：今日休市")

# ===== ④ 注册 Resource（只读数据） =====
@mcp.resource("announcements://latest")
def get_announcements() -> str:
    """最新投研公告"""
    return "[公告] 2026Q1 茅台净利同比增长 15.2%"

# ===== ⑤ 注册 Prompt 模板 =====
@mcp.prompt()
def analyze_stock(symbol: str) -> str:
    """生成股票分析提示词"""
    return f"请分析股票 {symbol}，包括行情、财务、风险三方面。"

# ===== ⑥ 启动 =====
if __name__ == "__main__":
    # transport="stdio" 表示通过标准输入/输出通信
    # Client 通过 subprocess 启动这个脚本，往 stdin 写 JSON-RPC，从 stdout 读响应
    mcp.run(transport="stdio")
```

---

## 第 3 步：理解 Server 背后发生了什么

当你运行 `mcp.run(transport="stdio")` 后，Server 进入一个无限循环：

```
while True:
    line = sys.stdin.readline()       # 从 stdin 读一行 JSON-RPC
    request = json.loads(line)        # 解析 JSON
    response = handle(request)        # 根据 method 字段路由到对应处理器
    sys.stdout.write(json.dumps(response) + "\n")  # 写回 stdout
```

`handle(request)` 内部的逻辑（SDK 自动做的，你不用写）：

```
if method == "initialize":
    → 返回 server 名称、版本、支持的 capabilities
elif method == "tools/list":
    → 遍历所有 @mcp.tool() 注册的函数
    → 用 inspect.signature() 提取参数名和类型注解
    → 把类型注解转成 JSON Schema（str → {"type": "string"}）
    → 返回工具列表
elif method == "tools/call":
    → 从 params.name 找到对应的 Python 函数
    → 从 params.arguments 提取参数值
    → 执行函数 func(**arguments)
    → 把返回值包装成 {"content": [{"type": "text", "text": result}]}
    → 返回
elif method == "resources/list":
    → 返回所有 @mcp.resource() 注册的资源 URI
elif method == "resources/read":
    → 根据 URI 找到对应的 Python 函数
    → 执行函数，返回内容
elif method == "prompts/list":
    → 返回所有 @mcp.prompt() 注册的模板
elif method == "prompts/get":
    → 执行对应的 prompt 函数，返回模板内容
```

---

## 第 4 步：写 Client

创建 `mcp_demo/test_client.py`：

```python
import asyncio
from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client

async def main():
    # ===== ① 指定 Server 的启动方式 =====
    # 这里不是 HTTP URL，而是"用 uv 启动这个 Python 文件"
    # Client 会用 subprocess 启动 Server 进程
    server_params = StdioServerParameters(
        command="uv",
        args=["run", "python", "mcp_demo/weather_server.py"],
    )

    # ===== ② 建立连接 =====
    # stdio_client() 做三件事：
    #   1. 启动 Server 子进程
    #   2. 创建 stdin/stdout 管道
    #   3. 返回 read_stream 和 write_stream
    async with stdio_client(server_params) as (read, write):
        # ClientSession 封装了 JSON-RPC 通信
        async with ClientSession(read, write) as session:
            # ③ 握手：Client 和 Server 交换协议版本和能力
            await session.initialize()

            # ===== ④ 调 tools/list：看看 Server 有什么工具 =====
            tools = await session.list_tools()
            for tool in tools.tools:
                print(f"工具: {tool.name} — {tool.description}")
                print(f"  参数: {tool.inputSchema}")
                # tool.inputSchema 长这样：
                # {"properties": {"city": {"type": "string"}}, "required": ["city"]}

            # ===== ⑤ 调 tools/call：执行工具 =====
            result = await session.call_tool("get_weather", arguments={"city": "北京"})
            print(result.content[0].text)  # "晴天 25°C"
            # result.content 是一个列表，每个元素是 TextContent 或 ImageContent
            # result.content[0].text 就是工具返回的字符串

            # ===== ⑥ 调 resources/read：读取资源 =====
            result = await session.read_resource("announcements://latest")
            print(result.contents[0].text)

            # ===== ⑦ 调 prompts/get：获取提示词模板 =====
            result = await session.get_prompt("analyze_stock", arguments={"symbol": "600519"})
            print(result.messages[0].content.text)

asyncio.run(main())
```

---

## 第 5 步：运行

```bash
cd aipy2
uv run python mcp_demo/test_client.py
```

输出：

```
[tools/list] 发现 2 个工具：
   * get_weather: 获取指定城市的实时天气...
     参数: {'properties': {'city': {'type': 'string'}}, 'required': ['city']}
   * get_stock_quote: 获取A股实时行情...
     参数: {'properties': {'symbol': {'type': 'string'}}, 'required': ['symbol']}

[tools/call] get_weather(city='北京') -> 晴天 25°C
[tools/call] get_stock_quote(symbol='600519') -> 贵州茅台 1856.00 +2.35%
[tools/call] get_stock_quote(symbol='000858') -> 五粮液 168.50 +1.20%

[resources/read] -> [公告] 2026Q1 茅台净利同比增长 15.2%

[prompts/get] -> 请分析股票 600519，包括行情、财务、风险三方面。
```

---

## 第 6 步：关键 API 速查

### Server 端（FastMCP）

| 装饰器 | 注册什么 | Client 怎么调 |
|--------|---------|-------------|
| `@mcp.tool()` | 可执行工具 | `session.call_tool(name, arguments={...})` |
| `@mcp.resource("uri://name")` | 只读数据源 | `session.read_resource("uri://name")` |
| `@mcp.prompt()` | 提示词模板 | `session.get_prompt(name, arguments={...})` |

### Client 端（ClientSession）

| 方法 | 对应 JSON-RPC method | 返回类型 |
|------|---------------------|---------|
| `session.initialize()` | `initialize` | 握手确认 |
| `session.list_tools()` | `tools/list` | `ListToolsResult`（含 `.tools` 列表） |
| `session.call_tool(name, arguments)` | `tools/call` | `CallToolResult`（含 `.content` 列表） |
| `session.list_resources()` | `resources/list` | `ListResourcesResult` |
| `session.read_resource(uri)` | `resources/read` | `ReadResourceResult`（含 `.contents`） |
| `session.list_prompts()` | `prompts/list` | `ListPromptsResult` |
| `session.get_prompt(name, arguments)` | `prompts/get` | `GetPromptResult`（含 `.messages`） |

### 传输方式

| 方式 | 适用场景 | Server 启动 | Client 连接 |
|------|---------|------------|------------|
| `stdio` | 本地通信 | `mcp.run(transport="stdio")` | `stdio_client(StdioServerParameters(...))` |
| `sse` | 远程通信 | `mcp.run(transport="sse", host="0.0.0.0", port=8000)` | `sse_client(url="http://...")` |

---

## 第 7 步：MCP 的代价——不只是多写几行代码



### 7.1 依赖成本

```bash
$ uv add mcp

# 新增 13 个包：
# mcp, httpx-sse, jsonschema, jsonschema-specifications,
# pyjwt, python-multipart, pywin32, referencing, rpds-py,
# sse-starlette, attrs, httpx (升级), tenacity (升级)
```

对比：

```
项目现在的做法：
  from app.tools.stockdata_tool import get_stock_quote_core
  → 零额外依赖。Python 内置 import 就行。

MCP 的做法：
  pip install mcp  → 13 个依赖
  → 全是为了"跨进程 JSON-RPC 通信"这一件事
```

### 7.2 不只是依赖，还有这些成本

| 成本 | 直接 import | MCP |
|------|-----------|-----|
| 依赖数量 | 0 | 13 个包 |
| 调用方式 | 同步，一行代码 | 异步，4 行代码 |
| 类型安全 | Pydantic 约束，IDE 补全 | 返回值是字符串，类型信息丢失 |
| 调试 | 断点直接进函数 | 得抓 JSON-RPC 消息 |
| 错误处理 | Python 异常直接捕获 | JSON-RPC Error + isError 两层 |
| 性能 | 函数调用（纳秒级） | 进程间通信 + JSON 序列化（毫秒级） |

### 7.3 什么时候这些代价值得付

```
不值得（我们的场景）：
  1 个项目 × 1 种语言 × 5 个工具 × 同一进程
  → 直接 import。MCP 的 13 个依赖全是浪费。

值得：
  10 个团队 × 3 种语言 × 50 个工具 × 不同服务器
  → 没有标准协议 = 每个团队为每个工具写适配代码
  → MCP 的 13 个依赖换来 50×10 个适配代码不写
```

```python
# ===== 项目现在的做法（直接 import 调用）=====
from app.tools.stockdata_tool import get_stock_quote_core

result = get_stock_quote_core.invoke({"symbol": "600519"})
# 一行代码，同步返回。调试可以打断点。类型有 Pydantic 约束。


# ===== 如果用 MCP（跨进程 JSON-RPC）=====
async with stdio_client(server_params) as (read, write):
    async with ClientSession(read, write) as session:
        await session.initialize()
        result = await session.call_tool("get_stock_quote", arguments={"symbol": "600519"})
        print(result.content[0].text)
# 4 行代码，异步。调试得抓 JSON-RPC 消息。类型约束丢失（返回值是字符串）。


# 结论：同一个项目里的工具，直接 import 调用就够了。
# MCP 的价值在"跨进程、跨语言、跨团队"时体现。
```

---

## 总结：记住三句话

1. **MCP Server = Python 函数 + `@mcp.tool()` 装饰器 + `mcp.run(transport="stdio")`**
2. **MCP Client = `ClientSession` + `session.call_tool(name, arguments={...})`**
3. **MCP 不是新技术——就是把函数调用包装成了 JSON-RPC 协议。同一个项目里直接 import 更简单。**

---

## 附：真正的 MCP 体验——不是我们 demo 这样

我们的 demo 是"人手动调 Client 代码去测 Server"。真正的 MCP 用法是 **AI 自动发现工具、自动调用**。

### 在 Claude Desktop 中使用 MCP Server

① 写配置文件 `claude_desktop_config.json`：

```json
{
    "mcpServers": {
        "weather": {
            "command": "uv",
            "args": ["run", "python", "mcp_demo/weather_server.py"]
        }
    }
}
```

② 打开 Claude Desktop，跟 Claude 对话：

```
你：北京今天天气怎么样？

Claude：[自动调 tools/list 发现 get_weather]
      [自动调 get_weather(city="北京")，拿到 "晴天 25°C"]
      [生成回答]
      
      北京今天晴天，气温 25°C。
```

**全程你没有写 Client 代码。** 你只是写了一个 Server + 一行配置，剩下全是 AI 自动做的。这才是 MCP 设计的真正使用场景。

### 我们 demo vs 真正 MCP

```
我们 demo:
  Server.py → [stdio JSON-RPC] → test_client.py（人手写调用逻辑）
  人在驱动，测管道通不通

真正的 MCP（Claude Desktop / Cursor / VS Code）:
  Server.py → [stdio JSON-RPC] → AI Host（自动发现 + 自动调用）
  AI 在驱动，人在聊天
```

我们的 demo 让你看清了管道里流的是什么。真正的 MCP 就是这套管道 + AI 自动决策。
