"""
股票行情与股票搜索工具

功能：
1. 获取单只/多只 A 股实时行情（通过腾讯行情接口）
2. 按关键字搜索股票（支持代码、中文名、拼音缩写）
3. 获取热门股票列表

数据源：
- 腾讯行情接口（qt.gtimg.cn）：实时价格、涨跌幅、成交量
- 新浪联想接口：搜索建议
- 腾讯联想接口：搜索建议

对外接口：
- get_stock_quote_core：获取单只股票行情（LangChain Tool）
- load_market_page：获取热门股票列表
- search_market_stocks：按关键字搜索股票
"""

from __future__ import annotations

import json
import re
import urllib.parse
from typing import Any

import requests
from langchain_core.tools import tool


TENCENT_QUOTE_URL = "https://qt.gtimg.cn/q={code}"
TENCENT_SUGGEST_URL = "https://smartbox.gtimg.cn/s3/?q={keyword}&t=all"
SINA_SUGGEST_URL = "https://suggest3.sinajs.cn/suggest/type=11,12,13,14,15&key={keyword}&name=suggestdata"
# 热门股票代码列表（涵盖各行业龙头）
HOT_SYMBOLS = [
    "600519",  # 贵州茅台
    "000001",  # 平安银行
    "300750",  # 宁德时代
    "601318",  # 中国平安
    "600036",  # 招商银行
    "601688",  # 华泰证券
    "002594",  # 比亚迪
    "000858",  # 五粮液
    "600900",  # 长江电力
    "601398",  # 工商银行
    "601288",  # 农业银行
    "601166",  # 兴业银行
    "600030",  # 中信证券
    "601012",  # 隆基绿能
    "600809",  # 山西汾酒
    "002415",  # 海康威视
    "300059",  # 东方财富
    "601899",  # 紫金矿业
    "000333",  # 美的集团
    "601888",  # 中国中免
    "600276",  # 恒瑞医药
    "688981",  # 中芯国际
    "603259",  # 药明康德
    "600887",  # 伊利股份
]

# 创建 requests 会话对象，复用 TCP 连接提升性能
_http = requests.Session()
_http.trust_env = False  # 不使用系统代理，避免开发环境干扰


def _build_market_code(symbol: str) -> str:
    """把 6 位股票代码转换成腾讯接口需要的市场前缀格式
    
    股票代码规则：
    - 以 5/6/9 开头：上海市场（sh）
    - 其他：深圳市场（sz）
    
    示例：600519 -> sh600519, 000001 -> sz000001
    """
    if not re.fullmatch(r"\d{6}", symbol):
        raise ValueError("symbol 必须是 6 位数字，例如 600519")
    return f"sh{symbol}" if symbol.startswith(("5", "6", "9")) else f"sz{symbol}"


def _safe_float(raw: str | None) -> float | None:
    """把原始字符串安全转换成浮点数。"""
    if raw is None or raw == "":
        return None
    try:
        return round(float(raw), 2)
    except (TypeError, ValueError):
        return None


def _safe_turnover(raw: str | None) -> float | None:
    """腾讯返回的成交额字段按“万元”理解，统一换算成“元”返回。"""
    value = _safe_float(raw)
    if value is None:
        return None
    return round(value * 10000, 2)


def _decode_quote_text(response: requests.Response) -> str:
    """腾讯行情接口显式按 GB18030 解码，避免中文名称乱码。"""
    return response.content.decode("gb18030", errors="ignore").strip()


def _empty_market_page(page: int, page_size: int) -> dict[str, Any]:
    """统一空分页结构。"""
    return {"page": page, "pageSize": page_size, "total": 0, "items": []}


def _parse_tencent_quote_payload(payload: str, fallback_symbol: str) -> dict[str, Any]:
    """解析腾讯单条行情字符串。"""
    fields = payload.split("~")
    if len(fields) < 45:
        return {"symbol": fallback_symbol, "name": fallback_symbol}
    
    # 解析时间：fields[30] 是日期（YYYYMMDD），fields[31] 是时间（HHMMSS）
    quote_time = ""
    if len(fields) > 31 and fields[30] and fields[31]:
        try:
            date_str = fields[30].strip()
            time_str = fields[31].strip()
            if len(date_str) == 8 and len(time_str) == 6:
                quote_time = f"{date_str[:4]}-{date_str[4:6]}-{date_str[6:8]} {time_str[:2]}:{time_str[2:4]}:{time_str[4:6]}"
        except Exception:
            pass
    
    return {
        "symbol": fields[2] or fallback_symbol,
        "name": fields[1] or fallback_symbol,
        "lastPrice": _safe_float(fields[3]),
        "highPrice": _safe_float(fields[33]),
        "lowPrice": _safe_float(fields[34]),
        "openPrice": _safe_float(fields[5]),
        "changePercent": _safe_float(fields[32]),
        "changeAmount": _safe_float(fields[31]),
        "volume": _safe_float(fields[36]),
        "turnover": _safe_turnover(fields[37]),
        "turnoverRate": _safe_float(fields[38]),
        "amplitude": _safe_float(fields[43]),
        "quoteTime": quote_time,
    }


