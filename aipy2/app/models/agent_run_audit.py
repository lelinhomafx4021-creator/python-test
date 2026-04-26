"""AI 运行技术审计模型。"""

from datetime import datetime
from typing import Optional

from sqlmodel import Field, SQLModel


class AgentRunAudit(SQLModel, table=True):
    """记录一次 Agent 运行的技术审计信息。"""

    __tablename__ = "ai_agent_runs"

    # 关联标识
    id: Optional[int] = Field(default=None, primary_key=True)
    trace_id: str = Field(index=True, max_length=64, description="链路追踪 ID")
    thread_id: str = Field(index=True, max_length=256, description="线程 ID")
    user_id: str = Field(default="customer_pro", index=True, max_length=64)

    # 输入输出
    query: str = Field(description="用户问题")
    final_answer: str = Field(default="", description="最终答案")

    # 运行状态
    status: str = Field(default="success", max_length=32, description="运行状态")
    error_message: str = Field(default="", description="错误信息")
    response_mode: str = Field(default="stream", max_length=16, description="响应模式")
    source: str = Field(default="Self-RAG-v2", max_length=64, description="运行来源")

    # 过程指标
    use_kb: bool = Field(default=False, description="是否走了知识库分支")
    retry_count: int = Field(default=0, description="重试次数")
    total_tokens: int = Field(default=0, description="总 token 消耗")

    first_step_ms: int = Field(default=0, description="第一条步骤事件耗时")
    first_content_ms: int = Field(default=0, description="第一段正文耗时")
    duration_ms: int = Field(default=0, description="总耗时")

    stage_path: str = Field(default="[]", description="阶段序列 JSON")
    critic_feedback: str = Field(default="", description="评审反馈")
    review_status: str = Field(default="", max_length=32, description="评审结论")

    # 时间信息
    created_at: datetime = Field(default_factory=datetime.utcnow, nullable=False)
    finished_at: Optional[datetime] = Field(default=None, description="结束时间")
