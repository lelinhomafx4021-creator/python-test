package com.aiinvestor.gateway.modules.papertrading.service;

import com.aiinvestor.gateway.modules.market.service.MarketService;
import com.aiinvestor.gateway.modules.market.vo.MarketQuoteVO;
import com.aiinvestor.gateway.modules.papertrading.dao.entity.PaperAccountDO;
import com.aiinvestor.gateway.modules.papertrading.dao.entity.PaperDailyAssetDO;
import com.aiinvestor.gateway.modules.papertrading.dao.entity.PaperOrderDO;
import com.aiinvestor.gateway.modules.papertrading.dao.entity.PaperPositionDO;
import com.aiinvestor.gateway.modules.papertrading.dao.entity.PaperTradeDO;
import com.aiinvestor.gateway.modules.papertrading.dao.mapper.PaperAccountMapper;
import com.aiinvestor.gateway.modules.papertrading.dao.mapper.PaperDailyAssetMapper;
import com.aiinvestor.gateway.modules.papertrading.dao.mapper.PaperOrderMapper;
import com.aiinvestor.gateway.modules.papertrading.dao.mapper.PaperPositionMapper;
import com.aiinvestor.gateway.modules.papertrading.dao.mapper.PaperTradeMapper;
import com.aiinvestor.gateway.modules.papertrading.dto.CreatePaperOrderRequest;
import com.aiinvestor.gateway.modules.papertrading.vo.PaperAccountVO;
import com.aiinvestor.gateway.modules.papertrading.vo.PaperOrderVO;
import com.aiinvestor.gateway.modules.papertrading.vo.PaperPositionVO;
import com.aiinvestor.gateway.modules.shared.config.AppCacheProperties;
import com.aiinvestor.gateway.modules.shared.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 模拟交易服务。
 */
@Service
public class PaperTradingService {

    private static final BigDecimal INITIAL_CASH = new BigDecimal("1000000");

    private final PaperAccountMapper paperAccountMapper;
    private final PaperPositionMapper paperPositionMapper;
    private final PaperOrderMapper paperOrderMapper;
    private final PaperTradeMapper paperTradeMapper;
    private final PaperDailyAssetMapper paperDailyAssetMapper;
    private final MarketService marketService;
    private final StringRedisTemplate stringRedisTemplate;
    private final AppCacheProperties appCacheProperties;

    public PaperTradingService(PaperAccountMapper paperAccountMapper,
                               PaperPositionMapper paperPositionMapper,
                               PaperOrderMapper paperOrderMapper,
                               PaperTradeMapper paperTradeMapper,
                               PaperDailyAssetMapper paperDailyAssetMapper,
                               MarketService marketService,
                               StringRedisTemplate stringRedisTemplate,
                               AppCacheProperties appCacheProperties) {
        this.paperAccountMapper = paperAccountMapper;
        this.paperPositionMapper = paperPositionMapper;
        this.paperOrderMapper = paperOrderMapper;
        this.paperTradeMapper = paperTradeMapper;
        this.paperDailyAssetMapper = paperDailyAssetMapper;
        this.marketService = marketService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.appCacheProperties = appCacheProperties;
    }

    /**
     * 获取或初始化当前用户的模拟账户。
     */
    @Transactional
    public PaperAccountVO getOrCreateMyAccount(Long userId) {
        return toAccountVO(ensureAccount(userId));
    }

    /**
     * 获取持仓列表。
     */
    public List<PaperPositionVO> listPositions(Long userId, Long accountId) {
        PaperAccountDO account = getOwnedAccount(userId, accountId);
        return paperPositionMapper.selectList(
                        new LambdaQueryWrapper<PaperPositionDO>()
                                .eq(PaperPositionDO::getAccountId, account.getId())
                                .orderByDesc(PaperPositionDO::getMarketValue)
                ).stream()
                .map(item -> {
                    MarketQuoteVO quote = marketService.getLatestQuote(item.getSymbol());
                    BigDecimal latestPrice = quote.getLastPrice() == null ? item.getAvgCost() : quote.getLastPrice();
                    BigDecimal latestMarketValue = latestPrice.multiply(BigDecimal.valueOf(item.getPositionQty()));
                    BigDecimal latestFloatingPnl = latestMarketValue.subtract(
                            item.getAvgCost().multiply(BigDecimal.valueOf(item.getPositionQty()))
                    );
                    return new PaperPositionVO(
                            item.getId(),
                            item.getSymbol(),
                            quote.getName(),
                            item.getPositionQty(),
                            item.getAvailableQty(),
                            item.getAvgCost(),
                            latestMarketValue,
                            latestFloatingPnl
                    );
                })
                .toList();
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

    private void refreshAccountSnapshot(Long accountId) {
        PaperAccountDO account = paperAccountMapper.selectById(accountId);
        List<PaperPositionDO> positions = paperPositionMapper.selectList(
                new LambdaQueryWrapper<PaperPositionDO>().eq(PaperPositionDO::getAccountId, accountId)
        );
        BigDecimal marketValue = positions.stream()
                .map(PaperPositionDO::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAsset = account.getCashBalance().add(marketValue);
        BigDecimal totalPnl = totalAsset.subtract(INITIAL_CASH);

        account.setTotalAsset(totalAsset);
        account.setTotalPnl(totalPnl);
        paperAccountMapper.updateById(account);

        PaperDailyAssetDO dailyAsset = paperDailyAssetMapper.selectOne(
                new LambdaQueryWrapper<PaperDailyAssetDO>()
                        .eq(PaperDailyAssetDO::getAccountId, accountId)
                        .eq(PaperDailyAssetDO::getTradeDate, LocalDate.now())
                        .last("limit 1")
        );
        if (dailyAsset == null) {
            dailyAsset = new PaperDailyAssetDO();
            dailyAsset.setAccountId(accountId);
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
        created.setCashBalance(INITIAL_CASH);
        created.setFrozenCash(BigDecimal.ZERO);
        created.setTotalAsset(INITIAL_CASH);
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
}
