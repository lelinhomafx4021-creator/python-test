package com.aiinvestor.gateway.modules.papertrading.service;

import com.aiinvestor.gateway.modules.market.service.MarketService;
import com.aiinvestor.gateway.modules.market.vo.MarketQuoteVO;
import com.aiinvestor.gateway.modules.papertrading.dao.entity.PaperAccountDO;
import com.aiinvestor.gateway.modules.papertrading.dao.entity.PaperCashTransferDO;
import com.aiinvestor.gateway.modules.papertrading.dao.entity.PaperDailyAssetDO;
import com.aiinvestor.gateway.modules.papertrading.dao.entity.PaperOrderDO;
import com.aiinvestor.gateway.modules.papertrading.dao.entity.PaperPositionDO;
import com.aiinvestor.gateway.modules.papertrading.dao.entity.PaperTradeDO;
import com.aiinvestor.gateway.modules.papertrading.dao.mapper.PaperAccountMapper;
import com.aiinvestor.gateway.modules.papertrading.dao.mapper.PaperCashTransferMapper;
import com.aiinvestor.gateway.modules.papertrading.dao.mapper.PaperDailyAssetMapper;
import com.aiinvestor.gateway.modules.papertrading.dao.mapper.PaperOrderMapper;
import com.aiinvestor.gateway.modules.papertrading.dao.mapper.PaperPositionMapper;
import com.aiinvestor.gateway.modules.papertrading.dao.mapper.PaperTradeMapper;
import com.aiinvestor.gateway.modules.papertrading.dto.CreatePaperCashTransferRequest;
import com.aiinvestor.gateway.modules.papertrading.dto.CreatePaperOrderRequest;
import com.aiinvestor.gateway.modules.papertrading.mq.TransactionEvent;
import com.aiinvestor.gateway.modules.papertrading.mq.TransactionEventProducer;
import com.aiinvestor.gateway.modules.papertrading.config.PaperTradingProperties;
import com.aiinvestor.gateway.modules.papertrading.vo.PaperAccountVO;
import com.aiinvestor.gateway.modules.papertrading.vo.PaperCashTransferVO;
import com.aiinvestor.gateway.modules.papertrading.vo.PaperOrderVO;
import com.aiinvestor.gateway.modules.papertrading.vo.PaperPortfolioSnapshotVO;
import com.aiinvestor.gateway.modules.papertrading.vo.PaperPositionVO;
import com.aiinvestor.gateway.modules.shared.config.AppCacheProperties;
import com.aiinvestor.gateway.modules.shared.exception.BusinessException;
import com.aiinvestor.gateway.modules.shared.service.UserNotificationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 模拟交易服务。
 */
@Service
public class PaperTradingService {

    private final PaperAccountMapper paperAccountMapper;
    private final PaperCashTransferMapper paperCashTransferMapper;
    private final PaperPositionMapper paperPositionMapper;
    private final PaperOrderMapper paperOrderMapper;
    private final PaperTradeMapper paperTradeMapper;
    private final PaperDailyAssetMapper paperDailyAssetMapper;
    private final MarketService marketService;
    private final StringRedisTemplate stringRedisTemplate;
    private final AppCacheProperties appCacheProperties;
    private final UserNotificationService userNotificationService;
    private final TransactionEventProducer transactionEventProducer;
    private final PaperTradingProperties paperTradingProperties;

    public PaperTradingService(PaperAccountMapper paperAccountMapper,
                               PaperCashTransferMapper paperCashTransferMapper,
                               PaperPositionMapper paperPositionMapper,
                               PaperOrderMapper paperOrderMapper,
                               PaperTradeMapper paperTradeMapper,
                               PaperDailyAssetMapper paperDailyAssetMapper,
                               MarketService marketService,
                               StringRedisTemplate stringRedisTemplate,
                               AppCacheProperties appCacheProperties,
                               UserNotificationService userNotificationService,
                               TransactionEventProducer transactionEventProducer,
                               PaperTradingProperties paperTradingProperties) {
        this.paperAccountMapper = paperAccountMapper;
        this.paperCashTransferMapper = paperCashTransferMapper;
        this.paperPositionMapper = paperPositionMapper;
        this.paperOrderMapper = paperOrderMapper;
        this.paperTradeMapper = paperTradeMapper;
        this.paperDailyAssetMapper = paperDailyAssetMapper;
        this.marketService = marketService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.appCacheProperties = appCacheProperties;
        this.userNotificationService = userNotificationService;
        this.transactionEventProducer = transactionEventProducer;
        this.paperTradingProperties = paperTradingProperties;
    }

