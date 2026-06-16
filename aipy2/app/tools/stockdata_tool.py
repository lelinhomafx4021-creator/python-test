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
import logging
import re
import urllib.parse
from typing import Any

import requests
from langchain_core.tools import tool

from app.tools.common import build_market_code

logger = logging.getLogger(__name__)


TENCENT_QUOTE_URL = "https://qt.gtimg.cn/q={code}"
TENCENT_SUGGEST_URL = "https://smartbox.gtimg.cn/s3/?q={keyword}&t=all"
SINA_SUGGEST_URL = "https://suggest3.sinajs.cn/suggest/type=11,12,13,14,15&key={keyword}&name=suggestdata"
# 东方财富搜索 API — 覆盖面更广，支持拼音/代码/中文名
EASTMONEY_SUGGEST_URL = "https://searchapi.eastmoney.com/api/suggest/get"
# 东方财富财务数据 — 获取 EPS/BPS 用于计算 PE/PB
EASTMONEY_DATACENTER_URL = "https://datacenter.eastmoney.com/securities/api/data/v1/get"

# ─── 全量 A 股列表（通过 mootdx 从腾讯行情服务器获取） ───
_full_stock_cache: list[dict[str, str]] | None = None


def _load_full_stock_list() -> list[dict[str, str]]:
    """从 mootdx 获取全量 A 股列表并缓存。"""
    global _full_stock_cache
    if _full_stock_cache is not None:
        return _full_stock_cache

    try:
        from mootdx.quotes import Quotes
        client = Quotes.factory(market='std')
        df_sh = client.stocks(market=1)  # 上海
        df_sz = client.stocks(market=0)  # 深圳

        stocks: list[dict[str, str]] = []
        seen: set[str] = set()

        for _, row in df_sh.iterrows():
            code = str(row.get('code', '')).strip()
            name = str(row.get('name', '')).strip()
            if _is_a_stock(code, name) and code not in seen:
                seen.add(code)
                stocks.append({'symbol': code, 'name': name})

        for _, row in df_sz.iterrows():
            code = str(row.get('code', '')).strip()
            name = str(row.get('name', '')).strip()
            if _is_a_stock(code, name) and code not in seen:
                seen.add(code)
                stocks.append({'symbol': code, 'name': name})

        _full_stock_cache = stocks
        logger.info("全量 A 股列表加载完成，共 %d 只", len(stocks))
        return stocks
    except Exception as e:
        logger.warning("mootdx 获取全量股票列表失败: %s", e)
        return []


def _is_a_stock(code: str, name: str = "", strict: bool = True) -> bool:
    """判断是否为 A 股股票。

    Args:
        code: 6位数字股票代码
        name: 股票名称（可选，strict=True时用于过滤ETF/基金）
        strict: 是否严格模式（过滤ETF/基金/可转债等）
    """
    if not re.fullmatch(r"\d{6}", code):
        return False

    # 非严格模式（仅用于代码判断）：排除明显非股票的代码
    if not strict:
        # 5xxxxx: ETF/基金
        if code.startswith("5"):
            return False
        # 1xxxxx: 可转债/国债
        if code.startswith("1"):
            return False
        # 399xxx: 深证指数
        if code.startswith("399"):
            return False
        # 0xxxxx: 深市股票是 000/001/002/003 开头，其他是指数
        if code.startswith("0") and code[:3] not in ("000", "001", "002", "003"):
            return False
        return True

    # 严格模式：额外过滤名称中的ETF/基金
    if name and ('指数' in name or 'ETF' in name or '基金' in name):
        return False

    # 沪市主板: 600/601/603/605
    if code.startswith(('600', '601', '603', '605')):
        return True
    # 科创板: 688/689
    if code.startswith(('688', '689')):
        return True
    # 深市主板: 001（排除 000 开头的指数）
    if code.startswith('001'):
        return True
    # 中小板: 002/003
    if code.startswith(('002', '003')):
        return True
    # 创业板: 300/301
    if code.startswith(('300', '301')):
        return True
    return False


