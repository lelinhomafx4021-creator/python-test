from typing import Optional
from pydantic import BaseModel, Field

class ChatRequest(BaseModel):
    """Java -> Python 的标准请求体。"""
    message: str = Field(min_length=1, description="用户问题")
    thread_id: str = Field(min_length=1, description="Java 侧生成的会话线程ID")
    trace_id: Optional[str] = Field(default=None, description="链路追踪ID")

class ChatResponse(BaseModel):
    """同步响应模型。"""
    trace_id: str
    answer: str
    source: str
