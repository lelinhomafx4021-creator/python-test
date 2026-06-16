# 37_MCP协议详解：基于官方规范 2025-11-25 版的完整教学

> **核心目标**: 读懂 MCP 官方规范，能自己写 MCP Server 和 Client
> **参考来源**: `modelcontextprotocol.io` 官方规范 v2025-11-25（Latest Stable）
> **项目关联**: 项目目前工具直接 import 调用，未使用 MCP。MCP 是 2026 年 AI 集成的事实标准，面试必考。

---

## 一、MCP 解决什么问题

### 1.1 一句话

**MCP = AI 应用连接外部工具的标准化协议。** 相当于 USB-C 统一充电接口、JDBC 统一数据库访问——一套协议，连接所有工具。

### 1.2 官方定义的四个角色

```
┌────────────────────────────────────────────────────────────┐
│  MCP Host（宿主应用）                                        │
│  例：Claude Desktop, VS Code, Cursor, 你的 FastAPI 应用      │
│  用户直接交互的程序。内部包含一个或多个 MCP Client。           │
│                                                             │
│  ┌───────────────────────────────────────────────┐         │
│  │ MCP Client（协议客户端）                        │         │
│  │ 与 MCP Server 维持 1:1 连接                    │         │
│  │ 负责：发送 JSON-RPC 请求、接收响应、管理生命周期  │         │
│  └───────────────────────────────────────────────┘         │
│                         ↕ JSON-RPC                         │
│  ┌───────────────────────────────────────────────┐         │
│  │ MCP Server（工具服务端）                        │         │
│  │ 暴露 Tools / Resources / Prompts               │         │
│  │ 负责：执行工具、提供数据、返回结果                │         │
│  └───────────────────────────────────────────────┘         │
└────────────────────────────────────────────────────────────┘
```

**Host vs Client 的区别**：Host 是用户看到的程序（如 Claude Desktop），Client 是程序内部的协议通信层。一个 Host 可以连接多个 MCP Server，每个 Server 对应一个独立的 Client 实例。

---

## 二、核心协议：JSON-RPC 2.0

MCP 的全部通信基于 JSON-RPC 2.0——一种用 JSON 格式进行远程过程调用的轻量协议。

### 2.1 四种消息类型

| 类型 | 方向 | 有 `id` 吗 | 要回复吗 | 用途 |
|------|------|-----------|---------|------|
| **Request（请求）** | 双向 | 有 | 必须回复 Response | 调用方法、查询数据 |
| **Response（响应）** | 双向 | 与请求相同 | 不 | 返回成功结果 |
| **Error（错误响应）** | 双向 | 与请求相同 | 不 | 返回错误信息 |
| **Notification（通知）** | 双向 | **无** | **不** | 单向通知，无需回复 |

### 2.2 消息格式详解

**Request（请求）**：
```json
{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
        "name": "get_weather",
        "arguments": {"city": "北京"}
    }
}
```

| 字段 | 必需 | 说明 |
|------|------|------|
| `jsonrpc` | ✅ | 固定 `"2.0"` |
| `id` | ✅ | 请求编号，可以是 string 或 number。Response 必须带相同 id 以匹配 |
| `method` | ✅ | 方法名，MCP 定义了一套标准方法（如 `tools/list`） |
| `params` | 否 | 方法参数，结构化 JSON |

**Response（成功响应）**：
```json
{
    "jsonrpc": "2.0",
    "id": 1,
    "result": {
        "content": [
            {"type": "text", "text": "北京今天晴天，25°C"}
        ]
    }
}
```

**Error（错误响应）**：
```json
{
    "jsonrpc": "2.0",
    "id": 1,
    "error": {
        "code": -32601,
        "message": "Method not found: bad_method"
    }
}
```

JSON-RPC 标准错误码：
| 错误码 | 含义 |
|--------|------|
| `-32700` | Parse error（JSON 格式错误） |
| `-32600` | Invalid Request（不是有效的 JSON-RPC） |
| `-32601` | Method not found（方法不存在） |
| `-32602` | Invalid params（参数类型错误） |
| `-32603` | Internal error（服务器内部错误） |

