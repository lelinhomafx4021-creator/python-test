"""
================================================================================
AI 投研助手 - LangGraph Self-RAG 工作流引擎
================================================================================

这是整个 AI-Investor 项目最核心的文件，定义了 AI 推理的完整流程图。

架构全景（类比：这就像是一个"聪明的 AI 生产线"）：

  用户提问
     │
     ▼
  ┌─────────┐    use_kb    ┌─────────┐    ┌─────────┐    ┌─────────┐
  │ intent  │──────────────│ rewrite  │───▶│ search  │───▶│ answer  │
  │(意图识别)│              │(问题改写) │    │(知识检索)│    │(生成回答)│
  └─────────┘              └─────────┘    └─────────┘    └─────────┘
     │ no_kb                                                    │
     ▼                                                          ▼
  ┌──────────────┐                                       ┌─────────┐
  │direct_answer │                                       │ critic  │
  │  (直接回答)   │                                       │(质量评审)│
  └──────────────┘                                       └─────────┘
       │                                                  │       │
       │                                             fail │       │ pass
       │                                     (打回重写)   │       │
       │                                                  ▼       ▼
       │                                              ┌──────────────┐
       └──────────────────────────────────────────────│     END      │
                                                      │  (输出结果)   │
                                                      └──────────────┘

核心设计理念（Self-RAG = Self-Reflective RAG）：

1. 意图路由 (Intent Routing)
   - 判断用户是在闲聊还是需要投研分析
   - 闲聊→直接回答（不浪费检索资源）
   - 投研→进入检索增强流程

2. 闭环质量管控 (Closed-Loop Quality Control)
   - Answer → Critic → Pass? YES → 输出
   - Answer → Critic → Pass? NO → 带着反馈意见回到 Rewrite → 重新检索 → 重新回答
   - 最多重试 3 次，防止死循环（安全兜底）

3. 流式输出 (Streaming)
   - 使用 LangGraph 的 astream 接口
   - 同时订阅 updates（节点状态）和 messages（token 增量）
   - 前端实时看到"正在思考..."→"正在检索..."→逐字输出→评审→完成

技术要点（面试加分）：
  - AgentState：LangGraph 的核心，像公文包一样在节点间传递
  - add_messages：消息追加而非覆盖，保持完整对话历史
  - Checkpointer：持久化对话状态，同一 threadId 可续接上下文
  - 条件边 (Conditional Edges)：根据状态动态选择下一步（路由/评审判断）
  - Pydantic Output Parser：将 LLM 的非结构化输出转为严格类型

节点职责速查：
  intent        - 意图识别：需要查资料吗？
  direct_answer - 闲聊回答：礼貌回复，不查资料
  rewrite       - 问题改写：把用户大白话转成精确搜索词
  search        - 知识检索：本地向量库 + 联网搜索 + 实时行情
  answer        - 生成回答：基于检索资料撰写投研报告
  critic        - 质量评审：检测幻觉，决定通过或打回重写
"""
import re
from typing import Annotated, TypedDict, Literal
from langgraph.graph import END, START, StateGraph
from langgraph.graph.message import add_messages
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from langgraph.checkpoint.memory import InMemorySaver
import app.core.llm as llm_core
from app.prompts.investor_prompts import (
    ANSWER_PROMPT,
    CRITIC_PROMPT,
    CRITIC_REVIEW_PARSER,
    DIRECT_ANSWER_PROMPT,
    INTENT_ROUTE_PARSER,
    INTENT_ROUTE_PROMPT,
    REWRITE_INITIAL_PROMPT,
    REWRITE_QUERIES_PARSER,
    REWRITE_RETRY_PROMPT,
)
from app.skills.stock_analysis_skill import StockAnalysisSkillInput, stock_analysis_skill

