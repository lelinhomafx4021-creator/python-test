from datetime import datetime
from typing import Optional

from sqlmodel import Field, SQLModel


class ChatTurn(SQLModel, table=True):
    __tablename__ = "ai_chat_turns"

    id: Optional[int] = Field(default=None, primary_key=True)
    user_id: str = Field(index=True, max_length=64)
    session_id: str = Field(index=True, max_length=128)
    thread_id: str = Field(max_length=256)
    trace_id: str = Field(max_length=64)

    query: str
    answer: str

    intent: str = Field(max_length=32)
    source: str = Field(max_length=64)
    review_passed: bool = Field(default=False)

    response_mode: str = Field(default="sync", max_length=16)
    a2a_count: int = Field(default=0)

    created_at: datetime = Field(default_factory=datetime.utcnow, nullable=False)
