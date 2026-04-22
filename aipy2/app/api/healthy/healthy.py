from fastapi import APIRouter
from langchain_core.messages import HumanMessage

from app.core.llm import llm
router=APIRouter(prefix="/chat",tags=["测试"])

@router.get("/hello")
def get_hello():
    response = llm.invoke([HumanMessage(content="你好啊, deepseek")])
    print(response)
    return response
