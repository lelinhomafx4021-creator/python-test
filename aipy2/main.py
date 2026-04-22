import time
import uuid
import uvicorn
from contextlib import asynccontextmanager
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

import traceback
from app.core.logger import logger
from app.core.llm import init_llm_components
from app.api.v1.chat import router as chat_router
from app.api.v1.util import router as util_router
from app.rag.vector_store import VectorStore
from app.core.config import settings

# /**
#  * -----------------------------------------------------------
#  * 【Python 核心指挥中心：main.py】
#  * -----------------------------------------------------------
#  * 这个文件是 Python AI 服务的入口。
#  * 哪怕你是新手，看到这个文件也应该能明白整个服务的血液循环。
#  */

@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    【生命周期管理器】
    知识点：这类似于汽车的“点火”和“熄火”过程。
    由于项目涉及数据库 (Postgres) 和大型模型 (LLM)，
    我们必须在启动时建立连接，并在退出时回收资源，否则会发生成内存泄漏。
    """
    logger.info(">>> AI-Investor Core 启动中...")
    
    # --- 知识点：自动让代码去改数据库 (Alembic) ---
    # 我们不需要在 Navicat 里手动敲 SQL。Alembic 会自动对比代码类和数据库，
    # 发现不一致就自动 ALTER TABLE。
    try:
        # from alembic.config import Config
        # from alembic import command
        # alembic_cfg = Config("alembic.ini")
        # command.upgrade(alembic_cfg, "head") # 告诉数据库：升级到代码要求的最新版本
        # --- 自动初始化 LangGraph 记忆表 ---
        await init_llm_components()
        
        # --- 自动初始化 RAG 向量检索表 ---
        # 知识点：以前需要手动运行脚本，现在集成到启动流程，实现“开箱即用”
        v_store = VectorStore(
            db_url=settings.DATABASE_URL,
            api_key=settings.DASH_API_KEY
        )
        try:
            v_store.create_collection()
            logger.info(">>> RAG 向量检索表初始化成功 (pgvector/HNSW)")
        finally:
            v_store.close()
    except Exception as e:
        logger.error(f">>> 数据库同步失败: {e}")
        traceback.print_exc()
        
    yield # 这里是分界线，程序在此处开始正常提供服务
    
    logger.info(">>> AI-Investor Core 正在优雅离场...")

# 实例化主应用
app = FastAPI(
    title="AI-Investor-Core", 
    version="1.0.0-PRO",
    description="专业金融投研 AI 核心 (FastAPI + LangGraph + RAG)",
    lifespan=lifespan # 把刚才写的“点火熄火”逻辑注册进去
)

# 1. 注册功能模块 (挂载路由)
app.include_router(chat_router)   # 核心聊天模块
app.include_router(util_router)   # 通用工具模块 (比如生成标题)

# 2. 全局链路拦截器 (Middleware)
@app.middleware("http")
async def trace_middleware(request: Request, call_next):
    """
    【企业级中间件：链路追踪】
    逻辑：请求进来 -> 给它一个 DNA 编号 (TraceId) -> 执行业务 -> 计算耗时 -> 返回
    这样即使云服务器有成千上万个请求，我们也能靠这个 ID 锁定特定的一次访问。
    """
    # 如果 Java 端传了 ID 过来我们就用 Java 的，否则自己生一个
    trace_id = request.headers.get("X-Trace-Id", str(uuid.uuid4()))
    request.state.trace_id = trace_id
    start_time = time.perf_counter()
    
    # 核心步骤：让请求去跑真正的 API 逻辑
    response = await call_next(request)
    
    # 计算耗时并记入日志，这是性能调优的原始数据
    process_time = int((time.perf_counter() - start_time) * 1000)
    response.headers["X-Trace-Id"] = trace_id
    logger.info(f"DONE | Path: {request.url.path} | Time: {process_time}ms | Trace: {trace_id}")
    return response

# 程序主入口
if __name__ == "__main__":
    # 启动进程。 reload=True 意味着你修改代码存盘时，程序会自动重启（开发神器）
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
