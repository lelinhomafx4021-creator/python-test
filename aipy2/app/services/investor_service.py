"""
AI 投研业务编排层。
职责：
1. 调用 LangGraph Agent 流程
2. 将事件流透传给上游接口
3. 在后台落一份 Python 侧技术审计记录
"""

import asyncio
import json
import time
from datetime import datetime
from typing import AsyncGenerator

from langfuse.langchain import CallbackHandler
from sqlmodel import Session

from app.core.config import settings
from app.core.db import engine
from app.core.logger import logger
from app.graph.investor_graph import multi_graph_agent
from app.models.agent_run_audit import AgentRunAudit


class InvestorService:
    """投研业务服务类。"""

    async def run_investor_flow(
        self,
        query: str,
        thread_id: str,
        trace_id: str,
    ) -> AsyncGenerator:
        """
        运行投研工作流，并持续产出流式事件。

        设计要点：
        - 一边消费 Agent 的流式事件，一边 yield 给调用方
        - 在 finally 中异步落库，确保成功/失败都记录审计
        """
        # Langfuse 回调按配置决定是否启用（观测链路可选，不影响主流程）。
        callbacks = []
        if settings.LANGFUSE_ENABLED and settings.LANGFUSE_PUBLIC_KEY:
            langfuse_handler = CallbackHandler(
                public_key=settings.LANGFUSE_PUBLIC_KEY,
                secret_key=settings.LANGFUSE_SECRET_KEY,
                host=settings.LANGFUSE_HOST,
                user_id="customer_pro",
                session_id=thread_id,
                trace_name="investor_chat",
            )
            callbacks.append(langfuse_handler)

        # 以下字段用于最终审计落库，过程中会被事件逐步更新。
        final_answer = ""
        status = "success"
        error_message = ""
        source = "AI 投研闭环引擎 (Self-RAG v2)"
        total_tokens = 0
        use_kb = False
        retry_count = 0
        review_status = ""
        critic_feedback = ""
        stages: list[str] = []

        # 记录业务开始时间与性能起点。
        started_at = datetime.utcnow()
        perf_started = time.perf_counter()
        first_step_at = None
        first_content_at = None

        try:
            # 从图代理获取流式事件，逐条透传给上游。
            async for event in multi_graph_agent.ask_stream_events(
                query=query,
                thread_id=thread_id,
                callbacks=callbacks,
            ):
                # stage_path 用于还原本次运行经过了哪些阶段。
                stage = event.get("stage", "")
                stages.append(stage)

                # first_step_ms：首个“非内容/非结束”步骤耗时，衡量流程启动速度。
                if stage not in {"content_delta", "final_answer", "done"} and first_step_at is None:
                    first_step_at = time.perf_counter()
                # first_content_ms：首个内容增量耗时，衡量首 token 体验。
                if stage == "content_delta" and first_content_at is None:
                    first_content_at = time.perf_counter()

                # 最终答案阶段补齐统计信息，供审计分析使用。
                if stage == "final_answer":
                    data = event.get("data", {})
                    final_answer = data.get("answer", "")
                    source = data.get("source", source)
                    total_tokens = data.get("usage", total_tokens)
                    use_kb = data.get("use_kb", use_kb)
                    retry_count = data.get("retry_count", retry_count)
                    review_status = data.get("review_status", review_status)
                    critic_feedback = data.get("critic_feedback", critic_feedback)
                elif stage == "error":
                    # 业务层错误：记录状态与错误信息，但不中断事件透传。
                    status = "error"
                    error_message = event.get("data", {}).get("msg", "未知错误")

                # 事件原样透传给调用方（例如 SSE/WebSocket 层）。
                yield event
        except Exception as exc:
            # 系统级异常：标记失败并继续向上抛出，让上层决定返回策略。
            status = "error"
            error_message = str(exc)
            raise
        finally:
            # finally 一定执行：统一计算时延并异步持久化审计信息。
            finished_at = datetime.utcnow()
            duration_ms = int((time.perf_counter() - perf_started) * 1000)
            first_step_ms = int((first_step_at - perf_started) * 1000) if first_step_at else 0
            first_content_ms = int((first_content_at - perf_started) * 1000) if first_content_at else 0

            # 不阻塞主请求：通过后台任务写审计库。
            asyncio.create_task(
                self._persist_agent_run(
                    trace_id=trace_id,
                    thread_id=thread_id,
                    query=query,
                    final_answer=final_answer,
                    status=status,
                    error_message=error_message,
                    response_mode="stream",
                    source=source,
                    use_kb=use_kb,
                    retry_count=retry_count,
                    total_tokens=total_tokens,
                    first_step_ms=first_step_ms,
                    first_content_ms=first_content_ms,
                    duration_ms=duration_ms,
                    stage_path=stages,
                    critic_feedback=critic_feedback,
                    review_status=review_status,
                    created_at=started_at,
                    finished_at=finished_at,
                )
            )

    async def _persist_agent_run(
        self,
        trace_id: str,
        thread_id: str,
        query: str,
        final_answer: str,
        status: str,
        error_message: str,
        response_mode: str,
        source: str,
        use_kb: bool,
        retry_count: int,
        total_tokens: int,
        first_step_ms: int,
        first_content_ms: int,
        duration_ms: int,
        stage_path: list[str],
        critic_feedback: str,
        review_status: str,
        created_at: datetime,
        finished_at: datetime,
    ):
        """后台持久化一次 Agent 运行的技术审计。"""
        try:
            # 使用短事务写入单条审计记录，避免长连接占用。
            with Session(engine) as session:
                run = AgentRunAudit(
                    trace_id=trace_id,
                    thread_id=thread_id,
                    query=query,
                    final_answer=final_answer,
                    status=status,
                    error_message=error_message,
                    response_mode=response_mode,
                    source=source,
                    use_kb=use_kb,
                    retry_count=retry_count,
                    total_tokens=total_tokens,
                    first_step_ms=first_step_ms,
                    first_content_ms=first_content_ms,
                    duration_ms=duration_ms,
                    stage_path=json.dumps(stage_path, ensure_ascii=False),
                    critic_feedback=critic_feedback,
                    review_status=review_status,
                    created_at=created_at,
                    finished_at=finished_at,
                )
                session.add(run)
                session.commit()
        except Exception as exc:
            # 审计失败不应反向影响主链路，因此只记录日志，不再抛异常。
            logger.error(f"Python 技术审计落库失败：{exc}")


# 模块级单例，供路由或上层服务直接复用。
investor_service = InvestorService()
