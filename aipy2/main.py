"""FastAPI 主入口。

这个文件可以理解成整个 Python 服务的“启动总控台”。
它主要负责：
1. 应用启动时做初始化
2. 注册路由
3. 加中间件
4. 本地开发时启动 uvicorn
"""

import asyncio
import sys
import time
import traceback
import uuid
from contextlib import asynccontextmanager

import uvicorn
from fastapi import FastAPI, Request

from app.api.v1.chat import router as chat_router
from app.api.v1.util import router as util_router
from app.core import llm as llm_core
from app.core.config import settings
from app.core.logger import logger
from app.rag.vector_store import VectorStore


# [教学修改] Windows 下 psycopg 的异步连接池和默认 ProactorEventLoop 不兼容。
# 如果不切换事件循环策略，应用启动时会报：
# "Psycopg cannot use the 'ProactorEventLoop' to run in async mode"
# 这一行的作用，就是把本机开发环境切到更兼容的 SelectorEventLoop。
if sys.platform.startswith("win"):
    asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())


@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期钩子。

    `yield` 之前：
    - 属于“启动阶段”
    - 适合初始化数据库连接、LLM 组件、检查业务表等

    `yield` 之后：
    - 属于“关闭阶段”
    - 适合释放资源、打印退出日志等
    """
    logger.info(">>> AI-Investor Core 启动中...")

    try:
        # 初始化 LangGraph 相关的数据库持久化组件。
        # 注意：这里初始化的是框架自带的 checkpoint 表，不是你们业务表。
        await llm_core.init_llm_components()
        if llm_core.checkpointer is None:
            logger.info(">>> LangGraph 已切换为内存记忆模式")
        else:
            logger.info(">>> LangGraph checkpoint ready")

        # 校验 RAG 相关业务表是否已经通过 Alembic 建好。
        # 这里不再偷偷建表，而是要求正式先执行 `alembic upgrade head`。
        if settings.is_dev and not llm_core.can_reach_postgres():
            logger.warning(">>> PostgreSQL 当前不可达，已跳过 RAG schema 校验")
        else:
            v_store = VectorStore(
                db_url=settings.DATABASE_URL,
                api_key=settings.DASH_API_KEY,
            )
            try:
                v_store.create_collection()
                logger.info(">>> RAG schema verified")
            finally:
                # 无论校验成功还是失败，都要关闭同步 PostgreSQL 连接。
                v_store.close()
    except Exception as e:
        # 初始化失败时，打印错误和完整堆栈，方便排查。
        logger.error(f">>> 数据库或启动组件初始化失败: {e}")
        traceback.print_exc()

    # 到这里表示启动准备阶段结束，FastAPI 开始正式对外提供服务。
    yield

    # 应用退出时打印收尾日志。
    await llm_core.shutdown_llm_components()
    logger.info(">>> AI-Investor Core 正在优雅退出...")


# 创建 FastAPI 应用对象。
app = FastAPI(
    title="AI-Investor-Core",
    version="1.0.0-PRO",
    description="专业金融投研 AI 核心 (FastAPI + LangGraph + RAG)",
    lifespan=lifespan,
)

# 注册聊天相关路由。
app.include_router(chat_router)
# 注册工具类路由，例如标题生成之类的小接口。
app.include_router(util_router)


@app.middleware("http")
async def trace_middleware(request: Request, call_next):
    """全局链路追踪中间件。

    作用：
    1. 给每个请求分配一个 TraceId
    2. 统计本次请求耗时
    3. 把 TraceId 回传给调用方，便于前后端和日志对齐
    """
    # 优先复用上游传来的 TraceId；如果没有，就现场生成一个。
    trace_id = request.headers.get("X-Trace-Id", str(uuid.uuid4()))
    request.state.trace_id = trace_id

    # 记录请求开始时间。
    start_time = time.perf_counter()

    # 继续把请求交给后续路由处理。
    response = await call_next(request)

    # 计算总耗时，单位毫秒。
    process_time = int((time.perf_counter() - start_time) * 1000)

    # 把 TraceId 放回响应头，方便调用方排查问题。
    response.headers["X-Trace-Id"] = trace_id
    logger.info(
        f"请求完成 | 路径: {request.url.path} | 耗时: {process_time}ms | TraceId: {trace_id}"
    )
    return response


if __name__ == "__main__":
    # 当前这套 AI 服务在 Windows 下接了 psycopg 异步连接池。
    # 对于 uvicorn 0.44 来说，开启 reload 会走子进程启动，
    # 子进程路径会使用 SelectorEventLoop，正好兼容 psycopg_pool。
    # 所以这里按当前项目的本地开发现实，统一恢复成开发模式开启热重载。
    enable_reload = settings.is_dev
    if settings.is_dev and sys.platform.startswith("win"):
        logger.info(">>> Windows 本地开发已启用热重载，用子进程模式兼容 PostgreSQL checkpoint")

    # 开启热重载时必须传 import string，让子进程重新导入应用。
    # 非热重载模式才直接传 app 对象，减少额外导入。
    app_target = "main:app" if enable_reload else app
    uvicorn.run(app_target, host="0.0.0.0", port=8000, reload=enable_reload)
