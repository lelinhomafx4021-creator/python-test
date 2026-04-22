import logging
from fastapi import APIRouter, Request
from app.core.llm import llm
from app.core.logger import logger

router = APIRouter(prefix="/ai/v1/util", tags=["AI通用工具接口"])

@router.post("/generate_title")
async def generate_title(request: Request):
    """
    【企业级工具接口】自动为对话生成一个 5 字以内的紧凑标题。
    后端 Java 在第一轮回合结束时会异步调用。
    """
    try:
        body = await request.json()
        query = body.get("query", "新对话")
        print(f"[generate_title] incoming query: {query}")

        prompt = f"请将以下投研问题总结为5字以内的核心标题，不要带标点，直接返回标题文字：\n{query}"
        res = await llm.ainvoke(prompt)
        title = res.content.strip()
        print(f"[generate_title] generated title: {title}")
        return {"code": 200, "data": {"title": title}, "message": "success"}
    except Exception as e:
        logger.error(f"Title generation failed: {str(e)}")
        return {"code": 500, "data": {"title": "金融投研对话"}, "message": "error"}
