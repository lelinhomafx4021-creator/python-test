"""新闻聚合接口。

本模块提供财经新闻聚合查询功能，整合多个数据源并进行情绪分析。

数据源：
- 同花顺（10jqka）财经资讯
- 东方财富公告资讯

功能：
- 按分类查询新闻（市场 / 公司 / 政策 / 宏观）
- 自动计算新闻情绪（利好 / 利空 / 中性）
- VIP 标记：深度分析类内容仅对 VIP 用户开放

情绪分析策略：
- 基于关键词匹配，不依赖大模型，响应速度快
- 利好关键词：利好、上涨、突破、业绩增长、回购、增持、涨停
- 利空关键词：利空、下跌、暴跌、亏损、减持、跌停、违规

典型用途：
- 财经资讯列表页
- 个股相关新闻聚合
- 市场情绪指标
"""

from __future__ import annotations

import time
from datetime import datetime
from typing import Any, Optional

import httpx
from fastapi import APIRouter, HTTPException, Query

from app.core.logger import logger
from app.tools.common import format_time, safe_text

# ---------------------------------------------------------------------------
# 配置常量
# ---------------------------------------------------------------------------

# HTTP 请求默认超时
HTTP_TIMEOUT = 8

# 新闻缓存（简单内存缓存，TTL 10 分钟）
_NEWS_CACHE_TTL = 600
_news_cache: dict[str, tuple[float, list[dict[str, Any]]]] = {}

# ---------------------------------------------------------------------------
# 情绪分析关键词表
# ---------------------------------------------------------------------------

# 利好关键词（出现即标记为 positive）
BULLISH_KEYWORDS = [
    "利好", "上涨", "突破", "涨停", "业绩增长", "净利润增长",
    "营收增长", "回购", "增持", "超预期", "大涨", "反弹",
    "创新高", "放量上涨", "主力流入", "北向资金买入",
    "利润增长", "分红", "派息", "送转", "重大合同", "中标",
]

# 利空关键词（出现即标记为 negative）
BEARISH_KEYWORDS = [
    "利空", "下跌", "暴跌", "跌停", "亏损", "净利润下降",
    "营收下降", "减持", "违规", "处罚", "风险", "大跌",
    "创新低", "主力流出", "北向资金卖出", "退市",
    "业绩下滑", "商誉减值", "坏账", "诉讼", "被调查",
]

# VIP 标记关键词（深度分析类内容）
VIP_KEYWORDS = [
    "深度分析", "研究报告", "投资策略", "深度解读",
    "专题研究", "行业深度", "公司深度", "策略报告",
]


# ---------------------------------------------------------------------------
# 情绪分析工具函数
# ---------------------------------------------------------------------------

def _analyze_sentiment(title: str, summary: str = "") -> str:
    """基于关键词计算新闻情绪。

    分析逻辑：
    1. 扫描标题和摘要中的利好/利空关键词
    2. 利好关键词数量 > 利空 → positive
    3. 利空关键词数量 > 利好 → negative
    4. 相等或都没有 → neutral

    参数：
        title: 新闻标题
        summary: 新闻摘要（可选）

    返回：
        "positive" / "negative" / "neutral"
    """
    text = f"{title} {summary}"

    bullish_count = sum(1 for kw in BULLISH_KEYWORDS if kw in text)
    bearish_count = sum(1 for kw in BEARISH_KEYWORDS if kw in text)

    if bullish_count > bearish_count:
        return "positive"
    elif bearish_count > bullish_count:
        return "negative"
    return "neutral"


def _is_vip_content(title: str, summary: str = "") -> bool:
    """判断是否为 VIP 专属内容。

    匹配策略：标题或摘要中包含 VIP 关键词即标记为 VIP 内容。
    """
    text = f"{title} {summary}"
    return any(kw in text for kw in VIP_KEYWORDS)


# ---------------------------------------------------------------------------
# 同花顺新闻采集（异步）
# ---------------------------------------------------------------------------

