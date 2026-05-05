"""财经热点新闻采集工具。"""

from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor, TimeoutError as FutureTimeoutError
from datetime import datetime
from typing import Any, Callable

import akshare as ak

from app.core.logger import logger

# 抓取超时上限（秒），避免网络异常时线程长期阻塞
_NEWS_FETCH_TIMEOUT_SECONDS = 8


def _safe_text(value: Any, fallback: str = "") -> str:
    """把任意值安全转换成字符串。"""
    if value is None:
        return fallback
    text = str(value).strip()
    return text or fallback


def _format_time(value: Any) -> str | None:
    """统一格式化时间字段。"""
    if value is None:
        return None
    text = str(value).strip()
    if not text:
        return None
    try:
        return str(datetime.fromisoformat(text.replace("Z", "+00:00")))
    except Exception:
        return text


def _run_with_timeout(task_name: str, supplier: Callable[[], Any]) -> Any:
    """给第三方联网抓取增加超时保护，避免断网时长时间卡死。"""
    with ThreadPoolExecutor(max_workers=1, thread_name_prefix="news-fetch") as executor:
        future = executor.submit(supplier)
        try:
            return future.result(timeout=_NEWS_FETCH_TIMEOUT_SECONDS)
        except FutureTimeoutError:
            logger.warning("热点新闻抓取超时，task=%s, timeout=%ss", task_name, _NEWS_FETCH_TIMEOUT_SECONDS)
        except Exception as exc:
            logger.warning("热点新闻抓取失败，task=%s, error=%s", task_name, exc)
    return None


def collect_hot_news(limit: int = 12) -> list[dict[str, Any]]:
    """采集财经热点新闻。

    联网失败时只影响本次请求：
    - 不抛出致命异常
    - 不影响服务继续运行
    - 当前接口返回可控空列表
    """
    result: list[dict[str, Any]] = []
    seen: set[str] = set()

    # 优先从财新数据通获取财经新闻
    cx_df = _run_with_timeout("财新数据通", ak.stock_news_main_cx)
    if cx_df is not None:
        for _, row in cx_df.head(limit).iterrows():
            url = _safe_text(row.get("url"))
            title = _safe_text(row.get("summary"))
            unique_key = url or title
            if not unique_key or unique_key in seen:
                continue
            seen.add(unique_key)
            result.append(
                {
                    "title": title,
                    "summary": title,
                    "tag": _safe_text(row.get("tag"), "市场动态"),
                    "source": "财新数据通",
                    "url": url,
                    "publishedAt": None,
                }
            )
            if len(result) >= limit:
                return result

    # 若财新数据不足，从东方财富补充
    em_df = _run_with_timeout("东方财富资讯", ak.stock_news_em)
    if em_df is not None:
        for _, row in em_df.iterrows():
            url = _safe_text(row.get("新闻链接"))
            title = _safe_text(row.get("新闻标题"))
            unique_key = url or title
            if not unique_key or unique_key in seen:
                continue
            seen.add(unique_key)
            result.append(
                {
                    "title": title,
                    "summary": _safe_text(row.get("新闻内容"), title)[:140],
                    "tag": _safe_text(row.get("关键词"), "股票资讯"),
                    "source": _safe_text(row.get("文章来源"), "东方财富"),
                    "url": url,
                    "publishedAt": _format_time(row.get("发布时间")),
                }
            )
            if len(result) >= limit:
                break

    return result[:limit]