# --- 1. 状态定义 ---
class AgentState(TypedDict):
    """
    【核心：Agent 的“记忆”】
    AgentState 是 LangGraph 的灵魂。它像是一个在各个节点之间传递的“公文包”。
    每个节点都可以打开这个公文包，读里面的内容，或者往里面塞新的东西。
    """
    # 所有的历史对话消息。Annotated 和 add_messages 告诉系统：新消息要追加在后面，而不是覆盖。
    messages: Annotated[list, add_messages] 
    
    # rewrite 节点生成的搜索关键词
    queries: list[str]
    
    # search 节点找回来的原始文本资料
    knowledge: str
    
    # 当前程序运行到了哪一步（前端显示“思考过程”就靠它）
    step: str
    
    # 记录“打回重做”的次数。面试点：如何防止 Agent 进入死循环？答：设置计数器。
    retry_count: int 
    
    # Critic 评审点的结论：是通过 (pass) 还是 失败 (fail)
    review_status: str 
    
    # 如果评审失败，专家给出的具体“修改意见”
    critic_feedback: str 
    
    # 累计消耗。面试点：AI 项目如何控成本？答：全链路追踪 Token 用量。
    total_tokens: int 
    
    # 路由判定结果：这题需不需要翻书（查知识库）
    use_kb: bool      
    
    # 存储股票行情等结构化数据的上下文
    skill_context: str

    # 人工兜底信号：当 AI 判断当前问题应该转人工时，这几个字段会被设置
    handoff_to_human: bool
    handoff_reason: str
    handoff_summary: str


def _latest_user_query(state: AgentState) -> str:
    """读取最近一条“用户消息”。

    为什么要专门写这个函数：
    - state["messages"] 里既有用户消息，也有模型消息。
    - 我们做路由/改写时必须基于“用户原问题”，不能误用 AI 草稿。
    """
    for message in reversed(state.get("messages", [])):
        if isinstance(message, HumanMessage):
            return message.content
    return ""


def _message_text(message) -> str:
    """把不同消息格式统一提取成纯文本字符串。"""
    content = getattr(message, "content", message)
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        text_parts = []
        for item in content:
            if isinstance(item, str):
                text_parts.append(item)
            elif isinstance(item, dict) and item.get("type") == "text":
                text_parts.append(item.get("text", ""))
        return "\n".join(part for part in text_parts if part)
    return str(content)


def _normalize_query_items(items: list[str]) -> list[str]:
    """清洗检索词：去序号、去空项、去重，并限制最多 3 条。"""
    cleaned: list[str] = []
    for item in items:
        normalized = re.sub(r"^[\-\d\.\)\s]+", "", item).strip()
        if normalized and normalized not in cleaned:
            cleaned.append(normalized)
    return cleaned[:3]


def _chunk_text(text: str, chunk_size: int = 24) -> list[str]:
    """将长文本按固定长度切成小片段（用于流式兜底拆分）。"""
    if not text:
        return []
    return [text[i:i + chunk_size] for i in range(0, len(text), chunk_size)]


def _token_count(response) -> int:
    """从模型响应元数据中提取 token 使用量。"""
    return response.response_metadata.get("token_usage", {}).get("total_tokens", 0)


def _state_total_tokens(state: AgentState, response) -> int:
    """把本次调用 token 累加到状态里，便于全链路成本统计。"""
    return state.get("total_tokens", 0) + _token_count(response)


def _content_delta_event(node_name: str, delta: str) -> dict:
    """构造统一的流式增量事件结构。"""
    return {"stage": "content_delta", "data": {"node": node_name, "delta": delta}}


def _stream_fallback_pieces(streamed_answer_parts: list[str], candidate_msg: str) -> list[str]:
    """当底层没返回 token chunk 时，基于完整文本做增量兜底。"""
    streamed_text = "".join(streamed_answer_parts)
    delta_text = candidate_msg[len(streamed_text):] if candidate_msg.startswith(streamed_text) else candidate_msg
    return _chunk_text(delta_text)


def _wants_detailed_answer(query: str) -> bool:
    """粗略判断用户是否要求“长回答/详细回答”。

    用正则命中关键词，决定 direct_answer 节点是否切换到更详细提示词。
    """
    patterns = [
        r"\d+\s*字",
        r"详细",
        r"展开",
        r"不少于",
        r"尽量长",
        r"分点",
        r"完整",
        r"深入",
    ]
    return any(re.search(pattern, query) for pattern in patterns)


def _wants_human_handoff(query: str) -> bool:
    """判断用户是否明确要求转人工。"""
    keywords = [
        "人工",
        "人工客服",
        "转人工",
        "客服",
        "投诉",
        "专员",
    ]
    return any(keyword in query for keyword in keywords)


