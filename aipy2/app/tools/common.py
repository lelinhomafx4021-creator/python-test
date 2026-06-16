"""共享工具函数。

本模块集中管理多个模块共用的工具函数，避免重复定义。

函数列表：
- build_market_code: 股票代码转腾讯行情接口市场前缀格式（sh/sz）
- build_secid: 股票代码转东方财富 secid 格式（1.xxxx/0.xxxx）
- extract_stock_code: 从文本中提取6位股票代码
- safe_text: 任意值安全转字符串
- format_time: 时间字段统一格式化为 ISO 格式
"""

from __future__ import annotations

import logging
import re
from datetime import datetime
from typing import Any, Optional

logger = logging.getLogger(__name__)


def build_market_code(symbol: str) -> str:
    """把 6 位股票代码转换成腾讯接口需要的市场前缀格式。

    股票代码规则：
    - 以 5/6/9 开头：上海市场（sh）—— 5xx=ETF/基金，6xx=股票，688=科创板，9xx=B股
    - 以 8 开头：北交所（bj）—— 8xx.xxx = 北交所股票
    - 其他：深圳市场（sz）—— 000=深市主板，001=深市主板，300/301=创业板

    Args:
        symbol: 6 位数字股票代码，例如 "600519"

    Returns:
        带市场前缀的代码，例如 "sh600519" 或 "sz000001"

    Raises:
        ValueError: 股票代码不是 6 位纯数字
    """
    if not re.fullmatch(r"\d{6}", symbol):
        raise ValueError("symbol 必须是 6 位数字，例如 600519")
    if symbol.startswith(("5", "6", "9")):
        return f"sh{symbol}"
    if symbol.startswith("8"):
        return f"bj{symbol}"
    return f"sz{symbol}"


def build_secid(symbol: str) -> str:
    """把 6 位股票代码转换成东方财富 secid 格式。

    东方财富接口的市场编码规则：
    - 以 5/6/9 开头：上海市场（1）
    - 以 8 开头：北交所（0）—— 东方财富北交所也用 0 前缀
    - 其他：深圳市场（0）

    Args:
        symbol: 6 位数字股票代码，例如 "600519"

    Returns:
        东方财富 secid 格式，例如 "1.600519" 或 "0.000001"

    Raises:
        ValueError: 股票代码不是 6 位纯数字
    """
    if not re.fullmatch(r"\d{6}", symbol):
        raise ValueError("symbol 必须是 6 位数字，例如 600519")
    if symbol.startswith(("5", "6", "9")):
        return f"1.{symbol}"
    return f"0.{symbol}"


_STOCK_CODE_RE = re.compile(r"(?<!\d)(\d{6})(?!\d)")


def extract_stock_code(text: str) -> str | None:
    """从文本中提取第一个 6 位股票代码。"""
    match = _STOCK_CODE_RE.search(text)
    return match.group(1) if match else None


def safe_text(value: Any, fallback: str = "") -> str:
    """把任意值安全转换成字符串。

    None 或空白字符串会返回 fallback 默认值。

    Args:
        value: 任意类型的值
        fallback: 值为空时的默认返回值

    Returns:
        去除首尾空白后的字符串，或 fallback
    """
    if value is None:
        return fallback
    text = str(value).strip()
    return text or fallback


def format_time(value: Any) -> Optional[str]:
    """统一格式化时间字段为 ISO 格式字符串。

    尝试将输入解析为 ISO 8601 格式。如果解析失败，原样返回字符串并记录调试日志。

    Args:
        value: 时间相关的值（字符串、datetime 等）

    Returns:
        ISO 格式的时间字符串，无法解析时原样返回；值为 None 或空白时返回 None
    """
    if value is None:
        return None
    text = str(value).strip()
    if not text:
        return None
    try:
        return str(datetime.fromisoformat(text.replace("Z", "+00:00")))
    except Exception:
        logger.debug("时间格式解析失败，原样返回: %s", text)
        return text
