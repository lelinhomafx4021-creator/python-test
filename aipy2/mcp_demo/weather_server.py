"""
MCP Weather + Stock Server — 可以直接跑起来的 MCP Server 示例

启动方式：
  cd aipy2
  uv run python mcp_demo/weather_server.py

测试方式（另开终端）：
  uv run python mcp_demo/test_client.py
"""

from mcp.server.fastmcp import FastMCP

# ============================================================
# 1. 创建 Server（一行代码）
# ============================================================
# FastMCP 是官方提供的高层封装，自动处理 JSON-RPC 协议细节
mcp = FastMCP("投研工具服务器 v1.0")


# ============================================================
# 2. 注册 Tool：get_weather
# ============================================================
# LLM 可以通过 tools/call 调用这个函数
@mcp.tool()
def get_weather(city: str) -> str:
    """获取指定城市的实时天气信息。当用户询问天气时调用。

    Args:
        city: 城市名称，如 北京、上海、深圳
    """
    # 模拟数据（实际项目调天气 API）
    weather_db = {
        "北京": "晴天，25°C，湿度 40%，风力 3 级",
        "上海": "多云，28°C，湿度 65%，风力 2 级",
        "深圳": "阵雨，30°C，湿度 80%，风力 4 级",
    }
    result = weather_db.get(city, f"{city}：晴转多云，22°C")
    return f"[天气] {result}"


# ============================================================
# 3. 注册 Tool：get_stock_quote（模拟 A 股行情）
# ============================================================
@mcp.tool()
def get_stock_quote(symbol: str) -> str:
    """获取 A 股股票实时行情。当用户询问股价、涨跌幅时调用。

    Args:
        symbol: 6 位股票代码，如 600519（贵州茅台）、000001（平安银行）
    """
    # 模拟数据（实际项目调腾讯/东方财富行情 API）
    stock_db = {
        "600519": "贵州茅台 现价 1856.00 涨跌幅 +2.35% 成交额 52.3亿",
        "000001": "平安银行 现价 12.85 涨跌幅 -0.62% 成交额 8.1亿",
        "000858": "五粮液 现价 168.50 涨跌幅 +1.20% 成交额 23.7亿",
    }
    result = stock_db.get(symbol, f"股票 {symbol}：今日休市或无数据")
    return f"[行情] {result}"


# ============================================================
# 4. 注册 Resource：投研公告
# ============================================================
# 应用代码通过 resources/read 读取，LLM 不直接调用
@mcp.resource("announcements://latest")
def get_latest_announcements() -> str:
    """最新的投研公告"""
    return """
    [公告] 最新公告 (2026-06-06)：
    1. 贵州茅台：2026Q1 净利润同比增长 15.2%
    2. 证监会：A 股交易印花税下调至 0.05%
    3. 央行：LPR 五年期利率维持 3.6% 不变
    """


# ============================================================
# 5. 注册 Prompt 模板
# ============================================================
@mcp.prompt()
def analyze_stock(symbol: str) -> str:
    """生成一只股票的投研分析提示词"""
    return f"""请对股票 {symbol} 进行投研分析，包括：
1. 调用 get_stock_quote 获取实时行情
2. 基于行情数据给出基本面概览
3. 指出需要关注的风险点
"""


# ============================================================
# 6. 启动
# ============================================================
if __name__ == "__main__":
    print("[启动] MCP 投研工具服务器启动中...", file=__import__("sys").stderr)
    print("   Tools: get_weather, get_stock_quote", file=__import__("sys").stderr)
    print("   Resources: announcements://latest", file=__import__("sys").stderr)
    print("   Prompts: analyze_stock", file=__import__("sys").stderr)
    print("   Transport: stdio", file=__import__("sys").stderr)
    print(file=__import__("sys").stderr)
    mcp.run(transport="stdio")
