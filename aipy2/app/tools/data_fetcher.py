"""
并行数据获取器 — asyncio.gather 同时拉多个数据源

核心思想：
用户问一个问题时，可能需要同时获取：
1. 行情数据（腾讯API）
2. 新闻数据（财新/东方财富）
3. 财务数据（东方财富Push2）
4. 检索结果（本地知识库/Tavily）

传统做法：一个一个串行调用，总耗时 = A + B + C + D
并行做法：asyncio.gather 同时发出，总耗时 = max(A, B, C, D)

这就是"子agent"的第一种模式：一个节点内部用 asyncio.gather 并行调多个工具。
"""

import asyncio
import logging
from typing import Any

import httpx

from app.tools.common import build_market_code, build_secid, extract_stock_code
from app.tools.retriever_tool import run_retrieval_async
from app.tools.news_tool import collect_hot_news

logger = logging.getLogger(__name__)

# 东方财富Push2 API — 一次拉取股价+PE+营收+利润+负债率等财务指标
EASTMONEY_PUSH2_URL = "https://push2.eastmoney.com/api/qt/stock/get"
EASTMONEY_PUSH2_FIELDS = (
    "f43,f44,f45,f46,f47,f48,f50,f51,f52,f55,f57,f58,f60,"
    "f115,f116,f117,f162,f163,f164,f165,f167,f168,f169,f170,f171,"
    "f173,f177,f183,f184,f185,f186,f187,f188,f190"
)

# 东方财富公告API — 获取个股最新公告
EASTMONEY_ANN_URL = "https://np-anotice-stock.eastmoney.com/api/security/ann"


# ============ 并行获取各数据源 ============

async def fetch_market_data(code: str, client: httpx.AsyncClient) -> dict[str, Any] | None:
    """【行情数据】从腾讯API获取实时行情。"""
    url = f"https://qt.gtimg.cn/q={build_market_code(code)}"
    try:
        resp = await client.get(url, timeout=8)
        text = resp.content.decode("gb18030", errors="ignore")
        fields = text.split("~")
        if len(fields) < 45:
            return None
        return {
            "symbol": fields[2],
            "name": fields[1],
            "lastPrice": fields[3],
            "changePercent": fields[32],
            "changeAmount": fields[31],
            "highPrice": fields[33],
            "lowPrice": fields[34],
            "openPrice": fields[5],
            "volume": fields[36],
            "turnover": fields[37],
            "turnoverRate": fields[38],
            "amplitude": fields[43],
        }
    except Exception as e:
        logger.warning("行情获取失败 code=%s: %s", code, e)
        return None


async def fetch_financial_data(code: str, client: httpx.AsyncClient) -> dict[str, Any] | None:
    """【财务数据】从东方财富Push2获取PE、营收、利润、负债率等。"""
    secid = build_secid(code)
    params = {"secid": secid, "fields": EASTMONEY_PUSH2_FIELDS}
    headers = {"User-Agent": "Mozilla/5.0"}
    try:
        resp = await client.get(EASTMONEY_PUSH2_URL, params=params, headers=headers, timeout=8)
        data = resp.json().get("data", {})
        if not data:
            return None

        # 注意：东方财富返回的字段有整数和浮点两种格式
        # 整数字段需要除以100（如f43=5383表示53.83）
        # 浮点字段直接用（如f184=6.11表示6.11%）
        return {
            "name": data.get("f58", ""),
            "currentPrice": data.get("f43", 0) / 100,
            "pe": data.get("f162", 0) / 100,
            "pb": data.get("f190"),
            "marketCap": round(data.get("f117", 0) / 1e8, 2),  # 亿
            "revenueTTM": round(data.get("f183", 0) / 1e8, 2),  # 亿
            "revenueGrowth": data.get("f184"),  # 已是百分比
            "profitGrowth": data.get("f185"),   # 已是百分比
            "grossMargin": data.get("f186"),    # 已是百分比
            "netMargin": data.get("f187"),      # 已是百分比
            "debtRatio": data.get("f188"),      # 已是百分比
            "high52w": data.get("f51", 0) / 100,
            "low52w": data.get("f52", 0) / 100,
        }
    except Exception as e:
        logger.warning("财务数据获取失败 code=%s: %s", code, e)
        return None


async def fetch_announcements(code: str, client: httpx.AsyncClient, limit: int = 5) -> list[dict]:
    """【公告数据】从东方财富获取个股最新公告标题。"""
    params = {
        "page_size": limit,
        "page_index": 1,
        "ann_type": "A",
        "stock_list": code,
        "f_node": 0,
        "s_node": 0,
    }
    headers = {"User-Agent": "Mozilla/5.0"}
    try:
        resp = await client.get(EASTMONEY_ANN_URL, params=params, headers=headers, timeout=8)
        data = resp.json().get("data", {})
        items = data.get("list", []) if isinstance(data, dict) else []
        return [
            {"date": item.get("notice_date", ""), "title": item.get("title_ch", "")}
            for item in items
        ]
    except Exception as e:
        logger.warning("公告获取失败 code=%s: %s", code, e)
        return []


