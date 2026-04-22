"""
用户等级（套餐）服务。

目标：
- 根据 user_id 获取用户等级
- 输出该等级可用的 AI 能力策略（给 graph 使用）
- 提供设置/升级等级能力（后续可由 Java 管理后台调用）
"""

from typing import Any, Literal

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

PlanCode = Literal["FREE", "PRO", "VIP"]


class UserTierService:
    """同花顺风格分层能力：免费 / 专业 / VIP。"""

    # 不同等级对应的 AI 策略（graph 会读取这个策略）
    PLAN_POLICY: dict[PlanCode, dict[str, Any]] = {
        "FREE": {
            "plan_code": "FREE",
            "display_name": "免费版",
            "enable_web_search": False,          # 免费版不开放联网检索
            "enable_retrieval_guard": False,     # 免费版不做检索纠错（降成本）
            "enable_review": True,               # 仍做基本回答评审
            "retrieval_top_k": 2,
            "answer_max_tokens": 1024,
        },
        "PRO": {
            "plan_code": "PRO",
            "display_name": "专业版",
            "enable_web_search": True,
            "enable_retrieval_guard": True,
            "enable_review": True,
            "retrieval_top_k": 3,
            "answer_max_tokens": 2048,
        },
        "VIP": {
            "plan_code": "VIP",
            "display_name": "VIP版",
            "enable_web_search": True,
            "enable_retrieval_guard": True,
            "enable_review": True,
            "retrieval_top_k": 5,
            "answer_max_tokens": 4096,
        },
    }

    @staticmethod
    async def get_or_init_user_plan(session: AsyncSession, user_id: str) -> PlanCode:
        """获取用户套餐，不存在则初始化为 FREE。"""
        select_sql = text(
            """
            SELECT plan_code
            FROM ai_user_profiles
            WHERE user_id = :user_id
            """
        )
        row = (await session.execute(select_sql, {"user_id": user_id})).mappings().first()
        if row and row.get("plan_code") in ("FREE", "PRO", "VIP"):
            return row["plan_code"]

        insert_sql = text(
            """
            INSERT INTO ai_user_profiles (user_id, plan_code)
            VALUES (:user_id, 'FREE')
            ON CONFLICT (user_id) DO NOTHING
            """
        )
        await session.execute(insert_sql, {"user_id": user_id})
        await session.commit()
        return "FREE"

    @classmethod
    def get_plan_policy(cls, plan_code: PlanCode) -> dict[str, Any]:
        return cls.PLAN_POLICY.get(plan_code, cls.PLAN_POLICY["FREE"])

    @classmethod
    async def get_user_policy(cls, session: AsyncSession, user_id: str) -> dict[str, Any]:
        plan = await cls.get_or_init_user_plan(session, user_id=user_id)
        return cls.get_plan_policy(plan)

    @staticmethod
    async def set_user_plan(session: AsyncSession, user_id: str, plan_code: PlanCode) -> None:
        if plan_code not in ("FREE", "PRO", "VIP"):
            raise ValueError("plan_code 必须是 FREE/PRO/VIP")

        upsert_sql = text(
            """
            INSERT INTO ai_user_profiles (user_id, plan_code)
            VALUES (:user_id, :plan_code)
            ON CONFLICT (user_id)
            DO UPDATE SET plan_code = EXCLUDED.plan_code,
                          updated_at = NOW()
            """
        )
        await session.execute(upsert_sql, {"user_id": user_id, "plan_code": plan_code})
        await session.commit()
