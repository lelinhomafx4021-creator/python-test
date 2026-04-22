"""
【企业级标准】AI 业务逻辑抽象层 (Service Layer)

知识点：
1. 解耦：API 层只负责接收参数和校验，核心业务逻辑（如何调 Agent）解构到 Service。
2. 可测试性：Service 可以在不启动 FastAPI 的情况下进行单元测试。
"""

import asyncio
from typing import AsyncGenerator
from app.graph.investor_graph import multi_graph_agent
from app.models.chat_turn import ChatTurn
from app.core.db import engine
from sqlmodel import Session

class InvestorService:
    """
    投研业务服务类。
    面试点：采用单例或注入模式管理，保证资源复用。
    """
    
    async def run_investor_flow(
        self,
        query: str, 
        thread_id: str, 
        trace_id: str
    ) -> AsyncGenerator:
        """
        运行投研工作流，并返回流式事件。
        """
        final_answer = ""
        
        async for event in multi_graph_agent.ask_stream_events(
            query=query,
            thread_id=thread_id,
            trace_id=trace_id
        ):
            # 捕获最终答案，用于后台异步落库
            if event["stage"] == "final_answer":
                final_answer = event["data"].get("answer", "")
                
            yield event

        # --- 核心性能点：后台异步落库 ---
        # 知识点：不要用 await，直接用 create_task。
        # 这样 Python 只要把回答发给用户，就会立刻结束这个函数，把存数据库的脏活累活留在后台慢慢干。
        if final_answer:
            asyncio.create_task(self._persist_chat_turn(
                query=query, 
                answer=final_answer, 
                thread_id=thread_id, 
                trace_id=trace_id
            ))

    async def _persist_chat_turn(self, query: str, answer: str, thread_id: str, trace_id: str):
        """后台持久化任务：将对话记录存入 ai_chat_turns 表"""
        try:
            # 知识点：在后台任务中重新打开 Session，保证线程/协程安全
            with Session(engine) as session:
                turn = ChatTurn(
                    user_id="customer_pro", # 实际生产中应从上下文获取
                    session_id=thread_id,
                    thread_id=thread_id,
                    trace_id=trace_id,
                    query=query,
                    answer=answer,
                    intent="stock_analysis", # 这里可以根据 Agent 的 state 动态设置
                    source="Self-RAG-v2"
                )
                session.add(turn)
                session.commit()
                # print(f">>> [Background] 已成功异步审计对话记录: {trace_id}")
        except Exception as e:
            # 如果存失败了，仅记录日志，绝不影响前端用户的正常对话
            print(f">>> [Background Error] 审计落库失败: {e}")

# 实例化
investor_service = InvestorService()
