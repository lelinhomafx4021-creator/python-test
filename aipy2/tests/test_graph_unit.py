"""
单元测试 — app.graph 包的纯函数测试。

覆盖：
- state.py 辅助函数
- routes.py 路由函数
"""

import pytest
from unittest.mock import MagicMock
from langchain_core.messages import HumanMessage, AIMessage

from app.graph.state import (
    AgentState,
    _latest_user_query,
    _message_text,
    _normalize_query_items,
    _chunk_text,
    _token_count,
    _state_total_tokens,
    _content_delta_event,
    _stream_fallback_pieces,
    _wants_detailed_answer,
    _wants_human_handoff,
    _build_handoff_summary,
)
from app.graph.routes import route_intent, route_data_source, route_judge


# ============================================================
# state.py 辅助函数测试
# ============================================================

class TestLatestUserQuery:
    """测试 _latest_user_query：从消息列表中提取最近的用户消息。"""

    def test_extracts_last_human_message(self):
        state = {
            "messages": [
                HumanMessage(content="你好"),
                AIMessage(content="你好！有什么可以帮你？"),
                HumanMessage(content="帮我分析宁德时代"),
            ]
        }
        assert _latest_user_query(state) == "帮我分析宁德时代"

    def test_returns_empty_when_no_human_message(self):
        state = {"messages": [AIMessage(content="你好！")]}
        assert _latest_user_query(state) == ""

    def test_returns_empty_when_no_messages(self):
        state = {"messages": []}
        assert _latest_user_query(state) == ""


class TestMessageText:
    """测试 _message_text：统一提取消息文本。"""

    def test_string_content(self):
        msg = MagicMock(content="hello")
        assert _message_text(msg) == "hello"

    def test_list_content_with_text_dicts(self):
        msg = MagicMock(content=[{"type": "text", "text": "hello"}, {"type": "text", "text": "world"}])
        assert _message_text(msg) == "hello\nworld"

    def test_list_content_with_strings(self):
        msg = MagicMock(content=["hello", "world"])
        assert _message_text(msg) == "hello\nworld"

    def test_plain_string(self):
        assert _message_text("raw string") == "raw string"

    def test_empty_content(self):
        msg = MagicMock(content=[])
        assert _message_text(msg) == ""


class TestNormalizeQueryItems:
    """测试 _normalize_query_items：清洗检索词。"""

    def test_removes_numbering(self):
        result = _normalize_query_items(["1. 宁德时代", "2. 比亚迪", "3. 赣锋锂业"])
        assert result == ["宁德时代", "比亚迪", "赣锋锂业"]

    def test_deduplicates(self):
        result = _normalize_query_items(["宁德时代", "宁德时代", "比亚迪"])
        assert result == ["宁德时代", "比亚迪"]

    def test_limits_to_3(self):
        result = _normalize_query_items(["A", "B", "C", "D", "E"])
        assert result == ["A", "B", "C"]

    def test_removes_empty(self):
        result = _normalize_query_items(["", "  ", "宁德时代"])
        assert result == ["宁德时代"]


class TestChunkText:
    """测试 _chunk_text：文本分片。"""

    def test_basic_chunking(self):
        assert _chunk_text("abcdefgh", 3) == ["abc", "def", "gh"]

    def test_empty_text(self):
        assert _chunk_text("") == []

    def test_shorter_than_chunk(self):
        assert _chunk_text("abc", 10) == ["abc"]


class TestTokenCount:
    """测试 _token_count：提取 token 用量。"""

    def test_extracts_total_tokens(self):
        resp = MagicMock()
        resp.response_metadata = {"token_usage": {"total_tokens": 42}}
        assert _token_count(resp) == 42

    def test_missing_usage_returns_zero(self):
        resp = MagicMock()
        resp.response_metadata = {}
        assert _token_count(resp) == 0


class TestStateTotalTokens:
    """测试 _state_total_tokens：累加 token 用量。"""

    def test_accumulates_tokens(self):
        state = {"total_tokens": 100}
        resp = MagicMock()
        resp.response_metadata = {"token_usage": {"total_tokens": 50}}
        assert _state_total_tokens(state, resp) == 150

    def test_handles_missing_total_tokens(self):
        state = {}
        resp = MagicMock()
        resp.response_metadata = {"token_usage": {"total_tokens": 30}}
        assert _state_total_tokens(state, resp) == 30


