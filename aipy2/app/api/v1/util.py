"""AI 通用工具接口（新手友好版）。

本模块当前提供：
- `/ai/v1/util/generate_title`：根据用户问题自动生成简短标题。
- `/ai/v1/util/health`：用于本地启动脚本探活。
- `/ai/v1/util/market/quotes`：给 Java 主业务服务提供统一行情适配接口。

典型用途：
- 聊天列表页第一轮对话结束后，异步请求标题用于展示会话名称。
"""

import json

from fastapi import APIRouter, Query, Request

from app.core.llm import llm
from app.core.logger import logger
from app.prompts.investor_prompts import GENERATE_TITLE_PROMPT, TitleResult
from app.tools.news_tool import collect_hot_news
from app.tools.stockdata_tool import get_stock_quote_core, load_market_page, search_market_stocks

router = APIRouter(prefix="/ai/v1/util", tags=["AI通用工具接口"])


@router.get("/health")
async def health():
    """轻量健康检查接口。"""
    return {"code": 200, "data": {"status": "ok"}, "message": "成功"}


@router.get("/market/quotes")
async def market_quotes(symbols: str = Query(..., description="多个股票代码，使用逗号分隔")):
    """批量获取股票行情。

    这是给 Java 主业务侧调用的内部适配接口，统一返回稳定的英文键名，
    这样 Java 不需要直接感知第三方行情源的原始字段结构。
    """
    result = []
    for raw_symbol in symbols.split(","):
        symbol = raw_symbol.strip()
        if not symbol:
            continue

        try:
            payload = json.loads(get_stock_quote_core.invoke({"symbol": symbol}))
            if payload.get("error"):
                result.append(
                    {
                        "symbol": symbol,
                        "status": "error",
                        "message": payload["error"],
                    }
                )
                continue

            result.append(
                {
                    "symbol": payload.get("代码") or symbol,
                    "name": payload.get("名称") or symbol,
                    "lastPrice": payload.get("最新价"),
                    "highPrice": payload.get("最高价"),
                    "lowPrice": payload.get("最低价"),
                    "openPrice": payload.get("今开"),
                    "changePercent": payload.get("涨跌幅(%)"),
                    "changeAmount": payload.get("涨跌额"),
                    "volume": payload.get("成交量(手)"),
                    "turnover": payload.get("成交额(元)"),
                    "turnoverRate": payload.get("换手率(%)"),
                    "amplitude": payload.get("振幅(%)"),
                    "quoteTime": payload.get("quoteTime", ""),
                    "status": "ok",
                }
            )
        except Exception as exc:
            logger.warning("批量行情接口查询失败，symbol=%s, error=%s", symbol, exc)
            result.append(
                {
                    "symbol": symbol,
                    "status": "error",
                    "message": "行情查询失败",
                }
            )

    return {"code": 200, "data": {"quotes": result}, "message": "成功"}


@router.get("/market/stocks")
async def market_stocks(
    page: int = Query(1, ge=1, description="页码，从 1 开始"),
    page_size: int = Query(40, ge=1, le=200, description="每页数量"),
    keyword: str = Query("", description="股票代码或名称关键字"),
):
    """获取股票列表或搜索结果。"""
    try:
        if keyword.strip():
            data = search_market_stocks(keyword=keyword, page=page, page_size=page_size)
        else:
            data = load_market_page(page=page, page_size=page_size)
        return {"code": 200, "data": data, "message": "成功"}
    except Exception as exc:
        logger.error("股票列表接口失败: %s", exc)
        return {"code": 500, "data": {"page": page, "pageSize": page_size, "total": 0, "items": []}, "message": "失败"}


@router.get("/news/hot")
async def hot_news(limit: int = Query(12, ge=1, le=30, description="新闻条数")):
    """获取财经热点新闻。"""
    try:
        data = collect_hot_news(limit=limit)
        return {"code": 200, "data": {"items": data}, "message": "成功"}
    except Exception as exc:
        logger.error("热点新闻接口失败: %s", exc)
        return {"code": 500, "data": {"items": []}, "message": "失败"}


def _normalize_title(raw_title: str) -> str:
    """规范化标题。

    处理规则：
    - 去掉常见中英文标点与空格。
    - 最多保留 5 个字。
    - 如果结果为空，回退为“投研对话”。
    """
    title = "".join(ch for ch in raw_title.strip() if ch not in "，。！？、；：,.!?;: ")
    return title[:5] or "投研对话"


def _response_text(response) -> str:
    """兼容不同模型返回结构，统一提取文本内容。"""
    content = getattr(response, "content", "")
    return content if isinstance(content, str) else str(content)


@router.post("/generate_title")
async def generate_title(request: Request):
    """根据用户问题生成简短中文标题。

    请求体（JSON）示例：
    {
      "query": "贵州茅台一季度利润怎么看"
    }

    返回示例：
    {
      "code": 200,
      "data": {"title": "茅台利润"},
      "message": "成功"
    }
    """
    try:
        # 1) 读取前端传入的原始问题
        body = await request.json()
        query = body.get("query", "新对话")
        logger.info("[标题生成] 收到问题：%s", query)

        # 2) 调用大模型，走原生 function calling 获取标题
        structured_llm = llm.with_structured_output(TitleResult)
        result = await structured_llm.ainvoke(
            GENERATE_TITLE_PROMPT.format_messages(query=query)
        )
        title = _normalize_title(result.title)

        logger.info("[标题生成] 生成结果：%s", title)
        return {"code": 200, "data": {"title": title}, "message": "成功"}
    except Exception as e:
        # 失败时统一返回兜底标题，保证前端有可展示内容
        logger.error(f"标题生成失败：{str(e)}")
        return {"code": 500, "data": {"title": "投研对话"}, "message": "失败"}
