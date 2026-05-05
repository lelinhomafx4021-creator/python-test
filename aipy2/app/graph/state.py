"""
AgentState 状态定义 + 辅助函数。

AgentState 是 LangGraph 的灵魂，像一个在各节点之间传递的"公文包"。
每个节点都可以打开公文包，读里面的内容，或者往里面塞新东西。
"""

import re
from typing import Annotated, TypedDict

from langgraph.graph.message import add_messages
from langchain_core.messages import HumanMessage


class AgentState(TypedDict):
    """
    【核心：Agent 的"记忆"】

    AgentState 是 LangGraph 的灵魂。它像是一个在各个节点之间传递的"公文包"。
    每个节点都可以打开这个公文包，读里面的内容，或者往里面塞新的东西。
    """
    # 所有的历史对话消息。Annotated 和 add_messages 告诉系统：新消息要追加在后面，而不是覆盖。
    messages: Annotated[list, add_messages]

    # rewrite 节点生成的搜索关键词
    queries: list[str]

    # search 节点找回来的原始文本资料
    knowledge: str

    # 当前程序运行到了哪一步（前端显示"思考过程"就靠它）
    step: str

    # 记录"打回重做"的次数。面试点：如何防止 Agent 进入死循环？答：设置计数器。
    retry_count: int

    # Critic 评审点的结论：是通过 (pass) 还是 失败 (fail)
    review_status: str

    # 如果评审失败，专家给出的具体"修改意见"
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

    # 用户角色：'normal'（普通用户）或 'vip'（VIP 用户）
    # 决定使用哪套图流程和提示词
    role: str

    # ===== 并行数据获取相关字段 =====
    # 由 fetch_data_node 填充，answer_node 读取
    # 这些字段让数据获取和回答生成解耦：
    # fetch_data_node 负责"找材料"，answer_node 负责"写报告"

    # 结构化的行情数据（来自腾讯API）
    market_data: dict
    # 结构化的财务数据（来自东方财富Push2）
    financial_data: dict
    # 最新公告列表
    announcements: list
    # 最新新闻列表
    news_data: list
    # 并行获取的数据源标识（用于前端展示"正在并行获取行情+新闻+财务..."）
    fetch_sources: list[str]


# ============================================================
# 辅助函数
# ============================================================

def _latest_user_query(state: AgentState) -> str:
    """读取最近一条"用户消息"。

    为什么要专门写这个函数：
    - state["messages"] 里既有用户消息，也有模型消息。
    - 我们做路由/改写时必须基于"用户原问题"，不能误用 AI 草稿。
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
    """粗略判断用户是否要求"长回答/详细回答"。

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
