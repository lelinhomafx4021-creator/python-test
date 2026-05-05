"""
Chat 接口单元测试。

测试范围：
- /ai/v1/chat 同步接口：正常返回、空回答、trace_id 生成
- /ai/v1/chat/stream 流式接口：SSE 格式、事件透传
- 请求体校验（缺少必填字段等）
- 外部 InvestorService mock，不依赖 LLM

运行方式：
    cd /mnt/d/ai-investor/aipy2
    python -m pytest tests/test_chat.py -v
"""

import json
from unittest.mock import AsyncMock, patch

import pytest


@pytest.mark.asyncio
class TestChatSyncAPI:
    """同步聊天接口测试。"""

    async def test_missing_message_returns_422(self, client):
        """缺少 message 字段应返回 422。"""
        resp = await client.post(
            "/ai/v1/chat",
            json={"thread_id": "t1"},
        )
        assert resp.status_code == 422

    async def test_missing_thread_id_returns_422(self, client):
        """缺少 thread_id 字段应返回 422。"""
        resp = await client.post(
            "/ai/v1/chat",
            json={"message": "你好"},
        )
        assert resp.status_code == 422

    async def test_empty_message_returns_422(self, client):
        """空消息应返回 422（min_length=1）。"""
        resp = await client.post(
            "/ai/v1/chat",
            json={"message": "", "thread_id": "t1"},
        )
        assert resp.status_code == 422

    async def test_success_normal_response(
        self, client, mock_investor_flow_events, sample_chat_request_body
    ):
        """正常请求应返回 trace_id、answer、source。"""
        with patch(
            "app.api.v1.chat.investor_service"
        ) as mock_service:
            mock_service.run_investor_flow = mock_investor_flow_events
            resp = await client.post("/ai/v1/chat", json=sample_chat_request_body)
            assert resp.status_code == 200
            body = resp.json()
            # 验证响应结构
            assert "trace_id" in body
            assert body["trace_id"]  # 非空
            assert "answer" in body
            assert len(body["answer"]) > 0
            assert "source" in body

    async def test_trace_id_from_request(
        self, client, mock_investor_flow_events, sample_chat_request_body
    ):
        """上游传入 trace_id 时应原样返回。"""
        with patch(
            "app.api.v1.chat.investor_service"
        ) as mock_service:
            mock_service.run_investor_flow = mock_investor_flow_events
            sample_chat_request_body["trace_id"] = "my-custom-trace-123"
            resp = await client.post("/ai/v1/chat", json=sample_chat_request_body)
            assert resp.status_code == 200
            assert resp.json()["trace_id"] == "my-custom-trace-123"

    async def test_auto_generated_trace_id(
        self, client, mock_investor_flow_events, sample_chat_request_body
    ):
        """未传 trace_id 时应自动生成 UUID 格式。"""
        with patch(
            "app.api.v1.chat.investor_service"
        ) as mock_service:
            mock_service.run_investor_flow = mock_investor_flow_events
            resp = await client.post("/ai/v1/chat", json=sample_chat_request_body)
            assert resp.status_code == 200
            trace_id = resp.json()["trace_id"]
            # UUID 格式：8-4-4-4-12
            assert len(trace_id) == 36
            assert trace_id.count("-") == 4

    async def test_empty_flow_returns_empty_answer(self, client, sample_chat_request_body):
        """Agent 返回空事件流时应返回空 answer。"""
        async def empty_flow(*args, **kwargs):
            return
            yield  # 使其成为 async generator

        with patch(
            "app.api.v1.chat.investor_service"
        ) as mock_service:
            mock_service.run_investor_flow = empty_flow
            resp = await client.post("/ai/v1/chat", json=sample_chat_request_body)
            assert resp.status_code == 200
            body = resp.json()
            assert body["answer"] == ""

    async def test_role_forwarded(
        self, client, mock_investor_flow_events, sample_chat_request_body
    ):
        """role 参数应被正确转发到 InvestorService。"""
        with patch(
            "app.api.v1.chat.investor_service"
        ) as mock_service:

            async def capturing_flow(query, thread_id, trace_id, role="normal"):
                # 记录传入的 role
                capturing_flow.last_role = role
                async for evt in mock_investor_flow_events(query, thread_id, trace_id, role):
                    yield evt

            capturing_flow.last_role = None
            mock_service.run_investor_flow = capturing_flow

            sample_chat_request_body["role"] = "vip"
            resp = await client.post("/ai/v1/chat", json=sample_chat_request_body)
            assert resp.status_code == 200
            assert capturing_flow.last_role == "vip"


