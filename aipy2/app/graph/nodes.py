"""
LangGraph 节点函数。

每个节点就是一个 Python 函数，执行完后返回更新后的状态（往公文包里塞东西）。
"""

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage

from app.core.logger import logger
import app.core.llm as llm_core
from app.tools.data_fetcher import fetch_all_data_parallel
from app.prompts.investor_prompts import (
    ANSWER_PROMPT,
    ANSWER_PROMPT_LITE,
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

from app.graph.state import (
    AgentState,
    _latest_user_query,
    _message_text,
    _normalize_query_items,
    _token_count,
    _state_total_tokens,
    _wants_detailed_answer,
    _wants_human_handoff,
    _build_handoff_summary,
)


# ============================================================
# 节点函数
# ============================================================

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

    llm = llm_core.get_llm(temperature=0)  # temperature=0 让 AI 的判断更稳定，不乱猜

    # ainvoke 是"异步调用"，程序发出请求后可以去处理别的任务，等 AI 返回了再回来继续
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
    )  # 闲聊可以稍微"活泼"一点
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
    """【检索节点】调用具体的 Skill（里面整合了向量+BM25+联网）去抓数据

    根据用户角色决定检索深度：
    - normal 用户：lite 模式，仅获取基础行情，top_k=1，节省 Token
    - vip 用户：full 模式，获取完整检索+行情+新闻，top_k=3
    """
    queries = state["queries"]
    user_query = _latest_user_query(state)
    role = state.get("role", "normal")

    if role == "vip":
        # VIP 用户：完整检索模式，获取更多资料和新闻
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
            "step": "🔍 [VIP] 高级 Skill 正在编排检索与行情数据..."
        }
    else:
        # 普通用户：精简检索模式，仅获取基础行情，跳过新闻/公告
        skill_result = await stock_analysis_skill.run(
            StockAnalysisSkillInput(
                query=user_query,
                queries=queries[:1],  # 普通用户只用第一个搜索词，减少调用
                top_k=1,
            )
        )
        return {
            "knowledge": skill_result.knowledge,
            "skill_context": skill_result.to_prompt_context(),
            "step": "🔍 [基础] 正在查询基础行情数据..."
        }


async def fetch_data_node(state: AgentState):
    """【并行数据获取节点】用 asyncio.gather 同时拉取多个数据源

    对比原来的 search_node（串行调用 stock_analysis_skill）：
    - 旧：retrieval → quote → news，总耗时 = A + B + C
    - 新：asyncio.gather 同时发出，总耗时 = max(A, B, C)

    数据源：
    1. 行情数据（腾讯API）— 实时价格、涨跌幅、成交量
    2. 财务数据（东方财富Push2）— PE、营收、利润、负债率
    3. 公告数据（东方财富）— 最新公司公告
    4. 新闻数据（财新/东方财富）— 财经热点
    5. 检索结果（本地知识库/Tavily）— 相关文档
    """
    user_query = _latest_user_query(state)
    queries = state.get("queries", [user_query])
    role = state.get("role", "normal")

    # 根据角色决定检索深度
    top_k = 3 if role == "vip" else 1

    try:
        result = await fetch_all_data_parallel(
            query=user_query,
            queries=queries,
            top_k=top_k,
        )
    except Exception as e:
        logger.error("并行数据获取异常: %s", e)
        return {
            "knowledge": "",
            "skill_context": "",
            "market_data": {},
            "financial_data": {},
            "announcements": [],
            "news_data": [],
            "fetch_sources": [],
            "step": "⚠️ 并行数据获取失败，已跳过",
        }

    # 统计成功获取的数据源
    sources = []
    if result.get("market"):
        sources.append("行情")
    if result.get("financial"):
        sources.append("财务")
    if result.get("announcements"):
        sources.append("公告")
    if result.get("news"):
        sources.append("新闻")
    if result.get("retrieval"):
        sources.append("检索")

    knowledge = result.get("knowledge", "")

    # 构造 skill_context 供 answer_node 使用
    skill_context = f"【并行数据源】{', '.join(sources) if sources else '无'}"
    if result.get("market"):
        m = result["market"]
        skill_context += f"\n【实时行情】{m.get('name','')}({m.get('symbol','')}) 现价{m.get('lastPrice','')} 涨跌幅{m.get('changePercent','')}%"
    if result.get("financial"):
        f_data = result["financial"]
        skill_context += f"\n【财务指标】PE:{f_data.get('pe','')} 营收增长:{f_data.get('revenueGrowth','')}% 利润增长:{f_data.get('profitGrowth','')}%"

    return {
        "knowledge": knowledge,
        "skill_context": skill_context,
        "market_data": result.get("market") or {},
        "financial_data": result.get("financial") or {},
        "announcements": result.get("announcements") or [],
        "news_data": result.get("news") or [],
        "fetch_sources": sources,
        "step": f"✅ [并行] 数据获取完成，命中: {', '.join(sources) if sources else '无'}",
    }


async def answer_node(state: AgentState):
    """【生成节点】基于搜集到的"知识库"内容来写投研报告

    根据用户角色使用不同提示词：
    - normal 用户：使用 ANSWER_PROMPT_LITE，禁止买卖建议，只回答数据问题
    - vip 用户：使用 ANSWER_PROMPT，可给出深度分析和投资建议
    """
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
    role = state.get("role", "normal")

    if role == "vip":
        # VIP 用户：使用完整的深度分析提示词
        prompt_messages = ANSWER_PROMPT.format_messages(
            user_msg=_latest_user_query(state),
            knowledge=knowledge or "无",
            skill_context=skill_context or "无",
            feedback=feedback or "无额外修正要求",
        )
    else:
        # 普通用户：使用精简提示词，禁止买卖建议
        prompt_messages = ANSWER_PROMPT_LITE.format_messages(
            user_msg=_latest_user_query(state),
            knowledge=knowledge or "无",
            skill_context=skill_context or "无",
        )

    response = await llm.ainvoke(
        prompt_messages[:1] + state["messages"] + prompt_messages[1:]
    )

    return {
        "messages": [response],
        "total_tokens": _state_total_tokens(state, response),
        "step": "✍️ 分析师正在撰写深度报告..." if role == "vip" else "✍️ 正在生成数据查询回答..."
    }


async def critic_node(state: AgentState):
    """
    【评审节点】Agent 的"质检员"
    知识点：Self-Correction (自纠错) 架构。模拟了人类社会中的"一人做，一人审"的模型。
    """
    llm = llm_core.get_llm(temperature=0)  # 评审需要极度客观，锁定 0 温度
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


async def lite_rewrite_node(state: AgentState):
    """【精简改写节点】普通用户专用：跳过 LLM 改写，直接用原始问题作为搜索词

    节省一次 LLM 调用，降低延迟和 Token 消耗。
    """
    user_msg = _latest_user_query(state)
    # 普通用户直接用原始问题作为搜索词，不做 LLM 改写
    queries = [user_msg.strip()] if user_msg.strip() else [user_msg]
    return {
        "queries": queries,
        "step": "🧠 [基础] 使用原始问题进行检索..."
    }
