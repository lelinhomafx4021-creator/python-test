package com.aiinvestor.gateway.modules.market.service;

import com.aiinvestor.gateway.modules.market.vo.MarketQuoteVO;
import com.aiinvestor.gateway.modules.market.vo.HotNewsItemVO;
import com.aiinvestor.gateway.modules.market.vo.MarketStockListItemVO;
import com.aiinvestor.gateway.modules.market.vo.MarketStockPageVO;
import com.aiinvestor.gateway.modules.market.dao.entity.StockDO;
import com.aiinvestor.gateway.modules.market.dao.mapper.StockMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Python 行情客户端。
 * 统一通过 Python 侧车查询实时行情和市场列表。
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class PythonMarketClient {

    private final WebClient pythonAiWebClient;
    private final StockMapper stockMapper;

    /**
     * 批量获取行情。
     *
     * @param symbols 股票代码列表
     * @return 对应股票的最新行情视图对象列表，未查到或状态异常的股票会被过滤
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
     * <p>
     * 从 Python 行情服务拉取分页数据，并补充数据库中的拼音首字母信息，
     * 用于支持前端的拼音搜索功能。
     *
     * @param page     页码（从1开始）
     * @param pageSize 每页条数
     * @param keyword  搜索关键词（代码/名称/拼音），为空则返回全量
     * @return 分页股票列表，含行情摘要和拼音首字母
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
            stockItem.setPe(decimalOf(item.path("pe")));
            stockItem.setPb(decimalOf(item.path("pb")));
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
     *
     * @param limit 返回条数上限
     * @return 热点财经新闻条目列表，包含标题、摘要、来源、链接等
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
     *
     * @param symbol 6位股票代码
     * @param period 周期类型：daily / intraday_1d / intraday_5d
     * @param days   数据窗口大小
     * @return K 线数据点列表，每项为 date/open/close/high/low/volume 组成的 Map
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
     * 从数据库加载拼音映射表，用于生成股票名称拼音首字母。
     * <p>
     * 仅加载已存储了拼音的股票记录，最多 5000 条。
     *
     * @return 股票代码 → 拼音首字母的映射
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