    /**
     * 获取或初始化当前用户的模拟账户。
     */
    @Transactional
    public PaperAccountVO getOrCreateMyAccount(Long userId) {
        return toAccountVO(ensureAccount(userId));
    }

    /**
     * 获取持仓快照。
     * 可选强制刷新实时行情，并把最新结果回写到 Redis 与账户资产快照。
     */
    @Transactional
    public PaperPortfolioSnapshotVO getPortfolioSnapshot(Long userId, Long accountId, boolean refreshQuote) {
        PaperAccountDO account = getOwnedAccount(userId, accountId);
        List<PaperPositionDO> positionEntities = paperPositionMapper.selectList(
                new LambdaQueryWrapper<PaperPositionDO>()
                        .eq(PaperPositionDO::getAccountId, account.getId())
                        .orderByDesc(PaperPositionDO::getMarketValue)
        );
        return buildPortfolioSnapshot(account, positionEntities, refreshQuote);
    }

    /**
     * 获取持仓列表。
     */
    public List<PaperPositionVO> listPositions(Long userId, Long accountId) {
        return listPositions(userId, accountId, false);
    }

    /**
     * 获取持仓列表，可选择强制刷新实时行情。     */
    public List<PaperPositionVO> listPositions(Long userId, Long accountId, boolean refreshQuote) {
        return getPortfolioSnapshot(userId, accountId, refreshQuote).getPositions();
    }

    /**
     * 获取委托列表。
     */
    public List<PaperOrderVO> listOrders(Long userId, Long accountId) {
        PaperAccountDO account = getOwnedAccount(userId, accountId);
        return paperOrderMapper.selectList(
                        new LambdaQueryWrapper<PaperOrderDO>()
                                .eq(PaperOrderDO::getAccountId, account.getId())
                                .orderByDesc(PaperOrderDO::getId)
                ).stream()
                .map(item -> new PaperOrderVO(
                        item.getId(),
                        item.getSymbol(),
                        item.getSide(),
                        item.getOrderType(),
                        item.getOrderPrice(),
                        item.getOrderQty(),
                        item.getFilledQty(),
                        item.getOrderStatus(),
                        item.getCreatedAt()
                ))
                .toList();
    }

    /**
     * 管理员查看指定用户的账户快照。
     */
    @Transactional
    public PaperPortfolioSnapshotVO getPortfolioSnapshotForAdmin(Long targetUserId, boolean refreshQuote) {
        PaperAccountDO account = ensureAccount(targetUserId);
        List<PaperPositionDO> positionEntities = paperPositionMapper.selectList(
                new LambdaQueryWrapper<PaperPositionDO>()
                        .eq(PaperPositionDO::getAccountId, account.getId())
                        .orderByDesc(PaperPositionDO::getMarketValue)
        );
        return buildPortfolioSnapshot(account, positionEntities, refreshQuote);
    }

    /**
     * 管理员查看指定用户最近委托。
     */
    public List<PaperOrderVO> listOrdersForAdmin(Long targetUserId) {
        PaperAccountDO account = ensureAccount(targetUserId);
        return paperOrderMapper.selectList(
                        new LambdaQueryWrapper<PaperOrderDO>()
                                .eq(PaperOrderDO::getAccountId, account.getId())
                                .orderByDesc(PaperOrderDO::getId)
                                .last("limit 20")
                ).stream()
                .map(this::toOrderVO)
                .toList();
    }

    /**
     * 获取充值转账记录。
     */
    public List<PaperCashTransferVO> listCashTransfers(Long userId, Long accountId) {
        PaperAccountDO account = getOwnedAccount(userId, accountId);
        return paperCashTransferMapper.selectList(
                        new LambdaQueryWrapper<PaperCashTransferDO>()
                                .eq(PaperCashTransferDO::getAccountId, account.getId())
                                .orderByDesc(PaperCashTransferDO::getId)
                ).stream()
                .map(this::toCashTransferVO)
                .toList();
    }

    /**
     * 创建模拟充值并立即到账。
     */
    @Transactional
    public PaperCashTransferVO createCashTransfer(Long userId, CreatePaperCashTransferRequest request) {
        return createCashTransfer(userId, request, "deposit");
    }

    /**
     * 创建模拟提现并立即到账。
     */
    @Transactional
    public PaperCashTransferVO createWithdrawTransfer(Long userId, CreatePaperCashTransferRequest request) {
        return createCashTransfer(userId, request, "withdraw");
    }

