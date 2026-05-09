package com.aiinvestor.gateway.modules.market.service;

import com.aiinvestor.gateway.modules.market.vo.MarketQuoteVO;
import com.aiinvestor.gateway.modules.market.vo.HotNewsItemVO;
import com.aiinvestor.gateway.modules.market.vo.MarketStockListItemVO;
import com.aiinvestor.gateway.modules.market.vo.MarketStockPageVO;
import com.aiinvestor.gateway.modules.market.dao.entity.StockDO;
import com.aiinvestor.gateway.modules.market.dao.mapper.StockMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Python 行情客户端。
 * 统一通过 Python 侧车查询实时行情和市场列表。
 */
@Service
public class PythonMarketClient {

    private static final Logger log = LoggerFactory.getLogger(PythonMarketClient.class);

    private final WebClient pythonAiWebClient;
    private final StockMapper stockMapper;

    public PythonMarketClient(WebClient pythonAiWebClient, StockMapper stockMapper) {
        this.pythonAiWebClient = pythonAiWebClient;
        this.stockMapper = stockMapper;
    }

    /**
     * 批量获取行情。
     */
    public List<MarketQuoteVO> fetchQuotes(List<String> symbols) {
        if (symbols.isEmpty()) {
            return List.of();
        }

        JsonNode response = pythonAiWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/ai/v1/util/market/quotes")
                        .queryParam("symbols", String.join(",", symbols))
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        List<MarketQuoteVO> result = new ArrayList<>();
        if (response == null || !response.has("data")) {
            return result;
        }

        for (JsonNode item : response.path("data").path("quotes")) {
            if (!"ok".equalsIgnoreCase(item.path("status").asText())) {
                continue;
            }
            result.add(new MarketQuoteVO(
                    item.path("symbol").asText(),
                    item.path("name").asText(),
                    decimalOf(item.path("lastPrice")),
                    decimalOf(item.path("changePercent")),
                    decimalOf(item.path("changeAmount")),
                    decimalOf(item.path("highPrice")),
                    decimalOf(item.path("lowPrice")),
                    decimalOf(item.path("openPrice")),
                    decimalOf(item.path("volume")),
                    decimalOf(item.path("turnover")),
                    decimalOf(item.path("turnoverRate")),
                    decimalOf(item.path("amplitude")),
                    LocalDateTime.now()
            ));
        }
        return result;
    }

    /**
     * 获取股票分页列表。
     */
    public MarketStockPageVO fetchStocks(int page, int pageSize, String keyword) {
        JsonNode response = pythonAiWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/ai/v1/util/market/stocks")
                        .queryParam("page", page)
                        .queryParam("page_size", pageSize)
                        .queryParam("keyword", keyword == null ? "" : keyword.trim())
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (response == null || !response.has("data")) {
            return new MarketStockPageVO(page, pageSize, 0, List.of());
        }

        // 从数据库查询拼音信息
        Map<String, String> pinyinMap = loadPinyinMap();

        JsonNode data = response.path("data");
        List<MarketStockListItemVO> items = new ArrayList<>();
        for (JsonNode item : data.path("items")) {
            String symbol = item.path("symbol").asText();
            String name = item.path("name").asText();
            String pinyin = pinyinMap.get(symbol);
            if (pinyin == null || pinyin.isBlank()) {
                // 动态计算拼音首字母
                pinyin = PinyinHelper.toPinyinInitials(name).toLowerCase();
            }

            MarketStockListItemVO stockItem = new MarketStockListItemVO();
            stockItem.setSymbol(symbol);
            stockItem.setName(name);
            stockItem.setPinyin(pinyin);
            stockItem.setLastPrice(decimalOf(item.path("lastPrice")));
            stockItem.setChangePercent(decimalOf(item.path("changePercent")));
            stockItem.setChangeAmount(decimalOf(item.path("changeAmount")));
            stockItem.setVolume(decimalOf(item.path("volume")));
            stockItem.setTurnover(decimalOf(item.path("turnover")));
            stockItem.setTurnoverRate(decimalOf(item.path("turnoverRate")));
            stockItem.setHighPrice(decimalOf(item.path("highPrice")));
            stockItem.setLowPrice(decimalOf(item.path("lowPrice")));
            stockItem.setOpenPrice(decimalOf(item.path("openPrice")));
            stockItem.setTotalMarketValue(decimalOf(item.path("totalMarketValue")));
            stockItem.setCirculatingMarketValue(decimalOf(item.path("circulatingMarketValue")));
            stockItem.setSixtyDayChangePercent(decimalOf(item.path("sixtyDayChangePercent")));
            stockItem.setYearToDateChangePercent(decimalOf(item.path("yearToDateChangePercent")));
            items.add(stockItem);
        }

        return new MarketStockPageVO(
                data.path("page").asInt(page),
                data.path("pageSize").asInt(pageSize),
                data.path("total").asInt(items.size()),
                items
        );
    }