### 2.3 Notification（通知）

```json
{
    "jsonrpc": "2.0",
    "method": "notifications/tools/list_changed"
}
```

**通知没有 `id` 字段**——收到方不需要回复。用于"嘿，我的工具列表变了，你该重新查询了"这类单向信号。

---

## 三、MCP 会话生命周期

```
客户端                                    服务器
  │                                         │
  │──── initialize ──────────────────────→  │  ← 握手：交换协议版本和能力
  │  {                                      │
  │    "method": "initialize",              │
  │    "params": {                          │
  │      "protocolVersion": "2025-11-25",   │
  │      "capabilities": {},                │
  │      "clientInfo": {"name": "...",      │
  │                     "version": "..."}   │
  │    }                                    │
  │  }                                      │
  │                                         │
  │←─── initialize response ─────────────── │  ← 服务器返回自己的能力和版本
  │  {                                      │
  │    "result": {                          │
  │      "protocolVersion": "2025-11-25",   │
  │      "capabilities": {"tools": {}},     │  ← 声明"我支持工具调用"
  │      "serverInfo": {"name": "weather",  │
  │                     "version": "1.0"}   │
  │    }                                    │
  │  }                                      │
  │                                         │
  │──── notifications/initialized ───────→  │  ← 客户端通知：初始化完成
  │                                         │
  │═════════ 进入操作阶段 ════════════════  │
  │                                         │
  │──── tools/list ──────────────────────→  │  ← 查询有哪些工具
  │←─── tools/list response ────────────── │
  │                                         │
  │──── tools/call ──────────────────────→  │  ← 调用工具
  │←─── tools/call response ────────────── │
  │                                         │
  │═════════ 会话结束 ════════════════════  │
```

**关键规则**：
1. 客户端必须先发 `initialize`，服务器响应后客户端发 `notifications/initialized`
2. 在 `initialized` 通知发出之前，客户端**不应该**发非 ping 的请求；服务器**不应该**发非 ping/非 logging 的请求
3. 操作阶段双方可以双向发消息
4. 协商的 capabilities 决定了会话中哪些功能可用——**不得使用未协商的功能**

**版本协商**：客户端在 `initialize` 里发送它支持的最新版本。如果服务器支持这个版本，回复相同版本。如果不支持，回复另一个版本。如果客户端不支持服务器回复的版本，**应该**断开连接。

**超时机制**：实现**应该**为每个发出的请求设置超时。超时未收到响应时，发送方**应该**发出取消通知并停止等待。收到进度通知时可以重置超时时钟（说明工作在进行中），但**应该**始终保底一个最大超时来限制异常行为的影响。

**HTTP 传输额外要求**：客户端**必须**在所有后续请求中包含 HTTP 头 `MCP-Protocol-Version: <协议版本>`。

---

## 四、三大原语（Primitives）

官方定义了三种原语，分别服务于不同的控制方：

| 原语 | 控制方 | 作用 | 类比 |
|------|--------|------|------|
| **Tools** | **模型控制**（Model-controlled） | LLM 决定何时调用、传什么参数 | REST POST——执行操作 |
| **Resources** | **应用控制**（Application-controlled） | 应用决定何时读取什么数据 | REST GET——读取数据 |
| **Prompts** | **用户控制**（User-controlled） | 用户选择使用哪个提示词模板 | 预设的工作流模板 |

### 4.1 Tools（工具）— Model-controlled

**定义**：可执行的函数，LLM 自主决策调用。每个 Tool 用 JSON Schema 定义输入参数。

**Tool 的完整字段（官方规范）**：

