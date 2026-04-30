package com.aiinvestor.gateway.modules.papertrading.controller;

import com.aiinvestor.gateway.annotation.LoginRequired;
import com.aiinvestor.gateway.context.UserContext;
import com.aiinvestor.gateway.model.vo.ApiResult;
import com.aiinvestor.gateway.modules.papertrading.dto.CreatePaperOrderRequest;
import com.aiinvestor.gateway.modules.papertrading.service.PaperTradingService;
import com.aiinvestor.gateway.modules.papertrading.vo.PaperAccountVO;
import com.aiinvestor.gateway.modules.papertrading.vo.PaperOrderVO;
import com.aiinvestor.gateway.modules.papertrading.vo.PaperPositionVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模拟交易控制器。
 */
@RestController
@RequestMapping("/api/v1/paper")
@LoginRequired
public class PaperTradingController {

    private final PaperTradingService paperTradingService;

    public PaperTradingController(PaperTradingService paperTradingService) {
        this.paperTradingService = paperTradingService;
    }

    /**
     * 获取当前用户模拟账户。
     */
    @GetMapping("/accounts/me")
    public ApiResult<PaperAccountVO> myAccount() {
        return ApiResult.ok(paperTradingService.getOrCreateMyAccount(UserContext.getUserId()));
    }

    /**
     * 获取持仓列表。
     */
    @GetMapping("/accounts/{id}/positions")
    public ApiResult<List<PaperPositionVO>> positions(@PathVariable("id") Long accountId) {
        return ApiResult.ok(paperTradingService.listPositions(UserContext.getUserId(), accountId));
    }

    /**
     * 获取委托列表。
     */
    @GetMapping("/accounts/{id}/orders")
    public ApiResult<List<PaperOrderVO>> orders(@PathVariable("id") Long accountId) {
        return ApiResult.ok(paperTradingService.listOrders(UserContext.getUserId(), accountId));
    }

    /**
     * 提交委托。
     */
    @PostMapping("/orders")
    public ApiResult<PaperOrderVO> placeOrder(@Valid @RequestBody CreatePaperOrderRequest request) {
        return ApiResult.ok(paperTradingService.placeOrder(UserContext.getUserId(), request));
    }

    /**
     * 撤单。
     */
    @PostMapping("/orders/{id}/cancel")
    public ApiResult<Void> cancel(@PathVariable("id") Long orderId) {
        paperTradingService.cancelOrder(UserContext.getUserId(), orderId);
        return ApiResult.ok(null);
    }
}
