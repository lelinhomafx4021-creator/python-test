package com.aiinvestor.gateway.modules.market.service;

import com.aiinvestor.gateway.modules.market.vo.MarketQuoteVO;
import com.aiinvestor.gateway.modules.market.vo.HotNewsItemVO;
import com.aiinvestor.gateway.modules.market.vo.MarketStockListItemVO;
import com.aiinvestor.gateway.modules.market.vo.MarketStockPageVO;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Python 行情客户端。
 * 统一通过 Python 侧车查询实时行情和市场列表。
 */
@Service
public class PythonMarketClient {

    private final WebClient pythonAiWebClient;

    public PythonMarketClient(WebClient pythonAiWebClient) {
        this.pythonAiWebClient = pythonAiWebClient;
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

        JsonNode data = response.path("data");
        List<MarketStockListItemVO> items = new ArrayList<>();
        for (JsonNode item : data.path("items")) {
            items.add(new MarketStockListItemVO(
                    item.path("symbol").asText(),
                    item.path("name").asText(),
                    decimalOf(item.path("lastPrice")),
                    decimalOf(item.path("changePercent")),
                    decimalOf(item.path("changeAmount")),
                    decimalOf(item.path("volume")),
                    decimalOf(item.path("turnover")),
                    decimalOf(item.path("turnoverRate")),
                    decimalOf(item.path("highPrice")),
                    decimalOf(item.path("lowPrice")),
                    decimalOf(item.path("openPrice")),
                    decimalOf(item.path("totalMarketValue")),
                    decimalOf(item.path("circulatingMarketValue")),
                    decimalOf(item.path("sixtyDayChangePercent")),
                    decimalOf(item.path("yearToDateChangePercent"))
            ));
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

    private BigDecimal decimalOf(JsonNode node) {
        if (node == null || node.isNull() || node.asText().isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(node.asText());
        } catch (Exception ignored) {
            return null;
        }
    }
}
