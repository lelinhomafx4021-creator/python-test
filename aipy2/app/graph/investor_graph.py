"""
终极进化版：带有“自我反思”机制的 AI 投研助手 (LangGraph Self-RAG)

特点：
1. 闭环控制：Answer -> Critic -> (Pass or Retry)
2. 异步高性能：全部采用 astream / ainvoke
3. 质量保险：检测到幻觉自动打回重搜
"""
import asyncio
from typing import Annotated, TypedDict, Literal
from langgraph.graph import END, START, StateGraph
from langgraph.graph.message import add_messages
from langchain_core.messages import HumanMessage, AIMessage
from app.core.llm import get_llm, checkpointer
from app.tools.retriever_tool import run_retrieval_async
# --- 1. 状态定义 ---
class AgentState(TypedDict):
    """
    Agent 状态对象：这是 LangGraph 的灵魂，记录了整个对话的上下文。
    面试谈资：StateGraph 允许我们定义结构化的状态，使得 Agent 的行为可预测、可追踪。
    """
    messages: Annotated[list, add_messages] 
    queries: list[str]
    knowledge: str
    step: str
    retry_count: int  # 记录重试次数，防止在环里绕死
    review_status: str # 评审结论：pass 或 fail
    critic_feedback: str # 记录评审专家的具体修改意见
    total_tokens: int # 【新增】记录全流程消耗的 Token 总量
    use_kb: bool      # 是否需要走知识库检索

# --- 2. 节点逻辑 ---

async def route_intent_node(state: AgentState):
    """【步骤0：AI 自主路由】判断是否需要知识库检索"""
    user_msg = state["messages"][-1].content
    llm = get_llm(temperature=0)

    prompt = (
        "你是投研助手的路由器。判断用户问题是否需要检索外部知识库。\n"
        "规则：\n"
        "1) 寒暄/闲聊（如：你好、在吗、谢谢）=> 返回 no_kb\n"
        "2) 明确投研/财报/估值/行业/公司分析问题 => 返回 use_kb\n"
        "3) 不确定时，优先返回 use_kb\n"
        f"用户问题：{user_msg}\n"
        "只允许输出一个词：use_kb 或 no_kb"
    )

    res = await llm.ainvoke(prompt)
    decision = str(res.content).strip().lower()
    use_kb = "use_kb" in decision and "no_kb" not in decision

    usage = res.response_metadata.get("token_usage", {})
    tokens = usage.get("total_tokens", 0)

    return {
        "use_kb": use_kb,
        "total_tokens": state.get("total_tokens", 0) + tokens,
        "step": "🧭 正在判断是否需要知识库检索..."
    }


async def direct_answer_node(state: AgentState):
    """【无需知识库】直接回答（寒暄/闲聊）"""
    llm = get_llm(temperature=0.6)
    user_msg = state["messages"][-1].content

    prompt = (
        "你是专业但友好的中文投研助手。\n"
        "当前用户问题不需要知识库检索，请直接简洁回复（1-3句）。\n"
        f"用户：{user_msg}"
    )

    res = await llm.ainvoke(prompt)
    usage = res.response_metadata.get("token_usage", {})
    tokens = usage.get("total_tokens", 0)

    return {
        "messages": [res],
        "total_tokens": state.get("total_tokens", 0) + tokens,
        "step": "💬 直接回答完成"
    }


async def rewrite_node(state: AgentState):
    """【步骤1：问题重写 / 任务拆解】"""
    user_msg = state["messages"][-1].content
    llm = get_llm(temperature=0.3)
    
    # 核心进化：根据反馈调整策略
    if state.get("retry_count", 0) > 0:
        feedback = state.get("critic_feedback", "信息不足")
        prompt = (
            "你是一个投研搜索专家。\n"
            f"上一次尝试失败了，评审建议是：{feedback}\n"
            f"原问题：{user_msg}\n"
            "请重新生成 3 个更精准、更能解决上述反馈的搜索关键词。每行一个。"
        )
    else:
        prompt = f"请将用户问题拆解出搜索词。例如“贵州茅台财报”拆解为“贵州茅台2024财报”、“茅台净利润”等。\n问题: {user_msg}"
    
    response = await llm.ainvoke(prompt)
    queries = [q.strip() for q in response.content.split("\n") if q.strip()]
    
    # 提取 Token 消耗 (LangChain 标准字段)
    usage = response.response_metadata.get("token_usage", {})
    tokens = usage.get("total_tokens", 0)
    
    return {
        "queries": queries,
        "total_tokens": tokens,
        "step": f"💡 正在重新校准搜索意图 (消耗: {tokens} tokens)..."
    }

async def search_node(state: AgentState):
    """【步骤2：多路检索】"""
    queries = state["queries"]
    # 模拟真实检索，这里会调用 tools/retriever_tool.py
    res = await run_retrieval_async(queries=queries, mode="auto")
    return {
        "knowledge": res,
        "step": "🔍 多路并行检索中（Vector + BM25 + Web）..."
    }