def _fetch_batch_quotes(symbols: list[str]) -> dict[str, dict[str, Any]]:
    """批量获取多只股票行情。"""
    normalized_symbols = [symbol.strip() for symbol in symbols if symbol and symbol.strip()]
    if not normalized_symbols:
        return {}

    market_codes = [_build_market_code(symbol) for symbol in normalized_symbols]
    response = _http.get(
        TENCENT_QUOTE_URL.format(code=",".join(market_codes)),
        headers={
            "Referer": "https://gu.qq.com/",
            "User-Agent": (
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                "AppleWebKit/537.36 (KHTML, like Gecko) "
                "Chrome/124.0.0.0 Safari/537.36"
            ),
        },
        timeout=8,
    )
    response.raise_for_status()

    text = _decode_quote_text(response)
    result: dict[str, dict[str, Any]] = {}
    for line in [item for item in text.split(";") if item.strip()]:
        if '"' not in line:
            continue
        raw_code = line.split("=", 1)[0].replace("v_", "").strip()
        payload = line.split('"', 2)[1]
        parsed = _parse_tencent_quote_payload(payload, raw_code[-6:])
        result[parsed["symbol"]] = parsed
    return result


def _parse_sina_suggestions(text: str) -> list[dict[str, str]]:
    """解析新浪联想结果。"""
    if '"' not in text:
        return []
    payload = text.split('"', 2)[1]
    if not payload:
        return []

    result: list[dict[str, str]] = []
    seen: set[str] = set()
    for row in payload.split(";"):
        fields = row.split(",")
        if len(fields) < 5:
            continue
        symbol = (fields[2] or "").strip()
        name = (fields[4] or symbol).strip()
        if not re.fullmatch(r"\d{6}", symbol) or symbol in seen:
            continue
        seen.add(symbol)
        result.append({"symbol": symbol, "name": name})
    return result


def _parse_tencent_suggestions(text: str) -> list[dict[str, str]]:
    """解析腾讯联想结果。"""
    if '"' not in text:
        return []
    payload = text.split('"', 2)[1]
    if payload in ("N", ""):
        return []

    result: list[dict[str, str]] = []
    seen: set[str] = set()
    for row in payload.split("^"):
        fields = row.split("~")
        if len(fields) < 3:
            continue
        symbol = (fields[1] or "").strip()
        name = (fields[2] or symbol).strip()
        if not re.fullmatch(r"\d{6}", symbol) or symbol in seen:
            continue
        seen.add(symbol)
        result.append({"symbol": symbol, "name": name})
    return result


def _request_sina_suggest(keyword: str) -> list[dict[str, str]]:
    """请求新浪联想接口。"""
    encoded = urllib.parse.quote(keyword.encode("gb2312", errors="ignore"))
    response = _http.get(
        SINA_SUGGEST_URL.format(keyword=encoded),
        headers={
            "Referer": "https://finance.sina.com.cn/",
            "User-Agent": "Mozilla/5.0",
        },
        timeout=8,
    )
    response.raise_for_status()
    text = response.content.decode("gb18030", errors="ignore")
    return _parse_sina_suggestions(text)


def _request_tencent_suggest(keyword: str) -> list[dict[str, str]]:
    """请求腾讯联想接口。"""
    response = _http.get(
        TENCENT_SUGGEST_URL.format(keyword=urllib.parse.quote(keyword)),
        headers={
            "Referer": "https://gu.qq.com/",
            "User-Agent": "Mozilla/5.0",
        },
        timeout=8,
    )
    response.raise_for_status()
    text = response.content.decode("utf-8", errors="ignore")
    return _parse_tencent_suggestions(text)


def _merge_candidates(*groups: list[dict[str, str]]) -> list[dict[str, str]]:
    """合并并去重候选股票。"""
    result: list[dict[str, str]] = []
    seen: set[str] = set()
    for group in groups:
        for item in group:
            symbol = item["symbol"]
            if symbol in seen:
                continue
            seen.add(symbol)
            result.append(item)
    return result


