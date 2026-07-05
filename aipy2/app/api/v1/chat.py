"""Python AI 能力接口定义（新手友好版）。

这个模块提供 2 个接口：
1) `/ai/v1/chat`：同步返回最终答案（一次性拿结果）。
2) `/ai/v1/chat/stream`：SSE 流式返回过程与结果（边生成边推送）。

如果你是前端新手，可以先用同步接口调通，再切到流式接口做“打字机效果”。
"""

import json
import uuid

from fastapi import APIRouter
from fastapi.responses import StreamingResponse

from app.schemas.chat_schema import ChatRequest, ChatResponse
from app.services.investor_service import investor_service

router = APIRouter(prefix="/ai/v1", tags=["AI能力层-v1"])


@router.post("/chat", response_model=ChatResponse)
async def post_chat(req: ChatRequest):
    """非流式对话接口。

    适用场景：
    - 后台任务、管理端页面。
    - 不需要实时展示"思考过程"，只关心最终答案。

    入参：
    - `req.message`：用户问题。
    - `req.thread_id`：会话线程 ID（用于上下文记忆）。
    - `req.trace_id`：可选链路追踪 ID；不传则后端自动生成。
    - `req.role`：用户角色（'normal' 或 'vip'），决定使用哪套 AI 流程。

    返回：
    - `trace_id`：本次请求唯一追踪号。
    - `answer`：最终答案文本。
    - `source`：答案来源说明。
    """
    # 如果上游没传 trace_id，就在这里自动创建一个，方便排查问题。
    trace_id = req.trace_id or str(uuid.uuid4())
    answer_text = ""
    source = ""

    # 统一走 InvestorService 的事件流。
    # 这里的 run_investor_flow 不是一次性返回，而是"边算边吐事件"。
    # `async for` 可以理解成：异步版 for 循环，每来一条事件就处理一条，不会阻塞整个服务。
    # 同步接口做法：把整条事件流读完，只在最终答案事件时提取结果。
    async for evt in investor_service.run_investor_flow(
        query=req.message,
        thread_id=req.thread_id,
        trace_id=trace_id,
        role=req.role,
    ):
        # event 是一个字典，通常长这样：
        # {"stage": "...", "data": {...}}
        # stage == final_answer 表示大模型最终输出已完成。
        if evt["stage"] == "final_answer":
            answer_text = evt["data"]["answer"]
            source = evt["data"].get("source", "") or ""

    return ChatResponse(trace_id=trace_id, answer=answer_text, source=source)


@router.post("/chat/stream")
async def post_chat_stream(req: ChatRequest):
    """SSE 流式对话接口。

    适用场景：
    - 聊天界面需要实时显示“正在思考”“逐字输出”。

    关键点：
    - 返回类型是 `text/event-stream`。
    - 每条消息格式：
      `event: message`
      `data: {...json...}`
    """
    trace_id = req.trace_id or str(uuid.uuid4())

    async def event_gen():
        """将内部事件流转换为 SSE 输出。"""
        try:
            # 关键点：流式接口不会等"最终答案"才返回。
            # 而是每收到一个 event 就立刻 yield 给前端，前端就能实时渲染。
            async for evt in investor_service.run_investor_flow(
                query=req.message,
                thread_id=req.thread_id,
                trace_id=trace_id,
                role=req.role,
            ):
                # SSE 的 data 建议传 JSON，前端解析稳定。
                payload = json.dumps(evt, ensure_ascii=False)
                # `yield` 在这里就像“推送一帧消息”给浏览器。
                yield f"event: message\ndata: {payload}\n\n"
        except Exception as exc:
            # 流式接口不能直接抛异常到前端，因此要把错误也包装成一条事件。
            err_payload = json.dumps(
                {"stage": "error", "data": {"msg": str(exc) or "服务器内部错误"}},
                ensure_ascii=False,
            )
            yield f"event: message\ndata: {err_payload}\n\n"

    # 这组 header 对 SSE 很关键：
    # - no-cache/no-transform：避免中间层缓存或篡改流内容。
    # - keep-alive：保持长连接不断开。
    # - X-Accel-Buffering: no：告诉 Nginx 不要缓冲，立刻向前端推送。
    return StreamingResponse(
        event_gen(),
        media_type="text/event-stream; charset=utf-8",
        headers={
            "Cache-Control": "no-cache, no-transform",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )
