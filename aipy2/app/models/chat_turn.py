"""对话轮次数据模型。"""

from datetime import datetime
from typing import Optional

from sqlmodel import Field, SQLModel


class ChatTurn(SQLModel, table=True):
    """聊天记录表，存储每轮问答与审计信息。"""

    __tablename__ = "ai_chat_turns"

    # 基础标识字段
    id: Optional[int] = Field(default=None, primary_key=True, description="主键ID")
    user_id: str = Field(index=True, max_length=64, description="用户ID")
    session_id: str = Field(index=True, max_length=128, description="会话ID")
    thread_id: str = Field(max_length=256, description="线程ID")
    trace_id: str = Field(max_length=64, description="链路追踪ID")

    # 问答主体
    query: str = Field(description="用户问题")
    answer: str = Field(description="AI回答")

    # 业务审计信息
    intent: str = Field(max_length=32, description="识别意图")
    source: str = Field(max_length=64, description="答案来源")
    review_passed: bool = Field(default=False, description="评审是否通过")

    # 响应模式与调用统计
    response_mode: str = Field(default="sync", max_length=16, description="响应模式")
    a2a_count: int = Field(default=0, description="A2A 调用次数")

    # 时间戳
    created_at: datetime = Field(default_factory=datetime.utcnow, nullable=False, description="创建时间")
