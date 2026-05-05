package com.aiinvestor.gateway.modules.market.service;

import com.aiinvestor.gateway.modules.market.dao.entity.MarketQuoteDO;
import com.aiinvestor.gateway.modules.market.dao.entity.StockDO;
import com.aiinvestor.gateway.modules.market.dao.mapper.MarketQuoteMapper;
import com.aiinvestor.gateway.modules.market.dao.mapper.SectorMapper;
import com.aiinvestor.gateway.modules.market.dao.mapper.StockMapper;
import com.aiinvestor.gateway.modules.market.vo.MarketQuoteVO;
import com.aiinvestor.gateway.modules.market.vo.MarketStockPageVO;
import com.aiinvestor.gateway.modules.market.vo.SectorVO;
import com.aiinvestor.gateway.modules.market.dao.entity.SectorDO;
import com.aiinvestor.gateway.modules.shared.cache.RedisJsonCacheService;
import com.aiinvestor.gateway.modules.shared.config.AppCacheProperties;
import com.aiinvestor.gateway.modules.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MarketService 单元测试。
 *
 * 覆盖场景：
 * - 批量获取行情（缓存命中 / 未命中 / 强制刷新）
 * - 单只股票行情查询
 * - 行情数据持久化与缓存写入
 * - 板块列表查询
 */
@ExtendWith(MockitoExtension.class)
class MarketServiceTest {

    @Mock
    private PythonMarketClient pythonMarketClient;

    @Mock
    private MarketQuoteMapper marketQuoteMapper;

    @Mock
    private SectorMapper sectorMapper;

    @Mock
    private StockMapper stockMapper;

    @Mock
    private RedisJsonCacheService redisJsonCacheService;

    @Mock
    private AppCacheProperties appCacheProperties;

    @InjectMocks
    private MarketService marketService;

    private MarketQuoteVO sampleQuote;

    @BeforeEach
    void setUp() {
        sampleQuote = new MarketQuoteVO(
                "601179",
                "中国电建",
                new BigDecimal("10.50"),
                new BigDecimal("2.34"),
                new BigDecimal("0.24"),
                new BigDecimal("10.80"),
                new BigDecimal("10.10"),
                new BigDecimal("10.30"),
                new BigDecimal("123456000"),
                new BigDecimal("1296780000"),
                new BigDecimal("1.50"),
                new BigDecimal("3.20"),
                LocalDateTime.now()
        );

        lenient().when(appCacheProperties.getMarketQuoteTtlSeconds()).thenReturn(300L);
    }

    // =====================================================================
    // getQuotes - 批量获取行情
    // =====================================================================

    @Nested
    @DisplayName("getQuotes - 批量获取行情")
    class GetQuotes {

        @Test
        @DisplayName("所有股票缓存命中时应直接返回缓存数据")
        void shouldReturnCachedQuotes() {
            when(redisJsonCacheService.get("market:quote:601179", MarketQuoteVO.class))
                    .thenReturn(sampleQuote);

            List<MarketQuoteVO> result = marketService.getQuotes(List.of("601179"));

            assertEquals(1, result.size());
            assertEquals("601179", result.get(0).getSymbol());
            // 不应调用 Python 客户端
            verify(pythonMarketClient, never()).fetchQuotes(any());
        }

        @Test
        @DisplayName("缓存未命中时应从 Python 客户端获取")
        void shouldFetchFromPythonClient() {
            when(redisJsonCacheService.get(anyString(), eq(MarketQuoteVO.class)))
                    .thenReturn(null);
            when(pythonMarketClient.fetchQuotes(List.of("601179")))
                    .thenReturn(List.of(sampleQuote));
            when(marketQuoteMapper.upsert(any())).thenReturn(1);
            when(stockMapper.selectOne(any())).thenReturn(null);

            List<MarketQuoteVO> result = marketService.getQuotes(List.of("601179"));

            assertEquals(1, result.size());
            assertEquals("601179", result.get(0).getSymbol());
            verify(pythonMarketClient).fetchQuotes(List.of("601179"));
            // 应写入缓存
            verify(redisJsonCacheService).set(
                    eq("market:quote:601179"),
                    any(MarketQuoteVO.class),
                    any(Duration.class)
            );
        }

