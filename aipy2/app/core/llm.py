import json
import psycopg
from typing import Optional
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain_openai import ChatOpenAI
from psycopg_pool import AsyncConnectionPool
from app.tools.stockdata_tool import get_stock_quote_core
from app.core.config import settings
from app.tools.retriever_tool import search_intelligent
from langgraph.checkpoint.postgres import PostgresSaver
from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
from langgraph.prebuilt import create_react_agent
from app.core.logger import logger

# --- 1. 高级模型配置 ---
from app.schemas.analysis_schema import StructuredInvestmentAnalysis

# --- 1. 高级模型配置 ---
def get_llm(temperature: float = 0.2) -> ChatOpenAI:
    return ChatOpenAI(
        model="mimo-v2-pro",
        temperature=temperature,
        api_key=settings.XIAOMIMINO_KEY,
        base_url="https://api.xiaomimimo.com/v1",
    )

# 实例化基础 LLM
base_llm = get_llm(temperature=0.3)

# --- 知识点：Structured Output (结构化输出) ---
# 我们给模型套上一个“模具”。这样它不再返回乱七八糟的文字，
# 而是返回一个 StructuredInvestmentAnalysis 类型的对象。
structured_llm = base_llm.with_structured_output(StructuredInvestmentAnalysis)

# 注册 AI 能够调用的工具
tools = [search_intelligent, get_stock_quote_core]

# AI 记忆数据库连接池 (LangGraph 专用) 
# 知识点：在异步环境中，必须在事件循环启动后才能初始化连接池。
# 我们将初始化逻辑延迟到 lifespan 中执行。
_memory_pool: Optional[AsyncConnectionPool] = None
checkpointer: Optional[AsyncPostgresSaver] = None

async def init_llm_components():
    """初始化异步组件（由 main.py lifespan 调用）"""
    global _memory_pool, checkpointer
    if _memory_pool is None:
        logger.info("[PG] 正在初始化异步连接池...")
        _memory_pool = AsyncConnectionPool(conninfo=settings.DATABASE_URL)
        checkpointer = AsyncPostgresSaver(_memory_pool)
        # 顺便完成表初始化
        await checkpointer.setup()
        logger.info("[PG] 异步连接池与 Checkpointer 初始化完成")

# --- 2. 【核心】高级结构化提示词 (Advanced Prompt) ---
# 知识点：大厂通用的提示词框架 (Role - Tasks - Constraints - Output)
SYSTEM_PROMPT = """
# Role
你是一名资深的【全行业顶级首席分析师】，拥有 15 年二级市场研究经验。你不仅懂冷冰冰的数据，更能洞察数据背后的逻辑。

# Task
你的任务是根据用户提供的【用户画像】和【提问内容】，给出具有专业深度的投研建议。

# Constraints（硬性约束，面试必讲：如何防范 AI 幻觉）
1. 引用溯源：所有数据（股价、市盈率、研报内容）必须明确标注来源，禁止凭空捏造。
2. 利益风险提示：在给出分析结论后，必须附带 1-2 条潜在的风险提示。
3. 角色坚守：绝对不要承认自己是 AI 助手，始终保持专业分析师的语气。
4. 语言要求：必须使用简体中文，风格要求【严谨、简洁、量化数据导向】。

# Context: Current User Profile
- 用户投资风格：{risk_level}
- 关注领域：{interested_sectors}

# Output Format
请按照以下结构输出：
【核心结论】：一句话总结你的观点。
【详细拆解】：基于工具调用的深度分析（逻辑链条要清晰）。
【风险警示】：列出 1-3 点可能的利空因素。
"""


# 实例化 LLM
llm = get_llm(temperature=0.3)

class InvestorAgent:
    """
    AI 投资助手类
    知识点：使用了 LangGraph 的 create_react_agent。
    ReAct 模式 = Reasoning (推理) + Acting (行动)。AI 会自己思考是否由于查数据。
    """
    def __init__(self):
        # 注意：这里不再在构造函数里写死 checkpointer
        # 因为此时 checkpointer 还没初始化（None）
        self._agent = None

    @property
    def agent(self):
        """延迟加载 Agent，确保 checkpointer 已就绪"""
        if self._agent is None:
            if checkpointer is None:
                raise RuntimeError("LLM components not initialized. Call init_llm_components() first.")
            self._agent = create_react_agent(
                model=llm,
                tools=tools,
                checkpointer=checkpointer,
            )
        return self._agent

    async def ask(self, query: str, thread_id: str, profile: dict = None):
        """
        提问核心逻辑
        @param profile: 动态传入的用户画像（从数据库查出来的）
        """
        # 1. 准备画像数据（如果没有就给默认值）
        risk = profile.get("risk_level", "稳健型") if profile else "中等风险"
        sectors = profile.get("interested_sectors", "全行业") if profile else "通用"

        # 2. 动态拼装高级提示词 (这就是你说的“塞”进提示词)
        formatted_system_prompt = SYSTEM_PROMPT.format(
            risk_level=risk,
            interested_sectors=sectors
        )

        # 3. 构造请求配置
        config = {"configurable": {"thread_id": thread_id}}
        
        # 4. 调用 Agent (异步执行)
        # 知识点：我们将 System Message 放在消息序列的开头，奠定基调
        input_data = {
            "messages": [
                ("system", formatted_system_prompt), 
                ("user", query)
            ]
        }
        
        try:
            res = await self.agent.ainvoke(input_data, config=config)
            last_msg = res["messages"][-1]
            return {
                "answer": last_msg.content,
                "trace_id": thread_id, # 暂用 thread_id 作为标识
                "source": "AI 分析引擎 v2.0"
            }
        except Exception as e:
            logger.error(f"Agent Execution Error: {e}")
            return {"answer": "抱歉，分析师暂时由于网络原因离开，请稍后再试。", "source": "error"}

# 导出实例
investor_agent = InvestorAgent()