@pytest.mark.asyncio
class TestChatStreamAPI:
    """SSE 流式聊天接口测试。"""

    async def test_returns_sse_content_type(
        self, client, mock_investor_flow_events, sample_chat_request_body
    ):
        """流式接口应返回 text/event-stream。"""
        with patch(
            "app.api.v1.chat.investor_service"
        ) as mock_service:
            mock_service.run_investor_flow = mock_investor_flow_events
            resp = await client.post("/ai/v1/chat/stream", json=sample_chat_request_body)
            assert resp.status_code == 200
            content_type = resp.headers.get("content-type", "")
            assert "text/event-stream" in content_type

    async def test_sse_headers(
        self, client, mock_investor_flow_events, sample_chat_request_body
    ):
        """流式接口应设置正确的 SSE 响应头。"""
        with patch(
            "app.api.v1.chat.investor_service"
        ) as mock_service:
            mock_service.run_investor_flow = mock_investor_flow_events
            resp = await client.post("/ai/v1/chat/stream", json=sample_chat_request_body)
            assert resp.headers.get("cache-control") == "no-cache, no-transform"
            assert resp.headers.get("x-accel-buffering") == "no"

    async def test_sse_event_format(
        self, client, mock_investor_flow_events, sample_chat_request_body
    ):
        """SSE 输出应符合 event/data 格式规范。"""
        with patch(
            "app.api.v1.chat.investor_service"
        ) as mock_service:
            mock_service.run_investor_flow = mock_investor_flow_events
            resp = await client.post("/ai/v1/chat/stream", json=sample_chat_request_body)
            text = resp.text

            # 应包含多条 event: message 行
            assert "event: message" in text
            # 每个 event 后应有 data: 行
            lines = text.strip().split("\n")
            event_lines = [l for l in lines if l.startswith("event: ")]
            data_lines = [l for l in lines if l.startswith("data: ")]
            assert len(event_lines) > 0
            assert len(data_lines) > 0
            assert len(event_lines) == len(data_lines)

    async def test_sse_data_is_valid_json(
        self, client, mock_investor_flow_events, sample_chat_request_body
    ):
        """每条 SSE data 应是合法 JSON。"""
        with patch(
            "app.api.v1.chat.investor_service"
        ) as mock_service:
            mock_service.run_investor_flow = mock_investor_flow_events
            resp = await client.post("/ai/v1/chat/stream", json=sample_chat_request_body)
            lines = resp.text.strip().split("\n")
            data_lines = [l for l in lines if l.startswith("data: ")]
            for data_line in data_lines:
                payload = data_line[len("data: "):]
                parsed = json.loads(payload)
                assert "stage" in parsed
                assert "data" in parsed

    async def test_stream_error_wraps_in_event(self, client, sample_chat_request_body):
        """Agent 异常应被包装为 error 事件，不中断流。"""
        async def failing_flow(*args, **kwargs):
            raise RuntimeError("模拟 LLM 调用失败")
            yield  # noqa

        with patch(
            "app.api.v1.chat.investor_service"
        ) as mock_service:
            mock_service.run_investor_flow = failing_flow
            resp = await client.post("/ai/v1/chat/stream", json=sample_chat_request_body)
            # 流式接口不应抛 500，而应正常返回 SSE 流
            assert resp.status_code == 200
            # 应包含 error 事件
            text = resp.text
            assert "error" in text
            # 解析 error payload
            data_lines = [l for l in text.strip().split("\n") if l.startswith("data: ")]
            assert len(data_lines) >= 1
            error_payload = json.loads(data_lines[0][len("data: "):])
            assert error_payload["stage"] == "error"
