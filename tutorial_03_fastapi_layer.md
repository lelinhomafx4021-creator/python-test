# 教程 03：FastAPI 分层架构 —— 从"能跑"到"能用"

## 一句话概念
FastAPI 分层架构就是把代码按**职责**分成不同的文件夹。就像餐厅里，前台负责点单（API 层），后厨负责做菜（Service 层），仓库负责备料（Graph 层）。

---

## 1. 我们的分层结构

```
aipy2/
├── main.py                    # 🏠 入口：启动 FastAPI 应用
├── app/
│   ├── api/                   # 📡 接口层：接收请求，返回响应
│   │   ├── v1/
│   │   │   └── chat.py        #     聊天相关接口
│   │   └── healthy/
│   │       └── healthy.py     #     健康检查接口
│   ├── services/              # 🧠 业务层：调度 AI 逻辑
│   │   └── investor_service.py
│   ├── graph/                 # 🤖 能力层：LangGraph 工作流
│   │   └── investor_graph.py
│   ├── schemas/               # 📋 契约层：数据格式定义
│   │   └── chat_schema.py
│   ├── core/                  # ⚙️ 基础设施：配置、日志、异常
│   │   ├── config.py
│   │   ├── logger.py
│   │   └── exceptions.py
│   └── tools/                 # 🔧 工具层：检索、外部 API
│       └── retriever_tool.py
```

---

## 2. 请求的完整旅程

当前端发来一个 POST 请求时，数据是这样流转的：

```
前端 POST /ai/v1/chat/stream
    │
    ▼
[main.py] → FastAPI 收到请求，路由到 ...
    │
    ▼
[api/v1/chat.py] → post_chat_stream() 接收参数
    │  ↓ 调用
    ▼
[services/investor_service.py] → run_investor_flow() 编排业务
    │  ↓ 调用
    ▼
[graph/investor_graph.py] → MultiGraphInvestorAgent 执行 LangGraph
    │  ↓ 需要检索
    ▼
[tools/retriever_tool.py] → 向量搜索 + BM25 + 联网
    │
    ▼
结果层层返回 → SSE 流推送到前端
```

---

## 3. 每一层到底干什么？

### 3.1 API 层 ([api/v1/chat.py](file:///d:/ai-investor/aipy2/app/api/v1/chat.py))

> **职责**：只管"接"和"发"。接收 HTTP 请求参数，调用 Service，返回结果。

```python
@router.post("/chat/stream")
async def post_chat_stream(req: ChatRequest):  # 接收参数
    trace_id = req.trace_id or str(uuid.uuid4())

    async def event_gen():
        async for evt in investor_service.run_investor_flow(  # 调用 Service
            query=req.message,
            thread_id=req.thread_id,
            trace_id=trace_id
        ):
            payload = json.dumps(evt, ensure_ascii=False)
            yield f"event: message\ndata: {payload}\n\n"  # 返回 SSE

    return StreamingResponse(event_gen(), media_type="text/event-stream")
```

> **规则**：API 层里**不允许**出现任何 AI 逻辑（比如直接调 LLM）。如果看到 `ChatOpenAI` 出现在这一层，说明架构有问题。

### 3.2 Service 层 ([services/investor_service.py](file:///d:/ai-investor/aipy2/app/services/investor_service.py))

> **职责**：编排业务逻辑。未来你可以在这里加限流、计费、权限二次校验等。

```python
class InvestorService:
    @staticmethod
    async def run_investor_flow(query, thread_id, trace_id):
        # 这里可以加业务前置逻辑
        async for event in multi_graph_agent.ask_stream_events(
            query=query, thread_id=thread_id, trace_id=trace_id
        ):
            yield event
```

> **面试点**："为什么不让 API 直接调 Graph？" —— 因为 Service 是**插入业务逻辑的标准位置**。如果以后要加"VIP 用户优先"或"每日调用限额"，只需要在 Service 层加代码，不需要动 API 或 Graph。

### 3.3 Schema 层 ([schemas/chat_schema.py](file:///d:/ai-investor/aipy2/app/schemas/chat_schema.py))

> **职责**：定义数据格式。确保前端传来的数据是合法的。

```python
class ChatRequest(BaseModel):
    message: str           # 用户问题（必填）
    thread_id: str = ""    # 会话线程ID（选填，默认空字符串）
    session_id: str = ""   # 业务会话ID
    trace_id: str = ""     # 链路追踪ID

class ChatResponse(BaseModel):
    trace_id: str
    answer: str
    source: str
```

> **知识点**：Pydantic 的 `BaseModel` 会在**请求进入时自动校验**。如果前端没传 [message](file:///d:/ai-investor/frontend/src/App.vue#56-78) 字段，FastAPI 会自动返回 422 错误，你不需要手动写 `if not message` 这种判断。

---

## 4. 核心概念拆解

### 4.1 `APIRouter` — 路由分组

```python
router = APIRouter(prefix="/ai/v1", tags=["ai能力层-v1"])
```

- `prefix="/ai/v1"`：所有接口自动加上前缀，比如 `/chat` 变成 `/ai/v1/chat`
- `tags=["ai能力层-v1"]`：在 Swagger 文档里分组显示

### 4.2 `StreamingResponse` — SSE 流式输出

```python
return StreamingResponse(event_gen(), media_type="text/event-stream")
```

- [event_gen()](file:///d:/ai-investor/aipy2/app/api/v1/chat.py#52-65) 是一个**异步生成器**，每 `yield` 一次就推送一条消息给前端
- `media_type="text/event-stream"` 告诉浏览器"这是 SSE 协议"
- **面试点**：SSE 是单向的（服务端 → 客户端），比 WebSocket 简单，适合"AI 打字机效果"

### 4.3 [exception_handler](file:///d:/ai-investor/aipy2/main.py#30-38) — 全局异常拦截

```python
@app.exception_handler(AiBaseException)
async def handler(request, exc):
    return JSONResponse(content={"code": exc.code, "message": exc.message})
```

- **作用**：无论代码在哪里抛出 [AiBaseException](file:///d:/ai-investor/aipy2/app/core/exceptions.py#7-13)，都会被这里统一捕获
- **面试点**：这就是 Spring 中 `@ControllerAdvice` 的 Python 等价物

---

## 5. 为什么叫 `v1`？

这是 **API 版本控制**。当你未来需要大改接口时：
- 旧版前端继续调 `/ai/v1/chat`
- 新版前端调 `/ai/v2/chat`
- 两个版本共存，不会互相影响

> 这在企业中叫做**向后兼容 (Backward Compatibility)**，是非常重要的工程实践。

