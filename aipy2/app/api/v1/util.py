"""AI 通用工具接口（新手友好版）。

本模块当前提供：
- `/ai/v1/util/generate_title`：根据用户问题自动生成简短标题。

典型用途：
- 聊天列表页第一轮对话结束后，异步请求标题用于展示会话名称。
"""

from fastapi import APIRouter, Request

from app.core.llm import llm
from app.core.logger import logger
from app.prompts.investor_prompts import GENERATE_TITLE_PROMPT, TITLE_PARSER

router = APIRouter(prefix="/ai/v1/util", tags=["AI通用工具接口"])


def _normalize_title(raw_title: str) -> str:
    """规范化标题。

    处理规则：
    - 去掉常见中英文标点与空格。
    - 最多保留 5 个字。
    - 如果结果为空，回退为“投研对话”。
    """
    title = "".join(ch for ch in raw_title.strip() if ch not in "，。！？、；：,.!?;: ")
    return title[:5] or "投研对话"


def _response_text(response) -> str:
    """兼容不同模型返回结构，统一提取文本内容。"""
    content = getattr(response, "content", "")
    return content if isinstance(content, str) else str(content)


def _parse_title(response) -> str:
    """优先按结构化协议解析标题，失败时回退为纯文本规范化。"""
    content_text = _response_text(response)
    try:
        return _normalize_title(TITLE_PARSER.parse(content_text).title)
    except Exception:
        return _normalize_title(content_text)


@router.post("/generate_title")
async def generate_title(request: Request):
    """根据用户问题生成简短中文标题。

    请求体（JSON）示例：
    {
      "query": "贵州茅台一季度利润怎么看"
    }

    返回示例：
    {
      "code": 200,
      "data": {"title": "茅台利润"},
      "message": "成功"
    }
    """
    try:
        # 1) 读取前端传入的原始问题
        body = await request.json()
        query = body.get("query", "新对话")
        print(f"[标题生成] 收到问题：{query}")

        # 2) 调用大模型，让其按结构化格式返回标题
        res = await llm.ainvoke(
            GENERATE_TITLE_PROMPT.format_messages(
                query=query,
                format_instructions=TITLE_PARSER.get_format_instructions(),
            )
        )
        title = _parse_title(res)

        print(f"[标题生成] 生成结果：{title}")
        return {"code": 200, "data": {"title": title}, "message": "成功"}
    except Exception as e:
        # 失败时统一返回兜底标题，保证前端有可展示内容
        logger.error(f"标题生成失败：{str(e)}")
        return {"code": 500, "data": {"title": "投研对话"}, "message": "失败"}
