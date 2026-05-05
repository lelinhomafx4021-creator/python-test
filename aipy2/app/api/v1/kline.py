"""K线数据接口。

本模块提供 A 股历史 K 线（OHLCV）数据查询功能。

数据源：
- 腾讯财经接口（web.ifzq.gtimg.cn）：获取前复权日K/周K数据

功能：
- 按股票代码、周期、天数查询历史 K 线
- 返回标准化的 OHLCV 数据（开盘价、收盘价、最高价、最低价、成交量）
- Redis 缓存 12 小时，避免频繁请求第三方接口

典型用途：
- 前端 K 线图绘制
- 技术分析指标计算
"""

import json
import time
import hashlib
from typing import Any, Optional

import httpx
from fastapi import APIRouter, HTTPException, Query

from app.core.logger import logger

# ---------------------------------------------------------------------------
# 配置常量
# ---------------------------------------------------------------------------

# 腾讯财经 K 线接口地址
# 参数格式：{market_code},{period},,,{days},qfq
# market_code: sh601179 / sz000001
# period: day(日K) / week(周K) / month(月K)
# qfq: 前复权
TENCENT_KLINE_URL = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get"

# 缓存过期时间（秒），默认 12 小时
CACHE_TTL_SECONDS = 43200

# 支持的 K 线周期映射
PERIOD_MAP = {
    "daily": "day",
    "week": "week",
    "weekly": "week",
    "month": "month",
    "monthly": "month",
}

# HTTP 请求超时时间（秒）
REQUEST_TIMEOUT = 10

# ---------------------------------------------------------------------------
# 内存缓存（项目未配置 Redis 时使用）
# ---------------------------------------------------------------------------

_memory_cache: dict[str, tuple[float, Any]] = {}


def _get_cache_key(symbol: str, period: str, days: int) -> str:
    """生成缓存键。"""
    raw = f"kline:{symbol}:{period}:{days}"
    return hashlib.md5(raw.encode()).hexdigest()


def _cache_get(key: str) -> Optional[Any]:
    """从缓存读取数据，过期返回 None。"""
    entry = _memory_cache.get(key)
    if entry is None:
        return None
    ts, value = entry
    if time.time() - ts > CACHE_TTL_SECONDS:
        _memory_cache.pop(key, None)
        return None
    return value


def _cache_set(key: str, value: Any) -> None:
    """写入缓存。"""
    _memory_cache[key] = (time.time(), value)


# ---------------------------------------------------------------------------
# 数据获取核心逻辑
# ---------------------------------------------------------------------------

def _build_market_code(symbol: str) -> str:
    """把 6 位股票代码转换成腾讯接口需要的市场前缀格式。

    股票代码规则：
    - 以 5/6/9 开头：上海市场（sh）
    - 其他：深圳市场（sz）

    示例：601179 -> sh601179, 000001 -> sz000001
    """
    symbol = symbol.strip()
    if not symbol.isdigit() or len(symbol) != 6:
        raise ValueError("symbol 必须是 6 位数字代码，例如 601179")
    return f"sh{symbol}" if symbol.startswith(("5", "6", "9")) else f"sz{symbol}"


async def _fetch_kline_from_tencent(symbol: str, period: str, days: int) -> list[dict[str, Any]]:
    """从腾讯财经接口获取 K 线原始数据。

    参数：
        symbol: 6 位股票代码
        period: K 线周期（day/week/month）
        days: 获取天数

    返回：
        标准化的 OHLCV K 线数据列表
    """
    market_code = _build_market_code(symbol)
    params = {
        "param": f"{market_code},{period},,,{days},qfq",
    }

    async with httpx.AsyncClient() as client:
        resp = await client.get(
            TENCENT_KLINE_URL,
            params=params,
            headers={
                "Referer": "https://web.ifzq.gtimg.cn/",
                "User-Agent": (
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    "AppleWebKit/537.36 (KHTML, like Gecko) "
                    "Chrome/124.0.0.0 Safari/537.36"
                ),
            },
            timeout=REQUEST_TIMEOUT,
        )
        resp.raise_for_status()
        data = resp.json()

    if not data or "data" not in data:
        raise ValueError("腾讯 K 线接口返回数据为空")

    # 腾讯接口返回结构：{data: {market_code: {period: [[日期, 开, 收, 高, 低, 成交量], ...]}}}
    stock_data = data["data"].get(market_code, {})

    # 尝试获取对应周期的数据（前复权后缀为 qfq + period）
    kline_key = f"qfq{period}"
    raw_klines = stock_data.get(kline_key) or stock_data.get(period, [])

    if not raw_klines:
        raise ValueError(f"未获取到 {symbol} 的 {period} K 线数据")

    return raw_klines


