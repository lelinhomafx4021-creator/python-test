package com.aiinvestor.gateway.modules.watchlist.service;

import com.aiinvestor.gateway.modules.market.service.MarketService;
import com.aiinvestor.gateway.modules.market.vo.MarketQuoteVO;
import com.aiinvestor.gateway.modules.membership.service.MembershipService;
import com.aiinvestor.gateway.modules.shared.exception.BusinessException;
import com.aiinvestor.gateway.modules.watchlist.dao.entity.WatchlistDO;
import com.aiinvestor.gateway.modules.watchlist.dao.entity.WatchlistItemDO;
import com.aiinvestor.gateway.modules.watchlist.dao.mapper.WatchlistItemMapper;
import com.aiinvestor.gateway.modules.watchlist.dao.mapper.WatchlistMapper;
import com.aiinvestor.gateway.modules.watchlist.dto.AddWatchlistItemRequest;
import com.aiinvestor.gateway.modules.watchlist.dto.CreateWatchlistRequest;
import com.aiinvestor.gateway.modules.watchlist.vo.WatchlistVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * WatchlistService 单元测试。
 *
 * 覆盖场景：
 * - 获取自选分组（首次自动创建默认分组、正常返回）
 * - 创建自选分组（正常 / 超配额）
 * - 添加自选股（正常 / 重复添加）
 * - 删除自选股（正常 / 不存在 / 非所属分组）
 */
@ExtendWith(MockitoExtension.class)
class WatchlistServiceTest {

    @Mock
    private WatchlistMapper watchlistMapper;

    @Mock
    private WatchlistItemMapper watchlistItemMapper;

    @Mock
    private MembershipService membershipService;

    @Mock
    private MarketService marketService;

    @InjectMocks
    private WatchlistService watchlistService;

    private WatchlistDO sampleWatchlist;

    @BeforeEach
    void setUp() {
        sampleWatchlist = new WatchlistDO();
        sampleWatchlist.setId(1L);
        sampleWatchlist.setUserId(100L);
        sampleWatchlist.setName("我的自选");
        sampleWatchlist.setIsDefault(true);
        sampleWatchlist.setSortOrder(1);
    }

    // =====================================================================
    // listWatchlists - 获取自选分组
    // =====================================================================

    @Nested
    @DisplayName("listWatchlists - 获取自选分组")
    class ListWatchlists {