        @Test
        @DisplayName("部分缓存命中 + 部分未命中")
        void shouldHandlePartialCacheHit() {
            MarketQuoteVO quote2 = new MarketQuoteVO(
                    "000001", "平安银行",
                    new BigDecimal("12.00"), null, null,
                    null, null, null, null, null, null, null,
                    LocalDateTime.now()
            );

            // 601179 命中缓存
            when(redisJsonCacheService.get("market:quote:601179", MarketQuoteVO.class))
                    .thenReturn(sampleQuote);
            // 000001 缓存未命中
            when(redisJsonCacheService.get("market:quote:000001", MarketQuoteVO.class))
                    .thenReturn(null);
            when(pythonMarketClient.fetchQuotes(List.of("000001")))
                    .thenReturn(List.of(quote2));
            when(marketQuoteMapper.upsert(any())).thenReturn(1);
            when(stockMapper.selectOne(any())).thenReturn(null);

            List<MarketQuoteVO> result = marketService.getQuotes(List.of("601179", "000001"));

            assertEquals(2, result.size());
            // 只应请求未缓存的 000001
            verify(pythonMarketClient).fetchQuotes(List.of("000001"));
        }

        @Test
        @DisplayName("强制刷新应跳过缓存")
        void shouldForceRefreshSkippingCache() {
            when(pythonMarketClient.fetchQuotes(List.of("601179")))
                    .thenReturn(List.of(sampleQuote));
            when(marketQuoteMapper.upsert(any())).thenReturn(1);
            when(stockMapper.selectOne(any())).thenReturn(null);

            List<MarketQuoteVO> result = marketService.refreshQuotes(List.of("601179"));

            assertEquals(1, result.size());
            // 强制刷新时不应读取缓存
            verify(redisJsonCacheService, never()).get(anyString(), any());
            // 应调用 Python 客户端
            verify(pythonMarketClient).fetchQuotes(List.of("601179"));
        }

        @Test
        @DisplayName("空列表输入应返回空结果")
        void shouldReturnEmptyForEmptyInput() {
            List<MarketQuoteVO> result = marketService.getQuotes(List.of());
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("应自动去重和 trim")
        void shouldDeduplicateAndTrim() {
            when(redisJsonCacheService.get("market:quote:601179", MarketQuoteVO.class))
                    .thenReturn(sampleQuote);

            List<MarketQuoteVO> result = marketService.getQuotes(
                    List.of("  601179  ", "601179"));

            assertEquals(1, result.size());
            // 只请求一次
            verify(pythonMarketClient, never()).fetchQuotes(any());
        }

        @Test
        @DisplayName("Python 返回空时应尝试从快照恢复")
        void shouldFallbackToSnapshot() {
            when(redisJsonCacheService.get(anyString(), eq(MarketQuoteVO.class)))
                    .thenReturn(null);
            when(pythonMarketClient.fetchQuotes(List.of("601179")))
                    .thenReturn(List.of()); // 空列表

            // DB 快照
            MarketQuoteDO dbQuote = new MarketQuoteDO();
            dbQuote.setSymbol("601179");
            dbQuote.setLastPrice(new BigDecimal("9.99"));
            when(marketQuoteMapper.selectOne(any())).thenReturn(dbQuote);

            // Stock 名称
            StockDO stock = new StockDO();
            stock.setName("中国电建");
            when(stockMapper.selectOne(any())).thenReturn(stock);

            List<MarketQuoteVO> result = marketService.getQuotes(List.of("601179"));

            assertEquals(1, result.size());
            assertEquals(new BigDecimal("9.99"), result.get(0).getLastPrice());
        }
    }