| 字段 | 必需 | 说明 |
|------|------|------|
| `name` | ✅ | 唯一标识，1-128 字符，允许 A-Za-z0-9 _ - . |
| `title` | 否 | 人类可读的显示名称 |
| `description` | ✅(建议) | 功能描述，LLM 据此决定何时调用 |
| `inputSchema` | ✅ | JSON Schema，默认 2020-12。无参数工具用 `{"type":"object","additionalProperties":false}` |
| `outputSchema` | 否 | 输出结构的 JSON Schema，用于校验返回结果 |
| `icons` | 否 | 图标数组（用于 UI 展示） |
| `annotations` | 否 | 工具行为描述（如只读/破坏性）。**客户端应将非受信来源的 annotations 视为不可信。** |
| `execution.taskSupport` | 否 | `"forbidden"`(默认) / `"optional"` / `"required"` — 是否支持异步任务模式 |

**协议方法**：

`tools/list` — 获取工具列表：
```json
// Request
{"jsonrpc": "2.0", "id": 1, "method": "tools/list", "params": {}}

// Response
{
    "jsonrpc": "2.0",
    "id": 1,
    "result": {
        "tools": [
            {
                "name": "get_weather",
                "description": "获取指定城市的实时天气",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "city": {
                            "type": "string",
                            "description": "城市名称"
                        }
                    },
                    "required": ["city"]
                }
            }
        ]
    }
}
```

`tools/call` — 调用工具：
```json
// Request
{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/call",
    "params": {
        "name": "get_weather",
        "arguments": {"city": "北京"}
    }
}

// Response（成功）
{
    "jsonrpc": "2.0",
    "id": 2,
    "result": {
        "content": [
            {"type": "text", "text": "北京今天晴天，25°C"}
        ],
        "isError": false
    }
}
```

**工具执行错误**：工具内部的业务错误**不通过 JSON-RPC Error 返回**，而是通过 `isError: true` 标记：

```json
{
    "jsonrpc": "2.0",
    "id": 2,
    "result": {
        "content": [
            {"type": "text", "text": "Invalid date: must be in the future."}
        ],
        "isError": true
    }
}
```

**区别**：
- `isError: true` = 工具成功执行了，但业务逻辑报错（如日期不在未来）。官方规范要求客户端**应该**把这类错误传给 LLM——因为 LLM 看到"日期不在未来"可以自我修正，换个日期重试。
- JSON-RPC Error = 协议层面出了问题（如工具名不存在、`inputSchema` 校验失败）。客户端**可以**传给 LLM，但 LLM 不太可能修复协议错误。

**结构化输出（官方规范新特性）**：工具可以同时返回非结构化文本和结构化 JSON：
```json
{
    "result": {
        "content": [{"type": "text", "text": "{\"temp\":22.5}"}],  // 向后兼容
        "structuredContent": {"temperature": 22.5, "conditions": "晴"},  // 程序直接读
        "isError": false
    }
}
```
`outputSchema` 字段可以声明结构化输出的期望格式，客户端应该据此校验。

**动态更新通知**：
```json
{"jsonrpc": "2.0", "method": "notifications/tools/list_changed"}
```
服务器声明了 `listChanged` 能力后，工具列表有变化时必须发这个通知。

### 4.2 Resources（资源）— Application-controlled

**定义**：只读数据源，应用代码决定何时读取。**没有副作用**——和 Tools 的本质区别。

**协议方法**：

`resources/list` — 获取资源列表：
```json
// Request
{"jsonrpc": "2.0", "id": 3, "method": "resources/list", "params": {}}

// Response
{
    "jsonrpc": "2.0",
    "id": 3,
    "result": {
        "resources": [
            {
                "uri": "file:///docs/茅台2024年报.pdf",
                "name": "茅台2024年报",
                "description": "贵州茅台2024年年度报告",
                "mimeType": "application/pdf"
            }
        ]
    }
}
```

`resources/read` — 读取资源内容：
```json
// Request
{
    "jsonrpc": "2.0",
    "id": 4,
    "method": "resources/read",
    "params": {"uri": "file:///docs/茅台2024年报.pdf"}
}

// Response
{
    "jsonrpc": "2.0",
    "id": 4,
    "result": {
        "contents": [
            {
                "uri": "file:///docs/茅台2024年报.pdf",
                "mimeType": "application/pdf",
                "text": "贵州茅台2024年营业收入..."
            }
        ]
    }
}
```

