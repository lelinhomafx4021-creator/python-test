"""
███ AI 运行技术审计模型 ███

============================================================
【功能说明】每次 AI 执行一次任务（回答用户问题），
都会在这张表里留一条"技术审计记录"。
这张表不是为了存聊天内容（那是 ai_chat_turns 的职责），
而是记录技术指标：耗时、Token、重试、评审结论等。

用途：
- 性能优化：看看哪类问题耗时最长
- 成本控制：统计 Token 用量，估算 API 费用
- 质量分析：Review 通过率、重试频率
- 故障排查：Trace ID 串联前后端日志
============================================================

===== 学习要点 =====

Q: SQLModel 和 SQLAlchemy 是什么关系？
A: SQLModel = Pydantic（数据校验）+ SQLAlchemy（数据库操作）。
   table=True 告诉 SQLModel："这个类对应数据库的一张表"。
   Field() 可以同时定义数据库列约束（index=True）和业务描述（description="..."）。
   面试金句："SQLModel 一个类同时充当 ORM 映射、数据校验和 API 文档三种角色。"
"""

from datetime import datetime
from typing import Optional

from sqlmodel import Field, SQLModel


class AgentRunAudit(SQLModel, table=True):
    """
    记录一次 Agent 运行的技术审计信息。

    每一条记录 = 一次完整的 AI 工作流执行。
    包含：谁问的、问了什么、AI 怎么处理的、用了多少资源、结果如何。
    """

    __tablename__ = "ai_agent_runs"

    # ── 关联标识 ──
    # 这些字段用于关联"这条记录是谁在什么时候产生的"。

    id: Optional[int] = Field(default=None, primary_key=True)  # 自增主键

    trace_id: str = Field(
        index=True,                # 建索引 → 查询快
        max_length=64,             # 长度限制 → 防止数据异常
        description="链路追踪 ID"   # 描述 → 自动生成 API 文档
    )
    # Trace ID 是排查问题的"黄金线索"。
    # 前端 → 网关 → Python → AI → 数据库：同一个 ID 打通整个调用链。

    thread_id: str = Field(
        index=True,
        max_length=256,
        description="线程 ID"
    )
    # Thread ID 用来区分不同的对话窗口。
    # 同一个 thread_id 的多次调用会共享 AI 的上下文记忆。

    user_id: str = Field(
        default="customer_pro",
        index=True,
        max_length=64
    )

    # ── 输入输出 ──
    query: str = Field(description="用户问题")              # 用户问了什么
    final_answer: str = Field(default="", description="最终答案")  # AI 回答了什么

    # ── 运行状态 ──
    status: str = Field(
        default="success",         # 默认成功
        max_length=32,
        description="运行状态"      # success / error / handoff
    )
    # status 的三种取值：
    # - success：顺利跑完
    # - error：过程中抛异常了
    # - handoff：AI 认为应该转人工处理

    error_message: str = Field(default="", description="错误信息")
    response_mode: str = Field(
        default="stream",          # 默认流式
        max_length=16,
        description="响应模式"      # sync（同步）/ stream（流式）
    )
    source: str = Field(
        default="Self-RAG-v2",
        max_length=64,
        description="运行来源"      # 标识是哪个工作流引擎产生的
    )

    # ── 过程指标 ──
    # 这些是生产环境最关注的"成本与质量"指标。

    use_kb: bool = Field(
        default=False,
        description="是否走了知识库分支"
    )
    # use_kb 区分两类问题：
    # True → 投研问题，走了完整的检索→回答→评审流程
    # False → 闲聊问题，直接回答了

    retry_count: int = Field(
        default=0,
        description="重试次数"
    )
    # 记下重试次数很重要：
    # - retry_count 高 → 用户问题太难，或知识库资料不够
    # - retry_count ≥ 3 → 触发人工兜底

    total_tokens: int = Field(
        default=0,
        description="总 token 消耗"
    )
    # Token 就是钱！累积统计可以估算 API 成本。

    # ── 性能指标 ──
    # 面试常问："你们怎么衡量 AI 服务的响应速度？" → 就看这几个字段。

    first_step_ms: int = Field(
        default=0,
        description="第一条步骤事件耗时"
    )
    # 从用户发送到第一个非内容事件（如"正在判断意图"）的毫秒数。
    # 衡量的是"系统启动响应"的速度。

    first_content_ms: int = Field(
        default=0,
        description="第一段正文耗时"
    )
    # 从用户发送到第一个内容 token（真正的回答文字）的毫秒数。
    # 这是用户体验的核心指标："等了多久才看到第一句话"。
    # 业界通常要求 < 500ms 才算"秒回体验"。

    duration_ms: int = Field(
        default=0,
        description="总耗时"
    )
    # 完整一次对话的总耗时，单位毫秒。

    # ── 质量指标 ──
    stage_path: str = Field(
        default="[]",
        description="阶段序列 JSON"
    )
    # 例如：["accepted","intent","rewrite","search","answer","critic","final_answer","done"]
    # 用 JSON 数组存，方便分析哪些路径最常见。

    critic_feedback: str = Field(
        default="",
        description="评审反馈"
    )
    # 评审员给出的具体问题，如"答案未覆盖用户问的时间维度"

    review_status: str = Field(
        default="",
        max_length=32,
        description="评审结论"     # pass / fail / handoff
    )

    # ── 时间信息 ──
    created_at: datetime = Field(
        default_factory=datetime.utcnow,  # 自动填入当前时间
        nullable=False
    )
    finished_at: Optional[datetime] = Field(
        default=None,
        description="结束时间"
    )
    # created_at 和 finished_at 同时存在 → 可以精确计算处理时长
    # Optional 表示收尾阶段才填入，创建时不填