    /**
     * 获取热点新闻。
     */
    public List<HotNewsItemVO> fetchHotNews(int limit) {
        JsonNode response = pythonAiWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/ai/v1/util/news/hot")
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (response == null || !response.has("data")) {
            return List.of();
        }

        List<HotNewsItemVO> items = new ArrayList<>();
        for (JsonNode item : response.path("data").path("items")) {
            items.add(new HotNewsItemVO(
                    item.path("title").asText(),
                    item.path("summary").asText(),
                    item.path("tag").asText(),
                    item.path("source").asText(),
                    item.path("url").asText(),
                    item.path("publishedAt").isMissingNode() || item.path("publishedAt").isNull()
                            ? null
                            : item.path("publishedAt").asText()
            ));
        }
        return items;
    }

    /**
     * 获取 K 线/分时数据。
     */
    public List<Map<String, Object>> fetchKline(String symbol, String period, int days) {
        JsonNode response = pythonAiWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/kline")
                        .queryParam("symbol", symbol)
                        .queryParam("period", period)
                        .queryParam("days", days)
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (response == null || !response.has("data")) {
            return List.of();
        }

        JsonNode dataNode = response.path("data");
        JsonNode itemsNode = dataNode.path("items");
        if (!itemsNode.isArray()) {
            return List.of();
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonNode item : itemsNode) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", textOf(item, "date", "day", "时间", "tradeDate"));
            row.put("open", decimalOrNull(item, "open", "开盘", "openPrice"));
            row.put("close", decimalOrNull(item, "close", "收盘", "closePrice"));
            row.put("high", decimalOrNull(item, "high", "最高", "highPrice"));
            row.put("low", decimalOrNull(item, "low", "最低", "lowPrice"));
            row.put("volume", decimalOrNull(item, "volume", "成交量", "vol"));
            items.add(row);
        }
        return items;
    }

    /**
     * 从数据库加载拼音映射表。
     */
    private Map<String, String> loadPinyinMap() {
        Map<String, String> pinyinMap = new HashMap<>();
        List<StockDO> stocks = stockMapper.selectList(
                new LambdaQueryWrapper<StockDO>()
                        .isNotNull(StockDO::getPinyin)
                        .ne(StockDO::getPinyin, "")
                        .last("limit 5000")
        );
        for (StockDO stock : stocks) {
            pinyinMap.put(stock.getSymbol(), stock.getPinyin());
        }
        return pinyinMap;
    }

    private BigDecimal decimalOf(JsonNode node) {
        if (node == null || node.isNull() || node.asText().isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(node.asText());
        } catch (Exception e) {
            log.debug("BigDecimal 解析失败 value={}", node.asText(), e);
            return null;
        }
    }

    private BigDecimal decimalOrNull(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode child = node.path(fieldName);
            BigDecimal value = decimalOf(child);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String textOf(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode child = node.path(fieldName);
            if (!child.isMissingNode() && !child.isNull() && !child.asText().isBlank()) {
                return child.asText();
            }
        }
        return "";
    }
}