    /**
     * 创建模拟资金变动记录。
     */
    @Transactional
    public PaperCashTransferVO createCashTransfer(Long userId, CreatePaperCashTransferRequest request, String direction) {
        PaperAccountDO account = getOwnedAccount(userId, request.getAccountId());
        BigDecimal amount = request.getAmount().setScale(2, RoundingMode.HALF_UP);

        if ("withdraw".equalsIgnoreCase(direction) && account.getCashBalance().compareTo(amount) < 0) {
            throw new BusinessException("可用资金不足，无法完成提现");
        }

        PaperCashTransferDO transfer = new PaperCashTransferDO();
        transfer.setAccountId(account.getId());
        transfer.setUserId(userId);
        transfer.setDirection(direction);
        transfer.setChannelCode("mock_gateway");
        transfer.setChannelName("演示支付通道");
        transfer.setOutTradeNo(("withdraw".equalsIgnoreCase(direction) ? "WITHDRAW-" : "TOPUP-") + System.currentTimeMillis());
        transfer.setChannelTradeNo("SUCCESS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        transfer.setAmount(amount);
        transfer.setStatus("success");
        transfer.setRemark(request.getRemark());
        transfer.setCreatedAt(LocalDateTime.now());
        transfer.setPaidAt(LocalDateTime.now());
        paperCashTransferMapper.insert(transfer);

        if ("withdraw".equalsIgnoreCase(direction)) {
            account.setCashBalance(account.getCashBalance().subtract(transfer.getAmount()));
        } else {
            account.setCashBalance(account.getCashBalance().add(transfer.getAmount()));
        }
        paperAccountMapper.updateById(account);
        refreshAccountSnapshot(account.getId());

        userNotificationService.createNotification(
                userId,
                "fund_transfer",
                "withdraw".equalsIgnoreCase(direction) ? "提现成功" : "充值到账",
                ("withdraw".equalsIgnoreCase(direction) ? "提现" : "充值")
                        + transfer.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString()
                        + " 元，资金已更新到交易账户。"
        );

        // 异步发送充值/提现事件（通过 MQ）
        PaperAccountDO updatedAccount = paperAccountMapper.selectById(account.getId());
        transactionEventProducer.send(new TransactionEvent(
                userId,
                "withdraw".equalsIgnoreCase(direction) ? "WITHDRAW" : "DEPOSIT",
                null,
                null,
                null,
                null,
                amount,
                updatedAccount.getCashBalance(),
                ("withdraw".equalsIgnoreCase(direction) ? "提现" : "充值") + " " + amount.toPlainString() + " 元",
                Instant.now()
        ));

        return toCashTransferVO(transfer);
    }

    /**
     * 提交模拟委托。
     */
    @Transactional
    public PaperOrderVO placeOrder(Long userId, CreatePaperOrderRequest request) {
        PaperAccountDO account = getOwnedAccount(userId, request.getAccountId());
        String lockKey = "paper:account:lock:" + account.getId();
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(
                lockKey,
                lockValue,
                Duration.ofSeconds(appCacheProperties.getAccountLockSeconds())
        );
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException("账户正在处理其他委托，请稍后重试");
        }

        try {
            if (request.getClientRequestId() != null && !request.getClientRequestId().isBlank()) {
                PaperOrderDO existed = paperOrderMapper.selectOne(
                        new LambdaQueryWrapper<PaperOrderDO>()
                                .eq(PaperOrderDO::getClientRequestId, request.getClientRequestId())
                                .last("limit 1")
                );
                if (existed != null) {
                    return toOrderVO(existed);
                }
            }

            MarketQuoteVO latestQuote = marketService.getLatestQuote(request.getSymbol().trim());
            BigDecimal dealPrice = latestQuote.getLastPrice();
            if (dealPrice == null || dealPrice.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("行情价格无效，无法完成模拟成交");
            }

            BigDecimal tradeAmount = dealPrice.multiply(BigDecimal.valueOf(request.getOrderQty()));
            String side = request.getSide().trim().toUpperCase();

            PaperOrderDO order = new PaperOrderDO();
            order.setAccountId(account.getId());
            order.setSymbol(request.getSymbol().trim());
            order.setSide(side);
            order.setOrderType(request.getOrderType() == null ? "market" : request.getOrderType());
            order.setOrderPrice(request.getOrderPrice() == null ? dealPrice : request.getOrderPrice());
            order.setOrderQty(request.getOrderQty());
            order.setFilledQty(request.getOrderQty());
            order.setOrderStatus("filled");
            order.setClientRequestId(request.getClientRequestId());
            order.setCreatedAt(LocalDateTime.now());
            paperOrderMapper.insert(order);

            if ("BUY".equals(side)) {
                handleBuy(account, order, dealPrice, tradeAmount);
            } else if ("SELL".equals(side)) {
                handleSell(account, order, dealPrice, tradeAmount);
            } else {
                throw new BusinessException("暂不支持的买卖方向：" + side);
            }

            createTrade(order, dealPrice, tradeAmount);
            refreshAccountSnapshot(account.getId());

            // 异步发送交易事件（通过 MQ）
            PaperAccountDO latestAccount = paperAccountMapper.selectById(account.getId());
            transactionEventProducer.send(new TransactionEvent(
                    userId,
                    "ORDER_FILLED",
                    order.getSymbol(),
                    side,
                    order.getOrderQty(),
                    dealPrice,
                    tradeAmount,
                    latestAccount.getCashBalance(),
                    ("BUY".equals(side) ? "买入" : "卖出") + order.getSymbol() + " " + order.getOrderQty() + "股",
                    Instant.now()
            ));

            return toOrderVO(order);
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    /**
     * 撤单。
     * 当前一期是“提交即成交”的轻撮合模型，所以只允许撤销未成交委托。
     */
    @Transactional
    public void cancelOrder(Long userId, Long orderId) {
        PaperOrderDO order = paperOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("委托不存在");
        }
        getOwnedAccount(userId, order.getAccountId());
        if (!"submitted".equalsIgnoreCase(order.getOrderStatus())) {
            throw new BusinessException("当前委托已成交或已撤销，无法再撤单");
        }
        order.setOrderStatus("cancelled");
        paperOrderMapper.updateById(order);

        // 异步发送撤单事件（通过 MQ）
        transactionEventProducer.send(new TransactionEvent(
                userId,
                "ORDER_CANCELLED",
                order.getSymbol(),
                order.getSide(),
                order.getOrderQty(),
                order.getOrderPrice(),
                null,
                null,
                "撤销" + order.getSymbol() + " 委托",
                Instant.now()
        ));
    }

    private void handleBuy(PaperAccountDO account, PaperOrderDO order, BigDecimal dealPrice, BigDecimal tradeAmount) {
        if (account.getCashBalance().compareTo(tradeAmount) < 0) {
            throw new BusinessException("账户可用资金不足");
        }

        account.setCashBalance(account.getCashBalance().subtract(tradeAmount));
        paperAccountMapper.updateById(account);

        PaperPositionDO position = paperPositionMapper.selectOne(
                new LambdaQueryWrapper<PaperPositionDO>()
                        .eq(PaperPositionDO::getAccountId, account.getId())
                        .eq(PaperPositionDO::getSymbol, order.getSymbol())
                        .last("limit 1")
        );

        if (position == null) {
            position = new PaperPositionDO();
            position.setAccountId(account.getId());
            position.setSymbol(order.getSymbol());
            position.setPositionQty(order.getOrderQty());
            position.setAvailableQty(order.getOrderQty());
            position.setAvgCost(dealPrice);
            position.setMarketValue(dealPrice.multiply(BigDecimal.valueOf(order.getOrderQty())));
            position.setFloatingPnl(BigDecimal.ZERO);
            paperPositionMapper.insert(position);
            return;
        }

        int newQty = position.getPositionQty() + order.getOrderQty();
        BigDecimal oldCost = position.getAvgCost().multiply(BigDecimal.valueOf(position.getPositionQty()));
        BigDecimal newCost = oldCost.add(tradeAmount)
                .divide(BigDecimal.valueOf(newQty), 4, RoundingMode.HALF_UP);
        position.setPositionQty(newQty);
        position.setAvailableQty(position.getAvailableQty() + order.getOrderQty());
        position.setAvgCost(newCost);
        position.setMarketValue(dealPrice.multiply(BigDecimal.valueOf(newQty)));
        position.setFloatingPnl(position.getMarketValue().subtract(newCost.multiply(BigDecimal.valueOf(newQty))));
        paperPositionMapper.updateById(position);
    }

    private void handleSell(PaperAccountDO account, PaperOrderDO order, BigDecimal dealPrice, BigDecimal tradeAmount) {
        PaperPositionDO position = paperPositionMapper.selectOne(
                new LambdaQueryWrapper<PaperPositionDO>()
                        .eq(PaperPositionDO::getAccountId, account.getId())
                        .eq(PaperPositionDO::getSymbol, order.getSymbol())
                        .last("limit 1")
        );
        if (position == null || position.getAvailableQty() < order.getOrderQty()) {
            throw new BusinessException("可卖持仓不足");
        }

        int remainQty = position.getPositionQty() - order.getOrderQty();
        account.setCashBalance(account.getCashBalance().add(tradeAmount));
        paperAccountMapper.updateById(account);

        if (remainQty <= 0) {
            paperPositionMapper.deleteById(position.getId());
            return;
        }

        position.setPositionQty(remainQty);
        position.setAvailableQty(position.getAvailableQty() - order.getOrderQty());
        position.setMarketValue(dealPrice.multiply(BigDecimal.valueOf(remainQty)));
        position.setFloatingPnl(position.getMarketValue().subtract(
                position.getAvgCost().multiply(BigDecimal.valueOf(remainQty))
        ));
        paperPositionMapper.updateById(position);
    }

    private void createTrade(PaperOrderDO order, BigDecimal dealPrice, BigDecimal tradeAmount) {
        PaperTradeDO trade = new PaperTradeDO();
        trade.setOrderId(order.getId());
        trade.setAccountId(order.getAccountId());
        trade.setSymbol(order.getSymbol());
        trade.setSide(order.getSide());
        trade.setTradePrice(dealPrice);
        trade.setTradeQty(order.getOrderQty());
        trade.setTradeAmount(tradeAmount);
        trade.setTradeTime(LocalDateTime.now());
        paperTradeMapper.insert(trade);
    }

    private PaperPortfolioSnapshotVO buildPortfolioSnapshot(PaperAccountDO account,
                                                            List<PaperPositionDO> positionEntities,
                                                            boolean refreshQuote) {
        if (positionEntities.isEmpty()) {
            refreshAccountSnapshot(account, BigDecimal.ZERO);
            return new PaperPortfolioSnapshotVO(toAccountVO(account), List.of(), LocalDateTime.now());
        }

        List<String> symbols = positionEntities.stream()
                .map(PaperPositionDO::getSymbol)
                .distinct()
                .toList();
        Map<String, MarketQuoteVO> quoteMap = (refreshQuote
                ? marketService.refreshQuotes(symbols)
                : marketService.getQuotes(symbols)).stream()
                .collect(Collectors.toMap(MarketQuoteVO::getSymbol, Function.identity(), (a, b) -> a));

        List<PaperPositionVO> positions = new ArrayList<>();
        BigDecimal totalMarketValue = BigDecimal.ZERO;

        for (PaperPositionDO item : positionEntities) {
            MarketQuoteVO quote = quoteMap.get(item.getSymbol());
            BigDecimal latestPrice = quote != null && quote.getLastPrice() != null
                    ? quote.getLastPrice()
                    : item.getAvgCost();
            BigDecimal latestMarketValue = latestPrice.multiply(BigDecimal.valueOf(item.getPositionQty()));
            BigDecimal latestFloatingPnl = latestMarketValue.subtract(
                    item.getAvgCost().multiply(BigDecimal.valueOf(item.getPositionQty()))
            );
            totalMarketValue = totalMarketValue.add(latestMarketValue);

            if (item.getMarketValue() == null
                    || item.getFloatingPnl() == null
                    || item.getMarketValue().compareTo(latestMarketValue) != 0
                    || item.getFloatingPnl().compareTo(latestFloatingPnl) != 0) {
                item.setMarketValue(latestMarketValue);
                item.setFloatingPnl(latestFloatingPnl);
                paperPositionMapper.updateById(item);
            }

            positions.add(new PaperPositionVO(
                    item.getId(),
                    item.getSymbol(),
                    quote == null || quote.getName() == null || quote.getName().isBlank() ? item.getSymbol() : quote.getName(),
                    item.getPositionQty(),
                    item.getAvailableQty(),
                    item.getAvgCost(),
                    latestMarketValue,
                    latestFloatingPnl,
                    latestPrice,
                    quote == null ? null : quote.getChangePercent(),
                    quote == null ? null : quote.getChangeAmount(),
                    quote == null ? null : quote.getQuoteTime()
            ));
        }

        refreshAccountSnapshot(account, totalMarketValue);
        return new PaperPortfolioSnapshotVO(toAccountVO(account), positions, LocalDateTime.now());
    }

    private void refreshAccountSnapshot(Long accountId) {
        PaperAccountDO account = paperAccountMapper.selectById(accountId);
        List<PaperPositionDO> positions = paperPositionMapper.selectList(
                new LambdaQueryWrapper<PaperPositionDO>().eq(PaperPositionDO::getAccountId, accountId)
        );
        BigDecimal marketValue = positions.stream()
                .map(PaperPositionDO::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        refreshAccountSnapshot(account, marketValue);
    }

    private void refreshAccountSnapshot(PaperAccountDO account, BigDecimal marketValue) {
        BigDecimal totalAsset = account.getCashBalance().add(marketValue);
        BigDecimal totalPnl = totalAsset.subtract(paperTradingProperties.getInitialCash());

        account.setTotalAsset(totalAsset);
        account.setTotalPnl(totalPnl);
        paperAccountMapper.updateById(account);

        PaperDailyAssetDO dailyAsset = paperDailyAssetMapper.selectOne(
                new LambdaQueryWrapper<PaperDailyAssetDO>()
                        .eq(PaperDailyAssetDO::getAccountId, account.getId())
                        .eq(PaperDailyAssetDO::getTradeDate, LocalDate.now())
                        .last("limit 1")
        );
        if (dailyAsset == null) {
            dailyAsset = new PaperDailyAssetDO();
            dailyAsset.setAccountId(account.getId());
            dailyAsset.setTradeDate(LocalDate.now());
            dailyAsset.setCashBalance(account.getCashBalance());
            dailyAsset.setMarketValue(marketValue);
            dailyAsset.setTotalAsset(totalAsset);
            dailyAsset.setDailyPnl(totalPnl);
            paperDailyAssetMapper.insert(dailyAsset);
            return;
        }
        dailyAsset.setCashBalance(account.getCashBalance());
        dailyAsset.setMarketValue(marketValue);
        dailyAsset.setTotalAsset(totalAsset);
        dailyAsset.setDailyPnl(totalPnl);
        paperDailyAssetMapper.updateById(dailyAsset);
    }

    private PaperAccountDO ensureAccount(Long userId) {
        PaperAccountDO account = paperAccountMapper.selectOne(
                new LambdaQueryWrapper<PaperAccountDO>()
                        .eq(PaperAccountDO::getUserId, userId)
                        .last("limit 1")
        );
        if (account != null) {
            return account;
        }

        PaperAccountDO created = new PaperAccountDO();
        created.setUserId(userId);
        created.setAccountNo("SIM-" + userId + "-" + System.currentTimeMillis());
        created.setCashBalance(paperTradingProperties.getInitialCash());
        created.setFrozenCash(BigDecimal.ZERO);
        created.setTotalAsset(paperTradingProperties.getInitialCash());
        created.setTotalPnl(BigDecimal.ZERO);
        created.setStatus("active");
        paperAccountMapper.insert(created);
        return created;
    }

    private PaperAccountDO getOwnedAccount(Long userId, Long accountId) {
        PaperAccountDO account = accountId == null ? ensureAccount(userId) : paperAccountMapper.selectById(accountId);
        if (account == null) {
            throw new BusinessException("模拟账户不存在");
        }
        if (!userId.equals(account.getUserId())) {
            throw new BusinessException("无权访问该模拟账户");
        }
        return account;
    }

    private PaperAccountVO toAccountVO(PaperAccountDO account) {
        return new PaperAccountVO(
                account.getId(),
                account.getAccountNo(),
                account.getCashBalance(),
                account.getFrozenCash(),
                account.getTotalAsset(),
                account.getTotalPnl(),
                account.getStatus()
        );
    }

    private PaperOrderVO toOrderVO(PaperOrderDO order) {
        return new PaperOrderVO(
                order.getId(),
                order.getSymbol(),
                order.getSide(),
                order.getOrderType(),
                order.getOrderPrice(),
                order.getOrderQty(),
                order.getFilledQty(),
                order.getOrderStatus(),
                order.getCreatedAt()
        );
    }

    private PaperCashTransferVO toCashTransferVO(PaperCashTransferDO transfer) {
        return new PaperCashTransferVO(
                transfer.getId(),
                transfer.getDirection(),
                transfer.getChannelCode(),
                transfer.getChannelName(),
                transfer.getOutTradeNo(),
                transfer.getChannelTradeNo(),
                transfer.getAmount(),
                transfer.getStatus(),
                transfer.getRemark(),
                transfer.getCreatedAt(),
                transfer.getPaidAt()
        );
    }
}
