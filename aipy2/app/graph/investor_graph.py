"""
LangGraph 工作流构建 + MultiGraphInvestorAgent 包装类。

负责根据用户角色构建不同复杂度的工作流图，并提供统一的流式调用入口。
"""

from langgraph.graph import END, START, StateGraph
from langgraph.checkpoint.memory import InMemorySaver
from langchain_core.messages import HumanMessage

import app.core.llm as llm_core

from app.graph.state import AgentState, _message_text, _content_delta_event, _stream_fallback_pieces
from app.graph.nodes import (
    route_intent_node,
    direct_answer_node,
    rewrite_node,
    search_node,
    fetch_data_node,
    answer_node,
    critic_node,
    handoff_node,
    lite_rewrite_node,
)
from app.graph.routes import route_intent, route_data_source, route_judge


def build_self_rag_graph(role: str = "normal"):
    """
    根据用户角色构建不同复杂度的工作流图。

    普通用户（role='normal'）精简流程：
        intent → lite_rewrite → search(lite) → answer(no buy/sell) → END
        跳过 critic 节点，节省 Token；answer 不给买卖建议。

    VIP 用户（role='vip'）完整流程：
        intent → rewrite → [路由] → fetch_data(并行) 或 search(旧) → answer(deep) → critic → END
        路由逻辑：VIP用户+有股票代码 → 并行获取（行情+财务+公告+新闻同时拉）
                                        → 其他情况走旧的串行skill（兼容性）
        保留完整的 Self-RAG 闭环：评审不通过可打回重写。

    面试点：LangGraph 的核心架构是什么？答：点(Nodes) + 边(Edges) + 状态(State)。
    """
    workflow = StateGraph(AgentState)

    if role == "vip":
        # ===== VIP 完整流程 =====
        # 注册所有节点（含 rewrite、critic）
        workflow.add_node("intent", route_intent_node)
        workflow.add_node("direct_answer", direct_answer_node)
        workflow.add_node("rewrite", rewrite_node)
        workflow.add_node("search", search_node)
        workflow.add_node("fetch_data", fetch_data_node)  # 新增：并行数据获取节点
        workflow.add_node("answer", answer_node)
        workflow.add_node("critic", critic_node)
        workflow.add_node("handoff", handoff_node)

        # 连线
        workflow.add_edge(START, "intent")

        # 意图识别后的分叉路：条件边
        workflow.add_conditional_edges(
            "intent",
            route_intent,
            {
                "use_kb": "rewrite",
                "no_kb": "direct_answer",
                "handoff": "handoff",
            }
        )

        # rewrite 之后根据数据源路由选择并行获取或旧skill
        workflow.add_conditional_edges(
            "rewrite",
            route_data_source,
            {
                "parallel": "fetch_data",  # VIP+股票代码 → 并行获取
                "legacy": "search",        # 其他 → 旧的串行skill
            }
        )

        # 两条路径都汇聚到 answer 节点
        workflow.add_edge("fetch_data", "answer")
        workflow.add_edge("search", "answer")
        workflow.add_edge("direct_answer", END)
        workflow.add_edge("handoff", END)
        workflow.add_edge("answer", "critic")

        # 闭环的关键：质量评审后的跳转
        workflow.add_conditional_edges(
            "critic",
            route_judge,
            {
                "retry": "rewrite",
                "handoff": "handoff",
                "end": END
            }
        )
    else:
        # ===== 普通用户精简流程 =====
        # 注册精简节点（无 rewrite、无 critic）
        workflow.add_node("intent", route_intent_node)
        workflow.add_node("direct_answer", direct_answer_node)
        workflow.add_node("rewrite", lite_rewrite_node)  # 复用 rewrite 节点名，实际走精简逻辑
        workflow.add_node("search", search_node)
        workflow.add_node("answer", answer_node)
        workflow.add_node("handoff", handoff_node)

        # 连线
        workflow.add_edge(START, "intent")

        # 意图识别后的分叉路
        workflow.add_conditional_edges(
            "intent",
            route_intent,
            {
                "use_kb": "rewrite",   # 走精简改写
                "no_kb": "direct_answer",
                "handoff": "handoff",
            }
        )

        # 普通用户精简连线：search → answer → END（无 critic）
        workflow.add_edge("direct_answer", END)
        workflow.add_edge("handoff", END)
        workflow.add_edge("rewrite", "search")
        workflow.add_edge("search", "answer")
        workflow.add_edge("answer", END)  # 直接结束，不走评审

    # 给图加上"持久化存储"，让 Agent 能记住历史对话
    active_checkpointer = llm_core.checkpointer or InMemorySaver()
    return workflow.compile(checkpointer=active_checkpointer)


class MultiGraphInvestorAgent:
    """对外提供统一的 LangGraph 调用入口（单例使用）。

    支持两种角色的图实例：
    - _graph_normal：普通用户的精简图（无 critic，省 Token）
    - _graph_vip：VIP 用户的完整图（含 rewrite + critic 闭环）
    """

    def __init__(self):
        """延迟初始化图对象，避免 import 阶段就做重型构建。"""
        self._graph_normal = None
        self._graph_vip = None

    def _get_graph(self, role: str = "normal"):
        """根据用户角色获取对应的图实例（懒加载）。"""
        if role == "vip":
            if self._graph_vip is None:
                self._graph_vip = build_self_rag_graph(role="vip")
            return self._graph_vip
        else:
            if self._graph_normal is None:
                self._graph_normal = build_self_rag_graph(role="normal")
            return self._graph_normal

    @property
    def graph(self):
        """兼容旧接口：默认返回普通用户图。"""
        return self._get_graph("normal")

    async def ask_stream_events(
        self,
        query: str,
        thread_id: str,
        role: str = "normal",
    ):
        """
        【流式接口】像水流一样一点点把 AI 的结果吐回给前端。

        根据 role 参数路由到对应的图实例：
        - role='normal'：普通用户精简流程
        - role='vip'：VIP 用户完整流程
        """
        # 根据角色获取对应图
        graph = self._get_graph(role)

        # input_data 是图执行的初始状态，等价于"第一版公文包"。
        input_data = {
            "messages": [HumanMessage(content=query)],
            "retry_count": 0,
            "critic_feedback": "",
            "total_tokens": 0,
            "role": role,  # 传递角色信息到状态中
        }
        # configurable.thread_id 决定"记忆隔离"：
        # - 同一个 thread_id：会续接上下文
        # - 不同 thread_id：视为新会话
        config = {
            "configurable": {"thread_id": thread_id},  # 线程 ID，用来区分不同的对话窗口
        }

        # 告诉前端：我收到请求了 (accepted)
        yield {"stage": "accepted", "data": {"query": query, "role": role}}

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
        async for mode, chunk in graph.astream(
            input_data,
            config=config,
            stream_mode=["updates", "messages"],
        ):
            if mode == "updates":
                # updates 是"按节点分组"的字典：{node_name: updates}
                for node_name, updates in chunk.items():
                    # 如果这个节点更新了 step 字段，我们就把它发给前端展示"思考过程"
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
                            logger.warning("流式消息处理异常", exc_info=True)

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
                "role": role,
            }
        }
        # 完结撒花
        yield {"stage": "done", "data": {"status": "success"}}


# 创建单例对象，供其他模块调用
multi_graph_agent = MultiGraphInvestorAgent()
