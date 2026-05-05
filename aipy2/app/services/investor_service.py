"""
================================================================================
AI 投研业务编排层 - InvestorService
================================================================================

职责定位：
  这是 Python 侧的"CEO"层——不直接做 AI 推理，而是编排整个推理流程。

三层架构：
  API 层 (chat.py)     → 接收 HTTP 请求，选择同步/流式模式
  编排层 (本文件)       → 调用 LangGraph Agent，处理事件流，落审计记录
  引擎层 (investor_graph.py) → 实际执行 AI 推理（意图→检索→回答→评审）

核心职责：
  1. 调用 LangGraph Agent 流式执行
      - 不是普通 API 调用，是"边推理边产出事件"的流式消费
      - 每个事件都会 yield 给上游，实现打字机效果

  2. Langfuse 可观测性追踪
      - 全链路 trace：每次对话创建 Langfuse trace
      - 阶段事件：记录流程走到了哪个节点
      - 性能指标：first_step_ms（首步耗时）、first_content_ms（首 token 耗时）

  3. 异步审计落库
      - 不阻塞主流程：使用 asyncio.create_task 在后台写入
      - 成功/失败都记录：finally 块确保无论结果如何都有审计
      - 详细指标：Token 用量、重试次数、评审状态、耗时分析

数据流向：
  用户请求 → chat.py → InvestorService.run_investor_flow()
    ├── 创建 Langfuse trace（可选）
    ├── 调用 multi_graph_agent.ask_stream_events()
    │     ├── 逐事件 yield 给上游（SSE 透传）
    │     ├── 记录首步/首内容耗时
    │     └── 收集 final_answer 中的统计信息
    └── finally 块
          ├── 结束 Langfuse span/trace
          └── 异步写入 AgentRunAudit 表
"""

import asyncio
import json
import time
from datetime import datetime
from typing import AsyncGenerator

from langfuse import Langfuse
from sqlmodel import Session

from app.core.config import settings
from app.core.db import engine
from app.core.llm import can_reach_postgres
from app.core.logger import logger
from app.graph.investor_graph import multi_graph_agent
from app.models.agent_run_audit import AgentRunAudit


_langfuse_client: Langfuse | None = None


def _init_langfuse_client() -> Langfuse:
    """初始化并缓存 Langfuse 客户端。

    说明：
    - 这里固定走 Langfuse v2 兼容写法，直接手工创建 trace/span。
    - 这样可以继续兼容你当前本地单容器的 Langfuse v2 服务。
    """
    global _langfuse_client
    if _langfuse_client is None:
        _langfuse_client = Langfuse(
            public_key=settings.LANGFUSE_PUBLIC_KEY,
            secret_key=settings.LANGFUSE_SECRET_KEY,
            host=settings.LANGFUSE_HOST,
        )
    return _langfuse_client


class InvestorService:
    """投研业务服务类。"""

    async def run_investor_flow(
        self,
        query: str,
        thread_id: str,
        trace_id: str,
        role: str = "normal",
    ) -> AsyncGenerator:
        """
        运行投研工作流，并持续产出流式事件。

        设计要点：
        - 一边消费 Agent 的流式事件，一边 yield 给调用方
        - 在 finally 中异步落库，确保成功/失败都记录审计
        - role 参数决定使用哪套图流程（normal/vip）
        """
        # Langfuse v2 兼容方案：
        # - session_id 使用 thread_id，把同一会话归到一起
        # - trace id 直接复用系统 trace_id，方便和数据库审计对齐
        langfuse_client = None
        langfuse_trace = None
        langfuse_root_span = None
        if (
            settings.LANGFUSE_ENABLED
            and settings.LANGFUSE_PUBLIC_KEY
            and settings.LANGFUSE_SECRET_KEY
        ):
            try:
                langfuse_client = _init_langfuse_client()
                langfuse_trace = langfuse_client.trace(
                    id=trace_id,
                    name="investor_chat",
                    user_id="customer_pro",
                    session_id=thread_id,
                    input={"query": query},
                    metadata={
                        "thread_id": thread_id,
                        "response_mode": "stream",
                    },
                )
                langfuse_root_span = langfuse_trace.span(
                    name="investor_flow",
                    input={"query": query},
                    metadata={"thread_id": thread_id},
                )
            except Exception as exc:
                logger.warning(f"Langfuse 初始化失败，已降级为不追踪：{exc}")
                langfuse_client = None
                langfuse_trace = None
                langfuse_root_span = None

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
        traced_stage_names: set[str] = set()

        try:
            # 从图代理获取流式事件，逐条透传给上游。
            async for event in multi_graph_agent.ask_stream_events(
                query=query,
                thread_id=thread_id,
                role=role,
            ):
                # stage_path 用于还原本次运行经过了哪些阶段。
                stage = event.get("stage", "")
                stages.append(stage)

                # 给 Langfuse 补一份轻量阶段轨迹，便于在 UI 里看到流程走向。
                if (
                    langfuse_trace
                    and stage
                    and stage not in {"content_delta", "final_answer", "done"}
                    and stage not in traced_stage_names
                ):
                    traced_stage_names.add(stage)
                    try:
                        langfuse_trace.event(
                            name=f"stage:{stage}",
                            input=event.get("data", {}),
                        )
                    except Exception as exc:
                        logger.warning(f"Langfuse 记录阶段事件失败：{exc}")

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
                    if langfuse_trace:
                        try:
                            langfuse_trace.generation(
                                name="final_answer",
                                model="investor-graph",
                                input={"query": query},
                                output=final_answer,
                                usage_details={"total": total_tokens},
                                metadata={
                                    "use_kb": use_kb,
                                    "retry_count": retry_count,
                                    "review_status": review_status,
                                },
                            )
                        except Exception as exc:
                            logger.warning(f"Langfuse 记录最终答案失败：{exc}")
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

            if langfuse_root_span:
                try:
                    langfuse_root_span.end(
                        output={
                            "status": status,
                            "duration_ms": duration_ms,
                            "first_step_ms": first_step_ms,
                            "first_content_ms": first_content_ms,
                        }
                    )
                except Exception as exc:
                    logger.warning(f"Langfuse 结束根 span 失败：{exc}")

            if langfuse_trace:
                try:
                    langfuse_trace.update(
                        output=final_answer,
                        metadata={
                            "status": status,
                            "error_message": error_message,
                            "duration_ms": duration_ms,
                            "stage_path": stages,
                            "use_kb": use_kb,
                            "retry_count": retry_count,
                            "review_status": review_status,
                        },
                    )
                except Exception as exc:
                    logger.warning(f"Langfuse 更新 trace 失败：{exc}")

            if langfuse_client:
                try:
                    langfuse_client.flush()
                except Exception as exc:
                    logger.warning(f"Langfuse flush 失败：{exc}")

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
            if settings.is_dev and not can_reach_postgres():
                logger.warning("PostgreSQL 当前不可达，已跳过 Agent 运行审计落库")
                return

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