### 4.3 Prompts（提示词模板）— User-controlled

**定义**：预定义的提示词模板，由**用户选择**使用，而非 LLM 或应用代码决定。

**协议方法**：

`prompts/list` — 获取提示词模板列表：
```json
// Request
{"jsonrpc": "2.0", "id": 5, "method": "prompts/list", "params": {}}

// Response
{
    "jsonrpc": "2.0",
    "id": 5,
    "result": {
        "prompts": [
            {
                "name": "analyze_stock",
                "description": "分析一只股票的投资价值",
                "arguments": [
                    {
                        "name": "symbol",
                        "description": "股票代码",
                        "required": true
                    },
                    {
                        "name": "depth",
                        "description": "分析深度：quick/detailed",
                        "required": false
                    }
                ]
            }
        ]
    }
}
```

`prompts/get` — 获取提示词模板内容：
```json
// Request
{
    "jsonrpc": "2.0",
    "id": 6,
    "method": "prompts/get",
    "params": {
        "name": "analyze_stock",
        "arguments": {"symbol": "600519", "depth": "detailed"}
    }
}

// Response
{
    "jsonrpc": "2.0",
    "id": 6,
    "result": {
        "description": "分析股票投资价值",
        "messages": [
            {"role": "user", "content": {"type": "text", "text": "请分析股票600519，要求分析深度：detailed"}}
        ]
    }
}
```

---

## 五、传输层

MCP 官方定义了两类传输方式：

### 5.1 stdio（标准输入/输出）

- 本地通信，Host 用 `subprocess` 启动 Server 进程
- Server 从 stdin 读 JSON-RPC，写到 stdout
- **性能最高**——无网络开销
- **单客户端天然隔离**——每个 Server 进程只连一个 Client
- 适用：本地工具（文件系统、数据库、CLI 命令）

```python
# stdio Server 的主循环（概念代码）
import sys, json

for line in sys.stdin:
    request = json.loads(line.strip())
    response = handle(request)
    sys.stdout.write(json.dumps(response) + "\n")
    sys.stdout.flush()
```

### 5.2 Streamable HTTP（远程传输）

- 远程通信，HTTP + SSE 组合
- 支持多客户端并发
- 适用：需要远程访问的服务、团队共享的工具

---

## 六、安全要求（官方规范原文）

服务器 **MUST**：
1. 校验所有工具输入（Validate all tool inputs）
2. 实现适当的访问控制（Implement proper access controls）
3. 限流工具调用（Rate limit tool invocations）
4. 净化工具输出（Sanitize tool outputs）

客户端 **SHOULD**：
1. 对敏感操作提示用户确认（Prompt for user confirmation on sensitive operations）
2. 调用前向用户展示工具输入——防止恶意或意外的数据泄露
3. 传递结果给 LLM 前校验（Validate tool results before passing to LLM）
4. 为工具调用设置超时（Implement timeouts for tool calls）
5. 记录工具使用日志用于审计（Log tool usage for audit purposes）

**官方对 HITL 的要求**：对于信任和安全，**始终应该有**一个人在回路中（human in the loop），有能力拒绝工具调用。应用**应该**清晰展示哪些工具暴露给了 AI 模型，在工具被调用时插入明显的视觉指示器，对操作向用户展示确认提示。

---

## 七、写一个完整的 MCP Server（Python）

基于官方 SDK `mcp` 包的写法：

