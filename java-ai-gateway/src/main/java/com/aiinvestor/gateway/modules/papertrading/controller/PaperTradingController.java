package com.aiinvestor.gateway.modules.papertrading.controller;

import com.aiinvestor.gateway.modules.shared.annotation.LoginRequired;
import com.aiinvestor.gateway.modules.shared.context.UserContext;
import com.aiinvestor.gateway.modules.shared.vo.ApiResult;
import com.aiinvestor.gateway.modules.papertrading.dto.CreatePaperCashTransferRequest;
import com.aiinvestor.gateway.modules.papertrading.dto.CreatePaperOrderRequest;
import com.aiinvestor.gateway.modules.papertrading.service.PaperTradingService;
import com.aiinvestor.gateway.modules.papertrading.vo.PaperAccountVO;
import com.aiinvestor.gateway.modules.papertrading.vo.PaperCashTransferVO;
import com.aiinvestor.gateway.modules.papertrading.vo.PaperOrderVO;
import com.aiinvestor.gateway.modules.papertrading.vo.PaperPortfolioSnapshotVO;
import com.aiinvestor.gateway.modules.papertrading.vo.PaperPositionVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

/**
 * 模拟交易控制器。
 */
@RestController
@RequestMapping("/api/v1/paper")
@LoginRequired
@Tag(name = "模拟交易", description = "模拟账户管理、委托下单、持仓查询、资金充值/提现")
public class PaperTradingController {

    private final PaperTradingService paperTradingService;

    public PaperTradingController(PaperTradingService paperTradingService) {
        this.paperTradingService = paperTradingService;
    }

    /** 获取当前用户模拟账户。 */
    @Operation(summary = "获取我的模拟账户", description = "获取或自动创建当前用户的模拟交易账户")
    @GetMapping("/accounts/me")
    public ApiResult<PaperAccountVO> myAccount() {
        return ApiResult.ok(paperTradingService.getOrCreateMyAccount(UserContext.getUserId()));
    }

    /** 获取持仓列表。 */
    @Operation(summary = "获取持仓列表", description = "查询指定模拟账户的持仓明细")
    @GetMapping("/accounts/{id}/positions")
    public ApiResult<List<PaperPositionVO>> positions(
            @Parameter(description = "模拟账户ID", required = true)
            @PathVariable("id") Long accountId,
                                                      @RequestParam(value = "refresh", defaultValue = "false") boolean refresh) {
        return ApiResult.ok(paperTradingService.listPositions(UserContext.getUserId(), accountId, refresh));
    }

    @Operation(summary = "获取投资组合快照", description = "获取账户资产总览、收益统计等快照数据")
    @GetMapping("/accounts/{id}/snapshot")
    public ApiResult<PaperPortfolioSnapshotVO> snapshot(
            @Parameter(description = "模拟账户ID", required = true)
            @PathVariable("id") Long accountId,
                                                        @RequestParam(value = "refresh", defaultValue = "false") boolean refresh) {
        return ApiResult.ok(paperTradingService.getPortfolioSnapshot(UserContext.getUserId(), accountId, refresh));
    }

    /** 获取委托列表。 */
    @Operation(summary = "获取委托列表", description = "查询指定模拟账户的所有委托记录")
    @GetMapping("/accounts/{id}/orders")
    public ApiResult<List<PaperOrderVO>> orders(
            @Parameter(description = "模拟账户ID", required = true)
            @PathVariable("id") Long accountId) {
        return ApiResult.ok(paperTradingService.listOrders(UserContext.getUserId(), accountId));
    }

    /** 获取充值转账记录。 */
    @Operation(summary = "获取资金流水", description = "查询指定模拟账户的充值/提现转账记录")
    @GetMapping("/accounts/{id}/transfers")
    public ApiResult<List<PaperCashTransferVO>> transfers(
            @Parameter(description = "模拟账户ID", required = true)
            @PathVariable("id") Long accountId) {
        return ApiResult.ok(paperTradingService.listCashTransfers(UserContext.getUserId(), accountId));
    }

    /** 提交委托。 */
    @Operation(summary = "提交委托", description = "向模拟账户提交买入/卖出委托订单")
    @PostMapping("/orders")
    public ApiResult<PaperOrderVO> placeOrder(@Valid @RequestBody CreatePaperOrderRequest request) {
        return ApiResult.ok(paperTradingService.placeOrder(UserContext.getUserId(), request));
    }

    /** 创建充值到账记录。 */
    @Operation(summary = "模拟充值", description = "向模拟账户充值资金（用于模拟交易）")
    @PostMapping("/transfers/deposit")
    public ApiResult<PaperCashTransferVO> deposit(@Valid @RequestBody CreatePaperCashTransferRequest request) {
        return ApiResult.ok(paperTradingService.createCashTransfer(UserContext.getUserId(), request));
    }

    /** 创建提现到账记录。 */
    @Operation(summary = "模拟提现", description = "从模拟账户提取资金")
    @PostMapping("/transfers/withdraw")
    public ApiResult<PaperCashTransferVO> withdraw(@Valid @RequestBody CreatePaperCashTransferRequest request) {
        return ApiResult.ok(paperTradingService.createWithdrawTransfer(UserContext.getUserId(), request));
    }

    /** 撤单。 */
    @Operation(summary = "撤单", description = "撤销指定的未成交委托")
    @PostMapping("/orders/{id}/cancel")
    public ApiResult<Void> cancel(
            @Parameter(description = "委托订单ID", required = true)
            @PathVariable("id") Long orderId) {
        paperTradingService.cancelOrder(UserContext.getUserId(), orderId);
        return ApiResult.ok(null);
    }
}