async def fetch_news_data(query: str) -> list[dict]:
    """【新闻数据】采集财经热点新闻（同步函数放线程池）。"""
    try:
        return await asyncio.to_thread(collect_hot_news, limit=8)
    except Exception as e:
        logger.warning("新闻获取失败: %s", e)
        return []


async def fetch_retrieval_data(queries: list[str], top_k: int = 3) -> str:
    """【检索数据】本地知识库 + 联网检索。"""
    try:
        return await run_retrieval_async(queries=queries, mode="auto", top_k=top_k)
    except Exception as e:
        logger.warning("检索失败: %s", e)
        return ""


# ============ 统一并行入口 ============

async def fetch_all_data_parallel(
    query: str,
    queries: list[str],
    top_k: int = 3,
) -> dict[str, Any]:
    """
    【核心函数】并行获取所有数据源
    
    一个函数内部用 asyncio.gather 同时发起多个请求：
    - 行情数据（如果有股票代码）
    - 财务数据（如果有股票代码）
    - 公告数据（如果有股票代码）
    - 新闻数据（总是获取）
    - 检索结果（总是获取）
    
    总耗时 = max(各个请求的耗时)，而不是加起来
    
    返回格式：
    {
        "market": {...},        # 行情数据
        "financial": {...},     # 财务数据
        "announcements": [...], # 公告列表
        "news": [...],          # 新闻列表
        "retrieval": "...",     # 检索文本
        "symbol": "603283",     # 提取到的股票代码（可能为None）
    }
    """
    symbol = extract_stock_code(query)
    
    # 行情 + 财务 + 公告：需要股票代码，用共享的httpx客户端
    if symbol:
        async with httpx.AsyncClient() as client:
            # 这三个任务共享同一个HTTP连接池
            market_task = fetch_market_data(symbol, client)
            financial_task = fetch_financial_data(symbol, client)
            ann_task = fetch_announcements(symbol, client)
            news_task = fetch_news_data(query)
            retrieval_task = fetch_retrieval_data(queries, top_k)
            
            # asyncio.gather 同时执行所有任务
            # return_exceptions=True 确保单个任务失败不会拖垮其他任务
            results = await asyncio.gather(
                market_task,
                financial_task,
                ann_task,
                news_task,
                retrieval_task,
                return_exceptions=True,
            )
    else:
        # 没有股票代码时，只获取新闻和检索
        news_task = fetch_news_data(query)
        retrieval_task = fetch_retrieval_data(queries, top_k)
        
        results = await asyncio.gather(
            news_task,
            retrieval_task,
            return_exceptions=True,
        )
        # 没有股票代码时，行情/财务/公告都是None
        results = [None, None, None] + list(results)
    
    # 解包结果，异常的返回None/空
    def _safe(result, default=None):
        """安全解包：如果result是异常，返回默认值。"""
        if isinstance(result, Exception):
            logger.warning("并行获取中某个任务失败: %s", result)
            return default
        return result
    
    market = _safe(results[0]) if symbol else None
    financial = _safe(results[1]) if symbol else None
    ann = _safe(results[2], []) if symbol else []
    news = _safe(results[3 if symbol else 0], [])
    retrieval = _safe(results[4 if symbol else 1], "")
    
    # 把结构化数据拼成文本，方便后续喂给LLM
    knowledge_parts = []
    
    if retrieval:
        knowledge_parts.append(f"【检索资料】\n{retrieval}")
    
    if market:
        market_text = (
            f"股票{market['name']}({market['symbol']})："
            f"现价{market['lastPrice']}元，"
            f"涨跌幅{market['changePercent']}%，"
            f"成交额{market['turnover']}万元，"
            f"换手率{market['turnoverRate']}%"
        )
        knowledge_parts.append(f"【实时行情】\n{market_text}")
    
    if financial:
        fin_text = (
            f"PE(TTM): {financial['pe']}，"
            f"总市值: {financial['marketCap']}亿，"
            f"TTM营收: {financial['revenueTTM']}亿，"
            f"营收同比: {financial['revenueGrowth']}%，"
            f"净利同比: {financial['profitGrowth']}%，"
            f"毛利率: {financial['grossMargin']}%，"
            f"净利率: {financial['netMargin']}%，"
            f"负债率: {financial['debtRatio']}%，"
            f"52周高: {financial['high52w']}，52周低: {financial['low52w']}"
        )
        knowledge_parts.append(f"【财务数据】\n{fin_text}")
    
    if ann:
        ann_text = "\n".join([f"- {a['date']}: {a['title']}" for a in ann])
        knowledge_parts.append(f"【最新公告】\n{ann_text}")
    
    if news:
        news_text = "\n".join([f"- {n.get('title', '')}" for n in news[:6]])
        knowledge_parts.append(f"【财经新闻】\n{news_text}")
    
    return {
        "knowledge": "\n\n".join(knowledge_parts),
        "market": market,
        "financial": financial,
        "announcements": ann,
        "news": news,
        "symbol": symbol,
    }
