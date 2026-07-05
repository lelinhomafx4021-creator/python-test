"""检索工具模块：统一封装本地知识库检索与联网检索。"""

import asyncio
import re
from typing import Literal

from langchain_core.tools import tool
from tavily import TavilyClient

from app.core.config import settings
from app.core.logger import logger
from app.rag.vector_store import VectorStore


# 本地知识库入口做成懒加载。
# 这样即使数据库暂时不可用，也不会在 import 阶段直接把整个服务拖死。
_vector_store: VectorStore | None = None
_vector_store_unavailable_logged = False

# 联网客户端做成模块级缓存，避免每次请求都重复初始化。
_tavily_client: TavilyClient | None = None
_tavily_missing_key_logged = False


def _query_preview(query: str, max_len: int = 80) -> str:
    """生成适合日志展示的短查询文本。"""
    normalized = " ".join((query or "").split())
    if len(normalized) <= max_len:
        return normalized
    return normalized[:max_len] + "..."


def _get_vector_store() -> VectorStore | None:
    """懒加载本地向量库；连接失败时返回 None。"""
    global _vector_store, _vector_store_unavailable_logged
    if _vector_store is not None:
        return _vector_store

    try:
        vector_store = VectorStore(
            db_url=settings.DATABASE_URL,
            api_key=settings.DASH_API_KEY,
            collection_name="doc_chunks",
        )
        vector_store.create_collection()
    except Exception as exc:
        if not _vector_store_unavailable_logged:
            logger.warning("本地向量库不可用，已跳过本地检索并交给联网检索：%s", exc)
            _vector_store_unavailable_logged = True
        _vector_store = None
        try:
            vector_store.close()  # type: ignore[possibly-undefined]
        except Exception:
            pass
        return None

    _vector_store = vector_store
    return _vector_store


def _clamp_top_k(top_k: int, *, min_k: int = 1, max_k: int = 8) -> int:
    """约束 top_k 的范围，避免传入异常值导致检索开销失控。"""
    try:
        normalized = int(top_k)
    except (TypeError, ValueError):
        normalized = min_k
    return max(min_k, min(max_k, normalized))


def _get_tavily_client() -> TavilyClient | None:
    """懒加载 Tavily 客户端；未配置 Key 时返回 None。"""
    global _tavily_client, _tavily_missing_key_logged
    if _tavily_client is not None:
        return _tavily_client

    api_key = settings.SEARCHER_API
    if not api_key:
        if not _tavily_missing_key_logged:
            logger.warning("Tavily 未配置 SEARCHER_API，联网检索已跳过")
            _tavily_missing_key_logged = True
        return None

    logger.info("Tavily 客户端已启用")
    _tavily_client = TavilyClient(api_key=api_key)
    return _tavily_client


async def _search_local_async(query: str, top_k: int = 3) -> str:
    """先查本地知识库。"""
    vector_store = _get_vector_store()
    if vector_store is None:
        logger.info("本地向量库不可用，跳过本地检索 query=%s", _query_preview(query))
        return ""

    loop = asyncio.get_running_loop()
    try:
        # VectorStore.search 是同步函数，这里放线程池，避免阻塞异步主循环。
        results = await loop.run_in_executor(
            None,
            lambda: vector_store.search(query, top_k=top_k),
        )
    except Exception as exc:
        logger.warning("本地向量库检索失败，已回退为无结果：%s", exc)
        return ""

    if not results:
        logger.info("本地向量库无命中 query=%s", _query_preview(query))
        return ""

    logger.info("本地向量库命中 %s 条 query=%s", len(results), _query_preview(query))
    formatted = [f"[Local KB] {res['source']}: {res['text']}" for res in results]
    return "\n".join(formatted)


