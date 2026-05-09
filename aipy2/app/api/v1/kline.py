"""K 线与分时数据接口。"""

from __future__ import annotations

import hashlib
import time
from datetime import datetime, timedelta
from typing import Any, Optional

import akshare as ak
import httpx
from fastapi import APIRouter, HTTPException, Query

from app.core.logger import logger
from app.tools.common import build_market_code

TENCENT_KLINE_URL = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get"
CACHE_TTL_SECONDS = 300
REQUEST_TIMEOUT = 10

PERIOD_MAP = {
    "daily": "day",
    "week": "week",
    "weekly": "week",
    "month": "month",
    "monthly": "month",
}

INTRADAY_PERIODS = {"intraday_1d", "intraday_5d"}

_memory_cache: dict[str, tuple[float, Any]] = {}


def _get_cache_key(symbol: str, period: str, days: int) -> str:
    raw = f"kline:{symbol}:{period}:{days}"
    return hashlib.md5(raw.encode()).hexdigest()


def _cache_get(key: str) -> Optional[Any]:
    entry = _memory_cache.get(key)
    if entry is None:
        return None
    ts, value = entry
    if time.time() - ts > CACHE_TTL_SECONDS:
        _memory_cache.pop(key, None)
        return None
    return value


def _cache_set(key: str, value: Any) -> None:
    _memory_cache[key] = (time.time(), value)


async def _fetch_daily_kline(symbol: str, period: str, days: int) -> list[dict[str, Any]]:
    market_code = build_market_code(symbol)
    params = {"param": f"{market_code},{period},,,{days},qfq"}

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
        raise ValueError("腾讯 K 线接口返回为空")

    stock_data = data["data"].get(market_code, {})
    kline_key = f"qfq{period}"
    raw_klines = stock_data.get(kline_key) or stock_data.get(period, [])
    if not raw_klines:
        raise ValueError(f"未获取到 {symbol} 的 {period} K 线数据")

    result: list[dict[str, Any]] = []
    for row in raw_klines:
        if len(row) < 6:
            continue
        try:
            open_price = float(row[1]) if row[1] else None
            close_price = float(row[2]) if row[2] else None
            high_price = float(row[3]) if row[3] else None
            low_price = float(row[4]) if row[4] else None
            volume = int(float(row[5])) if row[5] else 0
            if open_price is None or open_price == 0:
                continue
            result.append(
                {
                    "date": str(row[0]).strip(),
                    "open": round(open_price, 2),
                    "close": round(close_price, 2) if close_price is not None else None,
                    "high": round(high_price, 2) if high_price is not None else None,
                    "low": round(low_price, 2) if low_price is not None else None,
                    "volume": volume,
                    "symbol": symbol,
                }
            )
        except (ValueError, TypeError, IndexError) as exc:
            logger.debug("skip invalid daily kline row: %s, error=%s", row, exc)
    return result


def _normalize_intraday_df(df, symbol: str) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    if df is None or df.empty:
        return result

    column_map = {
        "date": ["时间", "day", "日期", "tradeDate"],
        "open": ["开盘", "open", "openPrice"],
        "close": ["收盘", "close", "closePrice"],
        "high": ["最高", "high", "highPrice"],
        "low": ["最低", "low", "lowPrice"],
        "volume": ["成交量", "volume", "vol"],
    }

    def pick(row: dict[str, Any], aliases: list[str]):
        for alias in aliases:
            if alias in row and row[alias] not in (None, "", "None"):
                return row[alias]
        return None

    for row in df.to_dict("records"):
        date_value = pick(row, column_map["date"])
        if not date_value:
            continue
        try:
            open_value = pick(row, column_map["open"])
            close_value = pick(row, column_map["close"])
            high_value = pick(row, column_map["high"])
            low_value = pick(row, column_map["low"])
            volume_value = pick(row, column_map["volume"])

            close_price = float(close_value) if close_value not in (None, "") else 0.0
            open_price = float(open_value) if open_value not in (None, "") else close_price
            high_price = float(high_value) if high_value not in (None, "") else max(open_price, close_price)
            low_price = float(low_value) if low_value not in (None, "") else min(open_price, close_price)
            volume = int(float(volume_value)) if volume_value not in (None, "") else 0

            if open_price == 0 and close_price > 0:
                open_price = close_price
            if high_price == 0 and close_price > 0:
                high_price = max(open_price, close_price)
            if low_price == 0 and close_price > 0:
                low_price = min(open_price, close_price)
            if open_price == 0 and close_price == 0:
                continue

            result.append(
                {
                    "date": str(date_value),
                    "open": round(open_price, 2),
                    "close": round(close_price, 2),
                    "high": round(high_price, 2),
                    "low": round(low_price, 2),
                    "volume": volume,
                    "symbol": symbol,
                }
            )
        except (ValueError, TypeError) as exc:
            logger.debug("skip invalid intraday row: %s, error=%s", row, exc)
    return result


