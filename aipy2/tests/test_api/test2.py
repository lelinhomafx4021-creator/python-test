from fastapi import APIRouter
from langchain_core.messages import HumanMessage

from app.core.llm import llm
router=APIRouter(prefix="/chat",tags=["测试"])

def get_hello():
    response=llm.invoke(HumanMessage(content="你好啊，deeptseek"))
    print(response)
    return response


if __name__ == "__main__":
    get_hello()