import asyncio
import sys
from typing import Optional

from langchain_openai import ChatOpenAI
from psycopg_pool import AsyncConnectionPool

from app.core.config import settings
from app.core.logger import logger
from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver

if sys.platform.startswith("win"):
    # psycopg async pool requires selector event loop on Windows.
    asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())


# --- 1. 高级模型配置 ---
def get_llm(
    temperature: float = 0.2,
    *,
    streaming: bool = False,
    max_completion_tokens: int | None = None,
) -> ChatOpenAI:
    extra_kwargs = {}
    if max_completion_tokens is not None:
        # DeepSeek's OpenAI-compatible API expects max_tokens.
        extra_kwargs["max_tokens"] = max_completion_tokens
    return ChatOpenAI(
        model="deepseek-v4-flash",
        temperature=temperature,
        api_key=settings.DEEPSEEK_API,
        base_url="https://api.deepseek.com",
        streaming=streaming,
        **extra_kwargs,
    )


# 对外共享的默认 LLM 实例（用于健康检查、调试接口）
llm = get_llm(temperature=0.3)

# AI 记忆数据库连接池（LangGraph 专用）
# 知识点：在异步环境中，必须在事件循环启动后才能初始化连接池。
# 我们将初始化逻辑延迟到 lifespan 中执行。
_memory_pool: Optional[AsyncConnectionPool] = None
checkpointer: Optional[AsyncPostgresSaver] = None


async def init_llm_components():
    """初始化异步组件（由 main.py lifespan 调用）。"""
    global _memory_pool, checkpointer
    if _memory_pool is None:
        logger.info("[PG] 正在初始化异步连接池...")
        _memory_pool = AsyncConnectionPool(
            conninfo=settings.DATABASE_URL,
            kwargs={"autocommit": True},
        )
        checkpointer = AsyncPostgresSaver(_memory_pool)
        await checkpointer.setup()
        logger.info("[PG] 异步连接池与 Checkpointer 初始化完成")


def can_reach_postgres() -> bool:
    """判断当前 PostgreSQL 是否可达。"""
    return _memory_pool is not None and checkpointer is not None


async def shutdown_llm_components():
    """关闭异步组件，释放连接池资源。"""
    global _memory_pool, checkpointer
    if _memory_pool is not None:
        try:
            await _memory_pool.close()
            logger.info("[PG] 异步连接池已关闭")
        except Exception as exc:
            logger.warning("[PG] 关闭异步连接池失败: %s", exc)
    _memory_pool = None
    checkpointer = None
