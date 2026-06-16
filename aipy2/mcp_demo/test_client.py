"""
MCP Client — 连接到 weather_server.py 并调用其工具

运行方式：
  cd aipy2
  uv run python mcp_demo/test_client.py
"""

import asyncio
from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client


async def main():
    # ============================================================
    # 1. 连接 MCP Server（通过 stdio 启动子进程）
    # ============================================================
    print("=" * 60)
    print("[连接] 正在连接 MCP Server...")
    print("=" * 60)

    server_params = StdioServerParameters(
        command="uv",                              # 启动命令
        args=["run", "python", "mcp_demo/weather_server.py"],  # 参数
    )

    async with stdio_client(server_params) as (read, write):
        async with ClientSession(read, write) as session:
            # (1) 初始化握手
            await session.initialize()
            print("[OK] 握手完成，连接成功！\n")

            # ============================================================
            # 2. 获取工具列表（发送 tools/list 请求）
            # ============================================================
            print("-" * 60)
            print("[tools/list] 获取工具列表...")
            tools = await session.list_tools()
            print(f"   共 {len(tools.tools)} 个工具：")
            for tool in tools.tools:
                print(f"   * {tool.name}: {tool.description}")
                print(f"     参数 schema: {tool.inputSchema}")
            print()

            # ============================================================
            # 3. 调用 Tool：get_weather
            # ============================================================
            print("-" * 60)
            print("[tools/call] 调用 get_weather(city='北京')...")
            result = await session.call_tool("get_weather", arguments={"city": "北京"})
            print(f"   返回: {result.content[0].text}")
            print()

            # ============================================================
            # 4. 调用 Tool：get_stock_quote（两个股票）
            # ============================================================
            print("-" * 60)
            print("[tools/call] 调用 get_stock_quote(symbol='600519')...")
            result = await session.call_tool("get_stock_quote", arguments={"symbol": "600519"})
            print(f"   返回: {result.content[0].text}")
            print()

            print("[tools/call] 调用 get_stock_quote(symbol='000858')...")
            result = await session.call_tool("get_stock_quote", arguments={"symbol": "000858"})
            print(f"   返回: {result.content[0].text}")
            print()

            # ============================================================
            # 5. 读取 Resource
            # ============================================================
            print("-" * 60)
            print("[resources/list] 获取资源列表...")
            resources = await session.list_resources()
            print(f"   共 {len(resources.resources)} 个资源：")
            for r in resources.resources:
                print(f"   * {r.name}: {r.uri}")

            print("[resources/read] 读取公告资源...")
            result = await session.read_resource("announcements://latest")
            print("   公告内容:")
            for content in result.contents:
                print(content.text)
            print()

            # ============================================================
            # 6. 获取 Prompt 模板
            # ============================================================
            print("-" * 60)
            print("[prompts/list] 获取提示词模板...")
            prompts = await session.list_prompts()
            print(f"   共 {len(prompts.prompts)} 个模板：")
            for p in prompts.prompts:
                print(f"   * {p.name}: {p.description}")

            print("[prompts/get] 获取 analyze_stock 模板内容...")
            result = await session.get_prompt("analyze_stock", arguments={"symbol": "600519"})
            print("   模板内容:")
            for msg in result.messages:
                print(f"     {msg.content.text}")
            print()

    # ============================================================
    # 完成
    # ============================================================
    print("=" * 60)
    print("[OK] 测试完成！所有 MCP 原语调用成功。")
    print("=" * 60)


if __name__ == "__main__":
    asyncio.run(main())