def _build_handoff_summary(state: AgentState, reason: str) -> str:
    """生成一段给人工客服看的交接摘要。"""
    query = _latest_user_query(state)
    retry_count = state.get("retry_count", 0)
    review_status = state.get("review_status", "")
    critic_feedback = state.get("critic_feedback", "")

    parts = [
        f"用户问题：{query}",
        f"转人工原因：{reason}",
    ]
    if retry_count:
        parts.append(f"当前重试次数：{retry_count}")
    if review_status:
        parts.append(f"当前评审状态：{review_status}")
    if critic_feedback:
        parts.append(f"评审反馈：{critic_feedback}")
    return "\n".join(parts)

# --- 2. 节点逻辑 (Nodes) ---
# 每个节点就是一个 Python 函数，它执行完后返回更新后的状态（往公文包里塞东西）

async def route_intent_node(state: AgentState):
    """【意图识别】判断用户是在闲聊还是在问正经的投研问题"""
    user_msg = _latest_user_query(state)

    if _wants_human_handoff(user_msg):
        return {
            "handoff_to_human": True,
            "handoff_reason": "user_requested_human",
            "handoff_summary": _build_handoff_summary(state, "user_requested_human"),
            "step": "🤝 用户明确要求人工客服，正在准备转接..."
        }

    llm = llm_core.get_llm(temperature=0) # temperature=0 让 AI 的判断更稳定，不乱猜

    # ainvoke 是“异步调用”，程序发出请求后可以去处理别的任务，等 AI 返回了再回来继续
    res = await llm.ainvoke(
        INTENT_ROUTE_PROMPT.format_messages(
            user_msg=user_msg,
            format_instructions=INTENT_ROUTE_PARSER.get_format_instructions(),
        )
    )
    decision_text = _message_text(res)
    try:
        route_result = INTENT_ROUTE_PARSER.parse(decision_text)
        use_kb = route_result.route == "use_kb"
    except Exception:
        decision = decision_text.strip().lower()
        use_kb = "use_kb" in decision and "no_kb" not in decision

    # 返回的值会自动合并到 AgentState 这个大公文包里
    return {
        "use_kb": use_kb,
        "total_tokens": _state_total_tokens(state, res),
        "step": "🧭 正在判断是否需要知识库检索..."
    }


async def direct_answer_node(state: AgentState):
    """【闲聊节点】不需要查资料时，让 AI 礼貌地回一句就行"""
    llm = llm_core.get_llm(
        temperature=0.6,
        streaming=True,
        max_completion_tokens=2048,
    ) # 闲聊可以稍微“活泼”一点
    user_msg = _latest_user_query(state)
    if _wants_detailed_answer(user_msg):
        res = await llm.ainvoke(
            [
                SystemMessage(
                    content="你是专业但友好的中文投研助手。对于不需要知识库检索的问题，直接回答即可，不要编造事实，也不要暴露内部实现。"
                ),
                HumanMessage(
                    content=(
                        "用户明确要求更详细的输出，请尽量满足篇幅和结构要求。\n"
                        "要求：\n"
                        "1. 只用中文\n"
                        "2. 优先按用户要求的字数、篇幅或分点结构回答\n"
                        "3. 如果问题本身信息量有限，也要尽量把背景、用途、注意点解释清楚\n"
                        "4. 不要提及知识库、路由、提示词或系统规则\n\n"
                        f"用户问题：\n{user_msg}"
                    )
                ),
            ]
        )
    else:
        res = await llm.ainvoke(
            DIRECT_ANSWER_PROMPT.format_messages(user_msg=user_msg)
        )
    return {
        "messages": [res],
        "total_tokens": _state_total_tokens(state, res),
        "step": "💬 直接回答完成"
    }


async def rewrite_node(state: AgentState):
    """【重写节点】把用户的大白话转成搜素引擎喜欢的关键词"""
    user_msg = _latest_user_query(state)
    llm = llm_core.get_llm(temperature=0.3)
    
    # 核心面试点：Self-RAG 的体现。如果之前的尝试被评审员打回来了，该怎么办？
    # 答：利用上一次的反馈信息，重新校准搜索方向。
    if state.get("retry_count", 0) > 0:
        feedback = state.get("critic_feedback", "信息不足")
        prompt_messages = REWRITE_RETRY_PROMPT.format_messages(
            feedback=feedback,
            user_msg=user_msg,
            format_instructions=REWRITE_QUERIES_PARSER.get_format_instructions(),
        )
    else:
        prompt_messages = REWRITE_INITIAL_PROMPT.format_messages(
            user_msg=user_msg,
            format_instructions=REWRITE_QUERIES_PARSER.get_format_instructions(),
        )
    
    response = await llm.ainvoke(prompt_messages)
    response_text = _message_text(response)
    try:
        parsed = REWRITE_QUERIES_PARSER.parse(response_text)
        queries = _normalize_query_items(parsed.queries)
    except Exception:
        queries = _normalize_query_items(response_text.split("\n"))
    if not queries:
        queries = [user_msg]
    
    return {
        "queries": queries,
        "total_tokens": _state_total_tokens(state, response),
        "step": f"🧠 正在重新校准搜索意图 (消耗: {_token_count(response)} tokens)..."
    }

