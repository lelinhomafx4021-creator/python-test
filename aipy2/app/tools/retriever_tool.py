"""检索工具模块：统一封装本地知识库检索与联网检索。"""

import asyncio
from typing import Literal

from langchain_core.tools import tool
from tavily import TavilyClient

from app.core.config import settings
from app.rag.vector_store import VectorStore


# 本地知识库入口。
# 这里会连 PostgreSQL + pgvector，去 doc_chunks 里查相似内容。
my_vector = VectorStore(
    db_url=settings.DATABASE_URL,
    api_key=settings.DASH_API_KEY,
    collection_name="doc_chunks",
)


async def _search_local_async(query: str, top_k: int = 3) -> str:
    """先查本地知识库。"""
    loop = asyncio.get_event_loop()
    # my_vector.search 是同步函数，这里放线程池，避免阻塞异步主循环。
    results = await loop.run_in_executor(
        None,
        lambda: my_vector.search(query, top_k=top_k),
    )
    if not results:
        return ""

    formatted = [f"[Local KB] {res['source']}: {res['text']}" for res in results]
    return "\n".join(formatted)


async def _search_web_async(query: str, top_k: int = 3) -> str:
    """本地没有时，再走联网搜索。"""
    api_key = settings.SEARCHER_API
    if not api_key:
        return ""

    client = TavilyClient(api_key=api_key)
    try:
        # Tavily 官方 SDK 是同步接口。
        # 这里丢到线程里跑，避免卡住 async 主流程。
        response = await asyncio.to_thread(
            client.search,
            query,
            topic="finance",
            search_depth="advanced",
            max_results=top_k,
            include_answer=False,
            include_raw_content=False,
            timeout=10,
        )
    except Exception:
        return ""

    results = response.get("results", [])
    if not results:
        return ""

    return "\n".join(
        f"[Web] {item.get('title', '')}: {item.get('content', '')}"
        for item in results
        if isinstance(item, dict)
    )


def _clean_queries(queries: list[str]) -> list[str]:
    """做最简单的清洗：去空、去重。"""
    cleaned: list[str] = []
    for query in queries:
        normalized = query.strip()
        if normalized and normalized not in cleaned:
            cleaned.append(normalized)
    return cleaned


async def run_retrieval_async(
    queries: list[str],
    mode: Literal["local", "web", "auto"] = "auto",
    top_k: int = 5,
) -> str:
    """统一检索入口。

    现在我们故意写得简单一点：
    1. local：只查本地知识库
    2. web：只查联网
    3. auto：先查本地，本地没有再查联网
    """
    # 入参先做清洗，避免空字符串和重复查询造成无效请求。
    cleaned_queries = _clean_queries(queries)
    if not cleaned_queries:
        return ""

    if mode == "local":
        # 仅本地模式：命中即返回，减少不必要查询。
        for query in cleaned_queries:
            local_result = await _search_local_async(query, top_k=min(top_k, 3))
            if local_result:
                return local_result
        return ""

    if mode == "web":
        # 仅联网模式：适合知识库尚未导入或需要最新资讯。
        for query in cleaned_queries:
            web_result = await _search_web_async(query, top_k=min(top_k, 3))
            if web_result:
                return web_result
        return ""

    # auto 模式不要再“自己调自己”了。
    # 这里直接写顺序逻辑，新手读起来更直观：
    # 先本地，没查到再联网。
    for query in cleaned_queries:
        local_result = await _search_local_async(query, top_k=min(top_k, 3))
        if local_result:
            return local_result

    for query in cleaned_queries:
        web_result = await _search_web_async(query, top_k=min(top_k, 3))
        if web_result:
            return web_result

    return ""


@tool
async def search_intelligent(query: str) -> str:
    """给 Agent 调用的统一搜索工具。"""
    return await run_retrieval_async(queries=[query], mode="auto")
