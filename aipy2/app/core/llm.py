"""
███ 大模型（LLM）客户端模块 ███

============================================================
【新手必读】本文件是整个项目的"大脑连接器"。
它的职责非常纯粹：帮你连接 DeepSeek 大模型，并管理 AI 的记忆数据库。
============================================================

===== 核心知识点 =====

Q1: 为什么用 ChatOpenAI 而不是直接调 HTTP API？
A: LangChain 的 ChatOpenAI 是一个"统一适配器"。
   只要目标模型的 API 格式兼容 OpenAI（如今 90% 的国产模型都兼容），
   就可以用同一套代码切换 DeepSeek / 智谱 / 月之暗面 / Qwen 等模型。
   面试金句："我用适配器模式统一了多模型接入，切换模型只需改几个参数。"

Q2: temperature 参数是什么意思？
A: 控制模型"想象力"的旋钮。范围 0~2：
   - 0：极度保守，每次回答几乎一样（适合意图路由、评审）
   - 0.3~0.5：适度创意（适合生成回答）
   - 0.6~0.8：比较活泼（适合闲聊）
   - 1.0+：天马行空（适合创意写作，投研场景禁止！）

Q3: 什么是 LangGraph Checkpointer（检查点）？
A: 你可以把它理解成"AI 的记忆硬盘"。
   - 没有 Checkpointer → AI 像金鱼，每次对话都失忆
   - 有 Checkpointer → 同一个 thread_id 可以续接上下文
   底层用 PostgreSQL 的 asyncpg，自动在对话步骤间保存状态。

Q4: 为什么 Windows 上要切换事件循环策略？
A: Python 的 asyncio 在 Windows 上默认用 ProactorEventLoop，
   但 psycopg 的异步连接池（asyncpg）不兼容它。
   SelectorEventLoop 虽然性能略低，但在 Windows 上最稳定。
   部署到 Linux 服务器后可以去掉这段，自动走得更快。
"""

import asyncio
import socket
import sys
from typing import Optional
from urllib.parse import urlparse

# ChatOpenAI：LangChain 封装的 OpenAI 兼容客户端
# 别看名字里有 OpenAI，实际上只要 API 兼容就能用，不一定要连 OpenAI
from langchain_openai import ChatOpenAI

# AsyncConnectionPool：异步数据库连接池
# 对比同步连接：异步连接池能让 FastAPI 在处理大量并发时不互相等待
from psycopg_pool import AsyncConnectionPool

from app.core.config import settings
from app.core.logger import logger

# AsyncPostgresSaver：LangGraph 官方提供的 PostgreSQL 检查点实现
# 它的作用是在 AI 工作流的每一步自动保存状态到数据库
from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver

if sys.platform.startswith("win"):
    # Windows 兼容：把事件循环切换到 SelectorEventLoop
    # 原因：psycopg 的异步连接池不支持 Windows 默认的 ProactorEventLoop
    # 面试说："在 Windows 开发环境下使用了 SelectorEventLoop 做兼容，生产 Linux 环境不需要"
    asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())


# ========================================
# 1. LLM 工厂函数 —— 创建大模型客户端
# ========================================

def get_llm(
    temperature: float = 0.2,
    *,
    streaming: bool = False,
    max_completion_tokens: int | None = None,
) -> ChatOpenAI:
    """
    【工厂函数】按需生产不同"性格"的大模型客户端。

    设计思想：不直接暴露全局 LLM 对象，而是让每个节点调用时传参数。
    这样：
    - 意图路由节点 → temperature=0（严谨判断）
    - 闲聊节点 → temperature=0.6（活泼一些）
    - 评审节点 → temperature=0（极度客观）

    参数说明：
    - temperature：创意度（0=保守，1=活跃）
    - streaming：是否开启流式输出（打字机效果）
    - max_completion_tokens：最大输出长度（不传则模型自定）
      注意 DeepSeek 的 OpenAI 兼容接口用 max_tokens 而非 max_completion_tokens
    """
    # 构建额外参数。DeepSeek 的 API 期望 max_tokens 而非 max_completion_tokens
    extra_kwargs = {}
    if max_completion_tokens is not None:
        extra_kwargs["max_tokens"] = max_completion_tokens

    # 关键：model 名称、API 地址、密钥全部来自 config，方便切换
    return ChatOpenAI(
        model="deepseek-v4-pro",           # 当前使用 DeepSeek V4 Pro
        temperature=temperature,
        api_key=settings.DEEPSEEK_API,      # API 密钥（从 .env 读取）
        base_url="https://api.deepseek.com", # API 地址
        streaming=streaming,                # 是否流式返回
        **extra_kwargs,                     # 额外参数（如 max_tokens）
    )