# 热门股票代码列表（作为 fallback，当 mootdx 不可用时使用）
HOT_SYMBOLS = [
    "600519", "000001", "300750", "601318", "600036",
    "601688", "002594", "000858", "600900", "601398",
    "601288", "601166", "600030", "601012", "600809",
    "002415", "300059", "601899", "000333", "601888",
    "600276", "688981", "603259", "600887",
]

# 创建 requests 会话对象，复用 TCP 连接提升性能
_http = requests.Session()
_http.trust_env = False  # 不使用系统代理，避免开发环境干扰


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
        except Exception as e:
            logger.debug("解析行情时间失败: %s", e)
    
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

    market_codes = [build_market_code(symbol) for symbol in normalized_symbols]
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


def _request_eastmoney_suggest(keyword: str) -> list[dict[str, str]]:
    """请求东方财富联想接口 — 覆盖面更广，支持拼音/代码/中文名。

    只返回 A 股股票（沪深主板+创业板+科创板+北交所），过滤掉指数、ETF、基金等。
    """
    params = {
        "input": keyword,
        "type": "14",  # 14 = 全部（股票+基金+债券）
        "token": "D43BF722C8E33BDC906FB84D85E326E8",
    }
    try:
        response = _http.get(
            EASTMONEY_SUGGEST_URL,
            params=params,
            headers={"User-Agent": "Mozilla/5.0"},
            timeout=8,
        )
        response.raise_for_status()
        data = response.json()
        table = data.get("QuotationCodeTable", {})
        rows = table.get("Data") or []
        result: list[dict[str, str]] = []
        seen: set[str] = set()
        for row in rows:
            # 东方财富返回 JSON 对象格式：{Code, Name, MarketType, Classify, ...}
            if isinstance(row, dict):
                symbol = str(row.get("Code", "")).strip()
                name = str(row.get("Name", "")).strip()
                classify = str(row.get("Classify", "")).strip()
            elif isinstance(row, str):
                # 兼容旧的管道分隔格式
                parts = row.split("|")
                if len(parts) < 2:
                    continue
                symbol = parts[0].strip()
                name = parts[1].strip()
                classify = ""
            else:
                continue
            # 只保留 6 位数字代码
            if not re.fullmatch(r"\d{6}", symbol) or symbol in seen:
                continue
            # 过滤掉非股票类型：
            # - Index: 指数
            # - BK: 板块
            # - ETF/基金: 以 5 开头的代码
            if classify in ("Index", "BK"):
                continue
            if symbol.startswith("5"):
                continue
            # 以 0 开头的代码中，000/001/002/003 是深市股票，其他是指数
            if symbol.startswith("0") and not symbol[:3] in ("000", "001", "002", "003"):
                continue
            seen.add(symbol)
            result.append({"symbol": symbol, "name": name})
        return result
    except Exception as e:
        logger.warning("东方财富搜索接口异常: %s", e)
        return []


# _is_a_stock_code 已合并到 _is_a_stock，使用 _is_a_stock(code, strict=False) 代替


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


def _fetch_eastmoney_detail(symbol: str) -> dict[str, Any] | None:
    """从东方财富获取单只股票的 PE/PB 等基本面数据。

    使用 datacenter API 获取 EPS 和 BPS，然后计算 PE 和 PB。
    """
    params = {
        "reportName": "RPT_LICO_FN_CPD",
        "columns": "SECURITY_CODE,BASIC_EPS,BPS",
        "filter": f'(SECURITY_CODE="{symbol}")',
        "pageSize": 1,
        "pageNumber": 1,
        "source": "WEB",
        "client": "WEB",
    }
    try:
        resp = _http.get(
            EASTMONEY_DATACENTER_URL,
            params=params,
            headers={"User-Agent": "Mozilla/5.0", "Referer": "https://quote.eastmoney.com/"},
            timeout=5,
        )
        data = resp.json()
        if not data.get("success") or not data.get("result", {}).get("data"):
            return None
        row = data["result"]["data"][0]
        eps = float(row.get("BASIC_EPS", 0) or 0)
        bps = float(row.get("BPS", 0) or 0)
        # PE 和 PB 需要结合实时价格计算，这里返回 EPS/BPS，由调用方计算
        return {
            "eps": eps if eps > 0 else None,
            "bps": bps if bps > 0 else None,
        }
    except Exception as e:
        logger.debug("东方财富财务数据获取失败 %s: %s", symbol, e)
        return None


