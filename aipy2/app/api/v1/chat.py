"""
Python AI 能力层接口（纯能力，不做业务会话存储）。

分工说明：
- Java 负责：用户、会话、历史、审计、限流
- Python 负责：LLM 多智能体推理与流式输出
"""

import json
import uuid
import traceback
from typing import Optional

from fastapi import APIRouter
from fastapi.responses import StreamingResponse
from app.services.investor_service import investor_service
from app.schemas.chat_schema import ChatRequest, ChatResponse

router = APIRouter(prefix="/ai/v1", tags=["ai能力层-v1"]) # 增加了 v1 前缀


@router.post("/chat", response_model=ChatResponse)
async def post_chat(req: ChatRequest):
    """
    同步问答：企业中虽然推荐流式，但后台管理系统或特定场景仍需同步接口。
    """
    trace_id = req.trace_id or str(uuid.uuid4())
    # 注意：同步接口在 Service 中也应该有一个对应的非流式方法。
    # 这里为了演示简单，直接封装一层。
    answer_text = ""
    source = []
    async for evt in investor_service.run_investor_flow(
        query=req.message,
        thread_id=req.thread_id,
        trace_id=trace_id
    ):
        if evt["stage"] == "final_answer":
            answer_text = evt["data"]["answer"]
            source = evt["data"]["source"]
            
    return ChatResponse(
        trace_id=trace_id,
        answer=answer_text,
        source=source
    )


@router.post("/chat/stream")
async def post_chat_stream(req: ChatRequest):
    """ SSE 流式输出 """
    trace_id = req.trace_id or str(uuid.uuid4())

    async def event_gen():
        try:
            print(f"[chat_stream] accepted trace_id={trace_id} thread_id={req.thread_id} query={req.message}")
            async for evt in investor_service.run_investor_flow(
                query=req.message,
                thread_id=req.thread_id,
                trace_id=trace_id
            ):
                stage = evt.get("stage", "unknown")
                data = evt.get("data", {}) or {}

                if stage == "final_answer":
                    answer = data.get("answer", "")
                    print(f"[chat_stream] final_answer trace_id={trace_id} len={len(answer)} answer={answer[:300]}")
                elif stage == "error":
                    print(f"[chat_stream] error trace_id={trace_id} msg={data.get('msg', '')}")
                elif "step" in data:
                    print(f"[chat_stream] step trace_id={trace_id} stage={stage} step={data.get('step', '')}")

                payload = json.dumps(evt, ensure_ascii=False)
                yield f"event: message\ndata: {payload}\n\n"
        except Exception as e:
            # 这里的异常处理在企业中应该记录日志并返回标准错误格式
            print(f"[chat_stream] exception trace_id={trace_id} err={e}")
            traceback.print_exc()
            err_payload = json.dumps({"stage": "error", "data": {"msg": str(e) or "Internal Server Error"}})
            yield f"event: message\ndata: {err_payload}\n\n"

    return StreamingResponse(event_gen(), media_type="text/event-stream")