```python
# weather_server.py
from mcp.server import Server, stdio_server
from mcp.types import Tool, TextContent
import asyncio

# 1. 创建 Server 实例
server = Server("weather-server")

# 2. 注册工具处理器
@server.tool()
async def get_weather(city: str) -> list[TextContent]:
    """获取指定城市的实时天气。当用户询问天气时调用。

    Args:
        city: 城市名称，如 "北京"、"上海"
    """
    # 实际项目中这里调天气 API
    weather = f"{city}今天晴天，25°C，湿度45%"
    return [TextContent(type="text", text=weather)]

# 3. 注册资源
@server.resource("file:///docs/readme.txt")
async def readme() -> str:
    return "这是项目的 README 文件内容..."

# 4. 注册提示词模板
@server.prompt()
async def analyze_stock(symbol: str, depth: str = "quick") -> list:
    return [
        {"role": "user", "content": f"分析股票 {symbol}，深度：{depth}"}
    ]

# 5. 启动（stdio 模式）
async def main():
    async with stdio_server() as (read_stream, write_stream):
        await server.run(
            read_stream,
            write_stream,
            server.create_initialization_options()
        )

asyncio.run(main())
```

**SDK 自动处理的事**（你不需要手写 JSON-RPC）：
- 收到 `tools/list` → 自动返回所有 `@mcp.tool()` 注册的工具的 inputSchema
- 收到 `tools/call` → 自动解析参数、执行函数、返回 content
- 收到 `initialize` → 自动完成能力协商
- 输入校验：SDK 基于函数的类型注解自动验证参数

### 这个 Server 怎么被 AI 真正使用

**不是让你写 Client 代码手动调它。** 真正的 MCP 用法是：

#### 配置 Claude Desktop（一行 JSON）

```json
// claude_desktop_config.json
{
    "mcpServers": {
        "weather": {
            "command": "uv",
            "args": ["run", "python", "weather_server.py"]
        }
    }
}
```

配置后，打开 Claude Desktop 跟 Claude 聊天：

```
你："北京今天天气怎么样？"

Claude 自动做的事：
  ① 启动 weather_server.py 子进程
  ② 调 tools/list 发现 get_weather 工具
  ③ 理解你的问题 → 决定调 get_weather(city="北京")
  ④ 拿到结果 → 生成回答："北京今天晴天，25°C"

全程你没有写一行 Client 代码。
你写了一个 Server + 一行 JSON 配置，剩下全是 AI 自动完成的。
```

**这就是 MCP 和普通 REST API 的核心区别**：REST API 是人看文档然后写调用代码。MCP 是 AI 自己 `tools/list` 发现，自己决定调用。

### 我们 demo 为什么要写 Client

`test_client.py` 是为了教学——让你看清管道里流的是什么 JSON-RPC 消息。真正的使用场景不需要写 Client，AI Host（Claude Desktop、Cursor、VS Code）自带了 MCP Client。

---

## 八、MCP 的本质：就是普通的 Tool，套了一层标准协议

**你感觉对了——MCP 不是什么魔法。**

```
不用 MCP（项目现在的做法）：
  @tool
  def get_weather(city: str) -> str:
      ...
  llm.bind_tools([get_weather])

用 MCP：
  @server.tool()
  def get_weather(city: str) -> str:
      ...
  # MCP Server 通过 JSON-RPC 暴露这个工具
  # MCP Client 通过 tools/list 发现 → tools/call 调用
```

**两边做的事情完全一样**：定义一个函数 → 把函数的签名（JSON Schema）告诉 LLM → LLM 决定调用 → 执行函数 → 返回结果。

### 和我们现在调 Tavily 对比——更直观

```python
# ===== 现在：直接调 Tavily REST API =====
resp = requests.post("https://api.tavily.com/search",
    json={"query": "茅台", "search_depth": "advanced"},
    headers={"Authorization": "Bearer xxx"}
)
data = resp.json()
# → {"results": [{"title": "...", "content": "..."}]}


# ===== 如果 Tavily 提供 MCP 接口：用 tools/call 调 =====
result = await session.call_tool("tavily_search", arguments={
    "query": "茅台",
    "search_depth": "advanced"
})
# → {"content": [{"type": "text", "text": "{\"results\": [...]}"}]}


# 区别只有三个：
# ① 前者用 HTTP POST + URL 表达"我要调什么"
#    后者用 JSON-RPC method="tools/call" + name 字段表达
# ② 前者返回格式 Tavily 自己定（{"results": [...]}）
#    后者返回格式 MCP 统一规定（{"content": [{"type": "text", ...}]}）
# ③ 前者靠看文档知道有什么 API
#    后者靠 tools/list 自动发现
```

