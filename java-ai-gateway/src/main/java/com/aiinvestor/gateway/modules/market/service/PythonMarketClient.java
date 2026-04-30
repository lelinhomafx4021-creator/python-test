package com.aiinvestor.gateway.modules.market.service;

import com.aiinvestor.gateway.modules.market.vo.MarketQuoteVO;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Python 行情客户端。
 * 统一通过 Python 侧车查询实时行情，避免 Java 直接依赖第三方行情源。
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