async def search_node(state: AgentState):
    """【检索节点】调用具体的 Skill（里面整合了向量+BM25+联网）去抓数据"""
    queries = state["queries"]
    user_query = _latest_user_query(state)
    # 这里不需要直接跟 LLM 说话，而是掉用 stock_analysis_skill 里的 Python 代码
    skill_result = await stock_analysis_skill.run(
        StockAnalysisSkillInput(
            query=user_query,
            queries=queries,
            top_k=3,
        )
    )
    return {
        "knowledge": skill_result.knowledge,
        "skill_context": skill_result.to_prompt_context(),
        "step": "🔍 高级 Skill 正在编排检索与行情数据..."
    }

async def answer_node(state: AgentState):
    """【生成节点】基于搜集到的“知识库”内容来写投研报告"""
    knowledge = (state.get("knowledge") or "").strip()
    if not knowledge:
        fallback = AIMessage(
            content=(
                "当前检索结果为空，我不能基于不足的资料直接下结论。"
                "请补充更具体的公司、行业、时间范围，或先导入相关知识库资料后再分析。"
            )
        )
        return {
            "messages": [fallback],
            "step": "⚠️ 未检索到有效资料，已停止生成结论",
        }

    llm = llm_core.get_llm(
        temperature=0.4,
        streaming=True,
        max_completion_tokens=4096,
    )
    skill_context = state.get("skill_context", "")
    feedback = state.get("critic_feedback", "")
    
    prompt_messages = ANSWER_PROMPT.format_messages(
        user_msg=_latest_user_query(state),
        knowledge=knowledge or "无",
        skill_context=skill_context or "无",
        feedback=feedback or "无额外修正要求",
    )
    response = await llm.ainvoke(
        prompt_messages[:1] + state["messages"] + prompt_messages[1:]
    )
    
    return {
        "messages": [response], 
        "total_tokens": _state_total_tokens(state, response),
        "step": "✍️ 分析师正在撰写深度报告..."
    }

async def critic_node(state: AgentState):
    """
    【评审节点】Agent 的“质检员”
    知识点：Self-Correction (自纠错) 架构。模拟了人类社会中的“一人做，一人审”的模型。
    """
    llm = llm_core.get_llm(temperature=0) # 评审需要极度客观，锁定 0 温度
    last_answer = _message_text(state["messages"][-1])
    knowledge = state["knowledge"]
    res = await llm.ainvoke(
        CRITIC_PROMPT.format_messages(
            user_msg=_latest_user_query(state),
            knowledge=knowledge or "无",
            answer=last_answer,
            format_instructions=CRITIC_REVIEW_PARSER.get_format_instructions(),
        )
    )
    content = _message_text(res)
    
    # 后处理 AI 的返回，提取出 pass 还是 fail
    try:
        review = CRITIC_REVIEW_PARSER.parse(content)
        status = review.verdict
        reason = review.reason.strip()
    except Exception:
        lowered = content.lower()
        status = "pass" if "结论: pass" in lowered or "status: pass" in lowered else "fail"
        reason = content.split("理由:")[-1].strip() if "理由:" in content else "内容不够详实"
    
    # 如果不通过，重试次数加 1
    new_retry = state.get("retry_count", 0) + (1 if status == "fail" else 0)

    # 重试 3 次仍然失败，就不要再硬答了，直接转人工。
    if status == "fail" and new_retry >= 3:
        handoff_reason = "critic_failed_after_retries"
        return {
            "review_status": "handoff",
            "critic_feedback": reason,
            "retry_count": new_retry,
            "handoff_to_human": True,
            "handoff_reason": handoff_reason,
            "handoff_summary": _build_handoff_summary(state, handoff_reason),
            "total_tokens": _state_total_tokens(state, res),
            "step": "🤝 多次修正后仍不稳定，正在转人工客服..."
        }
    else:
        step = "✅ 评审通过，内容可信" if status == "pass" else f"❌ 发现缺陷：{reason}，已打回重写..."

    return {
        "review_status": status,
        "critic_feedback": reason,
        "retry_count": new_retry,
        "total_tokens": _state_total_tokens(state, res),
        "step": step
    }