        @Test
        @DisplayName("有自选分组时应正常返回")
        void shouldReturnWatchlists() {
            when(watchlistMapper.selectList(any())).thenReturn(List.of(sampleWatchlist));
            when(watchlistItemMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(marketService.getQuotes(any())).thenReturn(Collections.emptyList());

            List<WatchlistVO> result = watchlistService.listWatchlists(100L, "normal");

            assertEquals(1, result.size());
            assertEquals("我的自选", result.get(0).getName());
            assertTrue(result.get(0).getIsDefault());
        }

        @Test
        @DisplayName("无自选分组时应自动创建默认分组")
        void shouldCreateDefaultWatchlistWhenEmpty() {
            // 第一次查询返回空 -> 创建默认分组 -> 第二次查询返回结果
            when(watchlistMapper.selectList(any()))
                    .thenReturn(Collections.emptyList())
                    .thenReturn(List.of(sampleWatchlist));
            when(membershipService.getQuotaLimit(100L, "normal", "watchlist_count"))
                    .thenReturn(5);
            when(watchlistItemMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(marketService.getQuotes(any())).thenReturn(Collections.emptyList());

            List<WatchlistVO> result = watchlistService.listWatchlists(100L, "normal");

            assertEquals(1, result.size());
            // 验证默认分组创建
            verify(watchlistMapper).insert(any(WatchlistDO.class));
            // 验证配额同步
            verify(membershipService).syncPermanentQuota(100L, "normal", "watchlist_count", 1);
        }

        @Test
        @DisplayName("配额为 0 时应抛出 BusinessException")
        void shouldThrowWhenQuotaExhausted() {
            when(watchlistMapper.selectList(any()))
                    .thenReturn(Collections.emptyList());
            when(membershipService.getQuotaLimit(100L, "normal", "watchlist_count"))
                    .thenReturn(0);

            assertThrows(BusinessException.class,
                    () -> watchlistService.listWatchlists(100L, "normal"));
        }

        @Test
        @DisplayName("分组内股票应附带行情数据")
        void shouldEnrichItemsWithQuotes() {
            WatchlistItemDO item = new WatchlistItemDO();
            item.setId(10L);
            item.setWatchlistId(1L);
            item.setSymbol("601179");
            item.setNote("重点观察");
            item.setAlertEnabled(true);
            item.setSortOrder(1);

            MarketQuoteVO quote = new MarketQuoteVO(
                    "601179", "中国电建",
                    new BigDecimal("10.50"), new BigDecimal("2.34"),
                    null, null, null, null, null, null, null, null,
                    LocalDateTime.now()
            );

            when(watchlistMapper.selectList(any())).thenReturn(List.of(sampleWatchlist));
            when(watchlistItemMapper.selectList(any())).thenReturn(List.of(item));
            when(marketService.getQuotes(List.of("601179"))).thenReturn(List.of(quote));

            List<WatchlistVO> result = watchlistService.listWatchlists(100L, "normal");

            assertEquals(1, result.size());
            assertEquals(1, result.get(0).getItems().size());
            assertEquals("601179", result.get(0).getItems().get(0).getSymbol());
            assertEquals("中国电建", result.get(0).getItems().get(0).getName());
            assertEquals(new BigDecimal("10.50"), result.get(0).getItems().get(0).getLastPrice());
        }
    }

    // =====================================================================
    // createWatchlist - 创建自选分组
    // =====================================================================

    @Nested
    @DisplayName("createWatchlist - 创建自选分组")
    class CreateWatchlist {

        @Test
        @DisplayName("正常创建应返回新分组")
        void shouldCreateWatchlist() {
            when(membershipService.getQuotaLimit(100L, "normal", "watchlist_count"))
                    .thenReturn(5);
            when(watchlistMapper.selectCount(any())).thenReturn(0L);
            when(watchlistMapper.insert(any(WatchlistDO.class))).thenReturn(1);

            CreateWatchlistRequest request = new CreateWatchlistRequest();
            request.setName("科技股");

            WatchlistVO result = watchlistService.createWatchlist(100L, "normal", request);

            assertNotNull(result);
            assertEquals("科技股", result.getName());
            assertFalse(result.getIsDefault());
            verify(watchlistMapper).insert(any(WatchlistDO.class));
            verify(membershipService).syncPermanentQuota(100L, "normal", "watchlist_count", 1);
        }

        @Test
        @DisplayName("达到配额上限应抛出 BusinessException")
        void shouldThrowWhenQuotaExceeded() {
            when(membershipService.getQuotaLimit(100L, "normal", "watchlist_count"))
                    .thenReturn(3);
            when(watchlistMapper.selectCount(any())).thenReturn(3L);

            CreateWatchlistRequest request = new CreateWatchlistRequest();
            request.setName("新分组");

            assertThrows(BusinessException.class,
                    () -> watchlistService.createWatchlist(100L, "normal", request));
            verify(watchlistMapper, never()).insert(any(WatchlistDO.class));
        }

        @Test
        @DisplayName("分组名应被 trim")
        void shouldTrimName() {
            when(membershipService.getQuotaLimit(100L, "normal", "watchlist_count"))
                    .thenReturn(5);
            when(watchlistMapper.selectCount(any())).thenReturn(0L);
            when(watchlistMapper.insert(any(WatchlistDO.class))).thenReturn(1);

            CreateWatchlistRequest request = new CreateWatchlistRequest();
            request.setName("  科技股  ");

            WatchlistVO result = watchlistService.createWatchlist(100L, "normal", request);

            assertEquals("科技股", result.getName());
        }
    }

    // =====================================================================
    // addItem - 向分组添加股票
    // =====================================================================

    @Nested
    @DisplayName("addItem - 向分组添加股票")
    class AddItem {

        @Test
        @DisplayName("正常添加应创建条目")
        void shouldAddItem() {
            when(watchlistMapper.selectById(1L)).thenReturn(sampleWatchlist);
            when(watchlistItemMapper.selectCount(any())).thenReturn(0L);
            when(watchlistItemMapper.insert(any(WatchlistItemDO.class))).thenReturn(1);

            AddWatchlistItemRequest request = new AddWatchlistItemRequest();
            request.setSymbol("601179");
            request.setNote("观察仓");
            request.setAlertEnabled(true);

            watchlistService.addItem(100L, 1L, request);

            verify(watchlistItemMapper).insert(argThat((WatchlistItemDO item) ->
                    "601179".equals(item.getSymbol())
                            && "观察仓".equals(item.getNote())
                            && Boolean.TRUE.equals(item.getAlertEnabled())
                            && item.getSortOrder() == 1
            ));
        }

        @Test
        @DisplayName("重复添加相同股票应抛出 BusinessException")
        void shouldThrowWhenDuplicateSymbol() {
            when(watchlistMapper.selectById(1L)).thenReturn(sampleWatchlist);
            // 已存在 1 条
            WatchlistItemDO existingItem = new WatchlistItemDO();
            existingItem.setSymbol("601179");
            when(watchlistItemMapper.selectCount(any())).thenReturn(1L);

            AddWatchlistItemRequest request = new AddWatchlistItemRequest();
            request.setSymbol("601179");

            assertThrows(BusinessException.class,
                    () -> watchlistService.addItem(100L, 1L, request));
            verify(watchlistItemMapper, never()).insert(any(WatchlistItemDO.class));
        }

        @Test
        @DisplayName("分组不存在应抛出 BusinessException")
        void shouldThrowWhenWatchlistNotFound() {
            when(watchlistMapper.selectById(999L)).thenReturn(null);

            AddWatchlistItemRequest request = new AddWatchlistItemRequest();
            request.setSymbol("601179");

            assertThrows(BusinessException.class,
                    () -> watchlistService.addItem(100L, 999L, request));
        }

        @Test
        @DisplayName("不属于当前用户的分组应抛出 BusinessException")
        void shouldThrowWhenNotOwnedByUser() {
            WatchlistDO otherUserWatchlist = new WatchlistDO();
            otherUserWatchlist.setId(1L);
            otherUserWatchlist.setUserId(200L); // 不是 100L
            when(watchlistMapper.selectById(1L)).thenReturn(otherUserWatchlist);

            AddWatchlistItemRequest request = new AddWatchlistItemRequest();
            request.setSymbol("601179");

            assertThrows(BusinessException.class,
                    () -> watchlistService.addItem(100L, 1L, request));
        }

        @Test
        @DisplayName("symbol 应被 trim")
        void shouldTrimSymbol() {
            when(watchlistMapper.selectById(1L)).thenReturn(sampleWatchlist);
            when(watchlistItemMapper.selectCount(any())).thenReturn(0L);
            when(watchlistItemMapper.insert(any(WatchlistItemDO.class))).thenReturn(1);

            AddWatchlistItemRequest request = new AddWatchlistItemRequest();
            request.setSymbol("  601179  ");

            watchlistService.addItem(100L, 1L, request);

            verify(watchlistItemMapper).insert(argThat((WatchlistItemDO item) ->
                    "601179".equals(item.getSymbol())
            ));
        }
    }

    // =====================================================================
    // deleteItem - 删除自选股条目
    // =====================================================================

    @Nested
    @DisplayName("deleteItem - 删除自选股条目")
    class DeleteItem {

        @Test
        @DisplayName("正常删除应调用 deleteById")
        void shouldDeleteItem() {
            when(watchlistMapper.selectById(1L)).thenReturn(sampleWatchlist);
            WatchlistItemDO item = new WatchlistItemDO();
            item.setId(10L);
            item.setWatchlistId(1L);
            when(watchlistItemMapper.selectById(10L)).thenReturn(item);
            when(watchlistItemMapper.deleteById(10L)).thenReturn(1);

            watchlistService.deleteItem(100L, 1L, 10L);

            verify(watchlistItemMapper).deleteById(10L);
        }

        @Test
        @DisplayName("条目不存在应抛出 BusinessException")
        void shouldThrowWhenItemNotFound() {
            when(watchlistMapper.selectById(1L)).thenReturn(sampleWatchlist);
            when(watchlistItemMapper.selectById(999L)).thenReturn(null);

            assertThrows(BusinessException.class,
                    () -> watchlistService.deleteItem(100L, 1L, 999L));
        }

        @Test
        @DisplayName("条目不属于该分组应抛出 BusinessException")
        void shouldThrowWhenItemNotInWatchlist() {
            when(watchlistMapper.selectById(1L)).thenReturn(sampleWatchlist);
            WatchlistItemDO item = new WatchlistItemDO();
            item.setId(10L);
            item.setWatchlistId(2L); // 不同的 watchlistId
            when(watchlistItemMapper.selectById(10L)).thenReturn(item);

            assertThrows(BusinessException.class,
                    () -> watchlistService.deleteItem(100L, 1L, 10L));
        }
    }
}