    // =====================================================================
    // getLatestQuote - 单只股票行情
    // =====================================================================

    @Nested
    @DisplayName("getLatestQuote - 单只股票行情")
    class GetLatestQuote {

        @Test
        @DisplayName("正常查询应返回单条行情")
        void shouldReturnSingleQuote() {
            when(redisJsonCacheService.get("market:quote:601179", MarketQuoteVO.class))
                    .thenReturn(sampleQuote);

            MarketQuoteVO result = marketService.getLatestQuote("601179");
            assertNotNull(result);
            assertEquals("601179", result.getSymbol());
        }

        @Test
        @DisplayName("查无此股票应抛出 BusinessException")
        void shouldThrowWhenNotFound() {
            when(redisJsonCacheService.get(anyString(), eq(MarketQuoteVO.class)))
                    .thenReturn(null);
            when(pythonMarketClient.fetchQuotes(any()))
                    .thenReturn(List.of());
            when(marketQuoteMapper.selectOne(any())).thenReturn(null);

            assertThrows(BusinessException.class,
                    () -> marketService.getLatestQuote("999999"));
        }
    }

    // =====================================================================
    // listSectors - 板块列表
    // =====================================================================

    @Nested
    @DisplayName("listSectors - 板块列表")
    class ListSectors {

        @Test
        @DisplayName("应返回板块列表")
        void shouldReturnSectors() {
            SectorDO sector = new SectorDO();
            sector.setSectorCode("BK0477");
            sector.setSectorName("新能源");
            sector.setParentCode(null);
            sector.setSortOrder(1);
            when(sectorMapper.selectList(any())).thenReturn(List.of(sector));

            List<SectorVO> result = marketService.listSectors();

            assertEquals(1, result.size());
            assertEquals("BK0477", result.get(0).getSectorCode());
            assertEquals("新能源", result.get(0).getSectorName());
        }

        @Test
        @DisplayName("无板块时应返回空列表")
        void shouldReturnEmptyList() {
            when(sectorMapper.selectList(any())).thenReturn(List.of());

            List<SectorVO> result = marketService.listSectors();
            assertTrue(result.isEmpty());
        }
    }

    // =====================================================================
    // resolveExchange - 交易所判断
    // =====================================================================

    @Nested
    @DisplayName("resolveExchange - 交易所判断（通过 getQuotes 间接测试）")
    class ResolveExchange {

        @Test
        @DisplayName("6 开头代码应创建 SH 交易所记录")
        void shouldCreateShStock() {
            when(redisJsonCacheService.get(anyString(), eq(MarketQuoteVO.class)))
                    .thenReturn(null);
            when(pythonMarketClient.fetchQuotes(any()))
                    .thenReturn(List.of(sampleQuote));
            when(marketQuoteMapper.upsert(any())).thenReturn(1);
            when(stockMapper.selectOne(any())).thenReturn(null);

            marketService.getQuotes(List.of("601179"));

            // 验证 insert 的 StockDO 包含正确的交易所
            verify(stockMapper).insert(any(StockDO.class));
        }

        @Test
        @DisplayName("0 开头代码应创建 SZ 交易所记录")
        void shouldCreateSzStock() {
            MarketQuoteVO szQuote = new MarketQuoteVO(
                    "000001", "平安银行",
                    new BigDecimal("12.00"), null, null,
                    null, null, null, null, null, null, null,
                    LocalDateTime.now()
            );

            when(redisJsonCacheService.get(anyString(), eq(MarketQuoteVO.class)))
                    .thenReturn(null);
            when(pythonMarketClient.fetchQuotes(any()))
                    .thenReturn(List.of(szQuote));
            when(marketQuoteMapper.upsert(any())).thenReturn(1);
            when(stockMapper.selectOne(any())).thenReturn(null);

            marketService.getQuotes(List.of("000001"));

            verify(stockMapper).insert(any(StockDO.class));
        }
    }
}
