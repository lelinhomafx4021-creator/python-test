package com.aiinvestor.gateway.modules.watchlist.controller;

import com.aiinvestor.gateway.modules.shared.annotation.LoginRequired;
import com.aiinvestor.gateway.modules.shared.context.UserContext;
import com.aiinvestor.gateway.modules.shared.vo.ApiResult;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

/**
 * 自选域控制器。
 */
@RestController
@RequestMapping("/api/v1/watchlists")
@LoginRequired
@Tag(name = "自选股", description = "自选股分组管理、添加/删除自选股")
public class WatchlistController {

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    /**
     * 获取当前用户的自选分组列表。
     * <p>
     * 首次访问时会自动创建默认分组"我的自选"。每个分组内含股票条目及实时行情。
     *
     * @return 自选分组视图列表，含分组信息和其中的股票条目
     */
    @Operation(summary = "获取自选分组列表", description = "获取当前用户的所有自选股分组")
    @GetMapping
    public ApiResult<List<WatchlistVO>> list() {
        return ApiResult.ok(
                watchlistService.listWatchlists(UserContext.getUserId(), UserContext.get().getRole())
        );
    }

    /**
     * 创建自选分组。
     * <p>
     * 受会员等级配额限制，超出限额会拒绝创建。
     *
     * @param request 包含分组名称的创建请求
     * @return 新建的分组视图（不含股票条目）
     */
    @Operation(summary = "创建自选分组", description = "新建一个自选股分组")
    @PostMapping
    public ApiResult<WatchlistVO> create(@Valid @RequestBody CreateWatchlistRequest request) {
        return ApiResult.ok(
                watchlistService.createWatchlist(UserContext.getUserId(), UserContext.get().getRole(), request)
        );
    }

    /**
     * 向指定分组中添加一只自选股。
     * <p>
     * 重复添加同一股票会被拒绝。
     *
     * @param watchlistId 自选分组 ID
     * @param request     包含股票代码和可选备注的添加请求
     * @return 空响应（成功即表示已添加）
     */
    @Operation(summary = "添加自选股", description = "向指定分组中添加一只股票")
    @PostMapping("/{id}/items")
    public ApiResult<Void> addItem(
            @Parameter(description = "自选分组ID", required = true)
            @PathVariable("id") Long watchlistId,
                                   @Valid @RequestBody AddWatchlistItemRequest request) {
        watchlistService.addItem(UserContext.getUserId(), watchlistId, request);
        return ApiResult.ok(null);
    }

    /**
     * 从指定分组中删除一只自选股。
     *
     * @param watchlistId 自选分组 ID
     * @param itemId      自选股条目 ID
     * @return 空响应（成功即表示已删除）
     */
    @Operation(summary = "删除自选股", description = "从指定分组中移除一只股票")
    @DeleteMapping("/{id}/items/{itemId}")
    public ApiResult<Void> deleteItem(
            @Parameter(description = "自选分组ID", required = true)
            @PathVariable("id") Long watchlistId,
            @Parameter(description = "自选股记录ID", required = true)
            @PathVariable("itemId") Long itemId) {
        watchlistService.deleteItem(UserContext.getUserId(), watchlistId, itemId);
        return ApiResult.ok(null);
    }
}
