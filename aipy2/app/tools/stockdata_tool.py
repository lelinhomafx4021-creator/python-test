"""股票行情工具：把 A 股代码转换后查询东方财富实时行情。"""

import json
import re
from typing import Any

import requests
from langchain_core.tools import tool


# 东方财富行情接口地址
EM_QUOTE_URL = "https://push2.eastmoney.com/api/qt/stock/get"


def _to_secid(symbol: str) -> str:
    """
    【辅助方法：转换代码格式】
    大模型只知道股票代码（如 600519），但行情接口需要 secid（如 1.600519）。
    这里实现了转换规则：
    - 5、6、9 开头的属于沪市 (1)
    - 其他（主要是 0, 3）开头属于深市 (0)
    """
    if not re.fullmatch(r"\d{6}", symbol):
        raise ValueError("symbol 必须是 6 位数字，例如 600519")

    market = "1" if symbol.startswith(("5", "6", "9")) else "0"
    return f"{market}.{symbol}"


def _safe_div_100(value: Any) -> float | None:
    """【辅助方法】接口返回的单位通常是分或万，这里统一除以 100 转换成元或百分比"""
    if value is None:
        return None
    try:
        return round(float(value) / 100, 2)
    except (TypeError, ValueError):
        return None


@tool
def get_stock_quote_core(symbol: str) -> str:
    """
    获取单只 A 股实时行情。
    
    【知识点：LangChain @tool】
    使用这个装饰器后，这个函数就能被大模型自动识别和调用。
    哪怕我们不手动调用它，Agent 也能根据函数名和注释自行决定要不要查行情。
    """
    try:
        # 第一步：把股票代码转换成接口要求的 secid
        secid = _to_secid(symbol)

        # 这些字段代码 (f57, f58...) 是东方财富接口的标准定义，代表价格、涨幅等
        fields = "f57,f58,f43,f44,f45,f46,f47,f48,f170,f169,f168,f162,f167,f50"

        # 发起网络请求，获取最新数据
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
                {"error": f"未获取到股票 {symbol} 的行情数据，可能代码无效。"},
                ensure_ascii=False,
            )

        # 将接口返回的“火星文”键名转换成易懂的中文，方便 AI 和人类阅读
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

        # 最终返回 JSON 字符串（而不是 dict）。
        # 原因：LangChain 的 tool 通道里，字符串是最稳妥的跨模型通用载体。
        # 下游节点再按需要 json.loads 即可。
        return json.dumps(result, ensure_ascii=False)

    except requests.Timeout:
        # 错误也保持 JSON 字符串格式，方便调用方统一处理。
        return json.dumps({"error": "查询超时：行情接口响应过慢。"}, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": f"获取股票行情失败: {str(e)}"}, ensure_ascii=False)
if __name__ == "__main__":
    print(get_stock_quote_core.invoke({"symbol": "600519"}))  # 工具调用方式
