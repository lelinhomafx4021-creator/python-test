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
 * <p>
 * 提供模拟交易的全部 HTTP 接口，包括：
 * <ul>
 *   <li>账户管理：获取/自动创建我的模拟账户</li>
 *   <li>持仓查询：查看持仓明细和投资组合快照</li>
 *   <li>委托下单：提交买入/卖出委托、撤销未成交委托</li>
 *   <li>资金划转：模拟充值和提现操作</li>
 * </ul>
 * 所有接口均需登录（@LoginRequired），通过 UserContext 获取当前用户身份。
 * </p>
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

    /**
     * 获取当前用户的模拟交易账户。
     * <p>
     * 如果用户还没有模拟账户，系统会自动创建一个初始资金为 100 万的模拟账户。
     * </p>
     *
     * @return 当前用户的模拟账户视图，包含资金、盈亏等摘要信息
     */
    @Operation(summary = "获取我的模拟账户", description = "获取或自动创建当前用户的模拟交易账户")
    @GetMapping("/accounts/me")
    public ApiResult<PaperAccountVO> myAccount() {
        return ApiResult.ok(paperTradingService.getOrCreateMyAccount(UserContext.getUserId()));
    }

    /**
     * 查询指定模拟账户的所有持仓明细。
     *
     * @param accountId 模拟账户 ID，须属于当前用户
     * @param refresh   是否强制刷新行情数据，false 优先返回缓存，true 实时查询
     * @return 持仓视图列表，每只股票一条记录，含成本、市值、浮动盈亏和最新行情
     */
    @Operation(summary = "获取持仓列表", description = "查询指定模拟账户的持仓明细")
    @GetMapping("/accounts/{id}/positions")
    public ApiResult<List<PaperPositionVO>> positions(
            @Parameter(description = "模拟账户ID", required = true)
            @PathVariable("id") Long accountId,
            @Parameter(description = "是否强制刷新行情数据，默认 false")
            @RequestParam(value = "refresh", defaultValue = "false") boolean refresh) {
        return ApiResult.ok(paperTradingService.listPositions(UserContext.getUserId(), accountId, refresh));
    }

    /**
     * 获取模拟账户的投资组合快照。
     * <p>
     * 将账户总览和全部持仓合并为一次请求返回，方便前端首页面板一次性刷新。
     * </p>
     *
     * @param accountId 模拟账户 ID，须属于当前用户
     * @param refresh   是否强制刷新行情数据，false 优先返回缓存
     * @return 投资组合快照，包含账户摘要、持仓列表和刷新时间
     */
    @Operation(summary = "获取投资组合快照", description = "获取账户资产总览、收益统计等快照数据")
    @GetMapping("/accounts/{id}/snapshot")
    public ApiResult<PaperPortfolioSnapshotVO> snapshot(
            @Parameter(description = "模拟账户ID", required = true)
            @PathVariable("id") Long accountId,
            @Parameter(description = "是否强制刷新行情数据，默认 false")
            @RequestParam(value = "refresh", defaultValue = "false") boolean refresh) {
        return ApiResult.ok(paperTradingService.getPortfolioSnapshot(UserContext.getUserId(), accountId, refresh));
    }

    /**
     * 查询指定模拟账户的所有委托订单。
     * <p>
     * 返回 ALL 类型的委托记录，包括待成交、部分成交、全部成交、已撤销等所有状态。
     * </p>
     *
     * @param accountId 模拟账户 ID，须属于当前用户
     * @return 委托订单视图列表，按创建时间降序排列
     */
    @Operation(summary = "获取委托列表", description = "查询指定模拟账户的所有委托记录")
    @GetMapping("/accounts/{id}/orders")
    public ApiResult<List<PaperOrderVO>> orders(
            @Parameter(description = "模拟账户ID", required = true)
            @PathVariable("id") Long accountId) {
        return ApiResult.ok(paperTradingService.listOrders(UserContext.getUserId(), accountId));
    }

    /**
     * 查询指定模拟账户的资金划转记录。
     * <p>
     * 返回该账户的全部充值/提现流水，按时间倒序。用于资金流水页面的列表展示。
     * </p>
     *
     * @param accountId 模拟账户 ID，须属于当前用户
     * @return 资金划转记录视图列表，按创建时间降序
     */
    @Operation(summary = "获取资金流水", description = "查询指定模拟账户的充值/提现转账记录")
    @GetMapping("/accounts/{id}/transfers")
    public ApiResult<List<PaperCashTransferVO>> transfers(
            @Parameter(description = "模拟账户ID", required = true)
            @PathVariable("id") Long accountId) {
        return ApiResult.ok(paperTradingService.listCashTransfers(UserContext.getUserId(), accountId));
    }

    /**
     * 提交模拟交易委托订单。
     * <p>
     * 执行买入或卖出操作，委托类型支持市价单和限价单。
     * 市价单会立即按当前行情撮合成交，限价单需等待行情达到指定价格。
     * 通过 clientRequestId 实现幂等，重复提交相同 ID 的请求不会创建新订单。
     * </p>
     *
     * @param request 创建委托请求，包含账户 ID、股票代码、方向、类型、价格、数量、幂等号
     * @return 创建的委托订单视图，包含委托 ID 和当前状态
     */
    @Operation(summary = "提交委托", description = "向模拟账户提交买入/卖出委托订单")
    @PostMapping("/orders")
    public ApiResult<PaperOrderVO> placeOrder(@Valid @RequestBody CreatePaperOrderRequest request) {
        return ApiResult.ok(paperTradingService.placeOrder(UserContext.getUserId(), request));
    }

    /**
     * 模拟充值，向指定账户入金。
     * <p>
     * 充值成功后，账户可用现金余额增加，并记录一条 DEPOSIT 方向的划转记录。
     * 模拟充值不涉及真实资金流转，仅用于学习测试。
     * </p>
     *
     * @param request 充值请求，包含账户 ID、金额、备注
     * @return 充值划转记录视图，包含流水号、金额、状态
     */
    @Operation(summary = "模拟充值", description = "向模拟账户充值资金（用于模拟交易）")
    @PostMapping("/transfers/deposit")
    public ApiResult<PaperCashTransferVO> deposit(@Valid @RequestBody CreatePaperCashTransferRequest request) {
        return ApiResult.ok(paperTradingService.createCashTransfer(UserContext.getUserId(), request));
    }

    /**
     * 模拟提现，从指定账户出金。
     * <p>
     * 提现成功后，账户可用现金余额减少，并记录一条 WITHDRAW 方向的划转记录。
     * 提现金额不能超过可用余额。
     * </p>
     *
     * @param request 提现请求，包含账户 ID、金额、备注
     * @return 提现划转记录视图，包含流水号、金额、状态
     */
    @Operation(summary = "模拟提现", description = "从模拟账户提取资金")
    @PostMapping("/transfers/withdraw")
    public ApiResult<PaperCashTransferVO> withdraw(@Valid @RequestBody CreatePaperCashTransferRequest request) {
        return ApiResult.ok(paperTradingService.createWithdrawTransfer(UserContext.getUserId(), request));
    }

    /**
     * 撤销指定的未成交委托订单。
     * <p>
     * 只有状态为 PENDING（待成交）或 PARTIAL（部分成交）的委托才可撤销。
     * 撤单后，之前冻结的现金或持仓会解冻归还到可用余额/可卖数量中。
     * </p>
     *
     * @param orderId 委托订单 ID，须属于当前用户的账户
     */
    @Operation(summary = "撤单", description = "撤销指定的未成交委托")
    @PostMapping("/orders/{id}/cancel")
    public ApiResult<Void> cancel(
            @Parameter(description = "委托订单ID", required = true)
            @PathVariable("id") Long orderId) {
        paperTradingService.cancelOrder(UserContext.getUserId(), orderId);
        return ApiResult.ok(null);
    }
}
