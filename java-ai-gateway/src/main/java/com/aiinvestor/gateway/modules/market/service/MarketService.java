package com.aiinvestor.gateway.modules.market.service;

import com.aiinvestor.gateway.modules.market.dao.entity.MarketQuoteDO;
import com.aiinvestor.gateway.modules.market.dao.entity.SectorDO;
import com.aiinvestor.gateway.modules.market.dao.entity.StockDO;
import com.aiinvestor.gateway.modules.market.dao.mapper.MarketQuoteMapper;
import com.aiinvestor.gateway.modules.market.dao.mapper.SectorMapper;
import com.aiinvestor.gateway.modules.market.dao.mapper.StockMapper;
import com.aiinvestor.gateway.modules.market.vo.MarketQuoteVO;
import com.aiinvestor.gateway.modules.market.vo.HotNewsItemVO;
import com.aiinvestor.gateway.modules.market.vo.MarketStockListItemVO;
import com.aiinvestor.gateway.modules.market.vo.MarketStockPageVO;
import com.aiinvestor.gateway.modules.market.vo.SectorVO;
import com.aiinvestor.gateway.modules.shared.cache.RedisKeys;
import com.aiinvestor.gateway.modules.shared.cache.RedisJsonCacheService;
import com.aiinvestor.gateway.modules.shared.config.AppCacheProperties;
import com.aiinvestor.gateway.modules.shared.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行情域服务。
 * 读取顺序为 Redis -> Python 实时行情 -> MySQL 快照。
 */
@Service
@Slf4j
public class MarketService {

    private final PythonMarketClient pythonMarketClient;
    private final MarketQuoteMapper marketQuoteMapper;
    private final SectorMapper sectorMapper;
    private final StockMapper stockMapper;
    private final RedisJsonCacheService redisJsonCacheService;
    private final AppCacheProperties appCacheProperties;

    public MarketService(PythonMarketClient pythonMarketClient,
                         MarketQuoteMapper marketQuoteMapper,
                         SectorMapper sectorMapper,
                         StockMapper stockMapper,
                         RedisJsonCacheService redisJsonCacheService,
                         AppCacheProperties appCacheProperties) {
        this.pythonMarketClient = pythonMarketClient;
        this.marketQuoteMapper = marketQuoteMapper;
        this.sectorMapper = sectorMapper;
        this.stockMapper = stockMapper;
        this.redisJsonCacheService = redisJsonCacheService;
        this.appCacheProperties = appCacheProperties;
    }

    /**
     * 批量获取行情（优先读缓存）。
     * <p>
     * 读取顺序：Redis 缓存 → Python 实时行情 → MySQL 快照。
     *
     * @param symbols 股票代码列表
     * @return 对应股票的最新行情视图列表
     */
    public List<MarketQuoteVO> getQuotes(List<String> symbols) {
        return loadQuotes(symbols, false);
    }

    /**
     * 强制刷新行情（跳过缓存，直连 Python 实时拉取）。
     *
     * @param symbols 股票代码列表
     * @return 对应股票的最新行情视图列表，结果同时回写缓存和数据库
     */
    public List<MarketQuoteVO> refreshQuotes(List<String> symbols) {
        return loadQuotes(symbols, true);
    }