async def answer_node(state: AgentState):
    """【步骤3：草稿生成】"""
    llm = get_llm(temperature=0.4)
    knowledge = state["knowledge"]
    feedback = state.get("critic_feedback", "")
    
    hint = f"\n(注意：之前的草稿未通过评审，原因是：{feedback}，请在本次生成中着重解决这个点。)" if feedback else ""
    
    prompt = f"参考资料:\n{knowledge}\n{hint}\n\n请作为资深分析师，撰写一份严谨的投研报告。要求：数据必须来源于参考资料。"
    
    response = await llm.ainvoke(state["messages"] + [HumanMessage(content=prompt)])
    
    usage = response.response_metadata.get("token_usage", {})
    tokens = usage.get("total_tokens", 0)

    return {
        "messages": [response], 
        "total_tokens": state.get("total_tokens", 0) + tokens,
        "step": "✍️ 分析师正在撰写深度报告..."
    }

async def critic_node(state: AgentState):
    """
    【步骤4：专家评审】核心进化节点
    知识点：Self-Correction (自纠错) 架构，模拟了人类社会中的“一人做，一人审”的协作模型。
    """
    llm = get_llm(temperature=0) 
    last_answer = state["messages"][-1].content
    knowledge = state["knowledge"]
    
    prompt = (
        "你是一个极其冷酷的投研合规评审员。\n"
        f"参考语料: {knowledge}\n"
        f"AI 生成的答案: {last_answer}\n\n"
        "任务：检查答案中是否有资料不支持的假数据？或者是否有未回答的关键点？\n"
        "输出格式：\n"
        "结论: <pass/fail>\n"
        "理由: <一句话说明原因>"
    )
    
    res = await llm.ainvoke(prompt)
    content = res.content.lower()
    
    usage = res.response_metadata.get("token_usage", {})
    tokens = usage.get("total_tokens", 0)
    
    status = "pass" if "结论: pass" in content or "status: pass" in content else "fail"
    reason = content.split("理由:")[-1].strip() if "理由:" in content else "内容不够详实"
    
    new_retry = state.get("retry_count", 0) + (1 if status == "fail" else 0)
    
    # 兜底机制：重试 3 次后无论如何都 pass，防止死循环
    if new_retry >= 3:
        status = "pass"
        step = "⚠️ 经过多次修正，已产出当前最优分析"
    else:
        step = "👸 评审通过，内容可信" if status == "pass" else f"🕵️ 发现缺陷：{reason}，已打回重写..."

    return {
        "review_status": status,
        "critic_feedback": reason,
        "retry_count": new_retry,
        "total_tokens": state.get("total_tokens", 0) + tokens,
        "step": step
    }

# --- 3. 路由逻辑 ---

def route_intent(state: AgentState) -> Literal["use_kb", "no_kb"]:
    """第一段路由：先判断要不要知识库"""
    return "use_kb" if state.get("use_kb", True) else "no_kb"


def route_judge(state: AgentState) -> Literal["retry", "end"]:
    """条件边逻辑：判断下一步去哪儿"""
    if state.get("review_status") == "fail":
        return "retry"
    return "end"

# --- 4. 构建闭环工作流 ---

def build_self_rag_graph():
    workflow = StateGraph(AgentState)

    workflow.add_node("intent", route_intent_node)
    workflow.add_node("direct_answer", direct_answer_node)
    workflow.add_node("rewrite", rewrite_node)
    workflow.add_node("search", search_node)
    workflow.add_node("answer", answer_node)
    workflow.add_node("critic", critic_node)

    workflow.add_edge(START, "intent")

    workflow.add_conditional_edges(
        "intent",
        route_intent,
        {
            "use_kb": "rewrite",
            "no_kb": "direct_answer"
        }
    )

    workflow.add_edge("direct_answer", END)
    workflow.add_edge("rewrite", "search")
    workflow.add_edge("search", "answer")
    workflow.add_edge("answer", "critic")

    # 核心：条件边跳转
    workflow.add_conditional_edges(
        "critic",
        route_judge,
        {
            "retry": "rewrite",
            "end": END
        }
    )

    return workflow.compile(checkpointer=checkpointer)

# --- 5. 包装类 ---

class MultiGraphInvestorAgent:
    def __init__(self):
        self._graph = None

    @property
    def graph(self):
        """延迟构建工作流，确保 checkpointer 已在 lifespan 中初始化"""
        if self._graph is None:
            self._graph = build_self_rag_graph()
        return self._graph

    async def ask_stream_events(self, query: str, thread_id: str, trace_id: str):
        input_data = {
            "messages": [HumanMessage(content=query)],
            "retry_count": 0,
            "critic_feedback": "",
            "total_tokens": 0
        }
        config = {"configurable": {"thread_id": thread_id}}

        yield {"stage": "accepted", "data": {"query": query}}
        final_msg = ""
        total_tokens = 0

        async for event in self.graph.astream(input_data, config=config, stream_mode="updates"):
            for node_name, updates in event.items():
                if "step" in updates:
                    # 推送中间步骤，增加前端透明度（Thought Process）
                    yield {"stage": node_name, "data": {"step": updates["step"]}}

                if "messages" in updates and updates["messages"]:
                    try:
                        final_msg = updates["messages"][-1].content or final_msg
                    except Exception:
                        pass

                if "total_tokens" in updates:
                    total_tokens = updates.get("total_tokens", total_tokens)

        yield {
            "stage": "final_answer",
            "data": {
                "answer": final_msg,
                "source": "AI 投研闭环引擎 (Self-RAG v2)",
                "usage": total_tokens
            }
        }
        yield {"stage": "done", "data": {"status": "success"}}

multi_graph_agent = MultiGraphInvestorAgent()
