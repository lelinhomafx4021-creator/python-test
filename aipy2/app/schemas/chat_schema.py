"""聊天接口请求与响应模型（给新手看的版本）。

为什么要单独定义模型？
- 能自动校验参数是否合法。
- 能在 FastAPI 文档里自动生成字段说明。
- 出错时返回更清晰的提示。
"""

from typing import Optional

from pydantic import BaseModel, Field


class ChatRequest(BaseModel):
    """聊天请求体。"""

    # 用户输入问题
    message: str = Field(
        min_length=1,
        description="用户问题正文。",
        examples=["帮我分析一下宁德时代近期走势"],
    )
    # 会话线程（决定是否续上下文）
    thread_id: str = Field(
        min_length=1,
        description="会话线程 ID；同一个 thread_id 会共享上下文记忆。",
        examples=["thread_001"],
    )
    # 可选追踪编号（便于日志排查）
    trace_id: Optional[str] = Field(
        default=None,
        description="链路追踪 ID（可选）；不传则后端自动生成。",
        examples=["trace_abc123"],
    )


class ChatResponse(BaseModel):
    """聊天响应体。"""

    trace_id: str = Field(description="本次请求的追踪 ID。")
    answer: str = Field(description="模型最终回答。")
    source: str = Field(description="回答来源说明（例如工作流名称或知识来源）。")
