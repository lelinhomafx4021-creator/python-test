from typing import Any

from sqlmodel import desc, func, select
from sqlmodel.ext.asyncio.session import AsyncSession

from app.models.chat_turn import ChatTurn


class ChatHistoryService:
    @staticmethod
    async def save_turn(
        session: AsyncSession,
        *,
        user_id: str,
        session_id: str,
        thread_id: str,
        trace_id: str,
        query: str,
        answer: str,
        intent: str,
        source: str,
        review_passed: bool,
        response_mode: str = "sync",
        a2a_count: int = 0,
    ) -> None:
        row = ChatTurn(
            user_id=user_id,
            session_id=session_id,
            thread_id=thread_id,
            trace_id=trace_id,
            query=query,
            answer=answer,
            intent=intent,
            source=source,
            review_passed=review_passed,
            response_mode=response_mode,
            a2a_count=a2a_count,
        )
        session.add(row)
        await session.commit()

    @staticmethod
    async def list_user_sessions(session: AsyncSession, user_id: str) -> list[dict[str, Any]]:
        stmt = (
            select(
                ChatTurn.session_id,
                ChatTurn.thread_id,
                func.count(ChatTurn.id).label("turn_count"),
                func.max(ChatTurn.created_at).label("last_at"),
                func.max(ChatTurn.response_mode).label("response_mode"),
            )
            .where(ChatTurn.user_id == user_id)
            .group_by(ChatTurn.session_id, ChatTurn.thread_id)
            .order_by(desc("last_at"))
        )
        rows = (await session.exec(stmt)).all()
        return [
            {
                "session_id": r[0],
                "thread_id": r[1],
                "turn_count": r[2],
                "last_at": r[3],
                "response_mode": r[4],
            }
            for r in rows
        ]

    @staticmethod
    async def list_session_turns(
        session: AsyncSession,
        *,
        user_id: str,
        session_id: str,
        limit: int = 50,
        offset: int = 0,
    ) -> list[dict[str, Any]]:
        stmt = (
            select(ChatTurn)
            .where(ChatTurn.user_id == user_id, ChatTurn.session_id == session_id)
            .order_by(ChatTurn.id.desc())
            .offset(offset)
            .limit(limit)
        )
        rows = (await session.exec(stmt)).all()
        return [
            {
                "id": r.id,
                "trace_id": r.trace_id,
                "query": r.query,
                "answer": r.answer,
                "intent": r.intent,
                "source": r.source,
                "review_passed": r.review_passed,
                "response_mode": r.response_mode,
                "a2a_count": r.a2a_count,
                "created_at": r.created_at,
            }
            for r in rows
        ]

    @staticmethod
    async def list_recent_context(
        session: AsyncSession,
        *,
        user_id: str,
        session_id: str,
        limit: int = 8,
    ) -> list[dict[str, Any]]:
        stmt = (
            select(ChatTurn)
            .where(ChatTurn.user_id == user_id, ChatTurn.session_id == session_id)
            .order_by(ChatTurn.id.desc())
            .limit(limit)
        )
        rows = (await session.exec(stmt)).all()
        rows.reverse()
        return [
            {
                "id": r.id,
                "query": r.query,
                "answer": r.answer,
                "intent": r.intent,
                "source": r.source,
                "created_at": r.created_at,
            }
            for r in rows
        ]