class TestContentDeltaEvent:
    """测试 _content_delta_event：构造流式增量事件。"""

    def test_structure(self):
        event = _content_delta_event("answer", "hello")
        assert event == {"stage": "content_delta", "data": {"node": "answer", "delta": "hello"}}


class TestStreamFallbackPieces:
    """测试 _stream_fallback_pieces：流式兜底拆分。"""

    def test_returns_new_delta_only(self):
        existing = ["hello"]
        result = _stream_fallback_pieces(existing, "hello world")
        assert result == [" world"]

    def test_handles_non_prefix(self):
        existing = ["abc"]
        result = _stream_fallback_pieces(existing, "xyz")
        assert result == ["xyz"]


class TestWantsDetailedAnswer:
    """测试 _wants_detailed_answer：判断是否要求详细回答。"""

    def test_detects_character_count(self):
        assert _wants_detailed_answer("帮我写一篇500字的分析") is True

    def test_detects_keywords(self):
        assert _wants_detailed_answer("请详细分析一下") is True
        assert _wants_detailed_answer("展开说说") is True
        assert _wants_detailed_answer("分点回答") is True

    def test_normal_query_returns_false(self):
        assert _wants_detailed_answer("宁德时代怎么样") is False


class TestWantsHumanHandoff:
    """测试 _wants_human_handoff：判断是否要求转人工。"""

    def test_detects_handoff_keywords(self):
        assert _wants_human_handoff("转人工") is True
        assert _wants_human_handoff("我要投诉") is True
        assert _wants_human_handoff("找客服") is True

    def test_normal_query_returns_false(self):
        assert _wants_human_handoff("宁德时代怎么样") is False


class TestBuildHandoffSummary:
    """测试 _build_handoff_summary：生成交接摘要。"""

    def test_basic_summary(self):
        state = {
            "messages": [HumanMessage(content="帮我分析茅台")],
            "retry_count": 0,
            "review_status": "",
            "critic_feedback": "",
        }
        summary = _build_handoff_summary(state, "test_reason")
        assert "用户问题：帮我分析茅台" in summary
        assert "转人工原因：test_reason" in summary

    def test_includes_retry_and_feedback(self):
        state = {
            "messages": [HumanMessage(content="问题")],
            "retry_count": 3,
            "review_status": "fail",
            "critic_feedback": "信息不足",
        }
        summary = _build_handoff_summary(state, "reason")
        assert "当前重试次数：3" in summary
        assert "当前评审状态：fail" in summary
        assert "评审反馈：信息不足" in summary


# ============================================================
# routes.py 路由函数测试
# ============================================================

class TestRouteIntent:
    """测试 route_intent：意图路由。"""

    def test_use_kb_when_enabled(self):
        state = {"use_kb": True}
        assert route_intent(state) == "use_kb"

    def test_no_kb_when_disabled(self):
        state = {"use_kb": False}
        assert route_intent(state) == "no_kb"

    def test_handoff_when_requested(self):
        state = {"use_kb": True, "handoff_to_human": True}
        assert route_intent(state) == "handoff"

    def test_defaults_to_use_kb(self):
        state = {}
        assert route_intent(state) == "use_kb"


class TestRouteDataSource:
    """测试 route_data_source：数据源路由。"""

    def test_parallel_for_vip_with_stock_code(self):
        state = {
            "role": "vip",
            "messages": [HumanMessage(content="分析600519")],
        }
        assert route_data_source(state) == "parallel"

    def test_legacy_for_normal_user(self):
        state = {
            "role": "normal",
            "messages": [HumanMessage(content="分析600519")],
        }
        assert route_data_source(state) == "legacy"

    def test_legacy_for_vip_without_stock_code(self):
        state = {
            "role": "vip",
            "messages": [HumanMessage(content="分析茅台")],
        }
        assert route_data_source(state) == "legacy"


class TestRouteJudge:
    """测试 route_judge：评审路由。"""

    def test_end_on_pass(self):
        state = {"review_status": "pass"}
        assert route_judge(state) == "end"

    def test_retry_on_fail(self):
        state = {"review_status": "fail"}
        assert route_judge(state) == "retry"

    def test_handoff_on_handoff(self):
        state = {"review_status": "handoff"}
        assert route_judge(state) == "handoff"

    def test_handoff_when_human_requested(self):
        state = {"handoff_to_human": True, "review_status": "pass"}
        assert route_judge(state) == "handoff"


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