def _parse_kline_data(raw_klines: list[list], symbol: str) -> list[dict[str, Any]]:
    """将腾讯原始 K 线数据解析为标准化 OHLCV 格式。

    腾讯原始格式：[日期, 开盘价, 收盘价, 最高价, 最低价, 成交量]

    标准化输出：
    {
        "date": "2026-01-01",
        "open": 10.5,
        "close": 11.2,
        "high": 11.5,
        "low": 10.3,
        "volume": 123456,
        "symbol": "601179"
    }
    """
    result: list[dict[str, Any]] = []

    for row in raw_klines:
        try:
            # 腾讯接口字段顺序：日期, 开盘价, 收盘价, 最高价, 最低价, 成交量
            # 有些周期可能还有更多字段，但我们只取前 6 个
            if len(row) < 6:
                continue

            date_str = str(row[0]).strip()
            open_price = float(row[1]) if row[1] else None
            close_price = float(row[2]) if row[2] else None
            high_price = float(row[3]) if row[3] else None
            low_price = float(row[4]) if row[4] else None
            volume = int(float(row[5])) if row[5] else 0

            # 跳过无效数据（价格全为 0 的记录）
            if open_price is None or open_price == 0:
                continue

            result.append({
                "date": date_str,
                "open": round(open_price, 2),
                "close": round(close_price, 2) if close_price else None,
                "high": round(high_price, 2) if high_price else None,
                "low": round(low_price, 2) if low_price else None,
                "volume": volume,
                "symbol": symbol,
            })
        except (ValueError, TypeError, IndexError) as exc:
            # 单条数据解析失败不影响整体，记录日志后跳过
            logger.debug("K 线数据解析跳过，row=%s, error=%s", row, exc)
            continue

    return result


# ---------------------------------------------------------------------------
# API 路由
# ---------------------------------------------------------------------------

router = APIRouter(prefix="/api/v1", tags=["K线数据接口"])


@router.get("/kline")
async def get_kline(
    symbol: str = Query(..., description="6 位股票代码，例如 601179"),
    period: str = Query("daily", description="K 线周期：daily/weekly/monthly"),
    days: int = Query(120, ge=1, le=500, description="获取天数，最多 500"),
):
    """获取 A 股历史 K 线数据。

    请求参数：
    - `symbol`：6 位股票代码（必填），例如 601179
    - `period`：K 线周期，可选 daily（日K）/ weekly（周K）/ monthly（月K），默认 daily
    - `days`：获取天数，默认 120，最大 500

    返回数据：
    - `symbol`：股票代码
    - `period`：请求的周期
    - `count`：返回的 K 线条数
    - `items`：标准化 OHLCV 数据列表

    缓存策略：
    - 同一查询参数的数据缓存 12 小时（日K一天更新一次）
    - 内存缓存，服务重启后清空
    """
    try:
        # 参数校验
        symbol = symbol.strip()
        if not symbol.isdigit() or len(symbol) != 6:
            raise HTTPException(status_code=400, detail="symbol 必须是 6 位数字代码")

        # 将前端传入的周期映射为腾讯接口格式
        tencent_period = PERIOD_MAP.get(period.lower())
        if not tencent_period:
            raise HTTPException(
                status_code=400,
                detail=f"不支持的 period: {period}，可选值：daily / weekly / monthly"
            )

        # 检查缓存
        cache_key = _get_cache_key(symbol, tencent_period, days)
        cached = _cache_get(cache_key)
        if cached is not None:
            logger.debug("K 线缓存命中，symbol=%s, period=%s", symbol, tencent_period)
            return {
                "code": 200,
                "data": {
                    "symbol": symbol,
                    "period": period,
                    "count": len(cached),
                    "items": cached,
                    "cached": True,
                },
                "message": "成功",
            }

        # 从腾讯接口拉取原始数据（异步，不阻塞事件循环）
        raw_klines = await _fetch_kline_from_tencent(symbol, tencent_period, days)

        # 解析为标准格式
        parsed = _parse_kline_data(raw_klines, symbol)

        # 写入缓存
        _cache_set(cache_key, parsed)

        logger.info(
            "K 线数据获取成功，symbol=%s, period=%s, count=%d",
            symbol, period, len(parsed)
        )

        return {
            "code": 200,
            "data": {
                "symbol": symbol,
                "period": period,
                "count": len(parsed),
                "items": parsed,
                "cached": False,
            },
            "message": "成功",
        }

    except HTTPException:
        # 已经处理过的业务异常，直接向上抛
        raise
    except httpx.TimeoutException:
        logger.warning("K 线数据请求超时，symbol=%s", symbol)
        raise HTTPException(status_code=504, detail="数据源请求超时，请稍后重试")
    except httpx.HTTPError as exc:
        logger.error("K 线数据请求失败，symbol=%s, error=%s", symbol, exc)
        raise HTTPException(status_code=502, detail="数据源请求失败")
    except ValueError as exc:
        logger.warning("K 线数据解析失败，symbol=%s, error=%s", symbol, exc)
        raise HTTPException(status_code=404, detail=str(exc))
    except Exception as exc:
        logger.error("K 线接口未知异常，symbol=%s, error=%s", symbol, exc)
        raise HTTPException(status_code=500, detail="服务器内部错误")