**本质上是同一件事——"调用一个远程函数，传参数，拿结果"。MCP 只是在外面套了一层统一的 JSON-RPC 信封。**

**MCP 多出来的东西**：
1. 把工具定义格式统一成标准 JSON Schema（`tools/list` 的返回格式）
2. 把调用方式统一成标准 JSON-RPC（`tools/call` 的请求/响应格式）
3. 加了一套生命周期管理（initialize/initialized/shutdown）
4. 加了一套动态通知（`tools/list_changed` 等）

**什么时候这些"多出来的东西"有价值**：

```
场景 A：你一个人开发，3 个工具，都在同一个代码仓库里
  → 直接 @tool + import 调用。MCP 是过度设计。

场景 B：10 个团队，50 个工具，分布在不同的代码仓库和服务里
  → MCP 的价值来了：
    - 不需要知道工具是用 Python 还是 TypeScript 写的
    - 不需要知道工具部署在哪台机器上
    - 新工具上线，Client 端通过 tools/list 自动发现，不需要改代码
    - 工具改参数，只要 inputSchema 更新就行，Client 端不用重新部署
```

**一句话**：MCP 不创造新能力，它解决的是"当工具有很多、来自不同团队、部署在不同地方时，怎么让所有人都能互相调用"的问题。

---

## 九、MCP 和 Function Calling 的关系

```
Function Calling  →  LLM 运行时决定"现在该调哪个函数"（决策层）
MCP              →  标准化"有哪些函数可用"和"怎么调用"（协议层）

两者是互补的，不是替代关系：
  ① MCP Server 暴露工具 → 
  ② Client 通过 tools/list 获取工具列表（含 JSON Schema）→
  ③ 工具列表传给 LLM → 
  ④ LLM 通过 Function Calling 决定调哪个 → 
  ⑤ Client 通过 tools/call 执行工具 → 
  ⑥ 结果返回给 LLM
```

---

## 十、面试速记

**Q: MCP 是不是就是普通的 Tool 套了一层协议？**
A: 本质上是。MCP 没创造新能力——Tool 的定义和执行逻辑和 `@tool` 装饰器做的事一样。MCP 的价值在标准化：当工具有 50 个、来自 10 个团队、用不同语言写、部署在不同服务器上时，没有标准协议意味着每个团队都要写适配代码。MCP 的 `tools/list` + `tools/call` 就是这套标准——不是什么新技术，是工程上的"统一度量衡"。

**Q: MCP 的三个原语分别是什么？谁控制它们？**
A: Tools（模型控制，LLM 决定何时调用）、Resources（应用控制，代码决定何时读取，无副作用）、Prompts（用户控制，用户选择模板）。三种控制方覆盖了 AI 应用集成的全部场景。

**Q: tools/call 的错误怎么区分是协议错误还是业务错误？**
A: 协议错误（工具名不存在、JSON格式错）走 JSON-RPC Error 响应。工具内部的业务错误（日期不在未来、查不到数据）走 `result.isError: true`。前者是"协议出问题了"，后者是"工具正常执行了但业务不满足"。

**Q: MCP 用的是什么通信协议？**
A: JSON-RPC 2.0。四种消息类型：Request（有id，要回复）、Response（有id，成功结果）、Error（有id，错误结果）、Notification（无id，不回复）。传输层支持 stdio（本地子进程）和 Streamable HTTP（远程）。

**Q: 你怎么向面试官证明你理解 MCP 而不是只背了概念？**
A: 我会说三个细节。第一，`notifications/initialized` 必须先于任何操作请求发出——这是初始化握手的规则。第二，`tools/list_changed` 通知让 Server 可以动态增删工具，Client 不需要重启。第三，安全要求里 Server 必须校验输入、限流、净化输出——MCP 不只是定义接口，还定义了生产级 Server 应该做什么。
