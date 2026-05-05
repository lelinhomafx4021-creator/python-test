"""
LLM 大模型配置与生命周期管理模块

职责：
1. 提供 LLM 实例创建工厂方法
2. 管理 PostgreSQL 异步连接池（用于 LangGraph 记忆存储）
3. 处理 Windows 兼容性问题

技术栈：
- ChatOpenAI：LangChain 的 OpenAI 兼容客户端
- psycopg_pool：PostgreSQL 异步连接池
- AsyncPostgresSaver：LangGraph 的对话状态持久化器
"""

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
    """创建 LLM 实例的工厂方法
    
    参数说明：
    - temperature：控制输出随机性（0=确定性，1=创意性）
    - streaming：是否启用流式输出（用于实时显示生成过程）
    - max_completion_tokens：最大生成 token 数（防止生成过长内容）
    
    返回：配置好的 ChatOpenAI 实例
    """
    extra_kwargs = {}
    if max_completion_tokens is not None:
        # DeepSeek API 使用 max_tokens 参数名
        extra_kwargs["max_tokens"] = max_completion_tokens
    return ChatOpenAI(
        # 使用 DeepSeek V4 Flash 模型
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
    """初始化异步组件（由 main.py lifespan 调用）
    
    初始化内容：
    1. 创建 PostgreSQL 异步连接池
    2. 创建 LangGraph 的 AsyncPostgresSaver 检查点保存器
    3. 执行数据库表结构初始化（checkpoint 表）
    """
    global _memory_pool, checkpointer
    if _memory_pool is None:
        logger.info("[PG] 正在初始化异步连接池...")
        # 创建异步连接池，autocommit=True 表示每条 SQL 自动提交
        _memory_pool = AsyncConnectionPool(
            conninfo=settings.DATABASE_URL,
            kwargs={"autocommit": True},
        )
        # 创建检查点保存器，用于持久化对话状态
        checkpointer = AsyncPostgresSaver(_memory_pool)
        # 执行数据库初始化（创建 checkpoint 表）
        await checkpointer.setup()
        logger.info("[PG] 异步连接池与 Checkpointer 初始化完成")


def can_reach_postgres() -> bool:
    """判断当前 PostgreSQL 是否可达
    
    用途：在启动时检查数据库连接，避免服务启动失败
    """
    return _memory_pool is not None and checkpointer is not None


async def shutdown_llm_components():
    """关闭异步组件，释放连接池资源
    
    在应用退出时调用，确保：
    1. 关闭所有数据库连接
    2. 释放连接池内存
    3. 避免连接泄漏
    """
    global _memory_pool, checkpointer
    if _memory_pool is not None:
        try:
            # 异步关闭连接池
            await _memory_pool.close()
            logger.info("[PG] 异步连接池已关闭")
        except Exception as exc:
            logger.warning("[PG] 关闭异步连接池失败: %s", exc)
    # 清空全局变量引用
    _memory_pool = None
    checkpointer = None