async def _fetch_10jqka_news(client: httpx.AsyncClient) -> list[dict[str, Any]]:
    """从同花顺获取财经资讯。

    使用同花顺公开的资讯列表接口，返回最新新闻。
    """
    url = "https://news.10jqka.com.cn/tapp/news/push/stock/"
    params = {
        "page": 1,
        "tag": "",
        "track": "website",
        "pagesize": 50,
    }

    resp = await client.get(
        url,
        params=params,
        headers={
            "Referer": "https://news.10jqka.com.cn/",
            "User-Agent": (
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                "AppleWebKit/537.36 (KHTML, like Gecko) "
                "Chrome/124.0.0.0 Safari/537.36"
            ),
        },
        timeout=HTTP_TIMEOUT,
    )
    resp.raise_for_status()

    data = resp.json()
    items = data.get("data", {}).get("list", [])

    result: list[dict[str, Any]] = []
    for item in items:
        title = safe_text(item.get("title"))
        if not title:
            continue

        summary = safe_text(item.get("digest"), title)[:200]
        sentiment = _analyze_sentiment(title, summary)
        is_vip = _is_vip_content(title, summary)

        result.append({
            "title": title,
            "summary": summary,
            "tag": safe_text(item.get("tag"), "财经资讯"),
            "source": "同花顺",
            "url": safe_text(item.get("url")),
            "publishedAt": format_time(item.get("ctime")),
            "sentiment": sentiment,
            "vipOnly": is_vip,
        })

    return result


# ---------------------------------------------------------------------------
# 东方财富新闻采集（异步）
# ---------------------------------------------------------------------------

async def _fetch_eastmoney_news(client: httpx.AsyncClient) -> list[dict[str, Any]]:
    """从东方财富获取财经资讯。

    使用东方财富公开的资讯列表接口。
    """
    url = "https://np-listapi.eastmoney.com/comm/web/getNewsByColumns"
    params = {
        "client": "web",
        "biz": "web_home_channel",
        "column": "350",
        "order": "1",
        "needInteractData": 0,
        "page_index": 1,
        "page_size": 50,
    }

    resp = await client.get(
        url,
        params=params,
        headers={
            "Referer": "https://www.eastmoney.com/",
            "User-Agent": (
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                "AppleWebKit/537.36 (KHTML, like Gecko) "
                "Chrome/124.0.0.0 Safari/537.36"
            ),
        },
        timeout=HTTP_TIMEOUT,
    )
    resp.raise_for_status()

    data = resp.json()
    items = data.get("data", {}).get("list", [])

    result: list[dict[str, Any]] = []
    for item in items:
        title = safe_text(item.get("title"))
        if not title:
            continue

        summary = safe_text(item.get("digest"), title)[:200]
        sentiment = _analyze_sentiment(title, summary)
        is_vip = _is_vip_content(title, summary)

        # 东方财富时间戳为毫秒级
        pub_time = item.get("showtime") or item.get("display_time")
        if isinstance(pub_time, (int, float)) and pub_time > 1e12:
            pub_time = datetime.fromtimestamp(pub_time / 1000).isoformat()

        result.append({
            "title": title,
            "summary": summary,
            "tag": safe_text(item.get("media_name"), "东方财富"),
            "source": "东方财富",
            "url": safe_text(item.get("url_unique") or item.get("art_url")),
            "publishedAt": format_time(pub_time),
            "sentiment": sentiment,
            "vipOnly": is_vip,
        })

    return result


# ---------------------------------------------------------------------------
# 新闻聚合核心逻辑
# ---------------------------------------------------------------------------

# 分类与数据源映射
CATEGORY_SOURCE_MAP = {
    "market": ["10jqka", "eastmoney"],      # 市场综合
    "company": ["eastmoney"],               # 公司公告
    "policy": ["10jqka"],                   # 政策动态
    "macro": ["10jqka", "eastmoney"],       # 宏观经济
}


def _get_cache_key(category: str, limit: int) -> str:
    """生成新闻缓存键。"""
    return f"news:{category}:{limit}"


async def _fetch_news_from_sources(
    client: httpx.AsyncClient,
    sources: list[str],
) -> list[dict[str, Any]]:
    """从指定数据源采集新闻并合并去重。"""
    all_news: list[dict[str, Any]] = []
    seen_titles: set[str] = set()

    fetcher_map = {
        "10jqka": _fetch_10jqka_news,
        "eastmoney": _fetch_eastmoney_news,
    }

    for source_name in sources:
        fetcher = fetcher_map.get(source_name)
        if not fetcher:
            continue

        try:
            news_items = await fetcher(client)
            for item in news_items:
                title_key = item["title"]
                if title_key in seen_titles:
                    continue
                seen_titles.add(title_key)
                all_news.append(item)

        except Exception as exc:
            logger.warning("新闻源 %s 采集失败: %s", source_name, exc)
            continue

    return all_news


