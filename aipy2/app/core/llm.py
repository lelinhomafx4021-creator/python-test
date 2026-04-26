"""大模型与异步记忆组件初始化。"""

import asyncio
import sys
from typing import Optional

from langchain_openai import ChatOpenAI
from psycopg_pool import AsyncConnectionPool

from app.core.config import settings
from app.core.logger import logger
from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver


if sys.platform.startswith("win"):
    # 在 Windows 下，psycopg 的异步连接池需要使用 Selector 事件循环策略。
    asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())


# --- 1. 大模型配置 ---
def get_llm(
    temperature: float = 0.2,
    *,
    streaming: bool = False,
    max_completion_tokens: int | None = None,
) -> ChatOpenAI:
    """创建 ChatOpenAI 客户端实例。

    参数解释（新手版）：
    - temperature：越低越稳定，越高越发散。
    - streaming：True 时支持流式输出（边生成边返回）。
    - max_completion_tokens：限制本次回答最大长度，防止超长输出。
    """
    extra_kwargs = {}
    if max_completion_tokens is not None:
        # DeepSeek 兼容 OpenAI 接口，这里需要映射到 max_tokens 参数。
        extra_kwargs["max_tokens"] = max_completion_tokens

    return ChatOpenAI(
        model="deepseek-v4-pro",
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
# 因此我们将初始化逻辑延迟到 lifespan 中执行。
_memory_pool: Optional[AsyncConnectionPool] = None
checkpointer: Optional[AsyncPostgresSaver] = None
# 上面两个全局变量为什么先设为 None：
# - 应用刚 import 时事件循环还没启动，不适合直接连数据库。
# - 等 main.py 启动阶段再初始化，避免“导入即连接”的副作用。


async def init_llm_components():
    """初始化异步连接池与 LangGraph 检查点组件（由 main.py 的 lifespan 调用）。"""
    global _memory_pool, checkpointer
    if _memory_pool is None:
        logger.info("[PG] 正在初始化异步连接池...")
        _memory_pool = AsyncConnectionPool(
            conninfo=settings.DATABASE_URL,
            kwargs={"autocommit": True},
        )
        # checkpointer 是 LangGraph 的“会话记忆后端”。
        # 它会把对话状态持久化到数据库，保证同一 thread_id 可以续上下文。
        checkpointer = AsyncPostgresSaver(_memory_pool)
        await checkpointer.setup()
        logger.info("[PG] 异步连接池与 Checkpointer 初始化完成")