    /**
     * 获取单只股票最新行情。
     * <p>
     * 若查不到行情数据则抛出业务异常。
     *
     * @param symbol 股票代码
     * @return 该股票的最新行情视图
     * @throws BusinessException 当股票行情不存在时
     */
    public MarketQuoteVO getLatestQuote(String symbol) {
        return getQuotes(List.of(symbol)).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException("未查询到股票行情：" + symbol));
    }

    /**
     * 获取股票列表或搜索结果。
     * <p>
     * 先从 Python 服务获取分页结果，再合并数据库中的拼音匹配结果。
     * 返回时同时批量缓存行情并确保股票主数据记录存在。
     *
     * @param page     页码（从1开始）
     * @param pageSize 每页条数
     * @param keyword  搜索关键词（代码/名称/拼音首字母）；为空则返回全量
     * @return 分页股票列表
     */
    public MarketStockPageVO listStocks(int page, int pageSize, String keyword) {
        String trimmedKeyword = keyword == null ? "" : keyword.trim();
        
        MarketStockPageVO stockPage;
        try {
            stockPage = pythonMarketClient.fetchStocks(page, pageSize, trimmedKeyword);
        } catch (Exception e) {
            log.warn("Python market stock service unavailable, fallback to database. page={}, pageSize={}, keyword={}",
                    page, pageSize, trimmedKeyword, e);
            MarketStockPageVO fallbackPage = listStocksFromDatabase(page, pageSize, trimmedKeyword);
            enrichStockItemsWithQuotes(fallbackPage.getItems());
            return fallbackPage;
        }

        if (stockPage == null) {
            stockPage = new MarketStockPageVO(page, pageSize, 0, new ArrayList<>());
        } else if (stockPage.getItems() == null) {
            stockPage.setItems(new ArrayList<>());
        }
        
        // 如果有搜索关键词，同时从数据库搜索拼音匹配的股票
        if (!trimmedKeyword.isEmpty()) {
            List<MarketStockListItemVO> dbResults = searchByPinyin(trimmedKeyword, pageSize);
            // 合并数据库搜索结果（去重）
            java.util.Set<String> existingSymbols = new java.util.HashSet<>();
            for (MarketStockListItemVO item : stockPage.getItems()) {
                existingSymbols.add(item.getSymbol());
            }
            for (MarketStockListItemVO item : dbResults) {
                if (!existingSymbols.contains(item.getSymbol())) {
                    stockPage.getItems().add(item);
                    existingSymbols.add(item.getSymbol());
                }
            }
            stockPage.setTotal(stockPage.getItems().size());
        }
        
        enrichStockItemsWithQuotes(stockPage.getItems());
        
        return stockPage;
    }

    private void enrichStockItemsWithQuotes(List<MarketStockListItemVO> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        List<String> symbols = items.stream()
                .map(MarketStockListItemVO::getSymbol)
                .filter(symbol -> symbol != null && !symbol.isBlank())
                .distinct()
                .toList();
        if (symbols.isEmpty()) {
            return;
        }

        Map<String, MarketQuoteVO> quoteMap = new LinkedHashMap<>();
        for (MarketQuoteVO quote : getQuotes(symbols)) {
            if (quote != null && quote.getSymbol() != null && !quote.getSymbol().isBlank() && hasQuoteData(quote)) {
                quoteMap.put(quote.getSymbol(), quote);
            }
        }

        for (MarketStockListItemVO item : items) {
            if (item.getSymbol() == null) {
                continue;
            }
            MarketQuoteVO quote = quoteMap.get(item.getSymbol());
            if (quote != null) {
                applyQuoteToStockItem(item, quote);
            }
        }
    }

    private void applyQuoteToStockItem(MarketStockListItemVO item, MarketQuoteVO quote) {
        if ((item.getName() == null || item.getName().isBlank())
                && quote.getName() != null && !quote.getName().isBlank()) {
            item.setName(quote.getName());
        }
        if (quote.getLastPrice() != null) {
            item.setLastPrice(quote.getLastPrice());
        }
        if (quote.getChangePercent() != null) {
            item.setChangePercent(quote.getChangePercent());
        }
        if (quote.getChangeAmount() != null) {
            item.setChangeAmount(quote.getChangeAmount());
        }
        if (quote.getVolume() != null) {
            item.setVolume(quote.getVolume());
        }
        if (quote.getTurnover() != null) {
            item.setTurnover(quote.getTurnover());
        }
        if (quote.getTurnoverRate() != null) {
            item.setTurnoverRate(quote.getTurnoverRate());
        }
        if (quote.getHighPrice() != null) {
            item.setHighPrice(quote.getHighPrice());
        }
        if (quote.getLowPrice() != null) {
            item.setLowPrice(quote.getLowPrice());
        }
        if (quote.getOpenPrice() != null) {
            item.setOpenPrice(quote.getOpenPrice());
        }
    }
    
    /**
     * 通过拼音首字母搜索股票。
     * <p>
     * 从数据库查询拼音、名称或代码匹配且状态为 active 的股票记录，
     * 动态计算拼音首字母供前端展示。
     *
     * @param keyword 搜索关键词
     * @param limit   最大返回条数
     * @return 匹配的股票列表项
     */
    private List<MarketStockListItemVO> searchByPinyin(String keyword, int limit) {
        String pinyinKeyword = keyword.toUpperCase();
        List<MarketStockListItemVO> results = new ArrayList<>();
        
        // 从数据库查询拼音匹配的股票
        List<StockDO> stocks = stockMapper.selectList(
                new LambdaQueryWrapper<StockDO>()
                        .and(w -> w
                                .like(StockDO::getPinyin, pinyinKeyword)
                                .or()
                                .like(StockDO::getName, keyword)
                                .or()
                                .like(StockDO::getSymbol, keyword)
                        )
                        .eq(StockDO::getStatus, "active")
                        .last("limit " + limit)
        );
        
        for (StockDO stock : stocks) {
            String pinyin = stock.getPinyin();
            if (pinyin == null || pinyin.isBlank()) {
                pinyin = PinyinHelper.toPinyinInitials(stock.getName()).toLowerCase();
            }
            
            MarketStockListItemVO item = new MarketStockListItemVO();
            item.setSymbol(stock.getSymbol());
            item.setName(stock.getName());
            item.setPinyin(pinyin);
            results.add(item);
        }
        
        return results;
     }

    /**
     * 获取热点新闻。
     *
     * @param limit 返回条数上限
     * @return 热点财经新闻列表
     */
    public List<HotNewsItemVO> listHotNews(int limit) {
        try {
            return pythonMarketClient.fetchHotNews(limit);
        } catch (Exception e) {
            log.warn("Python hot news service unavailable, return empty list. limit={}", limit, e);
            return List.of();
        }
    }

    /**
     * 获取 K 线/分时数据。
     *
     * @param symbol 6位股票代码
     * @param period 周期类型：daily / intraday_1d / intraday_5d
     * @param days   数据窗口大小
     * @return K 线数据点列表
     */
    public List<Map<String, Object>> getKline(String symbol, String period, int days) {
        try {
            return pythonMarketClient.fetchKline(symbol, period, days);
        } catch (Exception e) {
            log.warn("Python kline service unavailable, return empty list. symbol={}, period={}, days={}",
                    symbol, period, days, e);
            return List.of();
        }
    }

    /**
     * 获取板块列表。
     *
     * @return 所有行业板块，按排序权重升序
     */
    public List<SectorVO> listSectors() {
        return sectorMapper.selectList(
                        new LambdaQueryWrapper<SectorDO>().orderByAsc(SectorDO::getSortOrder, SectorDO::getId)
                ).stream()
                .map(item -> new SectorVO(
                        item.getSectorCode(),
                        item.getSectorName(),
                        item.getParentCode(),
                        item.getSortOrder()
                ))
                .toList();
    }

    /**
     * 核心行情加载逻辑：缓存 → Python实时 → 数据库快照 三级读取。
     *
     * @param symbols      股票代码列表
     * @param forceRefresh 是否强制跳过缓存直接拉取实时数据
     * @return 对应股票的最新行情视图列表
     */
    private List<MarketQuoteVO> loadQuotes(List<String> symbols, boolean forceRefresh) {
        List<String> normalizedSymbols = symbols.stream()
                .map(String::trim)
                .filter(symbol -> !symbol.isBlank())
                .distinct()
                .toList();
        if (normalizedSymbols.isEmpty()) {
            return List.of();
        }

        Map<String, MarketQuoteVO> result = new LinkedHashMap<>();
        List<String> missedSymbols = new ArrayList<>();

        for (String symbol : normalizedSymbols) {
            MarketQuoteVO cached = forceRefresh
                    ? null
                    : redisJsonCacheService.get(cacheKey(symbol), MarketQuoteVO.class);
            if (hasQuoteData(cached)) {
                result.put(symbol, cached);
            } else {
                missedSymbols.add(symbol);
            }
        }

        if (!missedSymbols.isEmpty()) {
            List<MarketQuoteVO> freshQuotes;
            try {
                freshQuotes = pythonMarketClient.fetchQuotes(missedSymbols);
            } catch (Exception e) {
                log.warn("Python market quote service unavailable, fallback to snapshots. symbols={}", missedSymbols, e);
                freshQuotes = List.of();
            }
            List<MarketQuoteVO> usableFreshQuotes = freshQuotes.stream()
                    .filter(this::hasQuoteData)
                    .toList();
            for (MarketQuoteVO quote : usableFreshQuotes) {
                result.put(quote.getSymbol(), quote);
            }
            batchCacheAndPersistQuotes(usableFreshQuotes);
            batchEnsureStockRecords(usableFreshQuotes);

            for (String symbol : missedSymbols) {
                if (result.containsKey(symbol)) {
                    continue;
                }
                fillFromSnapshot(symbol, result);
            }
        }

        return normalizedSymbols.stream()
                .map(result::get)
                .filter(item -> item != null)
                .toList();
    }

    /**
     * 单条行情缓存并持久化：写入 Redis 缓存 + MySQL upsert。
     *
     * @param quote 行情视图对象
     */
    private void cacheAndPersistQuote(MarketQuoteVO quote) {
        redisJsonCacheService.set(
                cacheKey(quote.getSymbol()),
                quote,
                Duration.ofSeconds(appCacheProperties.getMarketQuoteTtlSeconds())
        );

        MarketQuoteDO entity = new MarketQuoteDO();
        entity.setSymbol(quote.getSymbol());
        entity.setLastPrice(quote.getLastPrice());
        entity.setChangePct(quote.getChangePercent());
        entity.setChangeAmount(quote.getChangeAmount());
        entity.setHighPrice(quote.getHighPrice());
        entity.setLowPrice(quote.getLowPrice());
        entity.setOpenPrice(quote.getOpenPrice());
        entity.setVolume(quote.getVolume());
        entity.setTurnover(quote.getTurnover());
        entity.setTurnoverRate(quote.getTurnoverRate());
        entity.setAmplitude(quote.getAmplitude());
        entity.setQuoteTime(quote.getQuoteTime() == null ? LocalDateTime.now() : quote.getQuoteTime());
        marketQuoteMapper.upsert(entity);
    }

    /**
     * 确保股票主数据记录存在。
     * <p>
     * 若股票已存在但名称变更，则更新名称并重新生成拼音；
     * 若不存在则自动创建新记录（交易所根据代码前缀推断）。
     *
     * @param quote 行情视图对象，包含股票代码和名称
     */
    private void ensureStockRecord(MarketQuoteVO quote) {
        StockDO stock = stockMapper.selectOne(
                new LambdaQueryWrapper<StockDO>()
                        .eq(StockDO::getSymbol, quote.getSymbol())
                        .last("limit 1")
        );
        if (stock != null) {
            if (quote.getName() != null && !quote.getName().isBlank() && !quote.getName().equals(stock.getName())) {
                stock.setName(quote.getName());
                // 名称变更时重新生成拼音
                stock.setPinyin(PinyinHelper.toPinyinInitials(quote.getName()).toLowerCase());
                stockMapper.updateById(stock);
            }
            return;
        }

        StockDO created = new StockDO();
        created.setSymbol(quote.getSymbol());
        created.setName(quote.getName() == null || quote.getName().isBlank() ? quote.getSymbol() : quote.getName());
        created.setExchange(resolveExchange(quote.getSymbol()));
        created.setMarket("A");
        created.setStatus("active");
        // 自动生成拼音首字母
        if (created.getName() != null && !created.getName().isBlank()) {
            created.setPinyin(PinyinHelper.toPinyinInitials(created.getName()).toLowerCase());
        }
        stockMapper.insert(created);
    }

    /**
     * 批量缓存并持久化行情。
     * <p>
     * 1 次 Redis 批量写入 + N 次数据库 upsert，减少网络往返。
     *
     * @param quotes 行情视图对象列表
     */
    private void batchCacheAndPersistQuotes(List<MarketQuoteVO> quotes) {
        if (quotes.isEmpty()) {
            return;
        }
        Duration ttl = Duration.ofSeconds(appCacheProperties.getMarketQuoteTtlSeconds());
        Map<String, Object> redisEntries = new LinkedHashMap<>();
        List<MarketQuoteDO> dbEntities = new ArrayList<>();

        for (MarketQuoteVO quote : quotes) {
            redisEntries.put(cacheKey(quote.getSymbol()), quote);

            MarketQuoteDO entity = new MarketQuoteDO();
            entity.setSymbol(quote.getSymbol());
            entity.setLastPrice(quote.getLastPrice());
            entity.setChangePct(quote.getChangePercent());
            entity.setChangeAmount(quote.getChangeAmount());
            entity.setHighPrice(quote.getHighPrice());
            entity.setLowPrice(quote.getLowPrice());
            entity.setOpenPrice(quote.getOpenPrice());
            entity.setVolume(quote.getVolume());
            entity.setTurnover(quote.getTurnover());
            entity.setTurnoverRate(quote.getTurnoverRate());
            entity.setAmplitude(quote.getAmplitude());
            entity.setQuoteTime(quote.getQuoteTime() == null ? LocalDateTime.now() : quote.getQuoteTime());
            dbEntities.add(entity);
        }

        redisJsonCacheService.setAll(redisEntries, ttl);
        for (MarketQuoteDO entity : dbEntities) {
            marketQuoteMapper.upsert(entity);
        }
    }

    /**
     * 批量确保股票主数据记录存在。
     * <p>
     * 1 次批量查询现有记录 + 按需批量 insert/update，避免 N+1 问题。
     *
     * @param quotes 行情视图对象列表
     */
    private void batchEnsureStockRecords(List<MarketQuoteVO> quotes) {
        if (quotes.isEmpty()) {
            return;
        }
        List<String> symbols = quotes.stream().map(MarketQuoteVO::getSymbol).toList();
        List<StockDO> existingStocks = stockMapper.selectList(
                new LambdaQueryWrapper<StockDO>().in(StockDO::getSymbol, symbols)
        );
        Map<String, StockDO> existingMap = new LinkedHashMap<>();
        for (StockDO s : existingStocks) {
            existingMap.put(s.getSymbol(), s);
        }

        List<StockDO> toInsert = new ArrayList<>();
        List<StockDO> toUpdate = new ArrayList<>();

        for (MarketQuoteVO quote : quotes) {
            StockDO existing = existingMap.get(quote.getSymbol());
            if (existing != null) {
                if (quote.getName() != null && !quote.getName().isBlank() && !quote.getName().equals(existing.getName())) {
                    existing.setName(quote.getName());
                    existing.setPinyin(PinyinHelper.toPinyinInitials(quote.getName()).toLowerCase());
                    toUpdate.add(existing);
                }
            } else {
                StockDO created = new StockDO();
                created.setSymbol(quote.getSymbol());
                String name = (quote.getName() == null || quote.getName().isBlank()) ? quote.getSymbol() : quote.getName();
                created.setName(name);
                created.setExchange(resolveExchange(quote.getSymbol()));
                created.setMarket("A");
                created.setStatus("active");
                if (!name.isBlank()) {
                    created.setPinyin(PinyinHelper.toPinyinInitials(name).toLowerCase());
                }
                toInsert.add(created);
            }
        }

        for (StockDO s : toInsert) {
            stockMapper.insert(s);
        }
        for (StockDO s : toUpdate) {
            stockMapper.updateById(s);
        }
    }

    /**
     * 从缓存或数据库快照回填单只股票行情。
     * <p>
     * 当 Python 实时行情拉取失败时，降级使用本地存储的数据。
     *
     * @param symbol 股票代码
     * @param result 结果集（会被修改，填充查到的行情）
     */
    private void fillFromSnapshot(String symbol, Map<String, MarketQuoteVO> result) {
        MarketQuoteVO cached = redisJsonCacheService.get(cacheKey(symbol), MarketQuoteVO.class);
        if (hasQuoteData(cached)) {
            result.put(symbol, cached);
            return;
        }

        MarketQuoteDO dbQuote = marketQuoteMapper.selectOne(
                new LambdaQueryWrapper<MarketQuoteDO>()
                        .eq(MarketQuoteDO::getSymbol, symbol)
                        .last("limit 1")
        );
        if (dbQuote != null && hasQuoteData(dbQuote)) {
            result.put(symbol, toQuoteVO(dbQuote, findStockName(symbol)));
        }
    }

    /**
     * 根据股票代码查找股票名称。
     *
     * @param symbol 股票代码
     * @return 股票名称，若未找到则返回代码本身
     */
    private String findStockName(String symbol) {
        StockDO stock = stockMapper.selectOne(
                new LambdaQueryWrapper<StockDO>()
                        .eq(StockDO::getSymbol, symbol)
                        .last("limit 1")
        );
        return stock == null ? symbol : stock.getName();
    }

    /**
     * 将数据库实体转换为视图对象。
     *
     * @param entity 数据库行情快照实体
     * @param name   股票名称
     * @return 前端可用的行情视图对象
     */
    private MarketQuoteVO toQuoteVO(MarketQuoteDO entity, String name) {
        return new MarketQuoteVO(
                entity.getSymbol(),
                name,
                entity.getLastPrice(),
                entity.getChangePct(),
                entity.getChangeAmount(),
                entity.getHighPrice(),
                entity.getLowPrice(),
                entity.getOpenPrice(),
                entity.getVolume(),
                entity.getTurnover(),
                entity.getTurnoverRate(),
                entity.getAmplitude(),
                entity.getQuoteTime()
        );
    }

    /**
     * 根据股票代码推断所属交易所。
     * <p>
     * 规则：以 6/5/9 开头的代码属于上海交易所(SH)，其余属于深圳交易所(SZ)。
     *
     * @param symbol 股票代码
     * @return 交易所标识：SH 或 SZ
     */
    private String resolveExchange(String symbol) {
        return symbol.startsWith("6") || symbol.startsWith("5") || symbol.startsWith("9") ? "SH" : "SZ";
    }

    private MarketStockPageVO listStocksFromDatabase(int page, int pageSize, String keyword) {
        int total = stockMapper.selectCount(stockQuery(keyword)).intValue();
        int offset = Math.max(0, (page - 1) * pageSize);
        List<StockDO> stocks = stockMapper.selectList(
                stockQuery(keyword)
                        .orderByAsc(StockDO::getSymbol)
                        .last("limit " + pageSize + " offset " + offset)
        );
        List<MarketStockListItemVO> items = stocks.stream()
                .map(stock -> {
                    MarketStockListItemVO item = new MarketStockListItemVO();
                    item.setSymbol(stock.getSymbol());
                    item.setName(stock.getName());
                    item.setPinyin(stock.getPinyin());
                    return item;
                })
                .toList();
        return new MarketStockPageVO(page, pageSize, total, items);
    }

    private LambdaQueryWrapper<StockDO> stockQuery(String keyword) {
        LambdaQueryWrapper<StockDO> wrapper = new LambdaQueryWrapper<StockDO>()
                .eq(StockDO::getStatus, "active");
        if (keyword != null && !keyword.isBlank()) {
            String trimmed = keyword.trim();
            wrapper.and(w -> w
                    .like(StockDO::getSymbol, trimmed)
                    .or()
                    .like(StockDO::getName, trimmed)
                    .or()
                    .like(StockDO::getPinyin, trimmed.toUpperCase())
            );
        }
        return wrapper;
    }

    private boolean hasQuoteData(MarketQuoteVO quote) {
        return quote != null && (
                quote.getLastPrice() != null
                        || quote.getChangePercent() != null
                        || quote.getChangeAmount() != null
                        || quote.getHighPrice() != null
                        || quote.getLowPrice() != null
                        || quote.getOpenPrice() != null
                        || quote.getVolume() != null
                        || quote.getTurnover() != null
                        || quote.getTurnoverRate() != null
                        || quote.getAmplitude() != null
        );
    }

    private boolean hasQuoteData(MarketQuoteDO quote) {
        return quote != null && (
                quote.getLastPrice() != null
                        || quote.getChangePct() != null
                        || quote.getChangeAmount() != null
                        || quote.getHighPrice() != null
                        || quote.getLowPrice() != null
                        || quote.getOpenPrice() != null
                        || quote.getVolume() != null
                        || quote.getTurnover() != null
                        || quote.getTurnoverRate() != null
                        || quote.getAmplitude() != null
        );
    }

    /**
     * 构建 Redis 缓存键。
     *
     * @param symbol 股票代码
     * @return 格式为 "market:quote:{symbol}" 的缓存键
     */
    private String cacheKey(String symbol) {
        return RedisKeys.marketQuote(symbol);
    }
}