async def handoff_node(state: AgentState):
    """【人工兜底节点】当 AI 不适合继续处理时，输出转人工提示。"""
    reason = state.get("handoff_reason", "unknown")
    summary = state.get("handoff_summary", "")

    message = (
        "当前问题我已经为你转交人工客服继续处理。\n"
        "人工客服将基于当前对话上下文继续跟进，你不需要从头重复描述。\n"
        f"转接原因：{reason}"
    )
    if summary:
        message += "\n\n交接摘要：\n" + summary

    return {
        "messages": [AIMessage(content=message)],
        "review_status": "handoff",
        "step": "🤝 已生成人工交接信息"
    }

# --- 3. 路由逻辑 (Edges) ---
# 定义了在不同节点之间跳转的“规则”

def route_intent(state: AgentState) -> Literal["use_kb", "no_kb", "handoff"]:
    """第一段路由：根据 intent 节点的输出，决定走 rewrite 还是 direct_answer"""
    if state.get("handoff_to_human"):
        return "handoff"
    return "use_kb" if state.get("use_kb", True) else "no_kb"


def route_judge(state: AgentState) -> Literal["retry", "handoff", "end"]:
    """第二段路由：根据评审结论，决定是回退重试还是直接结束"""
    if state.get("handoff_to_human") or state.get("review_status") == "handoff":
        return "handoff"
    if state.get("review_status") == "fail":
        return "retry"
    return "end"

# --- 4. 构建闭环工作流 ---

def build_self_rag_graph():
    """
    在这里把所有的 Node（点）和 Edge（线）拼成一张图。
    面试点：LangGraph 的核心架构是什么？答：点(Nodes) + 边(Edges) + 状态(State)。
    """
    workflow = StateGraph(AgentState)

    # 1. 注册所有的节点
    workflow.add_node("intent", route_intent_node)
    workflow.add_node("direct_answer", direct_answer_node)
    workflow.add_node("rewrite", rewrite_node)
    workflow.add_node("search", search_node)
    workflow.add_node("answer", answer_node)
    workflow.add_node("critic", critic_node)
    workflow.add_node("handoff", handoff_node)

    # 2. 连线
    workflow.add_edge(START, "intent") # 从 START 开始，第一步到 intent

    # 意图识别后的分叉路：条件边
    workflow.add_conditional_edges(
        "intent",
        route_intent, # 调用上面定义的函数来决定走哪条路
        {
            "use_kb": "rewrite",
            "no_kb": "direct_answer",
            "handoff": "handoff",
        }
    )

    # 普通连线：一步接一步
    workflow.add_edge("direct_answer", END) # 直接回答完就结束 (END)
    workflow.add_edge("handoff", END)
    workflow.add_edge("rewrite", "search")
    workflow.add_edge("search", "answer")
    workflow.add_edge("answer", "critic")

    # 闭环的关键：质量评审后的跳转
    workflow.add_conditional_edges(
        "critic",
        route_judge,
        {
            "retry": "rewrite", # 如果失败，跳回 rewrite 节点重新开始
            "handoff": "handoff",
            "end": END
        }
    )

    # 给图加上“持久化存储”，让 Agent 能记住历史对话
    active_checkpointer = llm_core.checkpointer or InMemorySaver()
    return workflow.compile(checkpointer=active_checkpointer)

# --- 5. 最终包装类 (对外提供服务) ---

