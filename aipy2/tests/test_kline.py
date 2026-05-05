"""
K 线接口单元测试。

测试范围：
- /api/v1/kline 接口的正常流程、参数校验、缓存逻辑
- 内部辅助函数：_build_market_code、_parse_kline_data
- 外部 API 调用 mock，不依赖网络

运行方式：
    cd /mnt/d/ai-investor/aipy2
    python -m pytest tests/test_kline.py -v
"""

import json
import time
from unittest.mock import MagicMock, patch

import pytest

from app.api.v1.kline import (
    _build_market_code,
    _cache_get,
    _cache_set,
    _get_cache_key,
    _memory_cache,
    _parse_kline_data,
    CACHE_TTL_SECONDS,
)


# ===========================================================================
# 辅助函数单元测试
# ===========================================================================

class TestBuildMarketCode:
    """测试 _build_market_code 股票代码 -> 市场代码转换。"""

    def test_shanghai_6_prefix(self):
        """以 6 开头的代码 -> 上海市场。"""
        assert _build_market_code("601179") == "sh601179"

    def test_shanghai_5_prefix(self):
        """以 5 开头的代码（ETF）-> 上海市场。"""
        assert _build_market_code("510300") == "sh510300"

    def test_shanghai_9_prefix(self):
        """以 9 开头的代码（B股）-> 上海市场。"""
        assert _build_market_code("900901") == "sh900901"

    def test_shenzhen_0_prefix(self):
        """以 0 开头的代码 -> 深圳市场。"""
        assert _build_market_code("000001") == "sz000001"

    def test_shenzhen_3_prefix(self):
        """以 3 开头的代码（创业板）-> 深圳市场。"""
        assert _build_market_code("300750") == "sz300750"

    def test_shenzhen_2_prefix(self):
        """以 2 开头的代码 -> 深圳市场。"""
        assert _build_market_code("200001") == "sz200001"

    def test_strips_whitespace(self):
        """输入包含前后空格时应自动 trim。"""
        assert _build_market_code("  601179  ") == "sh601179"

    def test_invalid_length_raises(self):
        """非 6 位数字代码应抛 ValueError。"""
        with pytest.raises(ValueError, match="6 位数字"):
            _build_market_code("60117")

    def test_non_digit_raises(self):
        """包含字母的代码应抛 ValueError。"""
        with pytest.raises(ValueError, match="6 位数字"):
            _build_market_code("60AB79")

    def test_empty_string_raises(self):
        """空字符串应抛 ValueError。"""
        with pytest.raises(ValueError, match="6 位数字"):
            _build_market_code("")


class TestParseKlineData:
    """测试 _parse_kline_data 腾讯原始数据解析。"""

    def test_normal_data(self):
        """正常 6 字段数据解析为标准 OHLCV。"""
        raw = [
            ["2026-01-10", "10.50", "11.20", "11.50", "10.30", 123456],
            ["2026-01-11", "11.20", "10.80", "11.30", "10.70", 98765],
        ]
        result = _parse_kline_data(raw, "601179")
        assert len(result) == 2
        assert result[0]["date"] == "2026-01-10"
        assert result[0]["open"] == 10.50
        assert result[0]["close"] == 11.20
        assert result[0]["high"] == 11.50
        assert result[0]["low"] == 10.30
        assert result[0]["volume"] == 123456
        assert result[0]["symbol"] == "601179"

    def test_skip_short_row(self):
        """少于 6 个字段的行应被跳过。"""
        raw = [
            ["2026-01-10", "10.50", "11.20", "11.50", "10.30", 123456],
            ["2026-01-11", "11.20"],  # 不足 6 字段
        ]
        result = _parse_kline_data(raw, "601179")
        assert len(result) == 1

    def test_skip_zero_open_price(self):
        """开盘价为 0 的记录应被跳过。"""
        raw = [
            ["2026-01-10", "0", "0", "0", "0", 0],
            ["2026-01-11", "11.20", "10.80", "11.30", "10.70", 98765],
        ]
        result = _parse_kline_data(raw, "601179")
        assert len(result) == 1
        assert result[0]["date"] == "2026-01-11"

    def test_skip_none_open_price(self):
        """开盘价为 None 的记录应被跳过。"""
        raw = [
            [None, None, None, None, None, None],
            ["2026-01-11", "11.20", "10.80", "11.30", "10.70", 98765],
        ]
        result = _parse_kline_data(raw, "601179")
        assert len(result) == 1

    def test_empty_input(self):
        """空数据列表应返回空结果。"""
        result = _parse_kline_data([], "601179")
        assert result == []

    def test_volume_as_float_string(self):
        """成交量以浮点数字符串表示时应正确转换。"""
        raw = [
            ["2026-01-10", "10.50", "11.20", "11.50", "10.30", "123456.0"],
        ]
        result = _parse_kline_data(raw, "601179")
        assert result[0]["volume"] == 123456

    def test_price_rounding(self):
        """价格应四舍五入到 2 位小数。"""
        raw = [
            ["2026-01-10", "10.556", "11.234", "11.589", "10.301", 100],
        ]
        result = _parse_kline_data(raw, "601179")
        assert result[0]["open"] == 10.56
        assert result[0]["close"] == 11.23
        assert result[0]["high"] == 11.59
        assert result[0]["low"] == 10.30


# ===========================================================================
# 内存缓存单元测试
# ===========================================================================

