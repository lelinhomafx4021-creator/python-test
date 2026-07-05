"""K-line pattern annotation helpers."""

from __future__ import annotations

from typing import Any


def _round_ratio(value: float) -> float:
    return round(value, 4)


def _build_pattern(
    code: str,
    name: str,
    direction: str,
    score: float,
) -> dict[str, Any]:
    return {
        "code": code,
        "name": name,
        "direction": direction,
        "score": _round_ratio(score),
    }


def annotate_kline_patterns(items: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Attach candle pattern metadata to each K-line item."""
    if not items:
        return items

    for item in items:
        item["patterns"] = []

    for idx, item in enumerate(items):
        open_price = float(item.get("open") or 0)
        close_price = float(item.get("close") or 0)
        high_price = float(item.get("high") or 0)
        low_price = float(item.get("low") or 0)
        if min(open_price, close_price, high_price, low_price) <= 0:
            continue

        candle_range = max(high_price - low_price, 0.0001)
        body = abs(close_price - open_price)
        upper_shadow = max(high_price - max(open_price, close_price), 0.0)
        lower_shadow = max(min(open_price, close_price) - low_price, 0.0)
        body_ratio = body / candle_range
        upper_ratio = upper_shadow / candle_range
        lower_ratio = lower_shadow / candle_range
        bullish = close_price > open_price
        bearish = close_price < open_price

        patterns: list[dict[str, Any]] = item["patterns"]

        if body_ratio <= 0.1 and upper_ratio >= 0.2 and lower_ratio >= 0.2:
            patterns.append(_build_pattern("doji", "十字星", "neutral", 0.62))

        if lower_ratio >= 0.5 and upper_ratio <= 0.18 and body_ratio <= 0.35:
            patterns.append(_build_pattern("hammer", "锤子线", "bullish", 0.74))

        if upper_ratio >= 0.5 and lower_ratio <= 0.18 and body_ratio <= 0.35:
            patterns.append(_build_pattern("shooting_star", "射击之星", "bearish", 0.74))

        if idx == 0:
            continue

        prev = items[idx - 1]
        prev_open = float(prev.get("open") or 0)
        prev_close = float(prev.get("close") or 0)
        prev_high = float(prev.get("high") or 0)
        prev_low = float(prev.get("low") or 0)
        if min(prev_open, prev_close, prev_high, prev_low) <= 0:
            continue

        prev_bullish = prev_close > prev_open
        prev_bearish = prev_close < prev_open
        prev_body = abs(prev_close - prev_open)

        if (
            prev_bearish
            and bullish
            and open_price <= prev_close
            and close_price >= prev_open
            and body >= prev_body * 0.9
        ):
            patterns.append(_build_pattern("bullish_engulfing", "看涨吞没", "bullish", 0.84))

        if (
            prev_bullish
            and bearish
            and open_price >= prev_close
            and close_price <= prev_open
            and body >= prev_body * 0.9
        ):
            patterns.append(_build_pattern("bearish_engulfing", "看跌吞没", "bearish", 0.84))

        if idx < 2:
            continue

        first = items[idx - 2]
        first_open = float(first.get("open") or 0)
        first_close = float(first.get("close") or 0)
        first_high = float(first.get("high") or 0)
        first_low = float(first.get("low") or 0)
        if min(first_open, first_close, first_high, first_low) <= 0:
            continue

        first_body = abs(first_close - first_open)
        first_bullish = first_close > first_open
        first_bearish = first_close < first_open
        middle_body = prev_body

        if (
            first_bearish
            and middle_body <= first_body * 0.45
            and bullish
            and close_price >= first_open - first_body * 0.3
        ):
            patterns.append(_build_pattern("morning_star", "晨星", "bullish", 0.88))

        if (
            first_bullish
            and middle_body <= first_body * 0.45
            and bearish
            and close_price <= first_open + first_body * 0.3
        ):
            patterns.append(_build_pattern("evening_star", "暮星", "bearish", 0.88))

    return items
