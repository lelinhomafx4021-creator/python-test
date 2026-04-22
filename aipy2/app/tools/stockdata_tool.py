import json
import re
from typing import Any

import requests
from langchain_core.tools import tool


EM_QUOTE_URL = "https://push2.eastmoney.com/api/qt/stock/get"


def _to_secid(symbol: str) -> str:
    """把 6 位 A 股代码转换为东方财富 secid。"""
    if not re.fullmatch(r"\d{6}", symbol):
        raise ValueError("symbol 必须是 6 位数字，例如 600519")

    # 东方财富 secid 规则：1=沪市，0=深市（常见 A 股场景）
    market = "1" if symbol.startswith(("5", "6", "9")) else "0"
    return f"{market}.{symbol}"


def _safe_div_100(value: Any) -> float | None:
    if value is None:
        return None
    try:
        return round(float(value) / 100, 2)
    except (TypeError, ValueError):
        return None


@tool
def get_stock_quote_core(symbol: str) -> str:
    """
    获取单只 A 股实时行情（HTTP 直连东方财富接口，不依赖 akshare）。

    Args:
        symbol: 6位股票代码，例如 "600519"、"000001"。

    Returns:
        JSON 字符串。
    """
    try:
        secid = _to_secid(symbol)

        # 常用字段：代码/名称/最新价/涨跌幅/涨跌额/成交量/成交额/换手率/市盈率/市净率等
        fields = "f57,f58,f43,f44,f45,f46,f47,f48,f170,f169,f168,f162,f167,f50"

        resp = requests.get(
            EM_QUOTE_URL,
            params={"secid": secid, "fields": fields, "invt": "2", "fltt": "2"},
            timeout=8,
        )
        resp.raise_for_status()

        payload = resp.json()
        data = payload.get("data")
        if not data:
            return json.dumps(
                {"error": f"未获取到股票 {symbol} 的行情数据，可能代码无效或接口暂时不可用。"},
                ensure_ascii=False,
            )

        result = {
            "代码": data.get("f57"),
            "名称": data.get("f58"),
            "最新价": _safe_div_100(data.get("f43")),
            "最高价": _safe_div_100(data.get("f44")),
            "最低价": _safe_div_100(data.get("f45")),
            "今开": _safe_div_100(data.get("f46")),
            "涨跌幅(%)": _safe_div_100(data.get("f170")),
            "涨跌额": _safe_div_100(data.get("f169")),
            "成交量(手)": data.get("f47"),
            "成交额(元)": data.get("f48"),
            "换手率(%)": _safe_div_100(data.get("f168")),
            "市盈率-动态": data.get("f162"),
            "市净率": data.get("f167"),
            "振幅(%)": _safe_div_100(data.get("f50")),
        }

        return json.dumps(result, ensure_ascii=False)

    except requests.Timeout:
        return json.dumps({"error": "查询超时：行情接口响应过慢，请稍后重试。"}, ensure_ascii=False)
    except requests.RequestException as e:
        return json.dumps({"error": f"HTTP 请求失败: {str(e)}"}, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": f"获取股票行情失败: {str(e)}"}, ensure_ascii=False)
if __name__ == "__main__":
    print(get_stock_quote_core.invoke({"symbol": "600519"})) # tool 方式