def _build_stock_items(candidates: list[dict[str, str]]) -> list[dict[str, Any]]:
    """给股票候选补齐实时行情字段。"""
    quote_map = _fetch_batch_quotes([item["symbol"] for item in candidates])
    result: list[dict[str, Any]] = []
    for item in candidates:
        quote = quote_map.get(item["symbol"], {})
        result.append(
            {
                "symbol": item["symbol"],
                "name": quote.get("name") or item["name"],
                "lastPrice": quote.get("lastPrice"),
                "changePercent": quote.get("changePercent"),
                "changeAmount": quote.get("changeAmount"),
                "volume": quote.get("volume"),
                "turnover": quote.get("turnover"),
                "turnoverRate": quote.get("turnoverRate"),
                "highPrice": quote.get("highPrice"),
                "lowPrice": quote.get("lowPrice"),
                "openPrice": quote.get("openPrice"),
                "totalMarketValue": None,
                "circulatingMarketValue": None,
                "sixtyDayChangePercent": None,
                "yearToDateChangePercent": None,
            }
        )
    return result


def load_market_page(page: int = 1, page_size: int = 40) -> dict[str, Any]:
    """获取默认主流股票池列表
    
    参数：page-页码(从1开始), page_size-每页数量(默认40)
    返回：分页格式的股票列表
    """
    start = max(page - 1, 0) * page_size
    end = start + page_size
    sliced_symbols = HOT_SYMBOLS[start:end]
    if not sliced_symbols:
        return _empty_market_page(page, page_size)
    try:
        return {
            "page": page,
            "pageSize": page_size,
            "total": len(HOT_SYMBOLS),
            "items": _build_stock_items([{"symbol": symbol, "name": symbol} for symbol in sliced_symbols]),
        }
    except Exception:
        return _empty_market_page(page, page_size)


def search_market_stocks(keyword: str, page: int = 1, page_size: int = 40) -> dict[str, Any]:
    """按关键字搜索股票
    
    搜索策略：同时请求新浪+腾讯联想接口，合并去重后补充实时行情
    参数：keyword-搜索关键字, page-页码, page_size-每页数量
    """
    normalized_keyword = keyword.strip()
    if not normalized_keyword:
        return load_market_page(page=page, page_size=page_size)

    try:
        sina_candidates = _request_sina_suggest(normalized_keyword)
    except Exception:
        sina_candidates = []

    try:
        tencent_candidates = _request_tencent_suggest(normalized_keyword)
    except Exception:
        tencent_candidates = []

    filtered_sina = [
        item for item in sina_candidates
        if normalized_keyword in item["symbol"] or normalized_keyword in item["name"]
    ]
    merged = _merge_candidates(filtered_sina, tencent_candidates)
    start = max(page - 1, 0) * page_size
    end = start + page_size
    sliced = merged[start:end]
    return {
        "page": page,
        "pageSize": page_size,
        "total": len(merged),
        "items": _build_stock_items(sliced) if sliced else [],
    }


@tool
def get_stock_quote_core(symbol: str) -> str:
    """获取单只 A 股实时行情
    
    参数：symbol - 6 位股票代码（如 600519）
    返回：JSON 格式行情数据（代码、名称、价格、涨跌、成交量等）
    """
    try:
        payload = _fetch_batch_quotes([symbol]).get(symbol)
        if not payload:
            return json.dumps({"error": f"未获取到股票 {symbol} 的行情数据。"}, ensure_ascii=False)

        result = {
            "代码": payload["symbol"],
            "名称": payload["name"],
            "最新价": payload["lastPrice"],
            "最高价": payload["highPrice"],
            "最低价": payload["lowPrice"],
            "今开": payload["openPrice"],
            "涨跌幅(%)": payload["changePercent"],
            "涨跌额": payload["changeAmount"],
            "成交量(手)": payload["volume"],
            "成交额(元)": payload["turnover"],
            "换手率(%)": payload["turnoverRate"],
            "市盈率(动态)": None,
            "市净率": None,
            "振幅(%)": payload["amplitude"],
        }
        return json.dumps(result, ensure_ascii=False)
    except requests.Timeout:
        return json.dumps({"error": "行情查询超时，请稍后重试。"}, ensure_ascii=False)
    except Exception as exc:
        return json.dumps({"error": f"获取股票行情失败: {str(exc)}"}, ensure_ascii=False)


if __name__ == "__main__":
    print(get_stock_quote_core.invoke({"symbol": "600519"}))