class MultiGraphInvestorAgent:
    """对外提供统一的 LangGraph 调用入口（单例使用）。"""

    def __init__(self):
        """延迟初始化图对象，避免 import 阶段就做重型构建。"""
        self._graph = None

    @property
    def graph(self):
        """懒加载：只有在第一次用的时候才去构建图"""
        if self._graph is None:
            self._graph = build_self_rag_graph()
        return self._graph

    async def ask_stream_events(
        self,
        query: str,
        thread_id: str,
    ):
        """
        【流式接口】像水流一样一点点把 AI 的结果吐回给前端。
        """
        # input_data 是图执行的初始状态，等价于“第一版公文包”。
        input_data = {
            "messages": [HumanMessage(content=query)],
            "retry_count": 0,
            "critic_feedback": "",
            "total_tokens": 0
        }
        # configurable.thread_id 决定“记忆隔离”：
        # - 同一个 thread_id：会续接上下文
        # - 不同 thread_id：视为新会话
        config = {
            "configurable": {"thread_id": thread_id}, # 线程 ID，用来区分不同的对话窗口
        }

        # 告诉前端：我收到请求了 (accepted)
        yield {"stage": "accepted", "data": {"query": query}}
        
        final_msg = ""
        streamed_answer_parts: list[str] = []
        total_tokens = 0
        use_kb = False
        retry_count = 0
        review_status = ""
        critic_feedback = ""
        handoff_to_human = False
        handoff_reason = ""
        handoff_summary = ""

        # astream 是 LangGraph 的流式执行接口。
        # 我们同时订阅两类流：
        # 1) updates：节点状态更新（step、messages、total_tokens）
        # 2) messages：模型 token 增量（更细粒度）
        async for mode, chunk in self.graph.astream(
            input_data,
            config=config,
            stream_mode=["updates", "messages"],
        ):
            if mode == "updates":
                # updates 是“按节点分组”的字典：{node_name: updates}
                for node_name, updates in chunk.items():
                    # 如果这个节点更新了 step 字段，我们就把它发给前端展示“思考过程”
                    if "step" in updates:
                        yield {"stage": node_name, "data": {"step": updates["step"]}}

                    # 记录最终的回复文本
                    if "messages" in updates and updates["messages"]:
                        try:
                            candidate_msg = _message_text(updates["messages"][-1])
                            final_msg = candidate_msg or final_msg

                            # 某些模型适配层不会返回真正的 token chunk，这里兜底拆分完整答案继续推给前端。
                            if candidate_msg:
                                for piece in _stream_fallback_pieces(streamed_answer_parts, candidate_msg):
                                    streamed_answer_parts.append(piece)
                                    yield _content_delta_event(node_name, piece)
                        except Exception:
                            pass

                    # 更新 Token 消耗
                    if "total_tokens" in updates:
                        total_tokens = updates.get("total_tokens", total_tokens)
                    if "use_kb" in updates:
                        use_kb = updates.get("use_kb", use_kb)
                    if "retry_count" in updates:
                        retry_count = updates.get("retry_count", retry_count)
                    if "review_status" in updates:
                        review_status = updates.get("review_status", review_status)
                    if "critic_feedback" in updates:
                        critic_feedback = updates.get("critic_feedback", critic_feedback)
                    if "handoff_to_human" in updates:
                        handoff_to_human = updates.get("handoff_to_human", handoff_to_human)
                    if "handoff_reason" in updates:
                        handoff_reason = updates.get("handoff_reason", handoff_reason)
                    if "handoff_summary" in updates:
                        handoff_summary = updates.get("handoff_summary", handoff_summary)

            elif mode == "messages":
                # messages 模式拿到的是 (message_chunk, metadata)
                # 这里主要处理 direct_answer/answer 两个会产出正文的节点。
                message_chunk, metadata = chunk
                node_name = metadata.get("langgraph_node", "")
                if node_name not in {"direct_answer", "answer"}:
                    continue

                delta = _message_text(message_chunk)
                if not delta:
                    continue

                streamed_answer_parts.append(delta)
                yield _content_delta_event(node_name, delta)

        if streamed_answer_parts:
            final_msg = "".join(streamed_answer_parts).strip() or final_msg

        if handoff_to_human:
            yield {
                "stage": "handoff",
                "data": {
                    "step": "🤝 当前问题已触发人工兜底",
                    "reason": handoff_reason,
                    "summary": handoff_summary,
                }
            }

        # 整个图跑完了，把最终答案发出去
        yield {
            "stage": "final_answer",
            "data": {
                "answer": final_msg,
                "source": "AI 投研闭环引擎 (Self-RAG v2)",
                "usage": total_tokens,
                "use_kb": use_kb,
                "retry_count": retry_count,
                "review_status": review_status,
                "critic_feedback": critic_feedback,
                "handoff_to_human": handoff_to_human,
                "handoff_reason": handoff_reason,
                "handoff_summary": handoff_summary,
            }
        }
        # 完结撒花
        yield {"stage": "done", "data": {"status": "success"}}

# 创建单例对象，供其他模块调用
multi_graph_agent = MultiGraphInvestorAgent()