# 对外共享的默认 LLM 实例（用于健康检查、标题生成等简单场景）
# 其他模块可以直接 `from app.core.llm import llm` 拿这个现成的
llm = get_llm(temperature=0.3)


# ========================================
# 2. LangGraph 记忆管理 —— Checkpointer
# ========================================

# 这两个是模块级变量，由 lifespan 里的 init_llm_components() 赋值
# 初始为 None 是因为异步资源必须在事件循环启动后才能初始化

# 异步数据库连接池（管理多个到 PostgreSQL 的连接）
_memory_pool: Optional[AsyncConnectionPool] = None

# LangGraph 检查点保存器（把 Agent 的状态存到 PostgreSQL）
checkpointer: Optional[AsyncPostgresSaver] = None


def should_enable_postgres_checkpointer() -> bool:
    """判断当前运行环境是否优先尝试 PostgreSQL 记忆层。"""
    return True


def can_reach_postgres(timeout: float = 1.0) -> bool:
    """轻量探测 PostgreSQL 端口是否可达。"""
    try:
        parsed = urlparse(settings.DATABASE_URL)
        host = parsed.hostname or "127.0.0.1"
        port = parsed.port or 5432
        with socket.create_connection((host, port), timeout=timeout):
            return True
    except OSError:
        return False


async def init_llm_components():
    """
    【启动初始化】连接数据库，创建 Checkpointer。

    为什么不在 import 时就初始化？
    答：asyncpg 的连接池必须在 Python 事件循环启动后才能创建。
    如果放在 import 阶段（同步代码），会报 "no running event loop" 错误。
    所以这里延迟到 FastAPI 的 lifespan（启动钩子）里调用。

    调用链：main.py lifespan → init_llm_components() → 创建连接池 + Checkpointer
    """
    global _memory_pool, checkpointer

    if _memory_pool is not None or checkpointer is not None:
        return

    if not should_enable_postgres_checkpointer():
        logger.warning("[PG] 当前环境未启用 PostgreSQL 记忆层，已切换为内存记忆模式")
        _memory_pool = None
        checkpointer = None
        return

    if settings.is_dev and not can_reach_postgres():
        logger.warning("[PG] PostgreSQL 端口暂不可达，开发环境已降级为内存记忆模式")
        _memory_pool = None
        checkpointer = None
        return

    logger.info("[PG] 正在初始化异步连接池...")

    pool: Optional[AsyncConnectionPool] = None
    try:
        # 显式 open/wait 能避免构造阶段提前拉起后台连接，
        # 也方便我们在失败时立刻做清晰降级。
        pool = AsyncConnectionPool(
            conninfo=settings.DATABASE_URL,
            kwargs={"autocommit": True},
            open=False,
        )
        await pool.open(wait=True, timeout=10.0)

        # AsyncPostgresSaver：把连接池包装成 LangGraph 的 Checkpointer
        # 面试金句："我们用了 LangGraph 的异步 PostgreSQL Checkpointer，
        # 实现了对话状态的持久化和跨会话上下文恢复。"
        saver = AsyncPostgresSaver(pool)

        # setup() 方法：自动创建 LangGraph 需要的 checkpoint 表
        # 注意：这是 LangGraph 框架自己的表（checkpoint / checkpoint_writes），
        # 不是我们项目的业务表（业务表由 Alembic 管理）
        await saver.setup()

        _memory_pool = pool
        checkpointer = saver
        logger.info("[PG] 异步连接池与 Checkpointer 初始化完成")
    except Exception as exc:
        if pool is not None:
            try:
                await pool.close()
            except Exception:
                pass

        _memory_pool = None
        checkpointer = None

        if settings.is_dev:
            logger.warning(
                "[PG] PostgreSQL Checkpointer 初始化失败，开发环境已降级为内存记忆模式：%s",
                exc,
            )
            return

        raise


async def shutdown_llm_components():
    """关闭 LangGraph 记忆层相关资源。"""
    global _memory_pool, checkpointer

    if _memory_pool is not None:
        try:
            await _memory_pool.close()
            logger.info("[PG] 异步连接池已关闭")
        finally:
            _memory_pool = None
            checkpointer = None
