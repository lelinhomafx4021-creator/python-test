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
import com.aiinvestor.gateway.modules.shared.cache.RedisJsonCacheService;
import com.aiinvestor.gateway.modules.shared.config.AppCacheProperties;
import com.aiinvestor.gateway.modules.shared.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
public class MarketService {

    private static final String QUOTE_CACHE_PREFIX = "market:quote:";

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
     * 批量获取行情。
     */
    public List<MarketQuoteVO> getQuotes(List<String> symbols) {
        return loadQuotes(symbols, false);
    }

    /**
     * 强制刷新行情。
     */
    public List<MarketQuoteVO> refreshQuotes(List<String> symbols) {
        return loadQuotes(symbols, true);
    }

    /**
     * 获取单只股票最新行情。
     */
    public MarketQuoteVO getLatestQuote(String symbol) {
        return getQuotes(List.of(symbol)).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException("未查询到股票行情：" + symbol));
    }

    /**
     * 获取股票列表或搜索结果。
     */
    public MarketStockPageVO listStocks(int page, int pageSize, String keyword) {
        String trimmedKeyword = keyword == null ? "" : keyword.trim();
        
        // 先尝试通过 Python 服务获取
        MarketStockPageVO stockPage = pythonMarketClient.fetchStocks(page, pageSize, trimmedKeyword);
        
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
        
        // 批量缓存行情数据（减少 N+1）
        List<MarketQuoteVO> quotes = stockPage.getItems().stream()
                .map(item -> new MarketQuoteVO(
                        item.getSymbol(), item.getName(), item.getLastPrice(),
                        item.getChangePercent(), item.getChangeAmount(),
                        item.getHighPrice(), item.getLowPrice(), item.getOpenPrice(),
                        item.getVolume(), item.getTurnover(), item.getTurnoverRate(),
                        null, LocalDateTime.now()))
                .toList();
        batchCacheAndPersistQuotes(quotes);
        batchEnsureStockRecords(quotes);
        
        return stockPage;
    }
    
    /**
     * 通过拼音首字母搜索股票。
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
     */
    public List<HotNewsItemVO> listHotNews(int limit) {
        return pythonMarketClient.fetchHotNews(limit);
    }

    /**
     * 获取板块列表。
     */
    public List<Map<String, Object>> getKline(String symbol, String period, int days) {
        return pythonMarketClient.fetchKline(symbol, period, days);
    }

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
            if (cached != null) {
                result.put(symbol, cached);
            } else {
                missedSymbols.add(symbol);
            }
        }

        if (!missedSymbols.isEmpty()) {
            List<MarketQuoteVO> freshQuotes = pythonMarketClient.fetchQuotes(missedSymbols);
            for (MarketQuoteVO quote : freshQuotes) {
                result.put(quote.getSymbol(), quote);
            }
            batchCacheAndPersistQuotes(freshQuotes);
            batchEnsureStockRecords(freshQuotes);

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

    /** 批量缓存并持久化行情（1 次 Redis 批写 + N 次 DB upsert）。 */
    private void batchCacheAndPersistQuotes(List<MarketQuoteVO> quotes) {
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

    /** 批量确保股票记录存在（1 次批量查询 + 按需 insert/update）。 */
    private void batchEnsureStockRecords(List<MarketQuoteVO> quotes) {
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

    private void fillFromSnapshot(String symbol, Map<String, MarketQuoteVO> result) {
        MarketQuoteVO cached = redisJsonCacheService.get(cacheKey(symbol), MarketQuoteVO.class);
        if (cached != null) {
            result.put(symbol, cached);
            return;
        }

        MarketQuoteDO dbQuote = marketQuoteMapper.selectOne(
                new LambdaQueryWrapper<MarketQuoteDO>()
                        .eq(MarketQuoteDO::getSymbol, symbol)
                        .last("limit 1")
        );
        if (dbQuote != null) {
            result.put(symbol, toQuoteVO(dbQuote, findStockName(symbol)));
        }
    }

    private String findStockName(String symbol) {
        StockDO stock = stockMapper.selectOne(
                new LambdaQueryWrapper<StockDO>()
                        .eq(StockDO::getSymbol, symbol)
                        .last("limit 1")
        );
        return stock == null ? symbol : stock.getName();
    }

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

    private String resolveExchange(String symbol) {
        return symbol.startsWith("6") || symbol.startsWith("5") || symbol.startsWith("9") ? "SH" : "SZ";
    }

    private String cacheKey(String symbol) {
        return QUOTE_CACHE_PREFIX + symbol;
    }
}