async def aggregate_news(category: str = "market", limit: int = 20) -> list[dict[str, Any]]:
    """聚合多源财经新闻。

    参数：
        category: 新闻分类（market/company/policy/macro）
        limit: 返回条数限制

    返回：
        新闻列表，每条包含 title/summary/tag/source/publishedAt/sentiment/vipOnly
    """
    # 检查缓存
    cache_key = _get_cache_key(category, limit)
    cached = _news_cache.get(cache_key)
    if cached:
        ts, data = cached
        if time.time() - ts < _NEWS_CACHE_TTL:
            logger.debug("新闻缓存命中，category=%s", category)
            return data[:limit]

    # 确定要查询的数据源
    sources = CATEGORY_SOURCE_MAP.get(category, CATEGORY_SOURCE_MAP["market"])

    # 异步采集新闻
    async with httpx.AsyncClient() as client:
        all_news = await _fetch_news_from_sources(client, sources)

    # 按时间倒序排序（有时间的排前面）
    def _sort_key(item: dict[str, Any]) -> str:
        return item.get("publishedAt") or "0000"

    all_news.sort(key=_sort_key, reverse=True)

    # 写入缓存
    _news_cache[cache_key] = (time.time(), all_news)

    return all_news[:limit]


# ---------------------------------------------------------------------------
# API 路由
# ---------------------------------------------------------------------------

router = APIRouter(prefix="/api/v1", tags=["新闻聚合接口"])


@router.get("/news")
async def get_news(
    category: str = Query("market", description="新闻分类：market/company/policy/macro"),
    limit: int = Query(20, ge=1, le=50, description="返回条数，最多 50"),
    vip_only: Optional[bool] = Query(None, description="是否只返回 VIP 内容（需登录态）"),
):
    """获取聚合财经新闻列表。

    请求参数：
    - `category`：新闻分类，可选 market（市场综合）/ company（公司公告）/ policy（政策动态）/ macro（宏观经济），默认 market
    - `limit`：返回条数，默认 20，最大 50
    - `vip_only`：是否只返回 VIP 内容（可选，默认返回全部）

    返回数据：
    - `items`：新闻列表
      - `title`：标题
      - `summary`：摘要
      - `tag`：标签
      - `source`：来源（同花顺 / 东方财富）
      - `publishedAt`：发布时间
      - `sentiment`：情绪（positive / negative / neutral）
      - `vipOnly`：是否 VIP 专属内容
    - `meta`：元信息（数据源、分类、总数）
    """
    valid_categories = ["market", "company", "policy", "macro"]
    if category not in valid_categories:
        raise HTTPException(
            status_code=400,
            detail=f"不支持的 category: {category}，可选值：{' / '.join(valid_categories)}"
        )

    try:
        # 异步聚合新闻（不阻塞事件循环）
        all_news = await aggregate_news(category=category, limit=limit)

        # 按 VIP 筛选（如果指定了）
        if vip_only is True:
            all_news = [item for item in all_news if item.get("vipOnly")]

        # 截取到请求数量
        result_items = all_news[:limit]

        # 统计情绪分布
        sentiment_stats = {"positive": 0, "negative": 0, "neutral": 0}
        for item in result_items:
            s = item.get("sentiment", "neutral")
            if s in sentiment_stats:
                sentiment_stats[s] += 1

        logger.info(
            "新闻聚合完成，category=%s, count=%d, sentiment=%s",
            category, len(result_items), sentiment_stats
        )

        return {
            "code": 200,
            "data": {
                "items": result_items,
                "meta": {
                    "category": category,
                    "total": len(result_items),
                    "sentiment": sentiment_stats,
                    "sources": list({item["source"] for item in result_items}),
                },
            },
            "message": "成功",
        }

    except HTTPException:
        raise
    except Exception as exc:
        logger.error("新闻聚合接口异常，category=%s, error=%s", category, exc)
        raise HTTPException(status_code=500, detail="新闻聚合服务暂时不可用")
