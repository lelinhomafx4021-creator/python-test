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
import com.aiinvestor.gateway.modules.watchlist.vo.WatchlistItemVO;
import com.aiinvestor.gateway.modules.watchlist.vo.WatchlistVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 自选域服务。
 */
@Service
public class WatchlistService {

    private final WatchlistMapper watchlistMapper;
    private final WatchlistItemMapper watchlistItemMapper;
    private final MembershipService membershipService;
    private final MarketService marketService;

    public WatchlistService(WatchlistMapper watchlistMapper,
                            WatchlistItemMapper watchlistItemMapper,
                            MembershipService membershipService,
                            MarketService marketService) {
        this.watchlistMapper = watchlistMapper;
        this.watchlistItemMapper = watchlistItemMapper;
        this.membershipService = membershipService;
        this.marketService = marketService;
    }

    /**
     * 获取当前用户的自选分组。
     * 首次访问时会自动创建一个默认分组。
     */
    @Transactional
    public List<WatchlistVO> listWatchlists(Long userId, String role) {
        List<WatchlistDO> watchlists = watchlistMapper.selectList(
                new LambdaQueryWrapper<WatchlistDO>()
                        .eq(WatchlistDO::getUserId, userId)
                        .orderByAsc(WatchlistDO::getSortOrder, WatchlistDO::getId)
        );
        if (watchlists.isEmpty()) {
            createDefaultWatchlist(userId, role);
            watchlists = watchlistMapper.selectList(
                    new LambdaQueryWrapper<WatchlistDO>()
                            .eq(WatchlistDO::getUserId, userId)
                            .orderByAsc(WatchlistDO::getSortOrder, WatchlistDO::getId)
            );
        }
        List<Long> watchlistIds = watchlists.stream().map(WatchlistDO::getId).toList();
        List<WatchlistItemDO> items = watchlistIds.isEmpty()
                ? Collections.emptyList()
                : watchlistItemMapper.selectList(
                new LambdaQueryWrapper<WatchlistItemDO>()
                        .in(WatchlistItemDO::getWatchlistId, watchlistIds)
                        .orderByAsc(WatchlistItemDO::getSortOrder, WatchlistItemDO::getId)
        );

        Map<String, MarketQuoteVO> quoteMap = marketService.getQuotes(
                items.stream().map(WatchlistItemDO::getSymbol).distinct().toList()
        ).stream().collect(Collectors.toMap(MarketQuoteVO::getSymbol, Function.identity(), (a, b) -> a));

        Map<Long, List<WatchlistItemVO>> itemMap = items.stream()
                .collect(Collectors.groupingBy(
                        WatchlistItemDO::getWatchlistId,
                        Collectors.mapping(item -> {
                            MarketQuoteVO quote = quoteMap.get(item.getSymbol());
                            return new WatchlistItemVO(
                                    item.getId(),
                                    item.getSymbol(),
                                    quote == null ? item.getSymbol() : quote.getName(),
                                    item.getNote(),
                                    item.getAlertEnabled(),
                                    item.getSortOrder(),
                                    quote == null ? null : quote.getLastPrice(),
                                    quote == null ? null : quote.getChangePercent()
                            );
                        }, Collectors.toList())
                ));

        return watchlists.stream()
                .map(watchlist -> new WatchlistVO(
                        watchlist.getId(),
                        watchlist.getName(),
                        watchlist.getIsDefault(),
                        watchlist.getSortOrder(),
                        itemMap.getOrDefault(watchlist.getId(), List.of())
                ))
                .toList();
    }

    /**
     * 创建自选分组。
     */
    @Transactional
    public WatchlistVO createWatchlist(Long userId, String role, CreateWatchlistRequest request) {
        int limit = membershipService.getQuotaLimit(userId, role, "watchlist_count");
        int currentCount = watchlistMapper.selectCount(
                new LambdaQueryWrapper<WatchlistDO>().eq(WatchlistDO::getUserId, userId)
        ).intValue();
        if (currentCount >= limit) {
            throw new BusinessException("当前会员等级的自选分组数量已达上限");
        }

        WatchlistDO entity = new WatchlistDO();
        entity.setUserId(userId);
        entity.setName(request.getName().trim());
        entity.setIsDefault(Boolean.FALSE);
        entity.setSortOrder(currentCount + 1);
        watchlistMapper.insert(entity);

        membershipService.syncPermanentQuota(userId, role, "watchlist_count", currentCount + 1);
        return new WatchlistVO(entity.getId(), entity.getName(), entity.getIsDefault(), entity.getSortOrder(), List.of());
    }

    /**
     * 向分组中添加股票。
     */
    @Transactional
    public void addItem(Long userId, Long watchlistId, AddWatchlistItemRequest request) {
        WatchlistDO watchlist = getOwnedWatchlist(userId, watchlistId);
        long exists = watchlistItemMapper.selectCount(
                new LambdaQueryWrapper<WatchlistItemDO>()
                        .eq(WatchlistItemDO::getWatchlistId, watchlistId)
                        .eq(WatchlistItemDO::getSymbol, request.getSymbol().trim())
        );
        if (exists > 0) {
            throw new BusinessException("该股票已在当前自选分组中");
        }

        WatchlistItemDO entity = new WatchlistItemDO();
        entity.setWatchlistId(watchlist.getId());
        entity.setSymbol(request.getSymbol().trim());
        entity.setNote(request.getNote());
        entity.setAlertEnabled(Boolean.TRUE.equals(request.getAlertEnabled()));
        entity.setSortOrder(watchlistItemMapper.selectCount(
                new LambdaQueryWrapper<WatchlistItemDO>().eq(WatchlistItemDO::getWatchlistId, watchlistId)
        ).intValue() + 1);
        watchlistItemMapper.insert(entity);
    }

    /**
     * 删除自选股条目。
     */
    @Transactional
    public void deleteItem(Long userId, Long watchlistId, Long itemId) {
        getOwnedWatchlist(userId, watchlistId);
        WatchlistItemDO item = watchlistItemMapper.selectById(itemId);
        if (item == null || !watchlistId.equals(item.getWatchlistId())) {
            throw new BusinessException("自选股条目不存在");
        }
        watchlistItemMapper.deleteById(itemId);
    }

    private void createDefaultWatchlist(Long userId, String role) {
        int limit = membershipService.getQuotaLimit(userId, role, "watchlist_count");
        if (limit <= 0) {
            throw new BusinessException("当前会员等级暂不支持自选分组");
        }
        WatchlistDO entity = new WatchlistDO();
        entity.setUserId(userId);
        entity.setName("我的自选");
        entity.setIsDefault(Boolean.TRUE);
        entity.setSortOrder(1);
        watchlistMapper.insert(entity);
        membershipService.syncPermanentQuota(userId, role, "watchlist_count", 1);
    }

    private WatchlistDO getOwnedWatchlist(Long userId, Long watchlistId) {
        WatchlistDO watchlist = watchlistMapper.selectById(watchlistId);
        if (watchlist == null || !userId.equals(watchlist.getUserId())) {
            throw new BusinessException("自选分组不存在");
        }
        return watchlist;
    }
}
