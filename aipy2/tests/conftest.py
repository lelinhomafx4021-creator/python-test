"""
pytest 共享 fixtures。

为 FastAPI 提供异步测试客户端 (httpx.AsyncClient)，
并 mock 掉外部依赖（腾讯 K 线接口、LangGraph Agent 等），
让单元测试不依赖网络和数据库。
"""

import pytest
import pytest_asyncio
from httpx import ASGITransport, AsyncClient
from unittest.mock import AsyncMock, patch, MagicMock


# ---------------------------------------------------------------------------
# FastAPI 测试客户端
# ---------------------------------------------------------------------------

@pytest_asyncio.fixture
async def client():
    """
    异步 HTTP 测试客户端。

    注意：不经过 lifespan，避免触发数据库/LLM 初始化。
    直接构造 FastAPI 实例并挂载路由即可。
    """
    from fastapi import FastAPI
    from app.api.v1.kline import router as kline_router
    from app.api.v1.chat import router as chat_router

    app = FastAPI(title="test-app")
    app.include_router(kline_router)
    app.include_router(chat_router)

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://testserver") as ac:
        yield ac


# ---------------------------------------------------------------------------
# Kline 测试 fixtures
# ---------------------------------------------------------------------------

@pytest.fixture
def mock_tencent_response_success():
    """模拟腾讯财经接口成功返回的 JSON 数据。"""
    return {
        "code": 0,
        "msg": "",
        "data": {
            "sh601179": {
                "qfqday": [
                    ["2026-01-10", "10.50", "11.20", "11.50", "10.30", 123456],
                    ["2026-01-11", "11.20", "10.80", "11.30", "10.70", 98765],
                    ["2026-01-14", "10.80", "12.00", "12.10", "10.60", 150000],
                ]
            }
        }
    }


@pytest.fixture
def mock_tencent_response_empty():
    """模拟腾讯接口返回空数据。"""
    return {
        "code": 0,
        "msg": "",
        "data": {
            "sz000001": {}
        }
    }


# ---------------------------------------------------------------------------
# Chat 测试 fixtures
# ---------------------------------------------------------------------------

@pytest.fixture
def mock_investor_flow_events():
    """
    模拟 InvestorService.run_investor_flow 的事件流。

    返回一个 async generator，依次产出几个典型事件。
    """

    async def _gen(query, thread_id, trace_id, role="normal"):
        yield {"stage": "intent", "data": {"intent": "stock_analysis"}}
        yield {"stage": "retrieval", "data": {"chunks": []}}
        yield {"stage": "content_delta", "data": {"text": "正在分析..."}}
        yield {
            "stage": "final_answer",
            "data": {
                "answer": "宁德时代近期走势分析结果：...",
                "source": "AI 投研闭环引擎",
                "usage": 1024,
                "use_kb": True,
                "retry_count": 0,
                "review_status": "passed",
            },
        }

    return _gen


@pytest.fixture
def sample_chat_request_body():
    """标准聊天请求体。"""
    return {
        "message": "帮我分析一下宁德时代近期走势",
        "thread_id": "test_thread_001",
        "role": "normal",
    }
