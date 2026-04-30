package com.aiinvestor.gateway.modules.watchlist.controller;

import com.aiinvestor.gateway.annotation.LoginRequired;
import com.aiinvestor.gateway.context.UserContext;
import com.aiinvestor.gateway.model.vo.ApiResult;
import com.aiinvestor.gateway.modules.watchlist.dto.AddWatchlistItemRequest;
import com.aiinvestor.gateway.modules.watchlist.dto.CreateWatchlistRequest;
import com.aiinvestor.gateway.modules.watchlist.service.WatchlistService;
import com.aiinvestor.gateway.modules.watchlist.vo.WatchlistVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 自选域控制器。
 */
@RestController
@RequestMapping("/api/v1/watchlists")
@LoginRequired
public class WatchlistController {

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    /**
     * 获取自选分组列表。
     */
    @GetMapping
    public ApiResult<List<WatchlistVO>> list() {
        return ApiResult.ok(
                watchlistService.listWatchlists(UserContext.getUserId(), UserContext.get().getRole())
        );
    }

    /**
     * 创建自选分组。
     */
    @PostMapping
    public ApiResult<WatchlistVO> create(@Valid @RequestBody CreateWatchlistRequest request) {
        return ApiResult.ok(
                watchlistService.createWatchlist(UserContext.getUserId(), UserContext.get().getRole(), request)
        );
    }

    /**
     * 添加自选股。
     */
    @PostMapping("/{id}/items")
    public ApiResult<Void> addItem(@PathVariable("id") Long watchlistId,
                                   @Valid @RequestBody AddWatchlistItemRequest request) {
        watchlistService.addItem(UserContext.getUserId(), watchlistId, request);
        return ApiResult.ok(null);
    }

    /**
     * 删除自选股。
     */
    @DeleteMapping("/{id}/items/{itemId}")
    public ApiResult<Void> deleteItem(@PathVariable("id") Long watchlistId,
                                      @PathVariable("itemId") Long itemId) {
        watchlistService.deleteItem(UserContext.getUserId(), watchlistId, itemId);
        return ApiResult.ok(null);
    }
}