class TestMemoryCache:
    """测试内存缓存的读写与过期逻辑。"""

    def setup_method(self):
        """每个测试前清空缓存。"""
        _memory_cache.clear()

    def test_cache_set_and_get(self):
        """写入后应能读取到相同数据。"""
        key = _get_cache_key("601179", "day", 120)
        _cache_set(key, [{"date": "2026-01-10", "close": 11.2}])
        result = _cache_get(key)
        assert result is not None
        assert result[0]["close"] == 11.2

    def test_cache_miss_returns_none(self):
        """查询不存在的 key 应返回 None。"""
        result = _cache_get("non_existent_key")
        assert result is None

    def test_cache_expiration(self):
        """缓存过期后应返回 None。"""
        key = _get_cache_key("601179", "day", 120)
        # 手动写入一个已过期的缓存条目
        _memory_cache[key] = (time.time() - CACHE_TTL_SECONDS - 1, [{"fake": True}])
        result = _cache_get(key)
        assert result is None
        # 过期条目应被清理
        assert key not in _memory_cache

    def test_cache_key_consistency(self):
        """相同参数应生成相同的缓存键。"""
        k1 = _get_cache_key("601179", "day", 120)
        k2 = _get_cache_key("601179", "day", 120)
        assert k1 == k2

    def test_cache_key_different_params(self):
        """不同参数应生成不同的缓存键。"""
        k1 = _get_cache_key("601179", "day", 120)
        k2 = _get_cache_key("601179", "week", 120)
        assert k1 != k2


# ===========================================================================
# API 接口测试（使用 httpx AsyncClient）
# ===========================================================================

@pytest.mark.asyncio
class TestKlineAPI:
    """K 线 HTTP 接口测试。"""

    async def test_missing_symbol_returns_422(self, client):
        """缺少 symbol 参数应返回 422。"""
        resp = await client.get("/api/v1/kline")
        assert resp.status_code == 422

    async def test_invalid_symbol_length_returns_400(self, client):
        """非 6 位代码应返回 400。"""
        resp = await client.get("/api/v1/kline?symbol=12345")
        assert resp.status_code == 400
        body = resp.json()
        assert "6 位" in body["detail"]

    async def test_invalid_period_returns_400(self, client):
        """不支持的周期应返回 400。"""
        resp = await client.get("/api/v1/kline?symbol=601179&period=invalid")
        assert resp.status_code == 400

    async def test_success_with_mock(self, client, mock_tencent_response_success):
        """正常请求应返回 200 + K 线数据。"""
        with patch("app.api.v1.kline.requests.get") as mock_get:
            mock_resp = MagicMock()
            mock_resp.json.return_value = mock_tencent_response_success
            mock_resp.raise_for_status = MagicMock()
            mock_get.return_value = mock_resp

            # 清空内存缓存，确保走 mock 请求
            _memory_cache.clear()

            resp = await client.get("/api/v1/kline?symbol=601179&period=daily&days=3")
            assert resp.status_code == 200
            body = resp.json()
            assert body["code"] == 200
            assert body["data"]["symbol"] == "601179"
            assert body["data"]["count"] == 3
            assert body["data"]["cached"] is False
            assert len(body["data"]["items"]) == 3
            # 验证第一个 item 结构
            item = body["data"]["items"][0]
            assert "date" in item
            assert "open" in item
            assert "close" in item
            assert "high" in item
            assert "low" in item
            assert "volume" in item

    async def test_timeout_returns_504(self, client):
        """腾讯接口超时应返回 504。"""
        import requests as req_lib
        with patch("app.api.v1.kline.requests.get") as mock_get:
            mock_get.side_effect = req_lib.Timeout("timed out")
            _memory_cache.clear()
            resp = await client.get("/api/v1/kline?symbol=601179&period=daily&days=3")
            assert resp.status_code == 504
            assert "超时" in resp.json()["detail"]

    async def test_empty_data_returns_404(self, client, mock_tencent_response_empty):
        """腾讯接口返回空数据应返回 404。"""
        with patch("app.api.v1.kline.requests.get") as mock_get:
            mock_resp = MagicMock()
            mock_resp.json.return_value = mock_tencent_response_empty
            mock_resp.raise_for_status = MagicMock()
            mock_get.return_value = mock_resp
            _memory_cache.clear()
            resp = await client.get("/api/v1/kline?symbol=000001&period=daily&days=3")
            assert resp.status_code == 404

    async def test_cache_hit(self, client, mock_tencent_response_success):
        """第二次相同请求应命中缓存。"""
        _memory_cache.clear()

        with patch("app.api.v1.kline.requests.get") as mock_get:
            mock_resp = MagicMock()
            mock_resp.json.return_value = mock_tencent_response_success
            mock_resp.raise_for_status = MagicMock()
            mock_get.return_value = mock_resp

            # 第一次请求
            resp1 = await client.get("/api/v1/kline?symbol=601179&period=daily&days=3")
            assert resp1.status_code == 200
            assert resp1.json()["data"]["cached"] is False
            # requests.get 应被调用
            assert mock_get.call_count == 1

            # 第二次请求 - 应命中缓存
            resp2 = await client.get("/api/v1/kline?symbol=601179&period=daily&days=3")
            assert resp2.status_code == 200
            assert resp2.json()["data"]["cached"] is True
            # requests.get 不应再被调用
            assert mock_get.call_count == 1

    async def test_weekly_period(self, client, mock_tencent_response_success):
        """weekly 周期参数应正确映射。"""
        _memory_cache.clear()

        with patch("app.api.v1.kline.requests.get") as mock_get:
            mock_resp = MagicMock()
            mock_resp.json.return_value = mock_tencent_response_success
            mock_resp.raise_for_status = MagicMock()
            mock_get.return_value = mock_resp

            resp = await client.get("/api/v1/kline?symbol=601179&period=weekly&days=10")
            assert resp.status_code == 200
            # 验证传给腾讯的 param 包含 week
            call_args = mock_get.call_args
            assert "week" in call_args.kwargs.get("params", {}).get("param", "")
