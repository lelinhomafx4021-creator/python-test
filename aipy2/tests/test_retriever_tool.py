import pytest

from app.tools import retriever_tool


@pytest.mark.asyncio
async def test_auto_falls_back_to_web_when_local_returns_empty(monkeypatch):
    calls: list[tuple[str, str]] = []

    async def fake_local(query: str, top_k: int = 3) -> str:
        calls.append(("local", query))
        return ""

    async def fake_web(query: str, top_k: int = 3) -> str:
        calls.append(("web", query))
        return "[Web:Tavily] 命中结果"

    monkeypatch.setattr(retriever_tool, "_search_local_async", fake_local)
    monkeypatch.setattr(retriever_tool, "_search_web_async", fake_web)

    result = await retriever_tool.run_retrieval_async(["宁德时代 最新消息"], mode="auto", top_k=3)

    assert result == "[Web:Tavily] 命中结果"
    assert calls == [("local", "宁德时代 最新消息"), ("web", "宁德时代 最新消息")]


@pytest.mark.asyncio
async def test_auto_falls_back_to_web_when_local_task_raises(monkeypatch):
    calls: list[tuple[str, str]] = []

    async def fake_local(query: str, top_k: int = 3) -> str:
        calls.append(("local", query))
        raise RuntimeError("rag table missing")

    async def fake_web(query: str, top_k: int = 3) -> str:
        calls.append(("web", query))
        return "[Web:Tavily] 降级结果"

    monkeypatch.setattr(retriever_tool, "_search_local_async", fake_local)
    monkeypatch.setattr(retriever_tool, "_search_web_async", fake_web)

    result = await retriever_tool.run_retrieval_async(["贵州茅台 600519"], mode="auto", top_k=3)

    assert result == "[Web:Tavily] 降级结果"
    assert calls == [("local", "贵州茅台 600519"), ("web", "贵州茅台 600519")]


@pytest.mark.asyncio
async def test_auto_appends_web_for_freshness_queries_even_with_local_hit(monkeypatch):
    async def fake_local(query: str, top_k: int = 3) -> str:
        return "[Local KB] 历史资料"

    async def fake_web(query: str, top_k: int = 3) -> str:
        return "[Web:Tavily] 今日新闻"

    monkeypatch.setattr(retriever_tool, "_search_local_async", fake_local)
    monkeypatch.setattr(retriever_tool, "_search_web_async", fake_web)

    result = await retriever_tool.run_retrieval_async(["比亚迪 今天 最新 公告"], mode="auto", top_k=3)

    assert "[Local KB] 历史资料" in result
    assert "[Web:Tavily] 今日新闻" in result