def _latest_trade_dates(rows: list[dict[str, Any]], limit: int) -> list[str]:
    dates = sorted({str(item["date"])[:10] for item in rows if item.get("date")})
    return dates[-limit:]


def _fetch_intraday_1d(symbol: str) -> list[dict[str, Any]]:
    market_code = build_market_code(symbol)
    df = ak.stock_zh_a_minute(symbol=market_code, period="1", adjust="")
    rows = _normalize_intraday_df(df, symbol)
    if not rows:
        return []

    latest_dates = _latest_trade_dates(rows, 1)
    latest_date = latest_dates[0] if latest_dates else None
    if latest_date is None:
        return rows
    return [item for item in rows if str(item["date"]).startswith(latest_date)]


def _fetch_intraday_5d(symbol: str) -> list[dict[str, Any]]:
    end = datetime.now()
    start = end - timedelta(days=21)
    df = ak.stock_zh_a_hist_min_em(
        symbol=symbol,
        start_date=start.strftime("%Y-%m-%d 09:30:00"),
        end_date=end.strftime("%Y-%m-%d 15:00:00"),
        period="1",
        adjust="",
    )
    rows = _normalize_intraday_df(df, symbol)
    if not rows:
        return []

    latest_dates = set(_latest_trade_dates(rows, 5))
    return [item for item in rows if str(item["date"])[:10] in latest_dates]


router = APIRouter(prefix="/api/v1", tags=["K线数据接口"])


@router.get("/kline")
async def get_kline(
    symbol: str = Query(..., description="6位股票代码，例如 600519"),
    period: str = Query("daily", description="周期：daily / intraday_1d / intraday_5d / weekly / monthly"),
    days: int = Query(120, ge=1, le=500, description="获取天数或窗口大小"),
):
    try:
        symbol = symbol.strip()
        if not symbol.isdigit() or len(symbol) != 6:
            raise HTTPException(status_code=400, detail="symbol 必须是 6 位数字代码")

        normalized_period = period.lower()
        cache_key = _get_cache_key(symbol, normalized_period, days)
        cached = _cache_get(cache_key)
        if cached is not None:
            return {
                "code": 200,
                "data": {
                    "symbol": symbol,
                    "period": normalized_period,
                    "count": len(cached),
                    "items": cached,
                    "cached": True,
                },
                "message": "成功",
            }

        if normalized_period in INTRADAY_PERIODS:
            if normalized_period == "intraday_1d":
                parsed = _fetch_intraday_1d(symbol)
            else:
                parsed = _fetch_intraday_5d(symbol)
        else:
            tencent_period = PERIOD_MAP.get(normalized_period)
            if not tencent_period:
                raise HTTPException(
                    status_code=400,
                    detail="不支持的 period，可选：daily / weekly / monthly / intraday_1d / intraday_5d",
                )
            parsed = await _fetch_daily_kline(symbol, tencent_period, days)

        _cache_set(cache_key, parsed)
        logger.info("kline loaded: symbol=%s, period=%s, count=%d", symbol, normalized_period, len(parsed))
        return {
            "code": 200,
            "data": {
                "symbol": symbol,
                "period": normalized_period,
                "count": len(parsed),
                "items": parsed,
                "cached": False,
            },
            "message": "成功",
        }
    except HTTPException:
        raise
    except httpx.TimeoutException:
        raise HTTPException(status_code=504, detail="数据源请求超时，请稍后重试")
    except httpx.HTTPError:
        raise HTTPException(status_code=502, detail="数据源请求失败")
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc))
    except Exception as exc:
        logger.error("kline error: symbol=%s, period=%s, error=%s", symbol, period, exc)
        raise HTTPException(status_code=500, detail="服务器内部错误")