async def _search_web_async(query: str, top_k: int = 3) -> str:
    """本地没有时，再走联网搜索。"""
    client = _get_tavily_client()
    if client is None:
        return ""

    logger.info("Tavily 联网检索开始 query=%s top_k=%s", _query_preview(query), top_k)
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
        logger.warning("Tavily 网络搜索失败", exc_info=True)
        return ""

    results = response.get("results", [])
    if not results:
        logger.info("Tavily 联网检索无结果 query=%s", _query_preview(query))
        return ""

    logger.info("Tavily 联网检索命中 %s 条 query=%s", len(results), _query_preview(query))
    return "\n".join(
        f"[Web:Tavily] {item.get('title', '')} ({item.get('url', '')}): {item.get('content', '')}"
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


async def _first_non_empty_result(
    queries: list[str],
    search_fn,
    top_k: int,
    label: str = "search",
) -> str:
    """并发执行多条查询，按原查询顺序返回第一条非空结果。"""
    tasks = [asyncio.create_task(search_fn(query, top_k=top_k)) for query in queries]
    results = await asyncio.gather(*tasks, return_exceptions=True)

    # 保持“输入优先级”语义：即使并发执行，也按原顺序挑第一条可用结果。
    for query, result in zip(queries, results):
        if isinstance(result, Exception):
            logger.warning(
                "%s 检索任务异常 query=%s error=%s: %s",
                label,
                _query_preview(query),
                type(result).__name__,
                result,
            )
            continue
        if isinstance(result, str) and result.strip():
            return result
    return ""


_FRESHNESS_KEYWORDS = (
    "最新",
    "今天",
    "今日",
    "现在",
    "当前",
    "实时",
    "新闻",
    "快讯",
    "公告",
    "政策",
    "本周",
    "本月",
    "最近",
    "近况",
    "涨停",
    "跌停",
    "异动",
    "2026",
    "latest",
    "today",
    "current",
    "breaking",
    "news",
)
_YEAR_PATTERN = re.compile(r"(?<!\d)20\d{2}(?!\d)")


def _requires_web_freshness(queries: list[str]) -> bool:
    """判断问题是否明显需要时效信息。"""
    joined = " ".join(queries).lower()
    return _YEAR_PATTERN.search(joined) is not None or any(keyword.lower() in joined for keyword in _FRESHNESS_KEYWORDS)


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
    normalized_top_k = _clamp_top_k(top_k)
    needs_fresh_web = _requires_web_freshness(cleaned_queries)
    logger.info(
        "检索入口 mode=%s top_k=%s fresh_web=%s queries=%s",
        mode,
        normalized_top_k,
        needs_fresh_web,
        [_query_preview(query) for query in cleaned_queries],
    )

    if mode == "local":
        # 仅本地模式：并发查多条 query，返回第一条命中结果。
        return await _first_non_empty_result(
            cleaned_queries,
            _search_local_async,
            top_k=min(normalized_top_k, 3),
            label="local",
        )

    if mode == "web":
        # 仅联网模式：适合知识库尚未导入或需要最新资讯。
        return await _first_non_empty_result(
            cleaned_queries,
            _search_web_async,
            top_k=min(normalized_top_k, 3),
            label="tavily",
        )

    # auto 模式不要再“自己调自己”了。
    # 这里直接写顺序逻辑，新手读起来更直观：
    # 先本地，没查到再联网。
    local_result = await _first_non_empty_result(
        cleaned_queries,
        _search_local_async,
        top_k=min(normalized_top_k, 3),
        label="local",
    )
    if local_result and not needs_fresh_web:
        logger.info("自动检索命中本地知识库")
        return local_result

    if local_result:
        logger.info("自动检索命中本地知识库，但问题需要时效信息，继续补充 Tavily 联网检索")
    else:
        logger.info("自动检索本地无结果，切换 Tavily 联网检索")

    web_result = await _first_non_empty_result(
        cleaned_queries,
        _search_web_async,
        top_k=min(normalized_top_k, 3),
        label="tavily",
    )
    if local_result and web_result:
        return f"{local_result}\n\n{web_result}"
    if web_result:
        return web_result
    if not web_result:
        logger.info("自动检索 Tavily 也未返回可用结果")
    return local_result or ""


@tool
async def search_intelligent(query: str) -> str:
    """给 Agent 调用的统一搜索工具。"""
    return await run_retrieval_async(queries=[query], mode="auto")
