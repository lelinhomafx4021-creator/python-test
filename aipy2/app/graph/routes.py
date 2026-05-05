"""
路由函数 — 定义节点之间的跳转规则。

LangGraph 的条件边通过这些函数决定下一步走哪个节点。
"""

import re
from typing import Literal

from app.graph.state import AgentState, _latest_user_query


def route_intent(state: AgentState) -> Literal["use_kb", "no_kb", "handoff"]:
    """第一段路由：根据 intent 节点的输出，决定走 rewrite 还是 direct_answer"""
    if state.get("handoff_to_human"):
        return "handoff"
    return "use_kb" if state.get("use_kb", True) else "no_kb"


def route_data_source(state: AgentState) -> Literal["parallel", "legacy"]:
    """数据源路由：决定用并行获取还是旧的串行skill

    路由策略：
    - VIP用户 + 有股票代码 → 走并行获取（fetch_data_node）
    - 其他情况 → 走旧的 search_node（兼容性）

    面试点：条件边的作用是什么？
    答：让图根据运行时状态动态选择下一步，而不是写死流程。
    """
    role = state.get("role", "normal")
    user_query = _latest_user_query(state)
    has_stock_code = bool(re.search(r"(?<!\d)\d{6}(?!\d)", user_query))

    # VIP用户且问题涉及具体股票 → 走并行获取
    if role == "vip" and has_stock_code:
        return "parallel"
    # 其他情况走旧路径（保持兼容）
    return "legacy"


def route_judge(state: AgentState) -> Literal["retry", "handoff", "end"]:
    """第二段路由：根据评审结论，决定是回退重试还是直接结束"""
    if state.get("handoff_to_human") or state.get("review_status") == "handoff":
        return "handoff"
    if state.get("review_status") == "fail":
        return "retry"
    return "end"