def _build_stock_items(candidates: list[dict[str, str]]) -> list[dict[str, Any]]:
    """给股票候选补齐实时行情字段 + PE/PB（从 EPS/BPS 计算）。"""
    quote_map = _fetch_batch_quotes([item["symbol"] for item in candidates])
    result: list[dict[str, Any]] = []
    for item in candidates:
        quote = quote_map.get(item["symbol"], {})
        # 尝试获取 EPS/BPS 并计算 PE/PB（失败不影响主流程）
        detail = _fetch_eastmoney_detail(item["symbol"])
        price = quote.get("lastPrice")
        pe = None
        pb = None
        if detail and price:
            eps = detail.get("eps")
            bps = detail.get("bps")
            if eps and eps > 0:
                pe = round(price / eps, 2)
            if bps and bps > 0:
                pb = round(price / bps, 2)
        result.append(
            {
                "symbol": item["symbol"],
                "name": quote.get("name") or item["name"],
                "lastPrice": price,
                "changePercent": quote.get("changePercent"),
                "changeAmount": quote.get("changeAmount"),
                "volume": quote.get("volume"),
                "turnover": quote.get("turnover"),
                "turnoverRate": quote.get("turnoverRate"),
                "highPrice": quote.get("highPrice"),
                "lowPrice": quote.get("lowPrice"),
                "openPrice": quote.get("openPrice"),
                "totalMarketValue": None,  # 需要总股本数据，暂不支持
                "circulatingMarketValue": None,
                "pe": pe,
                "pb": pb,
                "sixtyDayChangePercent": None,
                "yearToDateChangePercent": None,
            }
        )
    return result


def load_market_page(page: int = 1, page_size: int = 40) -> dict[str, Any]:
    """获取股票池列表（全量 A 股，按代码排序）。

    参数：page-页码(从1开始), page_size-每页数量(默认40)
    返回：分页格式的股票列表
    """
    all_stocks = _load_full_stock_list()
    if not all_stocks:
        # fallback 到硬编码列表
        start = max(page - 1, 0) * page_size
        end = start + page_size
        sliced = HOT_SYMBOLS[start:end]
        if not sliced:
            return _empty_market_page(page, page_size)
        try:
            return {
                "page": page,
                "pageSize": page_size,
                "total": len(HOT_SYMBOLS),
                "items": _build_stock_items([{"symbol": s, "name": s} for s in sliced]),
            }
        except Exception as e:
            logger.warning("构建热门股票列表失败: %s", e)
            return _empty_market_page(page, page_size)

    # 按代码排序
    all_stocks.sort(key=lambda x: x["symbol"])
    total = len(all_stocks)
    start = max(page - 1, 0) * page_size
    end = start + page_size
    sliced = all_stocks[start:end]
    try:
        return {
            "page": page,
            "pageSize": page_size,
            "total": total,
            "items": _build_stock_items(sliced) if sliced else [],
        }
    except Exception as e:
        logger.warning("构建股票列表失败: %s", e)
        return _empty_market_page(page, page_size)


def search_market_stocks(keyword: str, page: int = 1, page_size: int = 40) -> dict[str, Any]:
    """按关键字搜索股票

    搜索策略：
    1. 从全量 A 股列表（mootdx）中按代码/名称匹配
    2. 同时请求新浪+腾讯+东方财富联想接口补充
    3. 合并去重后补充实时行情+基本面
    参数：keyword-搜索关键字, page-页码, page_size-每页数量
    """
    normalized_keyword = keyword.strip()
    if not normalized_keyword:
        return load_market_page(page=page, page_size=page_size)

    # 1) 从全量列表中匹配
    all_stocks = _load_full_stock_list()
    local_matches: list[dict[str, str]] = []
    if all_stocks:
        for stock in all_stocks:
            if (normalized_keyword in stock["symbol"] or
                    normalized_keyword in stock["name"]):
                local_matches.append(stock)

    # 2) 三源联想接口
    try:
        sina_candidates = _request_sina_suggest(normalized_keyword)
    except Exception as e:
        logger.warning("新浪搜索接口异常: %s", e)
        sina_candidates = []

    try:
        tencent_candidates = _request_tencent_suggest(normalized_keyword)
    except Exception as e:
        logger.warning("腾讯搜索接口异常: %s", e)
        tencent_candidates = []

    try:
        eastmoney_candidates = _request_eastmoney_suggest(normalized_keyword)
    except Exception as e:
        logger.warning("东方财富搜索接口异常: %s", e)
        eastmoney_candidates = []

    # 过滤联想结果，只保留 A 股代码
    filtered_sina = [
        item for item in sina_candidates
        if _is_a_stock(item["symbol"], strict=False)
    ]
    filtered_tencent = [
        item for item in tencent_candidates
        if _is_a_stock(item["symbol"], strict=False)
    ]

    # 合并去重：本地匹配 + 东方财富 + 新浪 + 腾讯
    merged = _merge_candidates(local_matches, eastmoney_candidates, filtered_sina, filtered_tencent)
    total = len(merged)
    start = max(page - 1, 0) * page_size
    end = start + page_size
    sliced = merged[start:end]
    return {
        "page": page,
        "pageSize": page_size,
        "total": total,
        "items": _build_stock_items(sliced) if sliced else [],
    }


@tool
def get_stock_quote_core(symbol: str) -> str:
    """获取单只 A 股实时行情

    参数：symbol - 6 位股票代码（如 600519）
    返回：JSON 格式行情数据（代码、名称、价格、涨跌、成交量、PE、PB等）
    """
    try:
        payload = _fetch_batch_quotes([symbol]).get(symbol)
        if not payload:
            return json.dumps({"error": f"未获取到股票 {symbol} 的行情数据。"}, ensure_ascii=False)

        # 补充 EPS/BPS 并计算 PE/PB
        detail = _fetch_eastmoney_detail(symbol)
        price = payload.get("lastPrice")
        pe = None
        pb = None
        if detail and price:
            eps = detail.get("eps")
            bps = detail.get("bps")
            if eps and eps > 0:
                pe = round(price / eps, 2)
            if bps and bps > 0:
                pb = round(price / bps, 2)

        result = {
            "代码": payload["symbol"],
            "名称": payload["name"],
            "最新价": price,
            "最高价": payload["highPrice"],
            "最低价": payload["lowPrice"],
            "今开": payload["openPrice"],
            "涨跌幅(%)": payload["changePercent"],
            "涨跌额": payload["changeAmount"],
            "成交量(手)": payload["volume"],
            "成交额(元)": payload["turnover"],
            "换手率(%)": payload["turnoverRate"],
            "市盈率(动态)": pe,
            "市净率": pb,
            "振幅(%)": payload["amplitude"],
        }
        return json.dumps(result, ensure_ascii=False)
    except requests.Timeout:
        return json.dumps({"error": "行情查询超时，请稍后重试。"}, ensure_ascii=False)
    except Exception as exc:
        return json.dumps({"error": f"获取股票行情失败: {str(exc)}"}, ensure_ascii=False)


if __name__ == "__main__":
    print(get_stock_quote_core.invoke({"symbol": "600519"}))
